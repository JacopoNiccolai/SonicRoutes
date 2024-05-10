package com.unipi.dii.sonicroutes.ui.dashboard
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.unipi.dii.sonicroutes.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale


class FileAdapter(private val files: MutableList<File> = mutableListOf()) :
    RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

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
                // Quando l'elemento viene cliccato, avvia una nuova Activity (in cui mostro per esempio l'heatmap del rumore del percorso seguito)
                // todo: implementare la nuova schermata, per ora mostro solo un toast
                Toast.makeText(holder.itemView.context, "Route clicked : $timestamp", Toast.LENGTH_SHORT).show()
                /*val context = holder.itemView.context
                val intent = Intent(context, RouteFragment::class.java)
                intent.putExtra("fileName", file.name) // Aggiungi il nome del file all'intent
                context.startActivity(intent)*/
            }

            holder.buttonShare.setOnClickListener {
                shareFile(file,holder.itemView.context)
            }
            holder.buttonDelete.setOnClickListener {
                deleteFile(file,holder.itemView.context)
            }
        }
    }

    private fun File.extractTimestampFromDataFileName(): String? {
        val timestampString = name.substringAfter("data_").substringBeforeLast(".json")
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
        val timestamp = file.name.substringAfter("data_").substringBefore(".json")
        if (file.delete() && position != -1) {
            files.removeAt(position) // Rimuove il file dall'elenco
            notifyItemRemoved(position) // Notifica alla RecyclerView la rimozione dell'elemento
            // take the timestamp fromthe file name
            Toast.makeText(context, "Route deleted : $timestamp", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Unable to delete route : $timestamp", Toast.LENGTH_SHORT).show()
        }
    }



    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.text_file_name)
        val buttonShare: Button = itemView.findViewById(R.id.button_share)
        val buttonDelete: Button = itemView.findViewById(R.id.button_delete)
    }
}
