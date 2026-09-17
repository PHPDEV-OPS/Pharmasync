package com.example.pharmasync.ui.profile

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.example.pharmasync.R
import com.example.pharmasync.data.model.Role
import com.example.pharmasync.data.model.UserProfile
import com.example.pharmasync.databinding.DialogEditProfileBinding
import com.example.pharmasync.databinding.FragmentProfileBinding
import com.example.pharmasync.databinding.IncludeSettingsRowBinding
import com.example.pharmasync.ui.auth.WelcomeActivity
import com.example.pharmasync.ui.common.Dialogs
import com.example.pharmasync.ui.session.SessionFragment
import com.example.pharmasync.util.Formatters
import com.example.pharmasync.util.collectWhileViewStarted
import com.example.pharmasync.util.setPillColors
import com.example.pharmasync.util.showMessage
import com.example.pharmasync.util.textValue
import com.example.pharmasync.util.validate
import com.example.pharmasync.util.visibleIf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ProfileFragment : SessionFragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // The system photo picker needs no storage permission on any Android version.
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Glide.with(this).load(uri).centerCrop().into(binding.avatar)
            binding.avatarInitials.visibility = View.GONE
            session.uploadProfilePhoto(uri)
        }
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) session.pinCurrentLocation()
        else binding.root.showMessage(getString(R.string.error_location_permission))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val choosePhoto = View.OnClickListener {
            if (session.uploadProgress.value == null) {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        binding.avatar.setOnClickListener(choosePhoto)
        binding.btnChangePhoto.setOnClickListener(choosePhoto)
        binding.btnEditProfile.setOnClickListener { session.profile.value?.let(::openEditDialog) }

        binding.rowLocation.bind(R.drawable.ic_my_location, R.string.action_update_location, R.string.action_update_location_desc) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        binding.rowResetPassword.bind(R.drawable.ic_lock_reset, R.string.action_reset_password, R.string.action_reset_password_desc) {
            session.sendPasswordReset()
        }
        binding.rowLogout.bind(R.drawable.ic_logout, R.string.action_logout, R.string.action_logout_desc) {
            Dialogs.confirm(
                requireContext(),
                title = getString(R.string.action_logout),
                message = getString(R.string.dialog_logout_message),
                confirmText = getString(R.string.action_logout),
            ) { logOut() }
        }

        collectWhileViewStarted(session.profile) { profile -> if (profile != null) render(profile) }
        collectWhileViewStarted(session.uploadProgress) { progress ->
            binding.avatarProgress.visibleIf(progress != null)
            binding.uploadStatus.visibleIf(progress != null)
            binding.btnChangePhoto.isEnabled = progress == null
            if (progress != null) {
                binding.avatarProgress.setProgressCompat(progress, true)
                binding.uploadStatus.text = getString(R.string.msg_uploading_photo, progress)
            }
        }
    }

    private fun render(profile: UserProfile) = with(binding) {
        val displayName = profile.name.ifBlank { profile.businessName }
        name.text = displayName
        email.text = profile.email
        role.setText(if (profile.role == Role.SUPPLIER) R.string.role_supplier else R.string.role_pharmacist)
        role.setPillColors(R.color.md_on_primary_container, R.color.md_primary_container)
        business.text = profile.businessName.ifBlank { getString(R.string.not_set) }
        address.text = profile.address.ifBlank { getString(R.string.not_set) }
        phone.text = profile.phone.ifBlank { getString(R.string.not_set) }
        rowLocation.root.visibleIf(profile.role == Role.PHARMACIST)

        // While uploading, keep showing the locally picked image.
        if (session.uploadProgress.value != null) return@with
        if (profile.photoUrl.isNotBlank()) {
            avatarInitials.visibility = View.GONE
            Glide.with(this@ProfileFragment)
                .load(profile.photoUrl)
                .signature(ObjectKey(profile.photoVersion))
                .centerCrop()
                .into(avatar)
        } else {
            Glide.with(this@ProfileFragment).clear(avatar)
            avatar.setImageDrawable(null)
            avatarInitials.visibility = View.VISIBLE
            avatarInitials.text = Formatters.initials(displayName)
        }
    }

    private fun openEditDialog(profile: UserProfile) {
        val form = DialogEditProfileBinding.inflate(layoutInflater)
        form.nameInput.setText(profile.name)
        form.businessInput.setText(profile.businessName)
        form.businessLayout.hint = getString(if (profile.role == Role.SUPPLIER) R.string.label_business_name else R.string.label_pharmacy_name)
        form.phoneInput.setText(profile.phone)
        form.addressInput.setText(profile.address)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_edit_profile)
            .setView(form.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val required = getString(R.string.error_required)
                val valid = listOf(
                    form.nameLayout.validate(if (form.nameLayout.textValue.isEmpty()) required else null),
                    form.businessLayout.validate(if (form.businessLayout.textValue.isEmpty()) required else null),
                    form.addressLayout.validate(if (form.addressLayout.textValue.isEmpty()) required else null),
                ).all { it }
                if (!valid) return@setOnClickListener
                session.updateProfile(
                    profile.copy(
                        name = form.nameLayout.textValue,
                        businessName = form.businessLayout.textValue,
                        phone = form.phoneLayout.textValue,
                        address = form.addressLayout.textValue,
                    )
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun logOut() {
        viewLifecycleOwner.lifecycleScope.launch {
            val activity = requireActivity()
            session.signOut()
            startActivity(Intent(activity, WelcomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            activity.finish()
        }
    }

    private fun IncludeSettingsRowBinding.bind(icon: Int, title: Int, subtitle: Int, onClick: () -> Unit) {
        rowIcon.setImageResource(icon)
        rowTitle.setText(title)
        rowSubtitle.setText(subtitle)
        root.setOnClickListener { onClick() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
