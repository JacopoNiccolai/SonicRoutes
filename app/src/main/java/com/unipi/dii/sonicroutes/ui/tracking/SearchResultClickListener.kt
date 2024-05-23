package com.unipi.dii.sonicroutes.ui.tracking

import com.unipi.dii.sonicroutes.model.Route

interface SearchResultClickListener {
    fun onSearchResultClicked(route: Route)
}
