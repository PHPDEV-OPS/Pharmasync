package com.example.pharmasync

import android.location.Address
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.example.pharmasync.databinding.ActivityHomeBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class Home : AppCompatActivity() {
    private lateinit var bind: ActivityHomeBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var fs: FirebaseFirestore
    private var address = ""
    private var P_longitude = 0.0
    private var P_latitude = 0.0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(bind.root)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.Frame, Medicines())
                .commit()
        }
        auth = FirebaseAuth.getInstance()
        fs = FirebaseFirestore.getInstance()

//        fs.collection("Medical-Store").document(auth.currentUser?.uid!!).collection("My-Store")
//            .whereEqualTo("Uid",auth.currentUser?.uid!!).get().addOnSuccessListener { documents ->
//            for (document in documents) {
//                // Assuming the field you want to retrieve is called "fieldName"
//                val fieldValue = document.getString("Address")
//                if (fieldValue != null) {
//                    address = fieldValue.toString()
//
//                    Log.d("hello", "Field value: $fieldValue")
//                    // You can use the field value as needed
//                } else {
//                    println("Field not found in document: ${document.id}")
//                }
//            }
//        }
//            .addOnFailureListener { exception ->
//                println("Error getting documents: $exception")
//            }


        bind.bottomNavigation.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.item_1 -> {
                    replacefragement(Medicines(), "Medicines")
                    Log.d("hello", "Field value: $address")

//                    loadFragment(dashboard())
                    true
                }

                R.id.item_2 -> {
                    replacefragement(invoices(), "inventory")
//                    loadFragment(inventory())

                    true
                }

                R.id.item_4 -> {
                    replacefragement(Suppliers(), "Suppliers")
                    true
                }

                R.id.item_5 -> {
                    replacefragement(PharmacistOrders(), "Orders")
                    true
                }

                R.id.item_3 -> {
//                    loadFragment(product())
                    replacefragement(profile(), "product")
                    true
                }

                else -> false
            }
        }
    }

    fun replacefragement(fragment: Fragment, tag: String) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        FragmentTransaction.TRANSIT_ENTER_MASK
        fragmentTransaction.replace(R.id.Frame, fragment)
        fragmentTransaction.commit()


    }

    private fun addressApi(Address: String, Uid: String) {
        RetrofitClient.geoapifyService.search(Address).enqueue(object : Callback<GeoapifyResponse> {
            override fun onResponse(call: Call<GeoapifyResponse>, response: Response<GeoapifyResponse>) {
                if (response.isSuccessful) {
                    val geoResponse = response.body()
                    val coordinates = geoResponse?.features?.get(0)?.geometry?.coordinates

                    if (coordinates != null && coordinates.size >= 2) {
                        P_longitude = coordinates[0]
                        P_latitude = coordinates[1]

                        Log.d("D_CHECK", "addressApi: $P_longitude  $P_latitude")
                        
                        val coordData = hashMapOf(
                            "Longitude" to P_longitude,
                            "Latitude" to P_latitude,
                            "Address" to Address,
                            "CreatedAt" to Timestamp.now().toDate(),
                            "UserID" to Uid
                        )
                        fs.collection("Cordinates").document(auth.currentUser?.uid!!)
                            .collection("MyCordinates").document().set(coordData)
                            .addOnSuccessListener {
                                Log.d("D_CHECK", "Successfully added to firestore addressApi: $coordData")
                            }
                    }
                }
            }

            override fun onFailure(call: Call<GeoapifyResponse>, t: Throwable) {
                Log.e("D_CHECK", "Retrofit error: ${t.message}")
            }
        })
    }
}
