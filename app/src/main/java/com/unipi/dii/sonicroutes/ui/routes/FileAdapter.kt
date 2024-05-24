package com.unipi.dii.sonicroutes.ui.routes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.unipi.dii.sonicroutes.R
import com.unipi.dii.sonicroutes.databinding.ItemFileBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class FileAdapter(
    private val files: MutableList<File> = mutableListOf(),
    private val fragmentManager: FragmentManager
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        if (files.size == 1 && files[0].name.isBlank()) {
            holder.fileName.text = holder.itemView.context.getString(R.string.no_routes_to_show)
            holder.buttonShare.visibility = View.GONE
            holder.buttonDelete.visibility = View.GONE
        } else {
            val file = files[position]
            val timestamp = file.extractTimestampFromDataFileName()
            if (timestamp != null) {
                holder.fileName.text = timestamp.toString()
            } else {
                holder.fileName.text = holder.itemView.context.getString(R.string.invalid_timestamp)
            }

            holder.itemView.setOnClickListener {
                val bundle = Bundle().apply {
                    putString("fileName", file.name)
                }
                val routeFragment = RouteFragment().apply {
                    arguments = bundle
                }
                fragmentManager.beginTransaction() // Use the passed fragmentManager
                    .replace(R.id.route_fragment_container, routeFragment)
                    .addToBackStack(null)
                    .commit()
            }

            holder.buttonShare.setOnClickListener {
                shareFile(file, holder.itemView.context)
            }
            holder.buttonDelete.setOnClickListener {
                deleteFile(file, holder.itemView.context)
            }
        }
    }

    private fun File.extractTimestampFromDataFileName(): String? {
        val timestampString = name.substringAfter("data_").substringBeforeLast(".csv")
        val inputFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return try {
            val date = inputFormat.parse(timestampString)
            date?.let { outputFormat.format(it) }
        } catch (e: Exception) {
            null
        }
    }

    override fun getItemCount(): Int {
        return if (files.size == 1 && files[0].name.isBlank()) {
            1 // Visualizza solo il messaggio "No routes to show", dummy item
        } else {
            files.size
        }
    }

    private fun shareFile(file: File, context: Context) {
        if (file.exists()) {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name} via:"))
        } else {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteFile(file: File, context: Context) {
        val position = files.indexOf(file)
        val timestamp = file.name.substringAfter("data_").substringBefore(".csv")
        if (file.delete() && position != -1) {
            files.removeAt(position) // Rimuove il file dall'elenco
            notifyItemRemoved(position) // Notifica alla RecyclerView la rimozione dell'elemento
            // take the timestamp from the file name
            Toast.makeText(context, "Route deleted : $timestamp", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Unable to delete route : $timestamp", Toast.LENGTH_SHORT).show()
        }
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val binding: ItemFileBinding = DataBindingUtil.bind(itemView)!!
        val fileName: TextView = binding.textFileName
        val buttonShare: Button = binding.buttonShare
        val buttonDelete: Button = binding.buttonDelete
    }
}
