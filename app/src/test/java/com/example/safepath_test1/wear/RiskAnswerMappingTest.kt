package com.example.safepath_test1.wear

import org.junit.Assert.*
import org.junit.Test

class RiskAnswerMappingTest {
    @Test fun mapsOnlyKnownWatchAnswers() {
        assertEquals("DISMISSED", serverRiskStatus("SAFE"))
        assertEquals("CONFIRMED", serverRiskStatus("HELP"))
        assertEquals("CONFIRMED", serverRiskStatus("NO_RESPONSE"))
        assertNull(serverRiskStatus(null))
        assertNull(serverRiskStatus("UNKNOWN"))
    }
}
