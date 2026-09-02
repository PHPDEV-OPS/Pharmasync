package com.example.pharmasync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OrderAdapter(
    private var orderList: List<Order>,
    private val onAction: (Order, String) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val pharmacist: TextView = view.findViewById(R.id.o_pharmacist)
        val medicine: TextView = view.findViewById(R.id.o_medicine)
        val qty: TextView = view.findViewById(R.id.o_qty)
        val status: TextView = view.findViewById(R.id.o_status)
        val actionLayout: LinearLayout = view.findViewById(R.id.action_layout)
        val acceptBtn: Button = view.findViewById(R.id.btn_accept)
        val cancelBtn: Button = view.findViewById(R.id.btn_cancel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.order_rv, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orderList[position]
        holder.pharmacist.text = order.PharmacistName
        holder.medicine.text = order.MedicineName
        holder.qty.text = "Quantity: ${order.Quantity}"
        holder.status.text = "Status: ${order.Status}"

        if (order.Status == "Pending") {
            holder.actionLayout.visibility = View.VISIBLE
        } else {
            holder.actionLayout.visibility = View.GONE
        }

        holder.acceptBtn.setOnClickListener { onAction(order, "Accepted") }
        holder.cancelBtn.setOnClickListener { onAction(order, "Cancelled") }
    }

    override fun getItemCount(): Int = orderList.size
}
