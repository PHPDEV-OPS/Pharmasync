package com.example.pharmasync.ui.explore

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.pharmasync.R
import com.example.pharmasync.data.model.Pharmacy
import com.example.pharmasync.databinding.ActivityHostBinding
import com.google.android.material.snackbar.Snackbar

/** Public browsing for patients: no account needed. */
class ExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHostBinding
    val viewModel: ExploreViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bottomNavigation.inflateMenu(R.menu.menu_explore_nav)
        if (savedInstanceState == null) showTab(R.id.nav_pharmacies)
        binding.bottomNavigation.setOnItemSelectedListener { showTab(it.itemId); true }
    }

    fun showOnMap(pharmacy: Pharmacy) {
        if (!pharmacy.hasLocation) {
            message(getString(R.string.error_no_location_for_store))
            return
        }
        viewModel.focusOn(pharmacy.uid)
        binding.bottomNavigation.selectedItemId = R.id.nav_map
    }

    fun openDirections(pharmacy: Pharmacy) {
        val query = if (pharmacy.hasLocation) "${pharmacy.latitude},${pharmacy.longitude}(${pharmacy.name})" else pharmacy.address
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(query))
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            message(getString(R.string.error_maps_app))
        }
    }

    fun message(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).setAnchorView(binding.bottomNavigation).show()
    }

    private fun showTab(itemId: Int) {
        val tag = "tab_$itemId"
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction().setReorderingAllowed(true)
        fm.fragments.filter { it.tag?.startsWith("tab_") == true && it.tag != tag }.forEach { transaction.hide(it) }
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) transaction.add(R.id.fragment_container, createTab(itemId), tag) else transaction.show(existing)
        transaction.commitNow()
    }

    private fun createTab(itemId: Int): Fragment = when (itemId) {
        R.id.nav_medicines -> PublicMedicinesFragment()
        R.id.nav_map -> PharmacyMapFragment()
        else -> PharmaciesFragment()
    }
}
