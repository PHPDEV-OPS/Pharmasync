package com.example.pharmasync

import android.os.Bundle
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.ArrayList
import java.util.UUID

class Suppliers : Fragment() {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var fs: FirebaseFirestore
    private var supplierList = arrayListOf<Supplier>()
    private lateinit var adapter: SupplierAdapter
    
    private lateinit var rvSupplier: RecyclerView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var searchInput: EditText
    private lateinit var msgText: TextView
    private lateinit var addBtn: View
    private lateinit var reorderBtn: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_suppliers, container, false)
        
        rvSupplier = view.findViewById(R.id.rv_supplier)
        progressBar = view.findViewById(R.id.Progress_bar)
        searchInput = view.findViewById(R.id.search)
        msgText = view.findViewById(R.id.msg)
        addBtn = view.findViewById(R.id.add_supplier)
        reorderBtn = view.findViewById(R.id.reorder_btn)
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        fs = FirebaseFirestore.getInstance()

        rvSupplier.layoutManager = LinearLayoutManager(requireContext())
        getSuppliers()

        // Pharmacists can only see registered suppliers, not add manually
        addBtn.visibility = View.GONE
        reorderBtn.visibility = View.GONE

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSuppliers(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun getSuppliers() {
        progressBar.visibility = View.VISIBLE
        Log.d("Suppliers", "Fetching suppliers...")
        
        val currentUid = auth.currentUser?.uid ?: ""

        // 1. Try local Room cache first for immediate rendering
        Thread {
            try {
                val localDb = AppDatabase.getDatabase(requireContext().applicationContext)
                val cachedSuppliers = localDb.supplierDao().getAllSuppliers()
                if (cachedSuppliers != null && cachedSuppliers.isNotEmpty()) {
                    val mappedList = ArrayList(cachedSuppliers.map {
                        Supplier(it.supplierId, it.name, it.contact, it.email, it.address)
                    })
                    activity?.runOnUiThread {
                        if (isAdded && supplierList.isEmpty()) {
                            supplierList.clear()
                            supplierList.addAll(mappedList)
                            updateUi(supplierList)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Suppliers", "Room cache read exception", e)
            }
        }.start()

        // 2. Fetch live data from Firestore Users and Suppliers-Data
        val fetchedSuppliers = arrayListOf<Supplier>()
        val roomEntities = arrayListOf<SupplierEntity>()

        fs.collection("Users").get().addOnSuccessListener { snapshot ->
            for (doc in snapshot.documents) {
                val uid = doc.getString("Uid") ?: doc.id
                if (uid == currentUid) continue // Skip currently logged in user

                val role = doc.getString("Role")
                val isSupplierRole = role != null && (role.equals("Supplier", ignoreCase = true) || role.contains("supplier", ignoreCase = true))
                val isNotPharmacist = role == null || !role.equals("Pharmacist", ignoreCase = true)

                if (isSupplierRole || isNotPharmacist) {
                    val shopName = doc.getString("Shop-Name") ?: doc.getString("ShopName") ?: doc.getString("Uname") ?: "Supplier Account"
                    val email = doc.getString("Email") ?: doc.getString("Contact") ?: "contact@supplier.com"
                    val address = doc.getString("Address") ?: "Registered Office"

                    val supplier = Supplier(
                        SupplierId = uid,
                        Name = shopName,
                        Contact = email,
                        Email = email,
                        Address = address
                    )
                    if (fetchedSuppliers.none { it.SupplierId == uid }) {
                        fetchedSuppliers.add(supplier)
                        roomEntities.add(SupplierEntity(uid, shopName, email, email, address))
                    }
                }
            }


            // Also check Suppliers-Data collection
            fs.collection("Suppliers-Data").get().addOnSuccessListener { suppSnapshot ->
                for (doc in suppSnapshot.documents) {
                    val uid = doc.getString("Uid") ?: doc.id
                    if (uid == currentUid) continue

                    if (fetchedSuppliers.none { it.SupplierId == uid }) {
                        val name = doc.getString("Name") ?: doc.getString("Shop-Name") ?: doc.getString("Uname") ?: "Supplier"
                        val email = doc.getString("Email") ?: ""
                        val address = doc.getString("Address") ?: ""
                        val supplier = Supplier(uid, name, email, email, address)
                        fetchedSuppliers.add(supplier)
                        roomEntities.add(SupplierEntity(uid, name, email, email, address))
                    }
                }

                // If no suppliers exist in database yet, provide default verified suppliers for testing
                if (fetchedSuppliers.isEmpty()) {
                    val defaultSuppliers = listOf(
                        Supplier("demo_supplier_1", "PharmaDirect Wholesalers", "orders@pharmadirect.com", "orders@pharmadirect.com", "Central Logistics Park, Building 4"),
                        Supplier("demo_supplier_2", "MedSync Supply Co.", "support@medsyncsupply.com", "support@medsyncsupply.com", "North Industrial Zone, Suite 12"),
                        Supplier("demo_supplier_3", "Apex Pharma Distributors", "sales@apexpharma.com", "sales@apexpharma.com", "742 Medical Center Blvd")
                    )
                    fetchedSuppliers.addAll(defaultSuppliers)
                    for (s in defaultSuppliers) {
                        roomEntities.add(SupplierEntity(s.SupplierId!!, s.Name, s.Contact, s.Email, s.Address))
                    }
                }

                progressBar.visibility = View.GONE
                supplierList.clear()
                supplierList.addAll(fetchedSuppliers)
                updateUi(supplierList)

                // Sync to Room Database
                Thread {
                    try {
                        val db = AppDatabase.getDatabase(requireContext().applicationContext)
                        db.supplierDao().clearAll()
                        db.supplierDao().insertAll(roomEntities)
                    } catch (e: Exception) {
                        Log.e("Suppliers", "Room cache write exception", e)
                    }
                }.start()

            }.addOnFailureListener {
                finishLoadingSuppliers(fetchedSuppliers, roomEntities)
            }
        }.addOnFailureListener { e ->
            Log.e("Suppliers", "Error fetching users", e)
            finishLoadingSuppliers(fetchedSuppliers, roomEntities)
        }
    }

    private fun finishLoadingSuppliers(fetchedSuppliers: ArrayList<Supplier>, roomEntities: ArrayList<SupplierEntity>) {
        progressBar.visibility = View.GONE
        if (fetchedSuppliers.isEmpty()) {
            val defaultSuppliers = listOf(
                Supplier("demo_supplier_1", "PharmaDirect Wholesalers", "orders@pharmadirect.com", "orders@pharmadirect.com", "Central Logistics Park, Building 4"),
                Supplier("demo_supplier_2", "MedSync Supply Co.", "support@medsyncsupply.com", "support@medsyncsupply.com", "North Industrial Zone, Suite 12")
            )
            fetchedSuppliers.addAll(defaultSuppliers)
        }
        supplierList.clear()
        supplierList.addAll(fetchedSuppliers)
        updateUi(supplierList)
    }


    private fun updateUi(list: ArrayList<Supplier>) {
        adapter = SupplierAdapter(requireContext(), list)
        rvSupplier.adapter = adapter

        if (list.isEmpty()) {
            msgText.visibility = View.VISIBLE
        } else {
            msgText.visibility = View.GONE
        }

        adapter.onEdit(object : SupplierAdapter.OnEditClickListener {
            override fun onEditClick(position: Int) {
                showReorderForSupplierDialog(list[position])
            }
        })

        adapter.onItem(object : SupplierAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                showReorderForSupplierDialog(list[position])
            }
        })
    }

    private fun showReorderForSupplierDialog(supplier: Supplier) {
        fs.collection("Medicines").document(auth.currentUser?.uid!!)
            .collection("MyMedicines").get()
            .addOnSuccessListener { snapshot ->
                val medList = arrayListOf<medicine>()
                for (doc in snapshot.documents) {
                    val med = doc.toObject(medicine::class.java)
                    if (med != null) {
                        medList.add(med)
                    }
                }

                if (medList.isEmpty()) {
                    customSnackbar("No medicines found in your inventory")
                    return@addOnSuccessListener
                }

                val medNames = medList.map { it.Medicine ?: "Unknown" }.toTypedArray()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Reorder from ${supplier.Name}")
                    .setItems(medNames) { _, which ->
                        showQuantityDialog(medList[which], supplier)
                    }
                    .show()
            }
    }

    private fun showQuantityDialog(med: medicine, supplier: Supplier) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.edit_dialog, null)
        val input = view.findViewById<TextInputEditText>(R.id.m_qty)
        view.findViewById<TextView>(R.id.textView).text = "Order Quantity for ${med.Medicine}"
        view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layout_3).hint = "Quantity"
        input.setText("100")

        MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setPositiveButton("Place Order") { _, _ ->
                val qty = input.text.toString().trim()
                if (qty.isNotEmpty()) {
                    placeFinalOrder(med, supplier, qty)
                } else {
                    customSnackbar("Please enter quantity")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun placeFinalOrder(med: medicine, supplier: Supplier, qty: String) {
        val uid = auth.currentUser?.uid ?: return
        val orderId = UUID.randomUUID().toString()

        fs.collection("Users").document(uid).get().addOnSuccessListener { doc ->
            val pharmacistName = doc.getString("Shop-Name") ?: "Unknown Pharmacy"

            val order = Order(
                OrderId = orderId,
                PharmacistId = uid,
                PharmacistName = pharmacistName,
                SupplierId = supplier.SupplierId,
                MedicineName = med.Medicine,
                Quantity = qty,
                Status = "Pending",
                CreatedAt = com.google.firebase.Timestamp.now()
            )

            fs.collection("Orders").document(orderId).set(order)
                .addOnSuccessListener {
                    customSnackbar("Order of $qty ${med.Medicine} placed to ${supplier.Name}")
                }
                .addOnFailureListener {
                    customSnackbar("Failed to place order")
                }
        }
    }

    private fun filterSuppliers(query: String) {
        val filtered = supplierList.filter {
            it.Name?.contains(query, ignoreCase = true) == true
        }
        updateUi(ArrayList(filtered))
    }

    private fun customSnackbar(message: String) {
        val bar = Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT)
        bar.setBackgroundTint(resources.getColor(R.color.blue))
        bar.setTextColor(resources.getColor(R.color.white))
        bar.show()
    }
}
