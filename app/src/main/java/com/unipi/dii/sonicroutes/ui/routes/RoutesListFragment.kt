package com.unipi.dii.sonicroutes.ui.routes

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.unipi.dii.sonicroutes.R
import java.io.File
import java.text.SimpleDateFormat

class RoutesListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_dashboard, container, false)
        recyclerView = rootView.findViewById(R.id.recycler_view_dashboard)
        adapter = FileAdapter(getDataFiles().toMutableList(),childFragmentManager)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        //tolgo tutti i figli dal childFragmentManager
        for (fragment in childFragmentManager.fragments) {
            childFragmentManager.beginTransaction().remove(fragment).commit()
        }
        return rootView
    }

    private fun getDataFiles(): List<File> {
        val context = requireContext().applicationContext
        val files = context.filesDir.listFiles { file ->
            file.name.startsWith("data_")
        }?.toList()

        // Define a date format to parse the timestamp
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")

        // Sort the files based on the parsed timestamp, in descending order
        val sortedFiles = files?.sortedByDescending { file ->
            val timestampStr = file.name.substringAfter("data_").substringBefore(".csv")
            dateFormat.parse(timestampStr)
        }

        return if (sortedFiles.isNullOrEmpty()) {
            listOf(File("")) // Aggiungi un oggetto "dummy" quando non ci sono file
        } else {
            sortedFiles
        }
    }

}
