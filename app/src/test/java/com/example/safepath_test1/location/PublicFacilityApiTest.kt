package com.example.safepath_test1.location

import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PublicFacilityApiTest {
    @Test fun fireStationProjectedCoordinatesAreConvertedToKoreanLatitudeAndLongitude() {
        val json = JSONObject("""{"header":{"resultCode":"00"},"body":{"totalCount":1,"items":{"item":[{"x":"14320813.9474","y":"4286168.61638"}]}}}""")
        val result = PublicFacilityApi.parseFireStationPage(json)
        assertEquals(1, result.rowCount)
        assertEquals(35.894894, result.facilities.single().latitude, 0.000001)
        assertEquals(128.646060, result.facilities.single().longitude, 0.000001)
    }
    @Test fun streetlightPageCountsRawRowsEvenWhenCoordinatesAreInvalid() {
        val page = PublicFacilityApi.parseStreetlightPage(JSONObject("""{
            "matchCount": 3, "data": [
                {"위도":"35.86244061", "경도":"128.6086914"},
                {"위도":"", "경도":""}
            ]
        }"""))
        assertEquals(2, page.rowCount)
        assertEquals(3, page.total)
        assertEquals(1, page.facilities.size)
        assertEquals(35.86244061, page.facilities.single().latitude, 0.00000001)
    }

    @Test(expected = IOException::class) fun malformedStreetlightPageIsRecoverableIoError() {
        PublicFacilityApi.parseStreetlightPage(JSONObject("""{"data": []}"""))
    }

    @Test fun bellPageAcceptsSingleObjectAndUsesWgs84Coordinates() {
        val page = PublicFacilityApi.parseBellPage(JSONObject("""{"response":{
            "header":{"resultCode":"0"},
            "body":{"totalCount":1,"items":{"item":{"WGS84_LAT":"37.681164","WGS84_LOT":"126.397633"}}}
        }}"""))
        assertEquals(1, page.rowCount)
        assertEquals(126.397633, page.facilities.single().longitude, 0.00000001)
    }

    @Test fun emptyBellResultIsAllowedOnlyWithZeroTotal() {
        val page = PublicFacilityApi.parseBellPage(JSONObject("""{"response":{
            "header":{"resultCode":"0"},"body":{"totalCount":0,"items":""}
        }}"""))
        assertTrue(page.facilities.isEmpty())
        assertEquals(0, page.rowCount)
    }

    @Test(expected = IOException::class) fun missingBellItemsCannotBecomeSuccessfulEmptyCache() {
        PublicFacilityApi.parseBellPage(JSONObject("""{"response":{
            "header":{"resultCode":"0"},"body":{"totalCount":100,"items":""}
        }}"""))
    }

    @Test(expected = IOException::class) fun apiErrorIsNotTreatedAsNoFacilities() {
        PublicFacilityApi.parseBellPage(JSONObject("""{"response":{"header":{"resultCode":"30"}}}"""))
    }
}
