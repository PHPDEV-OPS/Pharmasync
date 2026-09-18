package com.example.pharmasync.ui.explore

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.pharmasync.R
import com.example.pharmasync.data.model.Pharmacy
import com.example.pharmasync.databinding.FragmentMapBinding
import com.example.pharmasync.util.collectWhileViewStarted
import com.example.pharmasync.util.visibleIf
import kotlinx.coroutines.flow.combine
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * OpenStreetMap-based map of registered pharmacies plus the user's own position.
 * OSM tiles need no API key or billing account.
 */
class PharmacyMapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExploreViewModel by activityViewModels()

    private var myLocation: MyLocationNewOverlay? = null
    private val markers = mutableMapOf<String, Marker>()
    private var cameraPositioned = false

    private companion object {
        val NAIROBI = GeoPoint(-1.286389, 36.817223)
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) enableMyLocation(centerOnFix = true)
        else (activity as? ExploreActivity)?.message(getString(R.string.error_location_permission_map))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.header.headerTitle.setText(R.string.explore_map_title)
        binding.attribution.text = BasemapTiles.attribution
        with(binding.mapView) {
            setTileSource(BasemapTiles.source)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            isTilesScaledToDpi = true
            minZoomLevel = 5.0
            controller.setZoom(12.0)
            controller.setCenter(NAIROBI)
        }

        binding.btnMyLocation.setOnClickListener {
            if (hasLocationPermission()) centerOnUser() else requestLocation()
        }
        if (hasLocationPermission()) enableMyLocation(centerOnFix = true) else requestLocation()

        collectWhileViewStarted(combine(viewModel.pharmacies, viewModel.focus) { p, f -> p to f }) { (pharmacies, focus) ->
            val located = pharmacies.count { it.hasLocation }
            binding.header.headerSubtitle.visibleIf(true)
            binding.header.headerSubtitle.text = resources.getQuantityString(R.plurals.pharmacies_on_map, located, located)
            renderMarkers(pharmacies, focus)
        }
        collectWhileViewStarted(viewModel.loading) { binding.progress.visibleIf(it) }
        collectWhileViewStarted(viewModel.error) { error ->
            binding.statusCard.visibleIf(error != null)
            binding.statusText.text = error?.resolve(requireContext())
        }
    }

    private fun renderMarkers(pharmacies: List<Pharmacy>, focus: String?) {
        val map = _binding?.mapView ?: return
        markers.values.forEach { map.overlays.remove(it) }
        markers.clear()

        pharmacies.filter { it.hasLocation }.forEach { pharmacy ->
            val marker = Marker(map).apply {
                position = GeoPoint(pharmacy.latitude!!, pharmacy.longitude!!)
                title = pharmacy.name
                snippet = listOf(pharmacy.address, pharmacy.phone).filter { it.isNotBlank() }.joinToString("\n")
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_map_pin)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                relatedObject = pharmacy.uid
            }
            map.overlays.add(marker)
            markers[pharmacy.uid] = marker
        }

        val focused = focus?.let { markers[it] }
        when {
            focused != null -> {
                map.controller.animateTo(focused.position, 16.0, 800L)
                focused.showInfoWindow()
                cameraPositioned = true
            }
            // Only frame all pharmacies if the user's own location hasn't been shown yet.
            !cameraPositioned && markers.size == 1 -> {
                map.controller.setZoom(14.0)
                map.controller.setCenter(markers.values.first().position)
            }
            !cameraPositioned && markers.size > 1 -> map.post {
                if (_binding != null) {
                    val box = BoundingBox.fromGeoPoints(markers.values.map { it.position })
                    map.zoomToBoundingBox(box.increaseByScale(1.3f), false, 64)
                }
            }
        }
        map.invalidate()
    }

    private fun enableMyLocation(centerOnFix: Boolean) {
        val map = _binding?.mapView ?: return
        val overlay = myLocation ?: MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map).also {
            it.setDrawAccuracyEnabled(true)
            map.overlays.add(it)
            myLocation = it
        }
        overlay.enableMyLocation()
        if (centerOnFix && viewModel.focus.value == null) {
            overlay.runOnFirstFix {
                map.post {
                    if (_binding != null && viewModel.focus.value == null) {
                        map.controller.animateTo(overlay.myLocation, 14.0, 800L)
                        cameraPositioned = true
                    }
                }
            }
        }
    }

    private fun centerOnUser() {
        enableMyLocation(centerOnFix = false)
        val point = myLocation?.myLocation
        if (point != null) {
            binding.mapView.controller.animateTo(point, 15.0, 600L)
            cameraPositioned = true
        } else {
            (activity as? ExploreActivity)?.message(getString(R.string.msg_fetching_location))
            enableMyLocation(centerOnFix = true)
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() =
        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

    override fun onResume() {
        super.onResume()
        if (!isHidden) resumeMap()
    }

    override fun onPause() {
        super.onPause()
        pauseMap()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            pauseMap()
            // Clear the focus once the user leaves the map so the next visit shows every pharmacy.
            viewModel.focusOn(null)
        } else {
            resumeMap()
        }
    }

    private fun resumeMap() {
        _binding?.mapView?.onResume()
        if (hasLocationPermission()) myLocation?.enableMyLocation()
    }

    private fun pauseMap() {
        _binding?.mapView?.onPause()
        myLocation?.disableMyLocation()
    }

    override fun onDestroyView() {
        myLocation?.disableMyLocation()
        myLocation = null
        markers.clear()
        _binding?.mapView?.onDetach()
        super.onDestroyView()
        _binding = null
    }
}
