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
package org.trellisldp.webac;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.rdf.api.IRI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;
import org.trellisldp.api.Session;
import org.trellisldp.vocabulary.ACL;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebAcFilterTest {

    private static final Set<IRI> allModes = new HashSet<>();

    static {
        allModes.add(ACL.Append);
        allModes.add(ACL.Read);
        allModes.add(ACL.Write);
        allModes.add(ACL.Control);
    }

    @Mock
    private WebAcService mockWebAcService;

    @Mock
    private ContainerRequestContext mockContext;

    @Mock
    private ContainerResponseContext mockResponseContext;

    @Mock
    private SecurityContext mockSecurityContext;

    @Mock
    private UriInfo mockUriInfo;

    @Mock
    private MultivaluedMap<String, String> mockQueryParams;

    @Mock
    private Principal mockPrincipal;

    @BeforeAll
    static void setUpProperties() {
        System.setProperty(WebAcFilter.CONFIG_WEBAC_METHOD_READABLE, "READ");
        System.setProperty(WebAcFilter.CONFIG_WEBAC_METHOD_WRITABLE, "WRITE");
        System.setProperty(WebAcFilter.CONFIG_WEBAC_METHOD_APPENDABLE, "APPEND");
    }

    @AfterAll
    static void cleanUpProperties() {
        System.clearProperty(WebAcFilter.CONFIG_WEBAC_METHOD_READABLE);
        System.clearProperty(WebAcFilter.CONFIG_WEBAC_METHOD_WRITABLE);
        System.clearProperty(WebAcFilter.CONFIG_WEBAC_METHOD_APPENDABLE);
    }

    @BeforeEach
    void setUp() {
        initMocks(this);
        when(mockWebAcService.getAccessModes(any(IRI.class), any(Session.class))).thenReturn(allModes);
        when(mockContext.getUriInfo()).thenReturn(mockUriInfo);
        when(mockUriInfo.getQueryParameters()).thenReturn(mockQueryParams);
        when(mockQueryParams.getOrDefault(eq("ext"), eq(emptyList()))).thenReturn(emptyList());
        when(mockUriInfo.getPath()).thenReturn("");
        when(mockSecurityContext.getUserPrincipal()).thenReturn(mockPrincipal);
        when(mockSecurityContext.isUserInRole(anyString())).thenReturn(false);
        when(mockPrincipal.getName()).thenReturn("https://example.com/user#me");
    }

    @Test
    void testFilterUnknownMethod() {
        when(mockContext.getMethod()).thenReturn("FOO");

        final WebAcFilter filter = new WebAcFilter(mockWebAcService);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Exception thrown with unknown method!");
    }

    @Test
    void testFilterRead() {
        final Set<IRI> modes = new HashSet<>();
        when(mockContext.getMethod()).thenReturn("GET");
        when(mockWebAcService.getAccessModes(any(IRI.class), any(Session.class))).thenReturn(modes);

        final WebAcFilter filter = new WebAcFilter(mockWebAcService);
        modes.add(ACL.Read);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after adding Read ability!");

        modes.clear();
        assertThrows(NotAuthorizedException.class, () -> filter.filter(mockContext),
                "No expception thrown when not authorized!");

        when(mockContext.getSecurityContext()).thenReturn(mockSecurityContext);
        assertThrows(ForbiddenException.class, () -> filter.filter(mockContext),
                "No exception thrown!");
    }

    @Test
    void testFilterCustomRead() {
        final Set<IRI> modes = new HashSet<>();
        when(mockContext.getMethod()).thenReturn("READ");
        when(mockWebAcService.getAccessModes(any(IRI.class), any(Session.class))).thenReturn(modes);

        final WebAcFilter filter = new WebAcFilter(mockWebAcService);
        modes.add(ACL.Read);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after adding Read ability!");

        modes.clear();
        assertThrows(NotAuthorizedException.class, () -> filter.filter(mockContext),
                "No expception thrown when not authorized!");

        when(mockContext.getSecurityContext()).thenReturn(mockSecurityContext);
        assertThrows(ForbiddenException.class, () -> filter.filter(mockContext),
                "No exception thrown!");
    }


    @Test
    void testFilterWrite() {
        final Set<IRI> modes = new HashSet<>();
        when(mockContext.getMethod()).thenReturn("PUT");
        when(mockWebAcService.getAccessModes(any(IRI.class), any(Session.class))).thenReturn(modes);

        final WebAcFilter filter = new WebAcFilter(mockWebAcService);
        modes.add(ACL.Write);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after adding Write ability!");

        modes.clear();
        assertThrows(NotAuthorizedException.class, () -> filter.filter(mockContext),
                "No expception thrown when not authorized!");

        when(mockContext.getSecurityContext()).thenReturn(mockSecurityContext);
        assertThrows(ForbiddenException.class, () -> filter.filter(mockContext),
                "No exception thrown!");
    }

    @Test
    void testFilterCustomWrite() {
        final Set<IRI> modes = new HashSet<>();
        when(mockContext.getMethod()).thenReturn("WRITE");
        when(mockWebAcService.getAccessModes(any(IRI.class), any(Session.class))).thenReturn(modes);

        final WebAcFilter filter = new WebAcFilter(mockWebAcService);
        modes.add(ACL.Write);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after adding Write ability!");

        modes.clear();
        assertThrows(NotAuthorizedException.class, () -> filter.filter(mockContext),
                "No expception thrown when not authorized!");

        when(mockContext.getSecurityContext()).thenReturn(mockSecurityContext);
        assertThrows(ForbiddenException.class, () -> filter.filter(mockContext),
                "No exception thrown!");
    }

    @Test
    void testFilterAppend() {
        final Set<IRI> modes = new HashSet<>();
        when(mockContext.getMethod()).thenReturn("POST");
        when(mockWebAcService.getAccessModes(any(IRI.class), any(Session.class))).thenReturn(modes);

        final WebAcFilter filter = new WebAcFilter(mockWebAcService);
        modes.add(ACL.Append);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after adding Append ability!");

        modes.add(ACL.Write);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after adding Write ability!");

        modes.remove(ACL.Append);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after removing Append ability!");

        modes.clear();
        assertThrows(NotAuthorizedException.class, () -> filter.filter(mockContext),
                "No expception thrown when not authorized!");

        when(mockContext.getSecurityContext()).thenReturn(mockSecurityContext);
        assertThrows(ForbiddenException.class, () -> filter.filter(mockContext),
                "No exception thrown!");
    }

    @Test
    void testFilterCustomAppend() {
        final Set<IRI> modes = new HashSet<>();
        when(mockContext.getMethod()).thenReturn("APPEND");
        when(mockWebAcService.getAccessModes(any(IRI.class), any(Session.class))).thenReturn(modes);

        final WebAcFilter filter = new WebAcFilter(mockWebAcService);
        modes.add(ACL.Append);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after adding Append ability!");

        modes.add(ACL.Write);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after adding Write ability!");

        modes.remove(ACL.Append);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after removing Append ability!");

        modes.clear();
        assertThrows(NotAuthorizedException.class, () -> filter.filter(mockContext),
                "No expception thrown when not authorized!");

        when(mockContext.getSecurityContext()).thenReturn(mockSecurityContext);
        assertThrows(ForbiddenException.class, () -> filter.filter(mockContext),
                "No exception thrown!");
    }

    @Test
    void testFilterControl() {
        final Set<IRI> modes = new HashSet<>();
        when(mockContext.getMethod()).thenReturn("GET");
        when(mockWebAcService.getAccessModes(any(IRI.class), any(Session.class))).thenReturn(modes);

        final WebAcFilter filter = new WebAcFilter(mockWebAcService);
        modes.add(ACL.Read);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after adding Read ability!");

        when(mockContext.getHeaderString("Prefer"))
            .thenReturn("return=representation; include=\"" + Trellis.PreferAudit.getIRIString() + "\"");

        assertThrows(NotAuthorizedException.class, () -> filter.filter(mockContext),
                "No expception thrown when not authorized!");

        modes.add(ACL.Control);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after adding Control ability!");

        modes.clear();
        when(mockContext.getSecurityContext()).thenReturn(mockSecurityContext);
        assertThrows(ForbiddenException.class, () -> filter.filter(mockContext),
                "No exception thrown!");
    }

    @Test
    void testFilterControl2() {
        final Set<IRI> modes = new HashSet<>();
        when(mockContext.getMethod()).thenReturn("GET");
        when(mockWebAcService.getAccessModes(any(IRI.class), any(Session.class))).thenReturn(modes);

        final WebAcFilter filter = new WebAcFilter(mockWebAcService);
        modes.add(ACL.Read);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after adding Read ability!");

        when(mockQueryParams.getOrDefault(eq("ext"), eq(emptyList()))).thenReturn(singletonList("acl"));

        assertThrows(NotAuthorizedException.class, () -> filter.filter(mockContext),
                "No expception thrown when not authorized!");

        modes.add(ACL.Control);
        assertDoesNotThrow(() -> filter.filter(mockContext), "Unexpected exception after adding Control ability!");

        modes.clear();
        when(mockContext.getSecurityContext()).thenReturn(mockSecurityContext);
        assertThrows(ForbiddenException.class, () -> filter.filter(mockContext),
                "No exception thrown!");
    }

    @Test
    void testFilterChallenges() {
        when(mockContext.getMethod()).thenReturn("POST");
        when(mockWebAcService.getAccessModes(any(IRI.class), any(Session.class))).thenReturn(emptySet());

        final WebAcFilter filter = new WebAcFilter(mockWebAcService, asList("Foo", "Bar"), "my-realm",
                "http://example.com/");

        final List<Object> challenges = assertThrows(NotAuthorizedException.class, () -> filter.filter(mockContext),
                "No auth exception thrown with no access modes!").getChallenges();

        assertTrue(challenges.contains("Foo realm=\"my-realm\""), "Foo not among challenges!");
        assertTrue(challenges.contains("Bar realm=\"my-realm\""), "Bar not among challenges!");
    }

    @Test
    void testFilterResponse() {
        final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(mockResponseContext.getStatusInfo()).thenReturn(OK);
        when(mockResponseContext.getHeaders()).thenReturn(headers);
        when(mockUriInfo.getAbsolutePathBuilder()).thenReturn(UriBuilder.fromUri("http://localhost/"));

        final WebAcFilter filter = new WebAcFilter(mockWebAcService, asList("Foo", "Bar"), "my-realm", null);

        assertTrue(headers.isEmpty());
        filter.filter(mockContext, mockResponseContext);
        assertFalse(headers.isEmpty());

        final Link link = (Link) headers.getFirst("Link");
        assertNotNull(link);
        assertEquals("acl", link.getRel());
        assertEquals("http://localhost/?ext=acl", link.getUri().toString());
    }

    @Test
    void testFilterResponseDelete() {
        final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(mockContext.getMethod()).thenReturn(DELETE);
        when(mockResponseContext.getStatusInfo()).thenReturn(OK);
        when(mockResponseContext.getHeaders()).thenReturn(headers);
        when(mockUriInfo.getAbsolutePathBuilder()).thenReturn(UriBuilder.fromUri("http://localhost/"));

        final WebAcFilter filter = new WebAcFilter(mockWebAcService, asList("Foo", "Bar"), "my-realm", null);

        assertTrue(headers.isEmpty());
        filter.filter(mockContext, mockResponseContext);
        assertTrue(headers.isEmpty());
    }

    @Test
    void testFilterResponseBaseUrl() {
        final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(mockResponseContext.getStatusInfo()).thenReturn(OK);
        when(mockResponseContext.getHeaders()).thenReturn(headers);

        final WebAcFilter filter = new WebAcFilter(mockWebAcService, asList("Foo", "Bar"), "my-realm",
                "http://example.com/");

        assertTrue(headers.isEmpty());
        filter.filter(mockContext, mockResponseContext);
        assertFalse(headers.isEmpty());

        final Link link = (Link) headers.getFirst("Link");
        assertNotNull(link);
        assertEquals("acl", link.getRel());
        assertEquals("http://example.com/?ext=acl", link.getUri().toString());
    }

    @Test
    void testFilterResponseWebac2() {
        final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        final MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("ext", "foo");
        params.add("ext", "acl");
        when(mockResponseContext.getStatusInfo()).thenReturn(OK);
        when(mockResponseContext.getHeaders()).thenReturn(headers);
        when(mockUriInfo.getQueryParameters()).thenReturn(params);
        when(mockUriInfo.getAbsolutePathBuilder()).thenReturn(UriBuilder.fromUri("http://localhost/"));

        final WebAcFilter filter = new WebAcFilter(mockWebAcService, asList("Foo", "Bar"), "my-realm", null);

        assertTrue(headers.isEmpty());
        filter.filter(mockContext, mockResponseContext);
        assertTrue(headers.isEmpty());
    }

    @Test
    void testFilterResponseForbidden() {
        final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(mockResponseContext.getStatusInfo()).thenReturn(FORBIDDEN);
        when(mockResponseContext.getHeaders()).thenReturn(headers);

        final WebAcFilter filter = new WebAcFilter(mockWebAcService, asList("Foo", "Bar"), "my-realm", null);

        assertTrue(headers.isEmpty());
        filter.filter(mockContext, mockResponseContext);
        assertTrue(headers.isEmpty());
    }

    @Test
    void testNoParamCtor() {
        assertDoesNotThrow(() -> new WebAcFilter());
    }
}
