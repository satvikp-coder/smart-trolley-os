package com.dashline.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelCompatibilityTest {
    @Test
    fun coordinateValidationRejectsOutOfRangeAndNonFiniteValues() {
        assertTrue(GraphHopperProxyProvider.validCoordinates(23.0225, 72.5714))
        assertFalse(GraphHopperProxyProvider.validCoordinates(91.0, 72.5714))
        assertFalse(GraphHopperProxyProvider.validCoordinates(23.0225, Double.NaN))
    }

    @Test
    fun apiClientsRequireTls() {
        assertNull(TravelApiClient.configured("http://travel.example/"))
        assertNotNull(TravelApiClient.configured("https://travel.example/"))
        assertNull(GraphHopperProxyProvider.configured("http://travel.example/"))
    }
}
