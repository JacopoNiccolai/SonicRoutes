package com.unipi.dii.sonicroutes.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.unipi.dii.sonicroutes.databinding.FragmentDashboardBinding
import java.io.File

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textDashboard
        dashboardViewModel.loadData(requireContext())
        dashboardViewModel.text.observe(viewLifecycleOwner) { text ->
            textView.text = text
        }

        binding.deleteButton.setOnClickListener {
            deleteDataFile()
        }

        // Setta l'ascoltatore per il pulsante di condivisione
        binding.shareButton.setOnClickListener {
            shareDataFile()
        }

        return root
    }

    private fun deleteDataFile() {
        val file = File(requireContext().filesDir, "data.json")
        if (file.exists()) {
            if (file.delete()) {
                Toast.makeText(requireContext(), "File deleted successfully", Toast.LENGTH_SHORT).show()
                // Informa il ViewModel che i dati sono stati eliminati
                val dashboardViewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
                dashboardViewModel.loadData(requireContext())  // Ricarica i dati o reimposta il testo
            } else {
                Toast.makeText(requireContext(), "Failed to delete file", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "File does not exist", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareDataFile() {
        val file = File(requireContext().filesDir, "data.json")
        if (file.exists()) {
            val uri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share data.json via:"))
        } else {
            Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
