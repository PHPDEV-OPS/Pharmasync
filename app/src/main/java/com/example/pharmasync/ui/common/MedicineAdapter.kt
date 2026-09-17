package com.example.pharmasync.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmasync.R
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.databinding.ItemMedicineBinding
import com.example.pharmasync.util.Formatters
import com.example.pharmasync.util.ImageLoader
import com.example.pharmasync.util.setPillColors

/** A medicine row; [subtitle] replaces the category line (e.g. with a pharmacy name). */
data class MedicineRow(val medicine: Medicine, val subtitle: String? = null, val key: String = medicine.ownerId + medicine.id)

class MedicineAdapter(
    private val mode: Mode,
    private val onClick: (MedicineRow) -> Unit,
    private val onAction: (MedicineRow) -> Unit = {},
) : ListAdapter<MedicineRow, MedicineAdapter.ViewHolder>(Diff) {

    enum class Mode {
        /** Own inventory/catalog: edit button. */
        MANAGE,

        /** Another supplier's catalog: order button. */
        ORDER,

        /** Public listing: map button. */
        LOCATE,
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemMedicineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemMedicineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: MedicineRow) = with(binding) {
            val item = row.medicine
            val context = root.context
            name.text = item.name
            meta.text = listOf(row.subtitle ?: item.category, Formatters.money(item.price))
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            ImageLoader.load(image, item.imageRef)
            stock.bindStock(item, mode)

            root.setOnClickListener { onClick(row) }
            when (mode) {
                Mode.MANAGE -> {
                    action.visibility = View.VISIBLE
                    orderButton.visibility = View.GONE
                    action.setIconResource(R.drawable.ic_edit)
                    action.contentDescription = context.getString(R.string.action_edit)
                }
                Mode.ORDER -> {
                    action.visibility = View.GONE
                    orderButton.visibility = View.VISIBLE
                    orderButton.isEnabled = !item.isOutOfStock
                }
                Mode.LOCATE -> {
                    action.visibility = View.VISIBLE
                    orderButton.visibility = View.GONE
                    action.setIconResource(R.drawable.ic_map)
                    action.contentDescription = context.getString(R.string.action_view_on_map)
                }
            }
            action.setOnClickListener { onAction(row) }
            orderButton.setOnClickListener { onAction(row) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MedicineRow>() {
        override fun areItemsTheSame(oldItem: MedicineRow, newItem: MedicineRow) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: MedicineRow, newItem: MedicineRow) = oldItem == newItem
    }
}

fun android.widget.TextView.bindStock(item: Medicine, mode: MedicineAdapter.Mode = MedicineAdapter.Mode.MANAGE) {
    when {
        item.isOutOfStock -> {
            setText(R.string.stock_out)
            setPillColors(R.color.status_declined, R.color.status_declined_bg)
        }
        item.isLowStock && mode == MedicineAdapter.Mode.MANAGE -> {
            text = context.getString(R.string.stock_low, item.stock)
            setPillColors(R.color.status_pending, R.color.status_pending_bg)
        }
        else -> {
            text = context.getString(R.string.stock_count, item.stock)
            setPillColors(R.color.status_delivered, R.color.status_delivered_bg)
        }
    }
}
