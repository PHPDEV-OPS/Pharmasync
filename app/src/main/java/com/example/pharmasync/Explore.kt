package com.example.pharmasync

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pharmasync.databinding.FragmentExploreBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.getField
import com.google.firebase.storage.StorageReference
import org.json.JSONObject


class Explore : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var fs: FirebaseFirestore
    private lateinit var sr: StorageReference
    lateinit var med_store: ArrayList<MedicalStore>
    lateinit var binding: FragmentExploreBinding
    private var P_longitude = 0.0
    private var P_latitude = 0.0

    var textWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            // this function is called before text is edited
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            // this function is called when text is edited
            Log.d("hello", "onViewCreated: ${s.length}")
            if(s.trim().length>0) {
                searchMedicineAcrossAllShops(binding.search.text.toString().trim()) {
                    Log.d("hello", "onViewCreated: $it")
                    Log.d("hello", "onViewCreated: ${s.length}")

//                    updateUi(it)
                }
            }else if(s.length == 0){
                get_data()
                Log.d("hello", "CalledonViewCreated: ${s.length}")

            }
        }

        override fun afterTextChanged(s: Editable) {
            // this function is called after text is edited
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentExploreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = binding.rvShop
        auth = FirebaseAuth.getInstance()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.searchBtn.setOnClickListener {
            searchMedicineAcrossAllShops(binding.search.text.toString().trim()) {
                Log.d("hello", "onViewCreated: $it")
                updateUi(it)
            }
        }

        binding.search.addTextChangedListener(textWatcher)
        med_store = arrayListOf()
        get_data()
    }

    fun get_data() {
        binding.ProgressBar.visibility = View.VISIBLE
        fs = FirebaseFirestore.getInstance()
        fs.collection("Medical-Store").get()
            .addOnSuccessListener { snapshot ->
                binding.ProgressBar.visibility = View.GONE
                val store = ArrayList<MedicalStore>()
                for (doc in snapshot.documents) {
                    val r = doc.toObject(MedicalStore::class.java)
                    if (r != null) {
                        store.add(r)
                    }
                }
                Log.d("D_CHECK", "get_data: fetched ${store.size} stores")
                updateUi(store)
            }
            .addOnFailureListener { e ->
                binding.ProgressBar.visibility = View.GONE
                Log.e("D_CHECK", "Error getting stores", e)
                custom_snackbar("Failed to load stores")
            }
    }


    private fun updateUi(store: ArrayList<MedicalStore>) {
        simulateDataLoading()
        var exploreAdapter = exploreAdapter(store)

        binding.rvShop.adapter = exploreAdapter
        if (store.size == 0) {
            binding.msg.visibility = View.GONE
        }

        exploreAdapter.onEdit(object : exploreAdapter.EditClick {
            override fun onEditClick(position: Int) {
                addressApi(store[position].Address!!, store[position].Uid!!,store[position].ShopName!!)
                val mapFragment = MapsFragment()
                (activity as? User)?.replacefragement(mapFragment, "specific_inventory")
                Log.d("Hello", "onEditClick: Locate ${store[position].Address!!+ store[position].Uid!!+ store[position].ShopName!!}")
            }

        })

        binding.rvShop.viewTreeObserver.addOnGlobalLayoutListener {
            binding.ProgressBar.visibility = View.GONE
        }

    }

    private fun simulateDataLoading() {
        binding.ProgressBar.postDelayed({
            binding.rvShop.visibility = View.VISIBLE
        }, 2000)
    }

    private fun search(name: String) {
        med_store.clear()
        var foundMatch = false

        fs.collection("Users").get().addOnSuccessListener {
            for (data in it) {
//                Log.d("D_CHECK", "s_getinventory++: ${data.id}")
                fs.collection("Medicines").document(data.id).collection("MyMedicines")
                    .get().addOnSuccessListener {
                        Log.d("D_CHECK", "s_getinventory++: ${it}")

                        for (data in it) {
                            val r = data.toObject(medicine::class.java)
                            Log.d("D_CHECK", "s_getinventory+++: ${r}")
                            Log.d(
                                "D_CHECK",
                                "search: ${r.Medicine?.startsWith(name, ignoreCase = true)!!}"
                            )
                            if (r.Medicine?.startsWith(name, ignoreCase = true)!!) {
                                val medicine_id = r.MedicineId
                                Log.d("D_CHECK", "s_getinventory+++: ${medicine_id}")
                                fs.collection("Users").get().addOnSuccessListener { it ->
                                    for (data in it) {
                                        Log.d(
                                            "D_CHECK",
                                            "s_getinventory----:  ${data.id} + ${r.MedicineId}"
                                        )
                                        fs.collection("Medical-Store").document(data.id)
                                            .collection("My-Store")
                                            .whereEqualTo("medicine_id", medicine_id)
                                            .get()
                                            .addOnSuccessListener { documents ->
                                                if (!documents.isEmpty) {
                                                    foundMatch = true
                                                    for (document in documents) {
                                                        Log.d(
                                                            "DUB",
                                                            "Document exists: ${document} "
                                                        )
                                                        val r =
                                                            document.toObject(MedicalStore::class.java)
                                                        r.let {
                                                            med_store.add(it)
                                                        }
                                                    }
                                                    Log.d("D_CHECK", "search: ${med_store.size}")
                                                    updateUi(med_store)
                                                }
                                            }
                                    }
                                }
                            }
                        }


                    }
            }

        }


    }

    fun searchMedicineAcrossAllShops(name: String, callback: (ArrayList<MedicalStore>) -> Unit) {
        var isfound = false
        val db = FirebaseFirestore.getInstance()
        val med_store = arrayListOf<MedicalStore>()
        db.collection("Users").get().addOnSuccessListener { userSnapshots ->
            for (userDoc in userSnapshots.documents) {
                Log.d("hello", "searchMedicineAcrossAllShops: $userDoc")
                db.collection("Medicines").document(userDoc.id).collection("MyMedicines")
                    .get().addOnSuccessListener { medicineSnapshots ->
                        for (medicineDoc in medicineSnapshots.documents) {
                            val medicine = medicineDoc.toObject(medicine::class.java)
                            if (medicine?.Medicine?.startsWith(name, ignoreCase = true) == true && medicine.Stock?.toInt()!! > 0) { // Changes
                                val medicineId = medicine.MedicineId
                                isfound = true
                                Log.d("hello", "searchMedicineAcrossAllShops: $medicineId")
                                db.collection("Medical-Store").whereArrayContains("MedicineID",medicineId!!).get()
                                    .addOnSuccessListener { storeSnapshots ->
                                        for (storeDoc in storeSnapshots.documents) {
                                            Log.d("hello", "searchMedicineAcrossAllShops__: ${storeDoc.id}")

//                                            db.collection("Medical-Store").document(storeDoc.id)
//                                                .get().addOnSuccessListener { documents ->
                                                    Log.d("hello", "searchMedicineAcrossAllShops__: ${storeSnapshots.size()}")

//                                                        for (document in documents.documents) {
                                                            Log.d("hello", "_+_: $medicineId")
//                                                        if(storeDoc.getField<List<String>>("MedicineId")?.contains(medicineId)!!)
                                                         val store =
                                                                storeDoc.toObject(MedicalStore::class.java)
                                                            store?.let { med_store.add(it) }
//                                                        }
                                                        Log.d("hello", "searchMedicineAcrossAllShops_+_: $med_store")
                                                        updateUi(med_store)
                                                        callback(med_store)

//                                                }.addOnFailureListener { e ->
//                                                    Log.e(
//                                                        "D_CHECK",
//                                                        "Error fetching store medicines: ${e.message}"
//                                                    )
//                                                }
                                        }
                                    }.addOnFailureListener { e ->
                                    Log.e("D_CHECK", "Error fetching medical stores: ${e.message}")
                                }
                            }else{
                                updateUi(med_store)
                                callback(med_store)
//                                if(!isfound){
//                                    custom_snackbar("No Data Found")
//                                }

//                                Toast.makeText(requireContext(),"No Data Found",Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.addOnFailureListener { e ->
                        Log.e("D_CHECK", "Error fetching medicines: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("D_CHECK", "Error fetching users: ${e.message}")
        }
    }

    private fun custom_snackbar(message: String) {
        val bar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        bar.setBackgroundTint(resources.getColor(R.color.blue))

        bar.setAction("OK") {
            bar.dismiss()
        }
        bar.setActionTextColor(resources.getColor(R.color.white))
        bar.show()
    }
    private fun addressApi(Address: String, Uid: String, shopname: String) {
        Log.d("D_CHECK", "addressApi: $Address")
        
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
                            "UserID" to Uid,
                            "Shopname" to shopname
                        )

                        fs.collection("Cordinates").document(Uid)
                            .collection("MyCordinates").document("data").set(coordData)
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
