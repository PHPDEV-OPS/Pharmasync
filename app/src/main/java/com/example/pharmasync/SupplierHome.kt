package com.example.pharmasync

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class SupplierHome : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_supplier_home)

        if (savedInstanceState == null) {
            replaceFragment(SupplierOrders(), "Orders")
        }

        val navView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.item_orders -> {
                    replaceFragment(SupplierOrders(), "Orders")
                    true
                }
                R.id.item_stock -> {
                    replaceFragment(SupplierStock(), "Stock")
                    true
                }
                R.id.item_profile -> {
                    replaceFragment(profile(), "Profile")
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment, tag: String) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.Frame, fragment, tag)
        fragmentTransaction.commit()
    }
}
