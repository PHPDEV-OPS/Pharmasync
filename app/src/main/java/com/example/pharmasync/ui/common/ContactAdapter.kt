package com.example.pharmasync.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pharmasync.databinding.ItemContactBinding
import com.example.pharmasync.util.Formatters
import com.example.pharmasync.util.visibleIf

/** A supplier or pharmacy card. */
data class ContactRow(
    val id: String,
    val title: String,
    val line1: String,
    val line2: String,
    val photoUrl: String,
    val showMapActions: Boolean,
)

class ContactAdapter(
    private val onClick: (ContactRow) -> Unit,
    private val onViewMap: (ContactRow) -> Unit = {},
    private val onDirections: (ContactRow) -> Unit = {},
) : ListAdapter<ContactRow, ContactAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ContactRow) = with(binding) {
            title.text = row.title
            line1.text = row.line1
            line1.visibleIf(row.line1.isNotBlank())
            line2.text = row.line2
            line2.visibleIf(row.line2.isNotBlank())
            initials.text = Formatters.initials(row.title)
            if (row.photoUrl.isNotBlank()) {
                avatar.visibility = View.VISIBLE
                Glide.with(avatar).load(row.photoUrl).centerCrop().into(avatar)
            } else {
                Glide.with(avatar).clear(avatar)
                avatar.visibility = View.GONE
            }
            chevron.visibleIf(!row.showMapActions)
            actions.visibleIf(row.showMapActions)
            root.setOnClickListener { onClick(row) }
            primaryAction.setOnClickListener { onViewMap(row) }
            secondaryAction.setOnClickListener { onDirections(row) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ContactRow>() {
        override fun areItemsTheSame(oldItem: ContactRow, newItem: ContactRow) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ContactRow, newItem: ContactRow) = oldItem == newItem
    }
}
