package com.example.pharmasync.ui.supplier

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

class SupplierHomeActivity : SessionHostActivity() {
    override val sessionRole = Role.SUPPLIER
    override val menuRes = R.menu.menu_supplier_nav
    override val startTab = R.id.nav_orders

    override fun createTab(itemId: Int): Fragment = when (itemId) {
        R.id.nav_stock -> StockFragment()
        R.id.nav_profile -> ProfileFragment()
        else -> OrdersFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        collectWhileStarted(session.orders) { orders -> setBadge(R.id.nav_orders, orders.count { it.status == OrderStatus.PENDING }) }
        collectWhileStarted(session.stock) { items -> setBadge(R.id.nav_stock, items.count { it.isLowStock || it.isOutOfStock }) }
    }
}
