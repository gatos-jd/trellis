/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trellisldp.http.impl;

import static java.net.URI.create;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static javax.ws.rs.core.HttpHeaders.IF_UNMODIFIED_SINCE;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.Resource.SpecialResources.DELETED_RESOURCE;
import static org.trellisldp.api.Resource.SpecialResources.MISSING_RESOURCE;
import static org.trellisldp.api.TrellisUtils.TRELLIS_DATA_PREFIX;
import static org.trellisldp.api.TrellisUtils.getContainer;
import static org.trellisldp.http.core.HttpConstants.ACL;
import static org.trellisldp.http.core.HttpConstants.ACL_QUERY_PARAM;
import static org.trellisldp.http.impl.HttpUtils.checkRequiredPreconditions;
import static org.trellisldp.http.impl.HttpUtils.closeDataset;
import static org.trellisldp.http.impl.HttpUtils.ldpResourceTypes;
import static org.trellisldp.http.impl.HttpUtils.skolemizeQuads;
import static org.trellisldp.vocabulary.Trellis.PreferAccessControl;
import static org.trellisldp.vocabulary.Trellis.PreferUserManaged;
import static org.trellisldp.vocabulary.Trellis.UnsupportedInteractionModel;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDFSyntax;
import org.slf4j.Logger;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.Metadata;
import org.trellisldp.api.Resource;
import org.trellisldp.http.core.ServiceBundler;
import org.trellisldp.http.core.TrellisRequest;
import org.trellisldp.vocabulary.AS;
import org.trellisldp.vocabulary.LDP;

/**
 * The PUT response handler.
 *
 * @author acoburn
 */
public class PutHandler extends MutatingLdpHandler {

    private static final Logger LOGGER = getLogger(PutHandler.class);

    private final IRI internalId;
    private final RDFSyntax rdfSyntax;
    private final IRI heuristicType;
    private final IRI graphName;
    private final IRI otherGraph;
    private final boolean preconditionRequired;
    private final boolean createUncontained;

    /**
     * Create a builder for an LDP PUT response.
     *
     * @param req the LDP request
     * @param entity the entity
     * @param trellis the Trellis application bundle
     * @param preconditionRequired whether preconditions are required for PUT operations
     * @param createUncontained whether PUT creates uncontained resources
     * @param baseUrl the base URL
     */
    public PutHandler(final TrellisRequest req, final InputStream entity, final ServiceBundler trellis,
                    final boolean preconditionRequired, final boolean createUncontained, final String baseUrl) {
        super(req, trellis, baseUrl, entity);
        this.internalId = rdf.createIRI(TRELLIS_DATA_PREFIX + req.getPath());
        this.rdfSyntax = getRdfSyntax(req.getContentType(), trellis.getIOService().supportedWriteSyntaxes());
        this.heuristicType = req.getContentType() != null && rdfSyntax == null ? LDP.NonRDFSource : LDP.RDFSource;
        this.graphName = ACL.equals(req.getExt()) ? PreferAccessControl : PreferUserManaged;
        this.otherGraph = ACL.equals(req.getExt()) ? PreferUserManaged : PreferAccessControl;
        this.preconditionRequired = preconditionRequired;
        this.createUncontained = createUncontained;
    }

    /**
     * Initialize the response handler.
     * @param parent the parent resource
     * @param resource the resource
     * @return the response builder
     */
    public ResponseBuilder initialize(final Resource parent, final Resource resource) {
        setResource(DELETED_RESOURCE.equals(resource) || MISSING_RESOURCE.equals(resource) ? null : resource);

        // Check the cache
        if (getResource() != null) {
            final Instant modified = getResource().getModified();

            // Check the cache
            checkRequiredPreconditions(preconditionRequired, getRequest().getHeaders().getFirst(IF_MATCH),
                    getRequest().getHeaders().getFirst(IF_UNMODIFIED_SINCE));
            checkCache(modified, generateEtag(getResource()));
        }

        // One cannot put binaries into the ACL graph
        if (isAclRequest() && rdfSyntax == null) {
            throw new NotAcceptableException();
        }

        // For operations that modify resources, the parent resource may need to be updated via
        // ResourceService::touch. This allows us to keep a reference to the parent resource
        // since it has already been looked up. However, access to the parent resource is not necessary
        // if, in the case of creation/deletion, PUT operations are configured as 'uncontained' (not the default)
        // or, in the case of updates, the resource has no parent container.
        // Here, the `resource` object is used directly rather than doing a null check on `getResource()` since
        // in this case, they amount to the same thing.
        if (!createUncontained || resource.getContainer().isPresent()) {
            setParent(parent);
        }
        return status(NO_CONTENT);
    }

    /**
     * Store the resource to the persistence layer.
     * @param builder the response builder
     * @return the response builder
     */
    public CompletionStage<ResponseBuilder> setResource(final ResponseBuilder builder) {
        LOGGER.debug("Setting resource as {}", getIdentifier());

        final IRI ldpType = getLdpType();

        // Verify that the persistence layer supports the given interaction model
        if (!supportsInteractionModel(ldpType)) {
            throw new BadRequestException("Unsupported interaction model provided",
                    status(BAD_REQUEST).link(UnsupportedInteractionModel.getIRIString(),
                        LDP.constrainedBy.getIRIString()).build());
        }

        // It is not possible to change the LDP type to a type that is not a subclass
        if (getResource() != null && !isBinaryDescription()
                && ldpResourceTypes(ldpType).noneMatch(getResource().getInteractionModel()::equals)) {
            LOGGER.error("Cannot change the LDP type to {} for {}", ldpType, getIdentifier());
            throw new ClientErrorException("Cannot change the LDP type to " + ldpType, status(CONFLICT).build());
        }

        LOGGER.debug("Using LDP Type: {}", ldpType);

        final Dataset mutable = rdf.createDataset();
        final Dataset immutable = rdf.createDataset();
        LOGGER.debug("Persisting {} with mutable data:\n{}\n and immutable data:\n{}", getIdentifier(), mutable,
                        immutable);
        return handleResourceUpdate(mutable, immutable, builder, ldpType)
            .whenComplete((a, b) -> closeDataset(mutable))
            .whenComplete((a, b) -> closeDataset(immutable));
    }

    @Override
    protected IRI getInternalId() {
        return internalId;
    }

    @Override
    protected String getIdentifier() {
        return super.getIdentifier() + (isAclRequest() ? ACL_QUERY_PARAM : "");
    }

    private static RDFSyntax getRdfSyntax(final String contentType, final List<RDFSyntax> syntaxes) {
        if (contentType != null) {
            final MediaType mediaType = MediaType.valueOf(contentType);
            for (final RDFSyntax s : syntaxes) {
                if (mediaType.isCompatible(MediaType.valueOf(s.mediaType()))) {
                    return s;
                }
            }
        }
        return null;
    }

    private IRI getLdpType() {
        if (isBinaryDescription()) {
            return LDP.NonRDFSource;
        }
        final Link link = getRequest().getLink();
        if (link != null && "type".equals(link.getRel())) {
            final String uri = link.getUri().toString();
            if (uri.startsWith(LDP.getNamespace()) && !uri.equals(LDP.Resource.getIRIString())) {
                return rdf.createIRI(uri);
            }
        }
        if (getResource() != null) {
            return getResource().getInteractionModel();
        }
        return heuristicType;
    }

    private CompletionStage<ResponseBuilder> handleResourceUpdate(final Dataset mutable,
            final Dataset immutable, final ResponseBuilder builder, final IRI ldpType) {

        final Metadata.Builder metadata;
        final CompletionStage<Void> persistPromise;

        // Add user-supplied data
        if (LDP.NonRDFSource.equals(ldpType) && rdfSyntax == null) {
            LOGGER.trace("Successfully checked for bad digest value");
            final String mimeType = getRequest().getContentType() != null ? getRequest().getContentType()
                : APPLICATION_OCTET_STREAM;
            final IRI binaryLocation = rdf.createIRI(getServices().getBinaryService().generateIdentifier());

            // Persist the content
            final BinaryMetadata binary = BinaryMetadata.builder(binaryLocation).mimeType(mimeType)
                            .hints(getRequest().getHeaders()).build();
            persistPromise = persistContent(binary);

            metadata = metadataBuilder(internalId, ldpType, mutable).binary(binary);
            builder.link(getIdentifier() + "?ext=description", "describedby");
        } else {
            final RDFSyntax s = rdfSyntax != null ? rdfSyntax : TURTLE;
            readEntityIntoDataset(graphName, s, mutable);

            // Check for any constraints
            if (isAclRequest()) {
                mutable.getGraph(PreferAccessControl).ifPresent(graph -> checkConstraint(graph, LDP.RDFSource, s));
            } else {
                mutable.getGraph(PreferUserManaged).ifPresent(graph -> checkConstraint(graph, ldpType, s));
            }
            LOGGER.trace("Successfully checked for constraint violations");
            metadata = metadataBuilder(internalId, ldpType, mutable);
            if (getResource() != null) {
                getResource().getBinaryMetadata().ifPresent(metadata::binary);
            }
            persistPromise = completedFuture(null);
        }

        if (getResource() != null) {
            getResource().getContainer().ifPresent(metadata::container);
            metadata.revision(getResource().getRevision());
            LOGGER.debug("Resource {} found in persistence", getIdentifier());
            try (final Stream<Quad> remaining = getResource().stream(otherGraph)) {
                remaining.forEachOrdered(mutable::add);
            }
        } else if (!createUncontained) {
            getContainer(internalId).ifPresent(metadata::container);
        }

        auditQuads().stream().map(skolemizeQuads(getServices().getResourceService(), getBaseUrl()))
            .forEachOrdered(immutable::add);
        LOGGER.trace("Successfully calculated and skolemized immutable data");

        ldpResourceTypes(effectiveLdpType(ldpType)).map(IRI::getIRIString).forEach(type -> {
                LOGGER.debug("Adding link for type {}", type);
                builder.link(type, "type");
            });
        LOGGER.debug("Persisting mutable data for {} with data: {}", internalId, mutable);

        return persistPromise.thenCompose(future -> allOf(
                createOrReplace(metadata.build(), mutable).toCompletableFuture(),
                getServices().getResourceService().add(internalId, immutable).toCompletableFuture()))
            .thenCompose(future -> handleUpdateEvent(ldpType))
            .thenApply(future -> decorateResponse(builder));
    }

    private ResponseBuilder decorateResponse(final ResponseBuilder builder) {
        if (getResource() == null) {
            return builder.status(CREATED).contentLocation(create(getIdentifier()));
        }
        return builder;
    }

    private CompletionStage<Void> handleUpdateEvent(final IRI ldpType) {
        return emitEvent(getInternalId(), getResource() == null ? AS.Create : AS.Update,
                isAclRequest() ? LDP.RDFSource : ldpType);
    }

    private IRI effectiveLdpType(final IRI ldpType) {
        LOGGER.trace("Determining effective LDP type from offered type {}", ldpType.getIRIString());
        return isAclRequest() || (LDP.NonRDFSource.equals(ldpType) && isBinaryDescription()) ? LDP.RDFSource : ldpType;
    }

    private CompletionStage<Void> createOrReplace(final Metadata metadata, final Dataset dataset) {
        if (getResource() == null) {
            LOGGER.debug("Creating new resource {}", internalId);
            return getServices().getResourceService().create(metadata, dataset);
        } else {
            LOGGER.debug("Replacing old resource {}", internalId);
            return getServices().getResourceService().replace(metadata, dataset);
        }
    }

    private List<Quad> auditQuads() {
        if (getResource() != null) {
            return getServices().getAuditService().update(internalId, getSession());
        }
        return getServices().getAuditService().creation(internalId, getSession());
    }

    private boolean isBinaryDescription() {
        return getResource() != null && LDP.NonRDFSource.equals(getResource().getInteractionModel())
            &&  rdfSyntax != null;
    }
}
