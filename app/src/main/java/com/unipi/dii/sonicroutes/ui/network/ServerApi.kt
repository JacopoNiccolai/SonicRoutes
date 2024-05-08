package com.unipi.dii.sonicroutes.ui.network

import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ServerApi {
    @POST("/upload")
    fun uploadDataJson(@Body jsonData: JsonObject): Call<Void>
}
