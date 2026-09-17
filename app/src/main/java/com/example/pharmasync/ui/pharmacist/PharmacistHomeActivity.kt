package com.example.pharmasync.ui.pharmacist

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.example.pharmasync.R
import com.example.pharmasync.data.model.OrderStatus
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.ui.profile.ProfileFragment
import com.example.pharmasync.ui.session.SessionHostActivity
import com.example.pharmasync.ui.shared.OrdersFragment
import com.example.pharmasync.ui.shared.StockFragment
import com.example.pharmasync.util.collectWhileStarted

class PharmacistHomeActivity : SessionHostActivity() {
    override val sessionRole = Role.PHARMACIST
    override val menuRes = R.menu.menu_pharmacist_nav
    override val startTab = R.id.nav_inventory

    override fun createTab(itemId: Int): Fragment = when (itemId) {
        R.id.nav_suppliers -> SuppliersFragment()
        R.id.nav_orders -> OrdersFragment()
        R.id.nav_invoices -> InvoicesFragment()
        R.id.nav_profile -> ProfileFragment()
        else -> StockFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        collectWhileStarted(session.stock) { items -> setBadge(R.id.nav_inventory, items.count { it.isLowStock || it.isOutOfStock }) }
        collectWhileStarted(session.orders) { orders ->
            // Orders the pharmacy needs to act on (confirm delivery).
            setBadge(R.id.nav_orders, orders.count { it.status == OrderStatus.DISPATCHED })
        }
    }
}
