package com.example.pharmasync

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pharmasync.databinding.ActivityStartBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StartAct : AppCompatActivity() {
    lateinit var binding: ActivityStartBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
//        val serviceIntent = Intent(this, StockMonitorService::class.java)
//        ContextCompat.startForegroundService(this, serviceIntent)
        ObjectAnimator.ofFloat(binding.appNameText, "translationY", 100f).apply {
            duration = 2000
            start()
        }
        if (!isInternetAvailable()) {
            binding.animationView.visibility = View.GONE
            binding.appNameText.visibility = View.GONE
            showNoInternetDialog()
        } else {
            binding.animationView.visibility = View.VISIBLE
            binding.appNameText.visibility = View.VISIBLE
            auth = FirebaseAuth.getInstance()

            sharedPreferences = getSharedPreferences("USERDATA", Context.MODE_PRIVATE)
            editor = sharedPreferences.edit()

            auth = FirebaseAuth.getInstance()

            sharedPreferences = getSharedPreferences("USERDATA", MODE_PRIVATE)
            editor = sharedPreferences.edit()

            if (sharedPreferences.contains("Email") && sharedPreferences.contains("Pass")) {
                authenticateUser(
                    sharedPreferences.getString("Email", "").toString(),
                    sharedPreferences.getString("Pass", "").toString()
                )
            }
            else {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
//        return false
    }

    private fun showNoInternetDialog() {
        MaterialAlertDialogBuilder(this@StartAct)
            .setTitle("No Internet Connection")
            .setMessage("Please check your internet connection and try again.")
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    private fun authenticateUser(emailAddress: String, pass: String) {
        auth.signInWithEmailAndPassword(emailAddress, pass)
            .addOnCompleteListener { task ->
                val user = auth.currentUser
                if (task.isSuccessful) {
                    if (user != null && user.isEmailVerified) {
                        FirebaseFirestore.getInstance().collection("Users").document(user.uid).get()
                            .addOnSuccessListener { document ->
                                val role = document.getString("Role") ?: "Pharmacist"
                                val intent = if (role == "Supplier") {
                                    Intent(this, SupplierHome::class.java)
                                } else {
                                    Intent(this, Home::class.java)
                                }
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener {
                                val intent = Intent(this, Home::class.java)
                                startActivity(intent)
                                finish()
                            }
                    } else {
                        val bar = Snackbar.make(
                            binding.root,
                            "Verify Email",
                            Snackbar.LENGTH_SHORT
                        )
                        bar.setBackgroundTint(getColor(R.color.blue))
                        bar.setActionTextColor(getColor(R.color.blue))
                        bar.setAction("OK") {
                            bar.dismiss()
                        }
                        bar.show()
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }.addOnFailureListener {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
    }
}