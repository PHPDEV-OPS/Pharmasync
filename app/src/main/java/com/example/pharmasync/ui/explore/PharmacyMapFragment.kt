package com.example.pharmasync.ui.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.pharmasync.R
import com.example.pharmasync.data.model.Pharmacy
import com.example.pharmasync.databinding.FragmentMapBinding
import com.example.pharmasync.util.collectWhileViewStarted
import com.example.pharmasync.util.visibleIf
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.flow.combine

class PharmacyMapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExploreViewModel by activityViewModels()
    private var map: GoogleMap? = null
    private val markers = mutableMapOf<String, Marker>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.header.headerTitle.setText(R.string.explore_map_title)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map_container) as SupportMapFragment
        mapFragment.getMapAsync { googleMap ->
            map = googleMap
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.setOnInfoWindowClickListener { marker ->
                viewModel.pharmacies.value.firstOrNull { it.uid == marker.tag }?.let { (activity as? ExploreActivity)?.openDirections(it) }
            }
            render(viewModel.pharmacies.value, viewModel.focus.value)
        }
        collectWhileViewStarted(combine(viewModel.pharmacies, viewModel.focus) { p, f -> p to f }) { (pharmacies, focus) ->
            val located = pharmacies.count { it.hasLocation }
            binding.header.headerSubtitle.visibleIf(true)
            binding.header.headerSubtitle.text = resources.getQuantityString(R.plurals.pharmacies_on_map, located, located)
            render(pharmacies, focus)
        }
        collectWhileViewStarted(viewModel.loading) { binding.progress.visibleIf(it) }
    }

    private fun render(pharmacies: List<Pharmacy>, focus: String?) {
        val googleMap = map ?: return
        googleMap.clear()
        markers.clear()
        val located = pharmacies.filter { it.hasLocation }
        located.forEach { pharmacy ->
            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(LatLng(pharmacy.latitude!!, pharmacy.longitude!!))
                    .title(pharmacy.name)
                    .snippet(pharmacy.address)
            )
            if (marker != null) {
                marker.tag = pharmacy.uid
                markers[pharmacy.uid] = marker
            }
        }

        val focused = focus?.let { markers[it] }
        when {
            focused != null -> {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(focused.position, 15f))
                focused.showInfoWindow()
            }
            located.size == 1 -> googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markers.values.first().position, 13f))
            located.size > 1 -> {
                val bounds = LatLngBounds.builder().apply { markers.values.forEach { include(it.position) } }.build()
                binding.root.post {
                    if (_binding != null) googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                }
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Clear the focus once the user leaves the map so the next visit shows every pharmacy.
        if (hidden) viewModel.focusOn(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        map = null
        markers.clear()
        _binding = null
    }
}
