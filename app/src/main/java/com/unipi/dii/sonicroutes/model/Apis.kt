package com.unipi.dii.sonicroutes.model

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.unipi.dii.sonicroutes.ui.network.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class Apis (private val context: Context){
    // todo : sia questa classe che l'interfaccia in 'ui/network' van spostate
    private val serverApi: ServerApi

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.1.1.22:5000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        serverApi = retrofit.create(ServerApi::class.java)
    }

    fun getRoute(
        point1: LatLng,
        point2: LatLng,
        onComplete: (Route) -> Unit,
        onError: (String) -> Unit
    ) {
        val points = Points(point1, point2)
        val call = serverApi.sendPoints(points)

        call.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    val jsonResponse = response.body()
                    val path = jsonResponse?.getAsJsonArray("path")
                    Log.d("JSON Path", path.toString())
                    if (path != null) {
                        val route = handlePath(path)
                        onComplete(route) // Invoke the callback with the retrieved route
                    } else {
                        onError("Invalid server response")
                    }
                } else {
                    onError("Failed to send points to the server")
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                onError("Network error: ${t.message}")
                t.printStackTrace()
            }
        })
    }

    private fun handlePath(path: JsonArray): Route {

        val locations = mutableListOf<LatLng>()

        Log.d("Path", path.toString())

        // Convert the JSON array to a list of LatLng objects
        for (element in path) {
            val lat = element.asJsonArray[0].asDouble
            val lng = element.asJsonArray[1].asDouble
            locations.add(LatLng(lat, lng))
            Log.i("PathElement", element.toString())
        }

        // create a segment for each pair of points
        val segments = mutableListOf<Segment>()
        for (i in 0 until locations.size - 1) {
            val start = locations[i]
            val end = locations[i + 1]
            val amplitude = 0.0
            val segment = Segment(start, end, amplitude)
            segments.add(segment)
        }

        // create a route from the segments
        val route = Route(segments)
        route.printRoute()

        return route
    }



    fun uploadEdge(edge: Edge) {
        val jsonEntry = Gson().toJson(edge)
        val jsonElement = JsonParser.parseString(jsonEntry)
        val jsonObject = jsonElement.asJsonObject
        val call = serverApi.uploadDataJson(jsonObject)

        // Commend API call (this should be done only at the end of a street segment)
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (!response.isSuccessful) {
                    Toast.makeText(context, "Failed to send JSON entry", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                t.printStackTrace()
            }
        })

    }

    suspend fun getCrossingCoordinates(crossingId: Int): LatLng {
        return withContext(Dispatchers.IO) {
            try {
                val response = serverApi.getCrossingCoordinates(crossingId).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val crossingJson = responseBody.asJsonObject
                        val latitude = crossingJson["latitude"].asDouble
                        val longitude = crossingJson["longitude"].asDouble
                        LatLng(latitude, longitude)
                    } else {
                        throw IOException("Empty response body")
                    }
                } else {
                    throw IOException("HTTP error: ${response.code()}")
                }
            } catch (e: IOException) {
                // Gestione di IOException
                throw e
            } catch (e: HttpException) {
                // Gestione di HttpException (errore HTTP)
                throw IOException("HTTP error: ${e.code()}")
            }
        }
    }

    suspend fun getCrossings(cityName: String): List<Crossing> {
        return try {
            withContext(Dispatchers.IO) {
                val response = serverApi.getCrossings(cityName)
                // stampo nel log
                Log.d("getAllCrossings", response.toString())
                response.crossings
            }
        } catch (e: IOException) {
            // Handle IOException
            throw e
        } catch (e: HttpException) {
            // Handle HttpException (HTTP error)
            throw IOException("HTTP error: ${e.code()}")
        }
    }



}


