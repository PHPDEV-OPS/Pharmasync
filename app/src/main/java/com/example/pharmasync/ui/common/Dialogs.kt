package com.example.pharmasync.ui.common

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.example.pharmasync.R
import com.example.pharmasync.databinding.DialogNumberInputBinding
import com.example.pharmasync.util.visibleIf
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object Dialogs {

    fun confirm(
        context: Context,
        title: CharSequence,
        message: CharSequence? = null,
        confirmText: CharSequence = context.getString(R.string.action_ok),
        onConfirm: () -> Unit,
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(confirmText) { _, _ -> onConfirm() }
            .show()
    }

    /**
     * Asks for a whole number. [validate] returns an error message or null; [summary] renders a
     * live line under the field (e.g. the order total).
     */
    fun number(
        context: Context,
        title: CharSequence,
        hint: CharSequence,
        initial: Int?,
        confirmText: CharSequence,
        helper: CharSequence? = null,
        validate: (Int) -> String? = { null },
        summary: ((Int) -> CharSequence)? = null,
        onConfirm: (Int) -> Unit,
    ) {
        val binding = DialogNumberInputBinding.inflate(LayoutInflater.from(context))
        binding.inputLayout.hint = hint
        binding.inputLayout.helperText = helper
        initial?.let { binding.input.setText(it.toString()) }
        binding.input.setSelectAllOnFocus(true)

        fun refresh(): Int? {
            val value = binding.input.text?.toString()?.toIntOrNull()
            val error = when {
                value == null -> context.getString(R.string.error_invalid_number)
                else -> validate(value)
            }
            binding.inputLayout.error = if (binding.input.text.isNullOrEmpty()) null else error
            if (summary != null) {
                binding.summary.visibleIf(value != null)
                if (value != null) binding.summary.text = summary(value)
            }
            return value.takeIf { error == null }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(binding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(confirmText, null)
            .create()
        dialog.setOnShowListener {
            refresh()
            binding.input.requestFocus()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = refresh()
                if (value == null) {
                    if (binding.inputLayout.error == null) binding.inputLayout.error = context.getString(R.string.error_invalid_number)
                    return@setOnClickListener
                }
                onConfirm(value)
                dialog.dismiss()
            }
        }
        binding.input.doAfterTextChanged { refresh() }
        dialog.show()
    }
}
