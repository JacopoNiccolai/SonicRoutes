package com.unipi.dii.sonicroutes.ui.tracking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.unipi.dii.sonicroutes.R
import com.unipi.dii.sonicroutes.network.ClientManager
import com.unipi.dii.sonicroutes.model.Crossing



class SearchResultAdapter(
    private val crossings: List<Crossing>, private val query: String, private val userLocation: LatLng,
    private val searchView: SearchView,
    private val clickListener: SearchResultClickListener

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
        val streetMatch = crossing.getStreetName().find { street -> street.contains(query, ignoreCase = true) }
        val streetText = streetMatch ?: holder.itemView.context.getString(R.string.street_not_found)
        val checkpointText = holder.itemView.context.getString(R.string.checkpoint_text, crossing.getId(), streetText)
        holder.checkpointTextView.text = checkpointText
        holder.itemView.setOnClickListener {

            // invio al server la posizione corrente e quella del checkpoint finale
            val endingPoint = crossing.getCoordinates()

            ClientManager(holder.itemView.context).getRoute(userLocation,endingPoint,
                onComplete = { route ->
                    // show the route on the map of the HomeFragment
                    clickListener.onSearchResultClicked(route)
                },
                onError = { errorMessage ->
                    Toast.makeText(holder.itemView.context, errorMessage, Toast.LENGTH_SHORT).show()
                }
            )
            searchView.setQuery("", false)
            searchView.clearFocus()
        }
    }

    override fun getItemCount(): Int {
        return crossings.size
    }
}


