package com.unipi.dii.sonicroutes.ui.network

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.unipi.dii.sonicroutes.model.CrossingListResponse
import com.unipi.dii.sonicroutes.model.Points
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
    @GET("/getCrossingCoordinates/{crossingId}")
    fun getCrossingCoordinates(@Path("crossingId") crossingId: Int): Call<JsonElement>
    @GET("/getCrossings/{cityName}")
    suspend fun getCrossings(@Path("cityName") cityName: String): CrossingListResponse



}
