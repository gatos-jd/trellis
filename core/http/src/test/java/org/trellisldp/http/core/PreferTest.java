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
package org.trellisldp.http.core;

import static java.util.Optional.of;
import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * @author acoburn
 */
class PreferTest {

    @Test
    void testPreferNullValues() {
        final Prefer prefer = new Prefer(null, null, null, null, null);
        assertFalse(prefer.getPreference().isPresent());
        assertFalse(prefer.getHandling().isPresent());
        assertFalse(prefer.getRespondAsync());
        assertTrue(prefer.getInclude().isEmpty());
        assertTrue(prefer.getOmit().isEmpty());
    }

    @Test
    void testPrefer1() {
        final Prefer prefer = Prefer.valueOf("return=representation; include=\"http://example.org/test\"");
        assertAll("Check simple Prefer parsing", checkPreferInclude(prefer, "http://example.org/test"));
    }

    @Test
    void testPrefer1b() {
        final Prefer prefer = Prefer.ofInclude("http://example.org/test");
        assertAll("Check simple Prefer parsing", checkPreferInclude(prefer, "http://example.org/test"));
    }

    @Test
    void testPrefer1c() {
        final Prefer prefer = Prefer.valueOf("return=representation; include=http://example.org/test");
        assertAll("Check simple Prefer parsing", checkPreferInclude(prefer, "http://example.org/test"));
    }

    @Test
    void testPrefer2() {
        final Prefer prefer = Prefer.valueOf("return  =  representation;   include =  \"http://example.org/test\"");
        assertAll("Check simple Prefer parsing", checkPreferInclude(prefer, "http://example.org/test"));
    }

    @Test
    void testPrefer3() {
        final Prefer prefer = Prefer.valueOf("return=minimal");
        assertEquals(of("minimal"), prefer.getPreference(), "Check preference value");
        assertTrue(prefer.getInclude().isEmpty(), "Check that there are no includes");
        assertTrue(prefer.getOmit().isEmpty(), "Check omits count is zero");
        assertFalse(prefer.getHandling().isPresent(), "Check handling");
        assertFalse(prefer.getRespondAsync(), "Check respond async");
    }

    @Test
    void testPrefer4() {
        final Prefer prefer = Prefer.valueOf("return=other");
        assertTrue(prefer.getInclude().isEmpty(), "Check includes is empty");
        assertTrue(prefer.getOmit().isEmpty(), "Check omits is empty");
        assertFalse(prefer.getPreference().isPresent(), "Check preference type");
        assertFalse(prefer.getHandling().isPresent(), "Check handling type");
        assertFalse(prefer.getRespondAsync(), "Check respond async");
    }

    @Test
    void testPrefer5() {
        final Prefer prefer = Prefer.valueOf("return=representation; omit=\"http://example.org/test\"");
        assertEquals(of("representation"), prefer.getPreference(), "Check preference value");
        assertTrue(prefer.getInclude().isEmpty(), "Check for no includes");
        assertFalse(prefer.getOmit().isEmpty(), "Check for omits");
        assertTrue(prefer.getOmit().contains("http://example.org/test"), "Check omit value");
        assertFalse(prefer.getHandling().isPresent(), "Check handling");
        assertFalse(prefer.getRespondAsync(), "Check respond async");
    }

    @Test
    void testPrefer5b() {
        final Prefer prefer = Prefer.ofOmit("http://example.org/test");
        assertEquals(of("representation"), prefer.getPreference(), "Check preference value");
        assertTrue(prefer.getInclude().isEmpty(), "Check for no includes");
        assertFalse(prefer.getOmit().isEmpty(), "Check for omit values");
        assertTrue(prefer.getOmit().contains("http://example.org/test"), "Check omit value");
        assertFalse(prefer.getHandling().isPresent(), "Check handling");
        assertFalse(prefer.getRespondAsync(), "Check respond async");
    }

    @Test
    void testPrefer6() {
        final Prefer prefer = Prefer.valueOf("handling=lenient; return=minimal");
        assertTrue(prefer.getInclude().isEmpty(), "Check for no includes");
        assertTrue(prefer.getOmit().isEmpty(), "Check for no omits");
        assertEquals(of("minimal"), prefer.getPreference(), "Check preference value");
        assertEquals(of("lenient"), prefer.getHandling(), "Check handling value");
        assertFalse(prefer.getRespondAsync(), "Check respond async");
    }

    @Test
    void testPrefer7() {
        final Prefer prefer = Prefer.valueOf("respond-async; random-param");
        assertTrue(prefer.getInclude().isEmpty(), "Check for no includes");
        assertTrue(prefer.getOmit().isEmpty(), "Check for no omits");
        assertFalse(prefer.getPreference().isPresent(), "Check preference type");
        assertFalse(prefer.getHandling().isPresent(), "Check handling value");
        assertTrue(prefer.getRespondAsync(), "Check respond async");
    }

    @Test
    void testPrefer8() {
        final Prefer prefer = Prefer.valueOf("handling=strict; return=minimal");
        assertTrue(prefer.getInclude().isEmpty(), "Check for no includes");
        assertTrue(prefer.getOmit().isEmpty(), "Check for no omits");
        assertEquals(of("minimal"), prefer.getPreference(), "Check preference value");
        assertEquals(of("strict"), prefer.getHandling(), "Check handling value");
        assertFalse(prefer.getRespondAsync(), "Check respond async");
    }

    @Test
    void testPrefer9() {
        final Prefer prefer = Prefer.valueOf("handling=blah; return=minimal");
        assertTrue(prefer.getInclude().isEmpty(), "Check for no includes");
        assertTrue(prefer.getOmit().isEmpty(), "Check for no omits");
        assertEquals(of("minimal"), prefer.getPreference(), "Check preference type");
        assertFalse(prefer.getHandling().isPresent(), "Check handling");
        assertFalse(prefer.getRespondAsync(), "Check respond async");
    }

    @Test
    void testRoundTrip1() {
        final Prefer prefer = Prefer.valueOf("return=minimal; handling=lenient; respond-async");
        final String prefString = prefer.toString();
        final Prefer prefer2 = Prefer.valueOf(prefString);
        assertEquals(prefer.getInclude(), prefer2.getInclude());
        assertEquals(prefer.getOmit(), prefer2.getOmit());
        assertEquals(prefer.getPreference(), prefer2.getPreference());
        assertEquals(prefer.getHandling(), prefer2.getHandling());
        assertEquals(prefer.getRespondAsync(), prefer2.getRespondAsync());
    }

    @Test
    void testRoundTrip2() {
        final Prefer prefer = Prefer.valueOf("return=representation; include=\"https://example.com/Prefer\"");
        final String prefString = prefer.toString();
        final Prefer prefer2 = Prefer.valueOf(prefString);
        assertEquals(prefer.getInclude(), prefer2.getInclude());
        assertEquals(prefer.getOmit(), prefer2.getOmit());
        assertEquals(prefer.getPreference(), prefer2.getPreference());
        assertEquals(prefer.getHandling(), prefer2.getHandling());
        assertEquals(prefer.getRespondAsync(), prefer2.getRespondAsync());
    }

    @Test
    void testRoundTrip3() {
        final Prefer prefer = Prefer.valueOf("return=representation; omit=\"https://example.com/Prefer\"");
        final String prefString = prefer.toString();
        final Prefer prefer2 = Prefer.valueOf(prefString);
        assertEquals(prefer.getInclude(), prefer2.getInclude());
        assertEquals(prefer.getOmit(), prefer2.getOmit());
        assertEquals(prefer.getPreference(), prefer2.getPreference());
        assertEquals(prefer.getHandling(), prefer2.getHandling());
        assertEquals(prefer.getRespondAsync(), prefer2.getRespondAsync());
    }

    @Test
    void testStaticInclude() {
        final Prefer prefer = Prefer.ofInclude();
        assertEquals(of("representation"), prefer.getPreference(), "Check preference type");
        assertTrue(prefer.getInclude().isEmpty(), "Check for no includes");
        assertTrue(prefer.getOmit().isEmpty(), "Check for no omits");
    }

    @Test
    void testStaticOmit() {
        final Prefer prefer = Prefer.ofOmit();
        assertEquals(of("representation"), prefer.getPreference(), "Check preference type");
        assertTrue(prefer.getInclude().isEmpty(), "Check for no includes");
        assertTrue(prefer.getOmit().isEmpty(), "Check for no omits");
    }

    @Test
    void testPreferBadQuotes() {
        final Prefer prefer = Prefer.valueOf("return=representation; include=\"http://example.org/test");
        assertEquals(of("representation"), prefer.getPreference(), "Check preference type");
        assertEquals(1L, prefer.getInclude().size(), "Check includes count");
        assertTrue(prefer.getInclude().contains("\"http://example.org/test"));
        assertTrue(prefer.getOmit().isEmpty(), "Check omits count is zero");
        assertFalse(prefer.getHandling().isPresent(), "Check handling");
        assertFalse(prefer.getRespondAsync(), "Check respond async");
    }

    @Test
    void testPreferBadQuotes2() {
        final Prefer prefer = Prefer.valueOf("return=representation; include=\"");
        assertEquals(of("representation"), prefer.getPreference(), "Check preference type");
        assertEquals(1L, prefer.getInclude().size(), "Check for includes size");
        assertTrue(prefer.getInclude().contains("\""), "Check for weird quote in includes");
        assertTrue(prefer.getOmit().isEmpty(), "Check omits count is zero");
        assertFalse(prefer.getHandling().isPresent(), "Check handling");
        assertFalse(prefer.getRespondAsync(), "Check respond async");
    }

    @Test
    void testNullPrefer() {
        assertNull(Prefer.valueOf(null), "Check null value");
    }

    private Stream<Executable> checkPreferInclude(final Prefer prefer, final String url) {
        return Stream.of(
                () -> assertEquals(of("representation"), prefer.getPreference(), "Check preference"),
                () -> assertEquals(1L, prefer.getInclude().size(), "Check includes count"),
                () -> assertTrue(prefer.getInclude().contains(url), "Check includes value"),
                () -> assertTrue(prefer.getOmit().isEmpty(), "Check omits count"),
                () -> assertFalse(prefer.getHandling().isPresent(), "Check handling"),
                () -> assertFalse(prefer.getRespondAsync(), "Check respond async"));
    }
}
