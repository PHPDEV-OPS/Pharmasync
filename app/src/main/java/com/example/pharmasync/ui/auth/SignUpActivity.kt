package com.example.pharmasync.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pharmasync.R
import com.example.pharmasync.appContainer
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.data.repository.SignUpForm
import com.example.pharmasync.databinding.ActivitySignUpBinding
import com.example.pharmasync.util.UiMessage
import com.example.pharmasync.util.showMessage
import com.example.pharmasync.util.textValue
import com.example.pharmasync.util.validate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding

    private val selectedRole: Role
        get() = if (binding.roleToggle.checkedButtonId == R.id.role_supplier) Role.SUPPLIER else Role.PHARMACIST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnGoSignIn.setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
        }
        binding.roleToggle.addOnButtonCheckedListener { _, _, isChecked -> if (isChecked) renderRole() }
        renderRole()
        binding.btnSubmit.setOnClickListener { submit() }
        binding.confirmInput.setOnEditorActionListener { _, _, _ -> submit(); true }
    }

    private fun renderRole() {
        val supplier = selectedRole == Role.SUPPLIER
        binding.roleDescription.setText(if (supplier) R.string.role_supplier_desc else R.string.role_pharmacist_desc)
        binding.businessLayout.hint = getString(if (supplier) R.string.label_business_name else R.string.label_pharmacy_name)
    }

    private fun submit() {
        val required = getString(R.string.error_required)
        val email = binding.emailLayout.textValue
        val password = binding.passwordLayout.textValue
        val valid = listOf(
            binding.businessLayout.validate(if (binding.businessLayout.textValue.isEmpty()) required else null),
            binding.addressLayout.validate(if (binding.addressLayout.textValue.isEmpty()) required else null),
            binding.nameLayout.validate(if (binding.nameLayout.textValue.isEmpty()) required else null),
            binding.emailLayout.validate(if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) null else getString(R.string.error_invalid_email)),
            binding.passwordLayout.validate(if (password.length < 6) getString(R.string.error_password_short) else null),
            binding.confirmLayout.validate(if (binding.confirmLayout.textValue != password) getString(R.string.error_password_mismatch) else null),
        ).all { it }
        if (!valid) return

        val form = SignUpForm(
            role = selectedRole,
            name = binding.nameLayout.textValue,
            email = email,
            password = password,
            businessName = binding.businessLayout.textValue,
            address = binding.addressLayout.textValue,
            phone = binding.phoneLayout.textValue,
        )
        setLoading(true)
        lifecycleScope.launch {
            try {
                appContainer.authRepository.signUp(form)
                setLoading(false)
                showSuccess(email)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setLoading(false)
                binding.root.showMessage(UiMessage.Error(e))
            }
        }
    }

    private fun showSuccess(email: String) {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_check_circle)
            .setTitle(R.string.sign_up_success_title)
            .setMessage(getString(R.string.sign_up_success_message, email))
            .setCancelable(false)
            .setPositiveButton(R.string.action_go_to_sign_in) { _, _ ->
                startActivity(
                    Intent(this, SignInActivity::class.java)
                        .putExtra(SignInActivity.EXTRA_EMAIL, email)
                        .putExtra(SignInActivity.EXTRA_VERIFY_EMAIL, true)
                )
                finish()
            }
            .show()
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
        binding.btnSubmit.isEnabled = !loading
    }
}
