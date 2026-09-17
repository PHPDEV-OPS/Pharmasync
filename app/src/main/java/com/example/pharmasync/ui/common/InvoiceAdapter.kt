package com.example.pharmasync.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmasync.R
import com.example.pharmasync.data.model.Invoice
import com.example.pharmasync.databinding.ItemInvoiceBinding
import com.example.pharmasync.util.Formatters
import com.example.pharmasync.util.ImageLoader
import com.example.pharmasync.util.visibleIf

class InvoiceAdapter(private val onClick: (Invoice) -> Unit) : ListAdapter<Invoice, InvoiceAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemInvoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemInvoiceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(invoice: Invoice) = with(binding) {
            name.text = invoice.name
            description.text = invoice.description
            description.visibleIf(invoice.description.isNotBlank())
            date.text = Formatters.date(invoice.createdAt)
            ImageLoader.load(image, invoice.imageRef, placeholder = R.drawable.ic_receipt)
            root.setOnClickListener { onClick(invoice) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Invoice>() {
        override fun areItemsTheSame(oldItem: Invoice, newItem: Invoice) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Invoice, newItem: Invoice) = oldItem == newItem
    }
}
