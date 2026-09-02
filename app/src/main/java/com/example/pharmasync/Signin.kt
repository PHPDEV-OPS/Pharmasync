package com.example.pharmasync

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.pharmasync.databinding.ActivitySigninBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore

class Signin : AppCompatActivity() {
    lateinit var binding: ActivitySigninBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var auth: FirebaseAuth
    private lateinit var fs: FirebaseFirestore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySigninBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = FirebaseAuth.getInstance()
        fs = FirebaseFirestore.getInstance()

        sharedPreferences = getSharedPreferences("USERDATA", MODE_PRIVATE)
        editor = sharedPreferences.edit()

        if (sharedPreferences.contains("Email") && sharedPreferences.contains("Pass")) {
            authenticateUser(
                sharedPreferences.getString("Email", "").toString(),
                sharedPreferences.getString("Pass", "").toString()
            )
        }
        binding.frgPassword.setOnClickListener {
            if (!binding.email.text.isNullOrBlank()) {
                resetPassword(binding.email.text.toString())
            }
        }
        binding.LoginBtn.setOnClickListener {
            if (binding.email.text.toString().isNotBlank() && binding.password.text.toString()
                    .isNotBlank()
            ) {
                signIn(binding.email.text.toString(), binding.password.text.toString())
            } else {
                custom_snackbar("Enter Proper Credentials")
            }
        }
        binding.bSignIn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }


    private fun custom_snackbar(message: String) {
        val bar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        bar.setBackgroundTint(resources.getColor(R.color.blue))
        bar.setActionTextColor(resources.getColor(R.color.white))
        bar.setAction("OK") {
            bar.dismiss()
        }
        bar.setActionTextColor(resources.getColor(R.color.blue))
        bar.show()
    }


    private fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email).addOnCompleteListener {
            if (it.isSuccessful) {
                val bar = Snackbar.make(
                    binding.root,
                    "Reset Password Mail Sent",
                    Snackbar.LENGTH_SHORT
                )
                bar.setBackgroundTint(getColor(R.color.blue))
                bar.setActionTextColor(getColor(R.color.blue))
                bar.setAction("Ok") {
                    bar.dismiss()
                }
                bar.show()
            }
        }
    }

    private fun signIn(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener {
            val user = auth.currentUser
            if (it.isSuccessful) {
                if (user!!.isEmailVerified) {
                    editor.clear()
                    editor.putString("Email", email)
                    editor.putString("Pass", pass)
                    editor.commit()
                    
                    fs.collection("Users").document(user.uid).get().addOnSuccessListener { document ->
                        val role = document.getString("Role") ?: "Pharmacist"
                        val intent = if (role == "Supplier") {
                            Intent(this, SupplierHome::class.java)
                        } else {
                            Intent(this, Home::class.java)
                        }
                        startActivity(intent)
                        finish()
                    }.addOnFailureListener {
                        val intent = Intent(this, Home::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    val bar = Snackbar.make(binding.root, "Verify Email", Snackbar.LENGTH_SHORT)
                    bar.setBackgroundTint(getColor(R.color.blue))
                    bar.setActionTextColor(getColor(R.color.blue))
                    bar.setAction("Ok") {
                        bar.dismiss()
                    }
                    bar.show()
                    sendEmailVerification()
                }
            } else {
                when (it.exception) {
                    is FirebaseAuthInvalidUserException -> {
                        val bar = Snackbar.make(binding.root, "Email is not Register", Snackbar.LENGTH_SHORT)
                        bar.setAction("Ok") { bar.dismiss() }
                        bar.setBackgroundTint(getColor(R.color.blue))
                        bar.setActionTextColor(getColor(R.color.blue))
                        bar.show()
                    }
                    else -> {
                        val bar = Snackbar.make(binding.root, "Invalid Credential", Snackbar.LENGTH_SHORT)
                        bar.setAction("Ok") { bar.dismiss() }
                        bar.setBackgroundTint(getColor(R.color.blue))
                        bar.setActionTextColor(getColor(R.color.blue))
                        bar.show()
                    }
                }
            }
        }
    }

    private fun sendEmailVerification() {
        val user = auth.currentUser
        user?.sendEmailVerification()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val bar = Snackbar.make(binding.root, "Mail Sent", Snackbar.LENGTH_SHORT)
                bar.setBackgroundTint(getColor(R.color.blue))
                bar.setAction("OK") { bar.dismiss() }
                bar.setActionTextColor(getColor(R.color.blue))
                bar.show()
            }
        }
    }

    private fun authenticateUser(emailAddress: String, pass: String) {
        auth.signInWithEmailAndPassword(emailAddress, pass)
            .addOnCompleteListener { task ->
                val user = auth.currentUser
                if (task.isSuccessful) {
                    if (user != null && user.isEmailVerified) {
                        fs.collection("Users").document(user.uid).get().addOnSuccessListener { document ->
                            val role = document.getString("Role") ?: "Pharmacist"
                            val intent = if (role == "Supplier") {
                                Intent(this, SupplierHome::class.java)
                            } else {
                                Intent(this, Home::class.java)
                            }
                            startActivity(intent)
                            finish()
                        }.addOnFailureListener {
                            val intent = Intent(this, Home::class.java)
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        val bar = Snackbar.make(binding.root, "Verify Email", Snackbar.LENGTH_SHORT)
                        bar.setBackgroundTint(getColor(R.color.blue))
                        bar.setActionTextColor(getColor(R.color.blue))
                        bar.setAction("OK") { bar.dismiss() }
                        bar.show()
                        sendEmailVerification()
                    }
                }
            }
    }
}
