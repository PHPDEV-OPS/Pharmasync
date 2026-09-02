package com.example.pharmasync

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SupplierAdapter(private val context: Context, private var supplierList: List<Supplier>) :
    RecyclerView.Adapter<SupplierAdapter.SupplierViewHolder>() {

    private lateinit var editClickListener: OnEditClickListener
    private lateinit var itemClickListener: OnItemClickListener

    interface OnEditClickListener {
        fun onEditClick(position: Int)
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    fun onEdit(listener: OnEditClickListener) {
        editClickListener = listener
    }

    fun onItem(listener: OnItemClickListener) {
        itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SupplierViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.supplier_rv, parent, false)
        return SupplierViewHolder(view)
    }

    override fun onBindViewHolder(holder: SupplierViewHolder, position: Int) {
        val supplier = supplierList[position]
        holder.name.text = supplier.Name ?: "Supplier"
        holder.contact.text = if (!supplier.Contact.isNullOrEmpty()) supplier.Contact else (supplier.Email ?: supplier.Address ?: "Registered Supplier")

        holder.editBtn.setOnClickListener {
            if (::editClickListener.isInitialized) {
                editClickListener.onEditClick(position)
            }
        }

        holder.itemView.setOnClickListener {
            if (::itemClickListener.isInitialized) {
                itemClickListener.onItemClick(position)
            }
        }
    }

    override fun getItemCount(): Int {
        return supplierList.size
    }

    class SupplierViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.s_name)
        val contact: TextView = itemView.findViewById(R.id.s_contact)
        val editBtn: ImageButton = itemView.findViewById(R.id.edit_btn)
    }
}
