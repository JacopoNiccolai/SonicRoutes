package com.unipi.dii.sonicroutes.network

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.unipi.dii.sonicroutes.MainActivity
import com.unipi.dii.sonicroutes.model.Crossing
import com.unipi.dii.sonicroutes.model.Edge
import com.unipi.dii.sonicroutes.model.Route
import com.unipi.dii.sonicroutes.model.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import kotlin.coroutines.resume

class ClientManager (private val context: Context){

    private val serverApi: ServerApi

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.1.1.23:5000/")
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

        // Convert the JSON array to a list of LatLng objects
        for (element in path) {
            val lat = element.asJsonArray[0].asDouble
            val lng = element.asJsonArray[1].asDouble
            locations.add(LatLng(lat, lng))
        }

        // create a segment for each pair of points
        val segments = mutableListOf<Segment>()
        for (i in 0 until locations.size - 1) {
            val start = locations[i]
            val end = locations[i + 1]
            val segment = Segment(start, end)
            segments.add(segment)
        }

        // create a route from the segments
        val route = Route(segments)

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

    suspend fun getCrossingCoordinates(context: Context, crossingId: Int): LatLng {
        return retryOnFailure(context) {
            withContext(Dispatchers.IO) {
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
            }
        }
    }

    suspend fun getCrossings(context: Context, cityName: String): List<Crossing> {
        return retryOnFailure(context) {
            val response = serverApi.getCrossings(cityName)
            response.crossings
        }
    }

    private suspend fun showRetryDialog(context: Context): Boolean {
        return suspendCancellableCoroutine { continuation ->
            AlertDialog.Builder(context)
                .setTitle("Server not reachable right now")
                .setMessage("Impossible to connect to the server. Please try again later.")
                .setCancelable(false)
                .setPositiveButton("Retry") { _, _ ->
                    continuation.resume(true)
                }
                .setNegativeButton("Close the app") { _, _ ->
                    continuation.resume(false)
                    MainActivity.instance?.finishAffinity()
                }
                .show()
        }
    }

    private suspend fun <T> retryOnFailure(
        context: Context,
        apiCall: suspend () -> T
    ): T {
        return try {
            withContext(Dispatchers.IO) {
                apiCall()
            }
        } catch (e: IOException) {
            val shouldRetry = withContext(Dispatchers.Main) { showRetryDialog(context) }
            if (shouldRetry) {
                retryOnFailure(context, apiCall)
            } else {
                throw e
            }
        } catch (e: HttpException) {
            throw IOException("HTTP error: ${e.code()}")
        }
    }
}
