package io.github.hyh1627723044.shortvideokws

import org.junit.Assert.*
import org.junit.Test

class EndpointsTest {
    @Test fun defaultsAreValid() {
        assertNotNull(Endpoints.parse(CloudDefaults.ASR_URL))
        assertNotNull(Endpoints.parse(CloudDefaults.JEV_URL))
        assertTrue(CloudDefaults.ASR_URL.endsWith("/recognize/flash"))
    }
    @Test fun relayPathsAndQueriesAreKeptVerbatim() {
        val url = Endpoints.parse("  https://relay.example.com/v/asr/flash?token=route-a  ")!!
        assertEquals("/v/asr/flash", url.encodedPath)
        assertEquals("token=route-a", url.encodedQuery)
    }
    @Test fun rejectsInsecureOrAmbiguousUrls() {
        listOf(
            "http://openspeech.bytedance.com/api", "ftp://x.com/a", "https://user:pw@x.com/a",
            "https://user@x.com/a", "https://x.com/a#frag", "openspeech.bytedance.com", "", "https://",
        ).forEach { assertNull(it, Endpoints.parse(it)) }
    }
}
