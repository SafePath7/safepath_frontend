package com.example.safepath_test1.location

import com.example.safepath_test1.model.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.abs

internal data class RouteFetch(val routes: List<RouteResult>, val problem: String? = null, val cached: Boolean = false)
internal fun interface WalkingRouteSource {
    suspend fun fetch(points: List<GeoPoint>, alternatives: Boolean): RouteFetch
}

internal object WalkingRouteProtocol {
    const val endpointRadius = 75.0
    const val viaRadius = 40.0
    fun radius(index: Int,last: Int) = if(index==0 || index==last) endpointRadius else viaRadius
    fun parse(text: String, requested: List<GeoPoint>): RouteFetch = try {
        val root=JSONObject(text)
        if(root.optString("code")!="Ok") RouteFetch(emptyList(),root.optString("code","InvalidResponse"))
        else {
            val values=root.optJSONArray("routes") ?: JSONArray()
            val accepted=buildList {
                for(i in 0 until values.length()) {
                    try {
                    val raw=values.getJSONObject(i)
                    val snapped=raw.optJSONArray("waypoints") ?: root.optJSONArray("waypoints") ?: continue
                    if(snapped.length()!=requested.size) continue
                    val distances=mutableListOf<Double>(); var valid=true
                    for(j in requested.indices) {
                        val location=snapped.getJSONObject(j).optJSONArray("location")
                        val lat=location?.optDouble(1,Double.NaN) ?: Double.NaN
                        val lng=location?.optDouble(0,Double.NaN) ?: Double.NaN
                        if(!validPoint(lat,lng)) { valid=false; break }
                        val distance=FacilityRoutePlanner.distance(requested[j],GeoPoint(lat,lng))
                        if(distance>radius(j,requested.lastIndex)+0.5) { valid=false; break }
                        distances.add(distance)
                    }
                    if(!valid) continue
                    val geometry=raw.optJSONObject("geometry") ?: continue
                    if(geometry.optString("type")!="LineString") continue
                    val coordinates=geometry.optJSONArray("coordinates") ?: continue
                    if(coordinates.length() !in 2..30000) continue
                    val points=mutableListOf<GeoPoint>()
                    for(j in 0 until coordinates.length()) {
                        val p=coordinates.getJSONArray(j); val lat=p.optDouble(1,Double.NaN); val lng=p.optDouble(0,Double.NaN)
                        if(!validPoint(lat,lng)) { valid=false; break }
                        points.add(GeoPoint(lat,lng))
                    }
                    if(!valid) continue
                    if(FacilityRoutePlanner.distance(points.first(),requested.first())>endpointRadius+0.5 ||
                        FacilityRoutePlanner.distance(points.last(),requested.last())>endpointRadius+0.5) continue
                    val measured=points.zipWithNext().sumOf { (a,b)->FacilityRoutePlanner.distance(a,b) }
                    val distance=raw.optDouble("distance",Double.NaN); val duration=raw.optDouble("duration",Double.NaN)
                    if(!distance.isFinite() || distance<=0 || !duration.isFinite() || duration<=0 || measured>100000 ||
                        abs(measured-distance)>maxOf(30.0,distance*0.15)) continue
                    val maneuvers=buildList {
                        val legs=raw.optJSONArray("legs") ?: JSONArray()
                        for(j in 0 until legs.length()) {
                            val steps=legs.getJSONObject(j).optJSONArray("steps") ?: JSONArray()
                            for(k in 0 until steps.length()) {
                                val m=steps.getJSONObject(k).optJSONObject("maneuver") ?: continue
                                val p=m.optJSONArray("location") ?: continue
                                val lat=p.optDouble(1,Double.NaN); val lng=p.optDouble(0,Double.NaN)
                                if(!validPoint(lat,lng)) continue
                                val type=m.optString("type")
                                // Intermediate forcing points are not actual destinations.
                                if(type=="depart" && j>0 || type=="arrive" && j<legs.length()-1) continue
                                add(RouteManeuver(GeoPoint(lat,lng),type,m.optString("modifier").ifBlank { null }))
                            }
                        }
                    }
                    add(RouteResult(geometry.toString(),distance,maneuvers=maneuvers,durationSeconds=duration,
                        geometryPoints=points,snapDistancesMeters=distances))
                    } catch (_: Exception) {
                        // One malformed alternative must not erase other validated routes.
                        continue
                    }
                }
            }
            RouteFetch(accepted,if(accepted.isEmpty()) "SnapOrGeometryRejected" else null)
        }
    } catch (_: Exception) { RouteFetch(emptyList(),"InvalidResponse") }

    private fun validPoint(lat: Double,lng: Double)=lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
}

internal class MapboxWalkingClient(private val token: String): WalkingRouteSource {
    private val client=OkHttpClient.Builder().connectTimeout(3,TimeUnit.SECONDS).readTimeout(4,TimeUnit.SECONDS)
        .callTimeout(4,TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private data class Cached(val at: Long,val result: RouteFetch)
    private val cache=object: LinkedHashMap<String,Cached>(32,0.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Cached>?)=size>16
    }
    override suspend fun fetch(points: List<GeoPoint>, alternatives: Boolean): RouteFetch {
        require(points.size in 2..25)
        val coords=points.joinToString(";") { "${it.longitude},${it.latitude}" }
        val key="$coords|$alternatives"
        synchronized(cache) {
            cache[key]?.takeIf { (System.nanoTime()-it.at)/1e6<120000 }?.let { return it.result.copy(cached=true) }
        }
        val url=HttpUrl.Builder().scheme("https").host("api.mapbox.com")
            .addPathSegments("directions/v5/mapbox/walking").addPathSegment(coords)
            .addQueryParameter("alternatives",alternatives.toString())
            .addQueryParameter("continue_straight","true").addQueryParameter("waypoints_per_route","true")
            .addQueryParameter("radiuses",points.indices.joinToString(";") { WalkingRouteProtocol.radius(it,points.lastIndex).toInt().toString() })
            .addQueryParameter("geometries","geojson").addQueryParameter("overview","full").addQueryParameter("steps","true")
            .addQueryParameter("access_token",token).build()
        val result=suspendCancellableCoroutine<RouteFetch> { continuation ->
            val call=client.newCall(Request.Builder().url(url).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object: Callback {
                override fun onFailure(call: Call,e: IOException) {
                    if(continuation.isActive) continuation.resume(RouteFetch(emptyList(),"NetworkOrTimeout"))
                }
                override fun onResponse(call: Call,response: Response) {
                    val parsed=response.use {
                        if(!it.isSuccessful) RouteFetch(emptyList(),"HTTP${it.code()}")
                        else try { WalkingRouteProtocol.parse(it.body()?.string().orEmpty(),points) }
                        catch (_: Exception) { RouteFetch(emptyList(),"InvalidResponse") }
                    }
                    if(continuation.isActive) continuation.resume(parsed)
                }
            })
        }
        if(result.routes.isNotEmpty()) synchronized(cache) {
            cache[key]=Cached(System.nanoTime(),result)
            while(cache.values.sumOf { it.result.routes.sumOf { route -> route.geometryPoints.size } }>50000 && cache.isNotEmpty()) {
                cache.remove(cache.entries.first().key)
            }
        }
        return result
    }
}
