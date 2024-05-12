package com.unipi.dii.sonicroutes.ui.home

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.unipi.dii.sonicroutes.R
import com.unipi.dii.sonicroutes.model.Apis
import com.unipi.dii.sonicroutes.model.Crossing


// TODO: Jacopo dove van messi gli adapter?
class SearchResultAdapter(
    private val crossings: List<Crossing>, private val query: String, private val userLocation: LatLng,
    private val searchView: SearchView

) :
    RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder>() {

    inner class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkpointTextView: TextView = itemView.findViewById(R.id.checkpointTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        val crossing = crossings[position]
        val streetMatch = crossing.streetName.find { street -> street.contains(query, ignoreCase = true) }
        val streetText = streetMatch ?: holder.itemView.context.getString(R.string.street_not_found)
        val checkpointText = holder.itemView.context.getString(R.string.checkpoint_text, crossing.id, streetText)
        holder.checkpointTextView.text = checkpointText
        holder.itemView.setOnClickListener {
            // TODO : Implementa un'azione quando viene cliccato un risultato di ricerca
            Log.e("SearchResultAdapter", "Cliccato su ${crossing.id}")
            // invio al server la posizione corrente e quella del checkpoint finale
            val endingPoint = crossing.getCoordinates()
            Log.e("SearchResultAdapter", "Punto iniziale : $userLocation Punto finale: $endingPoint")
            Apis(holder.itemView.context).getRoute(userLocation,endingPoint)
            searchView.setQuery("", false)
            searchView.clearFocus()

        }
    }

    override fun getItemCount(): Int {
        return crossings.size
    }
}

