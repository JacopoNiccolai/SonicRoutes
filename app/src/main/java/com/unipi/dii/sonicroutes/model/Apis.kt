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

    fun getRoute(point1: LatLng, point2: LatLng) {
        val points = Points(point1, point2)
        val call = serverApi.sendPoints(points)

        // retrieve the path from the server
        call.enqueue(object: Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    val jsonResponse = response.body()
                    val path = jsonResponse?.getAsJsonArray("path")
                    if (path != null) {
                        handlePath(path)
                    } else {
                        Toast.makeText(context, "Invalid server response", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Failed to send points to the server", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                Toast.makeText(context, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                t.printStackTrace()
            }
        })

    }

    private fun handlePath(path: JsonArray) {
        // Esegui le operazioni necessarie con il percorso ottenuto dal server
        // Ad esempio, puoi iterare sugli elementi della lista `path`
        for (element in path) {
            // Esempio di operazione: stampa gli elementi del percorso
            Log.i("PathElement", element.toString())
        }
    }



    fun uploadEdge(edge: Edge) {
        val jsonEntry = Gson().toJson(edge)
        val jsonElement = JsonParser.parseString(jsonEntry)
        val jsonObject = jsonElement.asJsonObject
        val call = serverApi.uploadDataJson(jsonObject)

        // Commend API call (this should be done only at the end of a street segment)
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    // todo : rimuovi, altrimenti ogni volta l'utente vede un toast
                    Toast.makeText(context, "JSON entry sent correctly", Toast.LENGTH_SHORT).show()
                } else {
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
                val responseBody = response.toString()
                // stampo nel log
                Log.e("getAllCrossings", responseBody)

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


