package com.example.pharmasync.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.databinding.ActivityWelcomeBinding
import com.example.pharmasync.ui.explore.ExploreActivity
import com.example.pharmasync.ui.pharmacist.PharmacistHomeActivity
import com.example.pharmasync.ui.supplier.SupplierHomeActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnSignIn.setOnClickListener { startActivity(Intent(this, SignInActivity::class.java)) }
        binding.btnCreateAccount.setOnClickListener { startActivity(Intent(this, SignUpActivity::class.java)) }
        binding.btnExplore.setOnClickListener { startActivity(Intent(this, ExploreActivity::class.java)) }
    }
}

/** Home screen for [role], clearing the auth screens from the back stack. */
fun homeIntent(context: Context, role: Role): Intent =
    Intent(context, if (role == Role.SUPPLIER) SupplierHomeActivity::class.java else PharmacistHomeActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
