package com.example.pharmasync.ui.shared

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.pharmasync.R
import com.example.pharmasync.data.model.Order
import com.example.pharmasync.data.model.OrderStatus
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.databinding.FragmentCollectionBinding
import com.example.pharmasync.ui.common.CollectionScreen
import com.example.pharmasync.ui.common.Dialogs
import com.example.pharmasync.ui.common.OrderAction
import com.example.pharmasync.ui.common.OrderAdapter
import com.example.pharmasync.ui.pharmacist.PharmacistHomeActivity
import com.example.pharmasync.ui.session.SessionFragment
import com.example.pharmasync.ui.session.SessionViewModel
import com.example.pharmasync.util.collectWhileViewStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** Incoming orders for suppliers; placed orders for pharmacists. */
class OrdersFragment : SessionFragment() {

    private enum class Filter { ALL, PENDING, ACTIVE, COMPLETED }

    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(Filter.ALL)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val role = session.role
        val screen = CollectionScreen(binding)
        val adapter = OrderAdapter(role, ::onAction)
        binding.recycler.adapter = adapter

        screen.search(getString(R.string.search_hint)) { query.value = it }
        screen.filters(
            listOf(R.string.filter_all, R.string.filter_pending, R.string.filter_active, R.string.filter_completed).map(::getString),
            selected = filter.value.ordinal,
        ) { filter.value = Filter.entries[it] }

        collectWhileViewStarted(session.orders) { orders ->
            if (role == Role.SUPPLIER) {
                screen.header(getString(R.string.orders_title), getString(R.string.orders_subtitle_supplier, orders.count { it.status == OrderStatus.PENDING }))
            } else {
                screen.header(getString(R.string.my_orders_title), getString(R.string.orders_subtitle_pharmacist, orders.count { it.status.isOpen }))
            }
        }

        collectWhileViewStarted(combine(session.orders, query, filter) { o, q, f -> Triple(o, q, f) }) { (orders, q, f) ->
            val visible = orders
                .filter {
                    q.isEmpty() || it.medicineName.contains(q, true) ||
                        it.pharmacistName.contains(q, true) || it.supplierName.contains(q, true)
                }
                .filter {
                    when (f) {
                        Filter.ALL -> true
                        Filter.PENDING -> it.status == OrderStatus.PENDING
                        Filter.ACTIVE -> it.status == OrderStatus.ACCEPTED || it.status == OrderStatus.DISPATCHED
                        Filter.COMPLETED -> it.status.isClosed
                    }
                }
            adapter.submitList(visible)
            when {
                orders.isEmpty() -> screen.empty(
                    visible = true,
                    icon = R.drawable.ic_orders,
                    title = getString(R.string.empty_orders_title),
                    message = getString(if (role == Role.SUPPLIER) R.string.empty_orders_message_supplier else R.string.empty_orders_message_pharmacist),
                    actionText = if (role == Role.PHARMACIST) getString(R.string.nav_suppliers) else null,
                    action = { (activity as? PharmacistHomeActivity)?.selectTab(R.id.nav_suppliers) },
                )
                visible.isEmpty() -> screen.empty(
                    visible = true,
                    icon = R.drawable.ic_search,
                    title = getString(R.string.empty_search_title),
                    message = getString(R.string.empty_search_message),
                )
                else -> screen.empty(visible = false)
            }
        }

        collectWhileViewStarted(combine(session.syncing, session.busy) { s, b -> b || SessionViewModel.Sync.ORDERS in s }) {
            screen.loading(it)
        }
    }

    private fun onAction(order: Order, action: OrderAction) {
        when (action) {
            OrderAction.DECLINE -> Dialogs.confirm(
                requireContext(),
                title = getString(R.string.dialog_decline_title),
                message = "${order.quantity} × ${order.medicineName}",
                confirmText = getString(R.string.action_decline),
            ) { session.performOrderAction(order, action) }
            OrderAction.CANCEL -> Dialogs.confirm(
                requireContext(),
                title = getString(R.string.dialog_cancel_order_title),
                message = "${order.quantity} × ${order.medicineName}",
                confirmText = getString(R.string.action_cancel_order),
            ) { session.performOrderAction(order, action) }
            else -> session.performOrderAction(order, action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
