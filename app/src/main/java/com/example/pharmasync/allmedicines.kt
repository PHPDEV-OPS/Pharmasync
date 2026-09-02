package com.example.pharmasync

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pharmasync.databinding.FragmentAllmedicinesBinding
import com.example.pharmasync.databinding.FragmentMedicinesBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.StorageReference


class allmedicines : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var fs: FirebaseFirestore
    private lateinit var sr: StorageReference
    lateinit var binding: FragmentAllmedicinesBinding
    lateinit var medicineList: ArrayList<medicine>
    var imageUri: Uri = android.net.Uri.EMPTY
    private var m_img: ImageView? = null
    var textWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            // this function is called before text is edited
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            // this function is called when text is edited
            search()
        }

        override fun afterTextChanged(s: Editable) {
            // this function is called after text is edited
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAllmedicinesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        fs = FirebaseFirestore.getInstance()
        medicineList = arrayListOf()
        val recyclerView = binding.rvMedicine
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        get_data()
        binding.search.addTextChangedListener(textWatcher)
    }
    fun get_data() {
        binding.ProgressBar.visibility = View.VISIBLE
        fs.collectionGroup("MyMedicines").get().addOnSuccessListener { snapshot ->
            binding.ProgressBar.visibility = View.GONE
            medicineList.clear()
            for (doc in snapshot.documents) {
                val r = doc.toObject(medicine::class.java)
                if (r != null) {
                    medicineList.add(r)
                }
            }
            medicineList.sortByDescending { it.CreatedAt }
            Log.d("D_CHECK", "get_data: fetched ${medicineList.size} medicines")
            load_data(medicineList)
        }.addOnFailureListener { e ->
            binding.ProgressBar.visibility = View.GONE
            Log.e("D_CHECK", "Error fetching all medicines", e)
        }
    }

    fun load_data(medicine: ArrayList<medicine>) {
//        simulateDataLoading()
        val adapter = allmedicinesAdapter(requireContext(), medicine)
        binding.rvMedicine.adapter = adapter
        if (medicine.size == 0) {
            binding.msg.visibility = View.GONE
        }
//        binding.rvMedicine.viewTreeObserver.addOnGlobalLayoutListener {
//            binding.ProgressBar.visibility = View.GONE
//        }
    }
    private fun simulateDataLoading() {
        binding.ProgressBar.postDelayed({
            binding.rvMedicine.visibility = View.VISIBLE

        }, 5000)
    }
    fun search() {
        if (binding.search.text.toString().isNotEmpty()) {
            val filterList = medicineList.filter {
                it.Medicine?.startsWith(
                    binding.search.text.toString(),
                    ignoreCase = true
                )!!
            }
            load_data(java.util.ArrayList(filterList))
        } else {
            load_data(medicineList)
        }

    }
}