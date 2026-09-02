package com.example.pharmasync

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SupplierOrders : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var fs: FirebaseFirestore
    private var orderList = arrayListOf<Order>()
    
    private lateinit var rvOrders: RecyclerView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var msgText: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_supplier_orders, container, false)
        rvOrders = view.findViewById(R.id.rv_orders)
        progressBar = view.findViewById(R.id.ProgressBar)
        msgText = view.findViewById(R.id.msg)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        fs = FirebaseFirestore.getInstance()

        rvOrders.layoutManager = LinearLayoutManager(requireContext())
        getOrders()
    }

    private fun getOrders() {
        progressBar.visibility = View.VISIBLE
        val uid = auth.currentUser?.uid ?: return

        // Use Snapshot Listener for real-time sync with database
        fs.collection("Orders")
            .whereEqualTo("SupplierId", uid)
            .addSnapshotListener { snapshot, e ->
                progressBar.visibility = View.GONE
                if (e != null) {
                    Log.e("SupplierOrders", "Listen failed", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    orderList.clear()
                    for (doc in snapshot.documents) {
                        val order = doc.toObject(Order::class.java)
                        if (order != null) orderList.add(order)
                    }
                    orderList.sortByDescending { it.CreatedAt }
                    updateUi()
                }
            }
    }

    private fun updateUi() {
        if (orderList.isEmpty()) {
            msgText.visibility = View.VISIBLE
            rvOrders.visibility = View.GONE
        } else {
            msgText.visibility = View.GONE
            rvOrders.visibility = View.VISIBLE
            rvOrders.adapter = OrderAdapter(orderList) { order, newStatus ->
                updateOrderStatus(order, newStatus)
            }
        }
    }

    private fun updateOrderStatus(order: Order, newStatus: String) {
        val orderId = order.OrderId ?: return
        fs.collection("Orders").document(orderId)
            .update("Status", newStatus)
            .addOnSuccessListener {
                customSnackbar("Order $newStatus")
            }
            .addOnFailureListener {
                customSnackbar("Failed to update order")
            }
    }

    private fun customSnackbar(message: String) {
        val bar = Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT)
        bar.setBackgroundTint(resources.getColor(R.color.blue))
        bar.setTextColor(resources.getColor(R.color.white))
        bar.show()
    }
}
