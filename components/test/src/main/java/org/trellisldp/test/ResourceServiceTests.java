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
package org.trellisldp.test;

import static java.time.Instant.now;
import static java.util.Arrays.asList;
import static java.util.Optional.of;
import static java.util.function.Predicate.isEqual;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.generate;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.trellisldp.api.Resource.SpecialResources.DELETED_RESOURCE;
import static org.trellisldp.api.Resource.SpecialResources.MISSING_RESOURCE;
import static org.trellisldp.api.TrellisUtils.TRELLIS_BNODE_PREFIX;
import static org.trellisldp.api.TrellisUtils.TRELLIS_DATA_PREFIX;
import static org.trellisldp.api.TrellisUtils.getInstance;
import static org.trellisldp.vocabulary.RDF.type;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.Metadata;
import org.trellisldp.api.Resource;
import org.trellisldp.api.ResourceService;
import org.trellisldp.vocabulary.AS;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.FOAF;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.PROV;
import org.trellisldp.vocabulary.SKOS;
import org.trellisldp.vocabulary.Trellis;
import org.trellisldp.vocabulary.XSD;

/**
 * Resource Service tests.
 */
@TestInstance(PER_CLASS)
public interface ResourceServiceTests {

    String SUBJECT0 = "http://example.com/subject/0";

    String SUBJECT1 = "http://example.com/subject/1";

    String SUBJECT2 = "http://example.com/subject/2";

    IRI ROOT_CONTAINER = getInstance().createIRI(TRELLIS_DATA_PREFIX);

    /**
     * Get the resource service implementation.
     * @return the resource service implementation
     */
    ResourceService getResourceService();

    /**
     * Test creating a resource.
     */
    @Test
    @DisplayName("Test creating resource")
    default void testCreateResource() {
        final RDF rdf = getInstance();
        final IRI identifier = rdf.createIRI(TRELLIS_DATA_PREFIX + getResourceService().generateIdentifier());
        final Dataset dataset = buildDataset(identifier, "Creation Test", SUBJECT1);

        assertEquals(MISSING_RESOURCE, getResourceService().get(identifier).toCompletableFuture().join(),
                "Check for no pre-existing LDP-RS");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(identifier)
                    .interactionModel(LDP.RDFSource).container(ROOT_CONTAINER).build(), dataset)
                .toCompletableFuture().join(), "Check that the resource was successfully created");
        final Resource res = getResourceService().get(identifier).toCompletableFuture().join();
        assertAll("Check resource stream", res.stream(Trellis.PreferUserManaged)
                .map(q -> () -> assertTrue(dataset.contains(q), "Verify that the quad is from the dataset: " + q)));
    }

    /**
     * Test replacing a resource.
     */
    @Test
    @DisplayName("Test replacing resource")
    default void testReplaceResource() {
        final RDF rdf = getInstance();
        final IRI identifier = rdf.createIRI(TRELLIS_DATA_PREFIX + getResourceService().generateIdentifier());
        final Dataset dataset = buildDataset(identifier, "Replacement Test", SUBJECT2);

        assertEquals(MISSING_RESOURCE, getResourceService().get(identifier).toCompletableFuture().join(),
                "Check for no pre-existing LDP-RS");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(identifier)
                    .interactionModel(LDP.RDFSource).container(ROOT_CONTAINER).build(), dataset)
                .toCompletableFuture().join(), "Check that the LDP-RS was successfully created");

        dataset.clear();
        dataset.add(Trellis.PreferUserManaged, identifier, SKOS.prefLabel, rdf.createLiteral("preferred label"));
        dataset.add(Trellis.PreferUserManaged, identifier, SKOS.altLabel, rdf.createLiteral("alternate label"));
        dataset.add(Trellis.PreferUserManaged, identifier, type, SKOS.Concept);

        assertDoesNotThrow(() -> getResourceService().replace(Metadata.builder(identifier)
                    .interactionModel(LDP.RDFSource).container(ROOT_CONTAINER).build(), dataset)
                .toCompletableFuture().join(), "Check that the LDP-RS was successfully replaced");
        final Resource res = getResourceService().get(identifier).toCompletableFuture().join();
        assertAll("Check the replaced LDP-RS stream", res.stream(Trellis.PreferUserManaged)
                .map(q -> () -> assertTrue(dataset.contains(q), "Check that the quad comes from the dataset: " + q)));
        assertEquals(3L, res.stream(Trellis.PreferUserManaged).count(), "Check the total user-managed triple count");
    }

    /**
     * Test deleting a resource.
     */
    @Test
    @DisplayName("Test deleting resource")
    default void testDeleteResource() {
        final RDF rdf = getInstance();
        final IRI identifier = rdf.createIRI(TRELLIS_DATA_PREFIX + getResourceService().generateIdentifier());
        final Dataset dataset = buildDataset(identifier, "Deletion Test", SUBJECT1);

        assertEquals(MISSING_RESOURCE, getResourceService().get(identifier).toCompletableFuture().join(),
                "Check that the resource doesn't exist");

        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(identifier)
                            .interactionModel(LDP.RDFSource).container(ROOT_CONTAINER).build(), dataset)
                .toCompletableFuture().join(), "Check that the resource was successfully created");
        assertNotEquals(DELETED_RESOURCE, getResourceService().get(identifier).toCompletableFuture().join(),
                "Check that the resource isn't currently 'deleted'");

        assertDoesNotThrow(() -> getResourceService().delete(Metadata.builder(identifier)
                    .interactionModel(LDP.RDFSource).container(ROOT_CONTAINER).build()).toCompletableFuture().join(),
                "Check that the delete operation succeeded");
        final Resource resource = getResourceService().get(identifier).toCompletableFuture().join();
        assertTrue(DELETED_RESOURCE.equals(resource) || MISSING_RESOURCE.equals(resource),
                        "Verify that the resource is marked as deleted or missing");
    }

    /**
     * Test adding immutable data.
     */
    @Test
    @DisplayName("Test adding immutable data")
    default void testAddImmutableData() {
        final RDF rdf = getInstance();
        final IRI identifier = rdf.createIRI(TRELLIS_DATA_PREFIX + getResourceService().generateIdentifier());
        final Dataset dataset0 = buildDataset(identifier, "Immutable Resource Test", SUBJECT2);

        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(identifier)
                    .interactionModel(LDP.RDFSource).container(ROOT_CONTAINER).build(), dataset0)
                .toCompletableFuture().join(), "Check the successful creation of an LDP-RS");

        final IRI audit1 = rdf.createIRI(TRELLIS_BNODE_PREFIX + getResourceService().generateIdentifier());
        final Dataset dataset1 = rdf.createDataset();
        dataset1.add(Trellis.PreferAudit, identifier, PROV.wasGeneratedBy, audit1);
        dataset1.add(Trellis.PreferAudit, audit1, type, PROV.Activity);
        dataset1.add(Trellis.PreferAudit, audit1, type, AS.Create);
        dataset1.add(Trellis.PreferAudit, audit1, PROV.atTime, rdf.createLiteral(now().toString(), XSD.dateTime));

        assertDoesNotThrow(() -> getResourceService().add(identifier, dataset1).toCompletableFuture().join(),
                "Check the successful addition of audit quads");

        final Resource res = getResourceService().get(identifier).toCompletableFuture().join();
        assertAll("Check the audit stream", res.stream(Trellis.PreferAudit)
                .map(q -> () -> assertTrue(dataset1.contains(q), "Check that the audit stream includes: " + q)));
        assertEquals(4L, res.stream(Trellis.PreferAudit).count(), "Check the audit triple count");

        final IRI audit2 = rdf.createIRI(TRELLIS_BNODE_PREFIX + getResourceService().generateIdentifier());
        final Dataset dataset2 = rdf.createDataset();
        dataset2.add(Trellis.PreferAudit, identifier, PROV.wasGeneratedBy, audit2);
        dataset2.add(Trellis.PreferAudit, audit2, type, PROV.Activity);
        dataset2.add(Trellis.PreferAudit, audit2, type, AS.Update);
        dataset2.add(Trellis.PreferAudit, audit2, PROV.atTime, rdf.createLiteral(now().toString(), XSD.dateTime));

        assertDoesNotThrow(() -> getResourceService().add(identifier, dataset2).toCompletableFuture().join(),
                "Check that audit triples are added successfully");

        final Resource res2 = getResourceService().get(identifier).toCompletableFuture().join();

        final Dataset combined = rdf.createDataset();
        dataset1.stream().forEach(combined::add);
        dataset2.stream().forEach(combined::add);

        assertAll("Check the audit stream", res2.stream(Trellis.PreferAudit)
                .map(q -> () -> assertTrue(combined.contains(q), "Check that the audit stream includes: " + q)));
        assertEquals(8L, res2.stream(Trellis.PreferAudit).count(), "Check the audit triple count");
    }

    /**
     * Test an LDP:RDFSource.
     */
    @Test
    @DisplayName("Test LDP-RS")
    default void testLdpRs() {
        final Instant time = now();
        final RDF rdf = getInstance();
        final IRI identifier = rdf.createIRI(TRELLIS_DATA_PREFIX + getResourceService().generateIdentifier());
        final Dataset dataset = buildDataset(identifier, "Create LDP-RS Test", SUBJECT1);

        assertEquals(MISSING_RESOURCE, getResourceService().get(identifier).toCompletableFuture().join(),
                "Check for no pre-existing LDP-RS");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(identifier)
                    .interactionModel(LDP.RDFSource).container(ROOT_CONTAINER).build(), dataset)
                .toCompletableFuture().join(), "Check the creation of an LDP-RS");
        final Resource res = getResourceService().get(identifier).toCompletableFuture().join();
        assertAll("Check the LDP-RS resource", checkResource(res, identifier, LDP.RDFSource, time, dataset));
        assertEquals(3L, res.stream(Trellis.PreferUserManaged).count(), "Check the user triple count");
    }

    /**
     * Test an LDP:NonRDFSource.
     */
    @Test
    @DisplayName("Test LDP-NR")
    default void testLdpNr() {
        final Instant time = now();
        final RDF rdf = getInstance();
        final IRI identifier = rdf.createIRI(TRELLIS_DATA_PREFIX + getResourceService().generateIdentifier());
        final Dataset dataset = buildDataset(identifier, "Create LDP-NR Test", SUBJECT2);

        final IRI binaryLocation = rdf.createIRI("binary:location/" + getResourceService().generateIdentifier());
        final BinaryMetadata binary = BinaryMetadata.builder(binaryLocation).mimeType("text/plain").build();

        assertEquals(MISSING_RESOURCE, getResourceService().get(identifier).toCompletableFuture().join(),
                "Check for no pre-existing LDP-NR");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(identifier)
                    .interactionModel(LDP.NonRDFSource).container(ROOT_CONTAINER).binary(binary).build(), dataset)
                .toCompletableFuture().join(), "Check the creation of an LDP-NR");
        final Resource res = getResourceService().get(identifier).toCompletableFuture().join();
        assertAll("Check the LDP-NR resource", checkResource(res, identifier, LDP.NonRDFSource, time, dataset));
        assertEquals(3L, res.stream(Trellis.PreferUserManaged).count(), "Check the user-managed count of the LDP-NR");
        res.getBinaryMetadata().ifPresent(b -> {
            assertEquals(binaryLocation, b.getIdentifier(), "Check the binary identifier");
            assertEquals(of("text/plain"), b.getMimeType(), "Check the binary mimeType");
        });
    }

    /**
     * Test an LDP:Container.
     */
    @Test
    @DisplayName("Test LDP-C")
    default void testLdpC() {
        final Instant time = now();
        final RDF rdf = getInstance();
        final String base = TRELLIS_DATA_PREFIX + getResourceService().generateIdentifier();
        final IRI identifier = rdf.createIRI(base);
        final Dataset dataset0 = buildDataset(identifier, "Container Test", SUBJECT0);

        assertEquals(MISSING_RESOURCE, getResourceService().get(identifier).toCompletableFuture().join(),
                "Check for no pre-existing LDP-C");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(identifier)
                    .interactionModel(LDP.Container).container(ROOT_CONTAINER).build(), dataset0)
                .toCompletableFuture().join(), "Check that the LDP-C is created successfully");

        final IRI child1 = rdf.createIRI(base + "/child01");
        final Dataset dataset1 = buildDataset(child1, "Contained Child 1", SUBJECT1);

        assertEquals(MISSING_RESOURCE, getResourceService().get(child1).toCompletableFuture().join(),
                "Check for no child1 resource");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(child1).interactionModel(LDP.RDFSource)
                    .container(identifier).build(), dataset1).toCompletableFuture().join(),
                "Check that the first child was successfully created in the LDP-C");

        final IRI child2 = rdf.createIRI(base + "/child02");
        final Dataset dataset2 = buildDataset(child2, "Contained Child2", SUBJECT2);

        assertEquals(MISSING_RESOURCE, getResourceService().get(child2).toCompletableFuture().join(),
                "Check for no child2 resource");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(child2).interactionModel(LDP.RDFSource)
                    .container(identifier).build(), dataset2).toCompletableFuture().join(),
                "Check that the second child was successfully created in the LDP-C");

        final Resource res = getResourceService().get(identifier).toCompletableFuture().join();
        assertAll("Check the LDP-C resource", checkResource(res, identifier, LDP.Container, time, dataset0));
        assertEquals(2L, res.stream(LDP.PreferContainment).count(), "Check the containment triple count");
        final Graph graph = rdf.createGraph();
        res.stream(LDP.PreferContainment).map(Quad::asTriple).forEach(graph::add);
        assertTrue(graph.contains(identifier, LDP.contains, child1), "Check that child1 is contained in the LDP-C");
        assertTrue(graph.contains(identifier, LDP.contains, child2), "Check that child2 is contained in the LDP-C");
        assertEquals(3L, res.stream(Trellis.PreferUserManaged).count(), "Check the user-managed triple count");
    }

    /**
     * Test an LDP:BasicContainer.
     */
    @Test
    @DisplayName("Test LDP-BC")
    default void testLdpBC() {
        final Instant time = now();
        final RDF rdf = getInstance();
        final String base = TRELLIS_DATA_PREFIX + getResourceService().generateIdentifier();
        final IRI identifier = rdf.createIRI(base);
        final Dataset dataset0 = buildDataset(identifier, "Basic Container Test", SUBJECT0);

        assertEquals(MISSING_RESOURCE, getResourceService().get(identifier).toCompletableFuture().join(),
                "Check for a pre-existing LDP-BC");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(identifier)
                    .interactionModel(LDP.BasicContainer).container(ROOT_CONTAINER).build(), dataset0)
                .toCompletableFuture().join(), "Check that creating an LDP-BC succeeds");

        final IRI child1 = rdf.createIRI(base + "/child11");
        final Dataset dataset1 = buildDataset(child1, "Contained Child 1", SUBJECT1);

        assertEquals(MISSING_RESOURCE, getResourceService().get(child1).toCompletableFuture().join(),
                "Check for no child1 resource");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(child1).interactionModel(LDP.RDFSource)
                .container(identifier).build(), dataset1).toCompletableFuture().join(), "Check that child1 is created");

        final IRI child2 = rdf.createIRI(base + "/child12");
        final Dataset dataset2 = buildDataset(child2, "Contained Child2", SUBJECT2);

        assertEquals(MISSING_RESOURCE, getResourceService().get(child2).toCompletableFuture().join(),
                "Check for no child2 resource");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(child2).interactionModel(LDP.RDFSource)
                    .container(identifier).build(), dataset2).toCompletableFuture().join(),
                "Check that child2 is created");

        final Resource res = getResourceService().get(identifier).toCompletableFuture().join();
        assertAll("Check the LDP-BC resource", checkResource(res, identifier, LDP.BasicContainer, time, dataset0));
        assertEquals(2L, res.stream(LDP.PreferContainment).count(), "Check the containment triple count");
        final Graph graph = rdf.createGraph();
        res.stream(LDP.PreferContainment).map(Quad::asTriple).forEach(graph::add);
        assertTrue(graph.contains(identifier, LDP.contains, child1), "Check that child1 is contained in the LDP-BC");
        assertTrue(graph.contains(identifier, LDP.contains, child2), "Check that child2 is contained in the LDP-BC");
        assertEquals(3L, res.stream(Trellis.PreferUserManaged).count(), "Check the user-managed triple count");
        assertEquals(0L, res.stream(LDP.PreferMembership).count(), "Check for an absence of membership triples");
    }

    /**
     * Test an LDP:DirectContainer.
     */
    @Test
    @DisplayName("Test LDP-DC")
    default void testLdpDC() {
        // Only test DC if the backend supports it
        assumeTrue(getResourceService().supportedInteractionModels().contains(LDP.DirectContainer));

        final Instant time = now();
        final RDF rdf = getInstance();
        final String base = TRELLIS_DATA_PREFIX + getResourceService().generateIdentifier();
        final IRI identifier = rdf.createIRI(base);
        final IRI member = rdf.createIRI(base + "/member");
        final Dataset dataset0 = buildDataset(identifier, "Direct Container Test", SUBJECT0);
        dataset0.add(Trellis.PreferUserManaged, identifier, LDP.membershipResource, member);
        dataset0.add(Trellis.PreferUserManaged, identifier, LDP.isMemberOfRelation, DC.isPartOf);

        assertEquals(MISSING_RESOURCE, getResourceService().get(identifier).toCompletableFuture().join(),
                "Check that the DC doesn't exist");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(identifier)
                    .membershipResource(member).memberOfRelation(DC.isPartOf)
                    .interactionModel(LDP.DirectContainer).container(ROOT_CONTAINER).build(), dataset0)
                .toCompletableFuture().join(), "Check that creating the LDP-DC succeeds");

        final IRI child1 = rdf.createIRI(base + "/child1");
        final Dataset dataset1 = buildDataset(child1, "Child 1", SUBJECT1);

        assertEquals(MISSING_RESOURCE, getResourceService().get(child1).toCompletableFuture().join(),
                "Check that no child resource exists");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(child1).interactionModel(LDP.RDFSource)
                    .container(identifier).build(), dataset1).toCompletableFuture().join(),
                "Check that the child resource is successfully created");

        final IRI child2 = rdf.createIRI(base + "/child2");
        final Dataset dataset2 = buildDataset(child2, "Child 2", SUBJECT2);

        assertEquals(MISSING_RESOURCE, getResourceService().get(child2).toCompletableFuture().join(),
                "Check that no child2 resource exists");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(child2).interactionModel(LDP.RDFSource)
                    .container(identifier).build(), dataset2).toCompletableFuture().join(),
                "Check that the child2 resource is successfully created");

        final Resource res = getResourceService().get(identifier).toCompletableFuture().join();
        assertAll("Check the resource", checkResource(res, identifier, LDP.DirectContainer, time, dataset0));
        assertEquals(of(member), res.getMembershipResource(), "Check for ldp:membershipResource");
        assertEquals(of(DC.isPartOf), res.getMemberOfRelation(), "Check for ldp:isMemberOfRelation");
        assertFalse(res.getMemberRelation().isPresent(), "Check for no ldp:hasMemberRelation");
        assertFalse(res.getInsertedContentRelation().filter(isEqual(LDP.MemberSubject).negate()).isPresent(),
                "Check for no ldp:InsertedContentRelation, excepting ldp:MemberSubject");
        assertEquals(2L, res.stream(LDP.PreferContainment).count(), "Check the containment count");
        final Graph graph = rdf.createGraph();
        res.stream(LDP.PreferContainment).map(Quad::asTriple).forEach(graph::add);
        assertTrue(graph.contains(identifier, LDP.contains, child1), "Check that child1 is contained in the LDP-DC");
        assertTrue(graph.contains(identifier, LDP.contains, child2), "Check that child2 is contained in the LDP-DC");
        assertEquals(5L, res.stream(Trellis.PreferUserManaged).count(), "Check the user-managed triple count");
    }

    /**
     * Test an LDP:IndirectContainer.
     */
    @Test
    @DisplayName("Test LDP-IC")
    default void testLdpIC() {
        // Only execute this test if the backend supports it
        assumeTrue(getResourceService().supportedInteractionModels().contains(LDP.IndirectContainer));

        final Instant time = now();
        final RDF rdf = getInstance();
        final String base = TRELLIS_DATA_PREFIX + getResourceService().generateIdentifier();
        final IRI identifier = rdf.createIRI(base);
        final IRI member = rdf.createIRI(base + "/member");
        final Dataset dataset0 = buildDataset(identifier, "Indirect Container Test", SUBJECT0);
        dataset0.add(Trellis.PreferUserManaged, identifier, LDP.membershipResource, member);
        dataset0.add(Trellis.PreferUserManaged, identifier, LDP.hasMemberRelation, DC.relation);
        dataset0.add(Trellis.PreferUserManaged, identifier, LDP.insertedContentRelation, FOAF.primaryTopic);

        assertEquals(MISSING_RESOURCE, getResourceService().get(identifier).toCompletableFuture().join(),
                "Check for a missing resource");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(identifier)
                    .membershipResource(member).memberRelation(DC.relation).insertedContentRelation(FOAF.primaryTopic)
                    .interactionModel(LDP.IndirectContainer).container(ROOT_CONTAINER).build(), dataset0)
                .toCompletableFuture().join(), "Check that creating a resource succeeds");

        final IRI child1 = rdf.createIRI(base + "/child1");
        final Dataset dataset1 = buildDataset(child1, "Indirect Container Child 1", SUBJECT1);

        assertEquals(MISSING_RESOURCE, getResourceService().get(child1).toCompletableFuture().join(),
                "Check that the child resource doesn't exist");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(child1).interactionModel(LDP.RDFSource)
                    .container(identifier).build(), dataset1).toCompletableFuture().join(),
                "Check that creating a child resource succeeds");

        final IRI child2 = rdf.createIRI(base + "/child2");
        final Dataset dataset2 = buildDataset(child2, "Indirect Container Child 2", SUBJECT2);

        assertEquals(MISSING_RESOURCE, getResourceService().get(child2).toCompletableFuture().join(),
                "Check that the child resource doesn't exist");
        assertDoesNotThrow(() -> getResourceService().create(Metadata.builder(child2).interactionModel(LDP.RDFSource)
                    .container(identifier).build(), dataset2).toCompletableFuture().join(),
                "Check that creating the child resource succeeds");

        final Resource res = getResourceService().get(identifier).toCompletableFuture().join();
        assertAll("Check the resource", checkResource(res, identifier, LDP.IndirectContainer, time, dataset0));
        assertEquals(of(member), res.getMembershipResource(), "Check for ldp:membershipResource");
        assertEquals(of(DC.relation), res.getMemberRelation(), "Check for ldp:hasMemberRelation");
        assertEquals(of(FOAF.primaryTopic), res.getInsertedContentRelation(), "Check for ldp:insertedContentRelation");
        assertFalse(res.getMemberOfRelation().isPresent(), "Check for no ldp:isMemberOfRelation");
        assertEquals(2L, res.stream(LDP.PreferContainment).count(), "Check the containment triple count");
        final Graph graph = rdf.createGraph();
        res.stream(LDP.PreferContainment).map(Quad::asTriple).forEach(graph::add);
        assertTrue(graph.contains(identifier, LDP.contains, child1), "Check that child1 is contained in the LDP-IC");
        assertTrue(graph.contains(identifier, LDP.contains, child2), "Check that child2 is contained in the LDP-IC");
        assertEquals(6L, res.stream(Trellis.PreferUserManaged).count(), "Check the total triple count");
    }

    /**
     * Test identifier generation.
     */
    @Test
    @DisplayName("Test identifier generation")
    default void testIdentifierGeneration() {
        final int size = 1000;
        final Set<String> identifiers = generate(getResourceService()::generateIdentifier).limit(size).collect(toSet());
        assertEquals(size, identifiers.size(), "Check the uniqueness of the identifier generator");
    }

    /**
     * Check a Trellis Resource.
     * @param res the Resource
     * @param identifier the identifier
     * @param ldpType the LDP type
     * @param time an instant before which the resource shouldn't exist
     * @param dataset a dataset to compare against
     * @return a stream of testable assertions
     */
    default Stream<Executable> checkResource(final Resource res, final IRI identifier, final IRI ldpType,
            final Instant time, final Dataset dataset) {
        return Stream.of(
                () -> assertEquals(ldpType, res.getInteractionModel(), "Check the interaction model"),
                () -> assertEquals(identifier, res.getIdentifier(), "Check the identifier"),
                () -> assertFalse(res.getModified().isBefore(time), "Check the modification time (1)"),
                () -> assertFalse(res.getModified().isAfter(now()), "Check the modification time (2)"),
                () -> assertFalse(res.hasAcl(), "Check for an ACL"),
                () -> assertEquals(LDP.NonRDFSource.equals(ldpType), res.getBinaryMetadata().isPresent(),
                                   "Check Binary"),
                () -> assertEquals(asList(LDP.DirectContainer, LDP.IndirectContainer).contains(ldpType),
                       res.getMembershipResource().isPresent(), "Check ldp:membershipResource"),
                () -> assertEquals(asList(LDP.DirectContainer, LDP.IndirectContainer).contains(ldpType),
                       res.getMemberRelation().isPresent() || res.getMemberOfRelation().isPresent(),
                       "Check ldp:hasMemberRelation or ldp:isMemberOfRelation"),
                () -> assertEquals(asList(LDP.DirectContainer, LDP.IndirectContainer).contains(ldpType),
                       res.getInsertedContentRelation().isPresent(), "Check ldp:insertedContentRelation"),
                () -> res.stream(Trellis.PreferUserManaged).forEach(q ->
                        assertTrue(dataset.contains(q))));
    }

    /**
     * Build a dataset.
     * @param resource the resource IRI
     * @param title a title
     * @param subject a subject
     * @return a new dataset
     */
    default Dataset buildDataset(final IRI resource, final String title, final String subject) {
        final Dataset dataset = getInstance().createDataset();
        dataset.add(Trellis.PreferUserManaged, resource, DC.title, getInstance().createLiteral(title));
        dataset.add(Trellis.PreferUserManaged, resource, DC.subject, getInstance().createIRI(subject));
        dataset.add(Trellis.PreferUserManaged, resource, type, SKOS.Concept);
        return dataset;
    }
}
