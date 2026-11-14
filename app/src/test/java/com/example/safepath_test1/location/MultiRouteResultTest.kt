package com.example.safepath_test1.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultiRouteResultTest {
    private fun route(geometry: String) = RouteResult(geometry, 100.0)

    @Test fun identifiesRecommendedAndSafe() {
        assertEquals("추천·안전 동일", MultiRouteResult(route("a"), route("b"), route("a")).duplicateRouteLabel())
    }
    @Test fun identifiesRecommendedAndShortest() {
        assertEquals("추천·가장 빠른 동일", MultiRouteResult(route("b"), route("a"), route("a")).duplicateRouteLabel())
    }
    @Test fun identifiesSafeAndShortest() {
        assertEquals("안전·가장 빠른 동일", MultiRouteResult(route("a"), route("a"), route("b")).duplicateRouteLabel())
    }
    @Test fun identifiesAllThree() {
        assertEquals("추천·안전·가장 빠른 동일", MultiRouteResult(route("a"), route("a"), route("a")).duplicateRouteLabel())
    }
    @Test fun missingOrDistinctRoutesAreNotDuplicates() {
        assertNull(MultiRouteResult(null, null, null).duplicateRouteLabel())
        assertNull(MultiRouteResult(route("a"), route("b"), route("c")).duplicateRouteLabel())
        assertNull(MultiRouteResult(route(""), route(""), null).duplicateRouteLabel())
    }
}
