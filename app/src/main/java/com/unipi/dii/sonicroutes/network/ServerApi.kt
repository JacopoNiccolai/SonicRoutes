package com.unipi.dii.sonicroutes.network

import com.google.android.gms.maps.model.LatLng
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.unipi.dii.sonicroutes.model.Crossing
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ServerApi {
    @POST("/upload")
    fun uploadDataJson(@Body jsonData: JsonObject): Call<Void>
    @POST("/getRoute")
    fun sendPoints(@Body points: Points): Call<JsonObject>
    @GET("/getCrossings/{cityName}")
    suspend fun getCrossings(@Path("cityName") cityName: String): CrossingListResponse

}

// Data class for sending two points to the server
data class Points(val point1: LatLng, val point2: LatLng)

// Data class for retrieving a list of crossings
data class CrossingListResponse(val crossings: List<Crossing>)


