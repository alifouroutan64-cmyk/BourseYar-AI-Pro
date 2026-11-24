package com.bourseyar.ai.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TsetmcHttpClientTest {
    @Test
    fun acceptsOfficialHttpsHosts() {
        assertEquals("cdn.tsetmc.com", TsetmcHttpClient.validateUrl("https://cdn.tsetmc.com/api/test").host)
        assertEquals("members.tsetmc.com", TsetmcHttpClient.validateUrl("https://members.tsetmc.com/tsev2/chart/data/Financial.aspx").host)
    }

    @Test
    fun rejectsPlainHttp() {
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcHttpClient.validateUrl("http://cdn.tsetmc.com/api/test")
        }
    }

    @Test
    fun rejectsUntrustedHost() {
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcHttpClient.validateUrl("https://example.com/api/test")
        }
    }

    @Test
    fun rejectsCredentialsAndNonStandardPorts() {
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcHttpClient.validateUrl("https://user:pass@cdn.tsetmc.com/api/test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TsetmcHttpClient.validateUrl("https://cdn.tsetmc.com:8443/api/test")
        }
    }
}
