package com.unipi.dii.sonicroutes.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

        return root
    }

    private fun deleteDataFile() {
        val file = File(requireContext().filesDir, "data.json")
        if (file.exists()) {
            file.delete()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
