package com.example.pharmasync.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmasync.R
import com.example.pharmasync.data.model.Order
import com.example.pharmasync.data.model.OrderStatus
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.databinding.ItemOrderBinding
import com.example.pharmasync.util.Formatters
import com.example.pharmasync.util.bindStatus

enum class OrderAction(@StringRes val label: Int, val apiValue: String) {
    ACCEPT(R.string.action_accept, "accept"),
    DECLINE(R.string.action_decline, "decline"),
    DISPATCH(R.string.action_dispatch, "dispatch"),
    CANCEL(R.string.action_cancel_order, "cancel"),
    RECEIVE(R.string.action_mark_received, "receive");

    companion object {
        /** (primary, secondary) actions available to [role] for [order]. */
        fun available(order: Order, role: Role): Pair<OrderAction?, OrderAction?> = when (role) {
            Role.SUPPLIER -> when (order.status) {
                OrderStatus.PENDING -> ACCEPT to DECLINE
                OrderStatus.ACCEPTED -> DISPATCH to null
                else -> null to null
            }
            Role.PHARMACIST -> when (order.status) {
                OrderStatus.PENDING -> null to CANCEL
                OrderStatus.ACCEPTED, OrderStatus.DISPATCHED -> RECEIVE to null
                else -> null to null
            }
        }
    }
}

class OrderAdapter(
    private val role: Role,
    private val onAction: (Order, OrderAction) -> Unit,
) : ListAdapter<Order, OrderAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(order: Order) = with(binding) {
            val context = root.context
            medicine.text = order.medicineName
            status.bindStatus(order.status)
            counterparty.text = when (role) {
                Role.SUPPLIER -> context.getString(R.string.order_from, order.pharmacistName)
                Role.PHARMACIST -> context.getString(R.string.order_to, order.supplierName.ifBlank { "—" })
            }
            quantity.text = context.getString(R.string.order_qty_line, order.quantity, Formatters.money(order.unitPrice))
            total.text = Formatters.money(order.total)
            date.text = Formatters.dateTime(order.createdAt)

            val (primary, secondary) = OrderAction.available(order, role)
            actions.visibility = if (primary != null || secondary != null) View.VISIBLE else View.GONE
            primaryAction.bindAction(order, primary)
            secondaryAction.bindAction(order, secondary)
        }

        private fun Button.bindAction(order: Order, action: OrderAction?) {
            visibility = if (action == null) View.GONE else View.VISIBLE
            if (action != null) {
                setText(action.label)
                setOnClickListener { onAction(order, action) }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Order, newItem: Order) = oldItem == newItem
    }
}
