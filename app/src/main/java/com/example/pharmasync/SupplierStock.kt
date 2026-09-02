package com.example.pharmasync

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.ArrayList
import java.util.UUID

class SupplierStock : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var fs: FirebaseFirestore
    private var stockList = arrayListOf<medicine>()
    
    private lateinit var rvStock: RecyclerView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var msgText: TextView
    private lateinit var addBtn: ExtendedFloatingActionButton
    private var medicineNameInput: TextInputEditText? = null

    private val barcodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCAN_RESULT")
            medicineNameInput?.setText(barcode)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_supplier_stock, container, false)
        rvStock = view.findViewById(R.id.rv_stock)
        progressBar = view.findViewById(R.id.ProgressBar)
        msgText = view.findViewById(R.id.msg)
        addBtn = view.findViewById(R.id.add_stock)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        fs = FirebaseFirestore.getInstance()

        rvStock.layoutManager = LinearLayoutManager(requireContext())
        getStock()

        addBtn.setOnClickListener {
            showAddStockDialog()
        }
    }

    private fun getStock() {
        progressBar.visibility = View.VISIBLE
        val uid = auth.currentUser?.uid ?: return

        // Load Room cache first safely
        Thread {
            try {
                val localDb = AppDatabase.getDatabase(requireContext().applicationContext)
                val cached = localDb.medicineDao().getMedicinesForUser(uid)
                if (cached != null && cached.isNotEmpty()) {
                    val items = ArrayList(cached.map {
                        medicine(
                            Medicine = it.medicineName ?: "N/A",
                            Description = it.description ?: "",
                            PricePerUnit = it.pricePerUnit ?: "0",
                            MedicineId = it.medicineId,
                            Stock = it.stock ?: "0",
                            Category = it.category ?: "General",
                            LowStock = it.lowStock ?: "-1",
                            UserID = it.userId
                        )
                    })
                    activity?.runOnUiThread {
                        if (isAdded && stockList.isEmpty()) {
                            stockList.clear()
                            stockList.addAll(items)
                            updateUi()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SupplierStock", "Room cache error", e)
            }
        }.start()

        // Real-time sync for stock
        fs.collection("Supplier-Stock").document(uid).collection("MyStock")
            .addSnapshotListener { snapshot, e ->
                progressBar.visibility = View.GONE
                if (e != null) return@addSnapshotListener
                
                if (snapshot != null) {
                    stockList.clear()
                    val roomEntities = arrayListOf<MedicineEntity>()
                    for (doc in snapshot.documents) {
                        val item = doc.toObject(medicine::class.java)
                        if (item != null) {
                            stockList.add(item)
                            roomEntities.add(
                                MedicineEntity(
                                    item.MedicineId ?: doc.id,
                                    item.Medicine ?: "N/A",
                                    item.Description ?: "",
                                    item.PricePerUnit ?: "0",
                                    item.Stock ?: "0",
                                    item.Category ?: "General",
                                    item.LowStock ?: "-1",
                                    uid
                                )
                            )
                        }
                    }
                    updateUi()

                    // Sync to Room Database safely
                    Thread {
                        try {
                            val localDb = AppDatabase.getDatabase(requireContext().applicationContext)
                            localDb.medicineDao().clearForUser(uid)
                            localDb.medicineDao().insertAll(roomEntities)
                        } catch (e: Exception) {
                            Log.e("SupplierStock", "Room save error", e)
                        }
                    }.start()
                }
            }
    }

    private fun updateUi() {
        if (stockList.isEmpty()) {
            msgText.visibility = View.VISIBLE
            rvStock.visibility = View.GONE
        } else {
            msgText.visibility = View.GONE
            rvStock.visibility = View.VISIBLE
            rvStock.adapter = medicineAdapter(requireContext(), stockList).apply {
                onEdit(object : medicineAdapter.EditClick {
                    override fun onEditClick(position: Int) {
                        showEditStockDialog(stockList[position])
                    }
                })
                onItem(object : medicineAdapter.onitemclick {
                    override fun itemClickListener(position: Int) {
                        // Optional: Show details
                    }
                })
            }
        }
    }

    private fun showAddStockDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.medicine_add, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        val submitBtn = view.findViewById<Button>(R.id.submit_btn)
        medicineNameInput = view.findViewById<TextInputEditText>(R.id.medicine_name)

        view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layout_1).setEndIconOnClickListener {
            val intent = Intent(requireContext(), BarcodeScannerActivity::class.java)
            barcodeLauncher.launch(intent)
        }

        submitBtn.setOnClickListener {
            val name = view.findViewById<TextInputEditText>(R.id.medicine_name).text.toString().trim()
            val stock = view.findViewById<TextInputEditText>(R.id.stock).text.toString().trim()
            val price = view.findViewById<TextInputEditText>(R.id.priceper_unit).text.toString().trim()
            val category = view.findViewById<TextInputEditText>(R.id.category).text.toString().trim()
            val description = view.findViewById<TextInputEditText>(R.id.description).text.toString().trim()
            
            if (name.isNotEmpty() && stock.isNotEmpty()) {
                submitBtn.isEnabled = false // Prevent duplicate records
                
                val id = UUID.randomUUID().toString()
                val uid = auth.currentUser?.uid ?: ""

                val stockData = hashMapOf(
                    "Medicine" to name,
                    "Stock" to stock,
                    "PricePerUnit" to if (price.isNotEmpty()) price else "0",
                    "MedicineId" to id,
                    "Category" to if (category.isNotEmpty()) category else "General",
                    "Description" to description,
                    "LowStock" to "-1",
                    "UserID" to uid,
                    "CreatedAt" to com.google.firebase.Timestamp.now(),
                    "ImageUri" to ""
                )
                
                fs.collection("Supplier-Stock").document(uid)
                    .collection("MyStock").document(id).set(stockData)
                    .addOnSuccessListener {
                        customSnackbar("Item Added to Stock")
                        dialog.dismiss()
                    }
                    .addOnFailureListener {
                        submitBtn.isEnabled = true
                        customSnackbar("Failed to add item")
                    }
            } else {
                customSnackbar("Please enter name and stock")
            }
        }
        dialog.show()
    }

    private fun showEditStockDialog(item: medicine) {
        val view = View.inflate(requireContext(), R.layout.edit_dialog, null)
        view.findViewById<TextView>(R.id.textView).text = item.Medicine
        view.findViewById<TextInputEditText>(R.id.m_qty).text = Editable.Factory.getInstance().newEditable(item.Stock)

        MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newStock = view.findViewById<TextInputEditText>(R.id.m_qty).text.toString()
                fs.collection("Supplier-Stock").document(auth.currentUser?.uid!!)
                    .collection("MyStock").document(item.MedicineId!!).update("Stock", newStock)
                    .addOnSuccessListener {
                        customSnackbar("Stock Updated")
                    }
            }
            .show()
    }

    private fun customSnackbar(message: String) {
        val bar = Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT)
        bar.setBackgroundTint(resources.getColor(R.color.blue))
        bar.setTextColor(resources.getColor(R.color.white))
        bar.show()
    }
}
