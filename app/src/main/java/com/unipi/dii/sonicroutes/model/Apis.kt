package com.unipi.dii.sonicroutes.model

import android.content.Context
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.unipi.dii.sonicroutes.ui.network.ServerApi
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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

    fun sendPoints(point1: LatLng, point2: LatLng) {
        val points = Points(point1, point2)
        val call = serverApi.sendPoints(points)

        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    // Mostra un messaggio di successo all'utente
                    Toast.makeText(context, "Punti inviati correttamente al server", Toast.LENGTH_SHORT).show()
                } else {
                    // Mostra un messaggio di errore all'utente
                    Toast.makeText(context, "Errore nell'invio dei punti al server", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                // Mostra un messaggio di errore all'utente
                Toast.makeText(context, "Errore di rete: ${t.message}", Toast.LENGTH_SHORT).show()
                t.printStackTrace()
            }
        })
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

}