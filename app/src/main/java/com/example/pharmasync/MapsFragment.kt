package com.example.pharmasync

import androidx.fragment.app.Fragment
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MapsFragment : Fragment() {
    private lateinit var fs: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var coordinateList = arrayListOf<Cordinate>()

    private val callback = OnMapReadyCallback { googleMap ->
        Log.d("MapsFragment", "Map is ready")
        fetchCoordinatesAndAddMarkers(googleMap)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fs = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        return inflater.inflate(R.layout.fragment_maps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(callback)
    }

    private fun fetchCoordinatesAndAddMarkers(googleMap: GoogleMap) {
        coordinateList.clear()
        Log.d("MapsFragment", "Fetching coordinates...")
        
        fs.collectionGroup("MyCordinates").get()
            .addOnSuccessListener { snapshot ->
                Log.d("MapsFragment", "Fetched ${snapshot.size()} coordinate documents")
                if (snapshot.isEmpty) {
                    Log.d("MapsFragment", "No coordinate documents found")
                    return@addOnSuccessListener
                }

                for (document in snapshot.documents) {
                    try {
                        val lat = document.getDouble("Latitude")
                        val lng = document.getDouble("Longitude")
                        val shopName = document.getString("Shopname") ?: "Unknown Shop"
                        val address = document.getString("Address") ?: ""

                        if (lat != null && lng != null) {
                            val position = LatLng(lat, lng)
                            val markerTitle = "$shopName"
                            val markerSnippet = address
                            
                            googleMap.addMarker(
                                MarkerOptions()
                                    .position(position)
                                    .title(markerTitle)
                                    .snippet(markerSnippet)
                            )
                            
                            val coord = document.toObject(Cordinate::class.java)
                            if (coord != null) coordinateList.add(coord)
                        }
                    } catch (e: Exception) {
                        Log.e("MapsFragment", "Error parsing coordinate document", e)
                    }
                }

                // Move camera to the first found coordinate or a default location
                if (coordinateList.isNotEmpty()) {
                    val first = LatLng(coordinateList[0].Latitude ?: 0.0, coordinateList[0].Longitude ?: 0.0)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(first, 12f))
                } else {
                    // Default to a broad view if no markers
                    val defaultPos = LatLng(0.0, 0.0)
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultPos, 1f))
                }
            }
            .addOnFailureListener { e ->
                Log.e("MapsFragment", "Error fetching markers: ${e.message}", e)
                if (e.message?.contains("index") == true) {
                    Log.e("MapsFragment", "Index required! Please check Firestore console for the link to create the index.")
                }
            }
    }
}
