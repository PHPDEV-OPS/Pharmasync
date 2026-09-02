package com.example.pharmasync

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pharmasync.databinding.ActivitySignupBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import kotlin.random.Random

class Signup : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var fs: FirebaseFirestore
    private var P_longitude = 0.0
    private var P_latitude = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        fs = FirebaseFirestore.getInstance()

        // final minor changes
        binding.backSignUp.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.SignupBtn.setOnClickListener {
            if (binding.username.text.toString().isNotBlank() && binding.email.text.toString()
                    .isNotEmpty() && binding.password.text.toString()
                    .isNotEmpty() && binding.shopname.text.toString().isNotEmpty()
            ) {
                signUpUser(binding.email.text.toString(), binding.password.text.toString())
            }
        }
    }

    private fun signUpUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    sendEmailVerification(binding.shopname.text.toString())
                } else {
                    when (val exception = task.exception) {
                        is FirebaseAuthInvalidCredentialsException -> {
                            Log.d("hello", "Invalid email or password format: ${exception.message}")

                            val bar =
                                Snackbar.make(binding.root, "Invalid format", Snackbar.LENGTH_SHORT)
                            bar.setBackgroundTint(getColor(R.color.blue))
                            bar.setAction("OK") {
                                bar.dismiss()
                            }
                            bar.setActionTextColor(getColor(R.color.blue))
                            bar.show()
                        }

                        is FirebaseAuthUserCollisionException -> {
                            Log.d("hello", "Email address already in use: ${exception.message}")
                            val bar =
                                Snackbar.make(
                                    binding.root,
                                    "Email Already Exist",
                                    Snackbar.LENGTH_SHORT
                                )
                            bar.setBackgroundTint(getColor(R.color.blue))
                            bar.setAction("OK") {
                                bar.dismiss()
                            }
                            bar.setActionTextColor(getColor(R.color.blue))
                            bar.show()
                        }

                        is FirebaseAuthInvalidUserException -> {
                            Log.d("hello", "Invalid user: ${exception.message}")
                            val bar =
                                Snackbar.make(
                                    binding.root,
                                    "Invalid credential",
                                    Snackbar.LENGTH_SHORT
                                )
                            bar.setBackgroundTint(getColor(R.color.blue))
                            bar.setAction("OK") {
                                bar.dismiss()
                            }
                            bar.setActionTextColor(getColor(R.color.blue))
                            bar.show()
                        }

                        else -> {
                            Log.d("hello", "Sign-up failed: ${exception?.message}")
                            val bar = Snackbar.make(binding.root, "Else ‼️", Snackbar.LENGTH_SHORT)
                            bar.setBackgroundTint(getColor(R.color.blue))
                            bar.setAction("OK") {
                                bar.dismiss()
                            }
                            bar.setActionTextColor(getColor(R.color.blue))
                            bar.show()
                        }
                    }
                }
            }
    }

    private fun sendEmailVerification(shopname: String) {
        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {

                    val bar = Snackbar.make(binding.root, "Mail Sent", Snackbar.LENGTH_SHORT)
                    bar.setBackgroundTint(getColor(R.color.blue))
                    bar.setAction("OK") {
                        bar.dismiss()
                        val intent = Intent(this, Signin::class.java)
                        startActivity(intent)
                        finish()
                    }
                    bar.setActionTextColor(getColor(R.color.blue))
                    bar.show()
                    val randomInt = Random.nextInt(0, 100)
                    val role = if (binding.roleSupplier.isChecked) "Supplier" else "Pharmacist"
                    val userData = hashMapOf(
                        "Email" to auth.currentUser?.email,
                        "Uid" to auth.currentUser?.uid,
                        "Uname" to binding.username.text.toString() + "firestore-$randomInt",
                        "Shop-Name" to shopname,
                        "Address" to binding.address.text.toString(),
                        "Role" to role
                    )

                    fs.collection("Users")
                        .document(auth.currentUser?.uid.toString())
                        .set(userData)
                    
                    if (role == "Pharmacist") {
                        val medicalStore = hashMapOf(
                            "Uid" to auth.currentUser?.uid,
                            "Uname" to binding.username.text.toString() + "firestore-$randomInt",
                            "ShopName" to shopname,
                            "Address" to binding.address.text.toString(),
                            "MedicineID" to emptyList<String>()
                        )
                        fs.collection("Medical-Store").document(auth.currentUser?.uid!!).set(medicalStore)
                    } else {
                        // For Supplier, we might want a different collection or data structure
                        val supplierData = hashMapOf(
                            "Uid" to auth.currentUser?.uid,
                            "Uname" to binding.username.text.toString() + "firestore-$randomInt",
                            "Name" to shopname, // Using shopname as business name
                            "Address" to binding.address.text.toString(),
                            "Email" to auth.currentUser?.email
                        )
                        fs.collection("Suppliers-Data").document(auth.currentUser?.uid!!).set(supplierData)
                    }
                    
                    val address = binding.address.text.toString()
                    addressApi(address, auth.currentUser?.uid!!,shopname) //Just call after changing Api key
                } else {
                    Log.d("D_CHECK", "sendEmailVerification: $task.exception?.message")
                }
            }?.addOnFailureListener {
                val bar = Snackbar.make(binding.root, "An Error Occurred", Snackbar.LENGTH_SHORT)

                bar.setAction("OK") {
                    bar.dismiss()
                }
                bar.setActionTextColor(getColor(R.color.white))
                bar.show()
            }
    }
    private fun addressApi(Address: String, Uid: String, shopname: String) {
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
                        fs.collection("Cordinates").document(auth.currentUser?.uid!!)
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
