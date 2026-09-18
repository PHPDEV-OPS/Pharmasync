package com.example.pharmasync.ui.pharmacist

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.example.pharmasync.R
import com.example.pharmasync.databinding.DialogInvoiceFormBinding
import com.example.pharmasync.databinding.FragmentCollectionBinding
import com.example.pharmasync.ui.common.CollectionScreen
import com.example.pharmasync.ui.common.InvoiceAdapter
import com.example.pharmasync.ui.session.SessionFragment
import com.example.pharmasync.ui.session.SessionViewModel
import com.example.pharmasync.util.collectWhileViewStarted
import com.example.pharmasync.util.textValue
import com.example.pharmasync.util.validate
import com.example.pharmasync.util.visibleIf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class InvoicesFragment : SessionFragment() {

    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!
    private val query = MutableStateFlow("")

    private var form: DialogInvoiceFormBinding? = null
    private var pickedImage: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val current = form ?: return@registerForActivityResult
        if (uri != null) {
            pickedImage = uri
            current.imagePlaceholder.visibility = View.GONE
            current.image.visibility = View.VISIBLE
            current.imageError.visibility = View.GONE
            Glide.with(current.image).load(uri).centerCrop().into(current.image)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val screen = CollectionScreen(binding)
        screen.search(getString(R.string.search_hint)) { query.value = it }
        screen.fab(getString(R.string.action_add_invoice), R.drawable.ic_add) { openForm() }
        val adapter = InvoiceAdapter { startActivity(InvoicePreviewActivity.intent(requireContext(), it.id)) }
        binding.recycler.adapter = adapter

        collectWhileViewStarted(combine(session.invoices, query) { i, q -> i to q }) { (invoices, q) ->
            screen.header(getString(R.string.invoices_title), getString(R.string.invoices_subtitle, invoices.size))
            val visible = invoices.filter { q.isEmpty() || it.name.contains(q, true) || it.description.contains(q, true) }
            adapter.submitList(visible)
            when {
                invoices.isEmpty() -> screen.empty(
                    visible = true,
                    icon = R.drawable.ic_receipt,
                    title = getString(R.string.empty_invoices_title),
                    message = getString(R.string.empty_invoices_message),
                    actionText = getString(R.string.action_add_invoice),
                    action = ::openForm,
                )
                visible.isEmpty() -> screen.empty(true, R.drawable.ic_search, getString(R.string.empty_search_title), getString(R.string.empty_search_message))
                else -> screen.empty(visible = false)
            }
        }
        collectWhileViewStarted(combine(session.syncing, session.busy) { s, b -> b || SessionViewModel.Sync.INVOICES in s }) {
            screen.loading(it)
        }
    }

    private fun openForm() {
        val formBinding = DialogInvoiceFormBinding.inflate(layoutInflater)
        form = formBinding
        pickedImage = null
        formBinding.imageCard.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.invoice_form_title)
            .setView(formBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = formBinding.nameLayout.textValue
                val nameOk = formBinding.nameLayout.validate(if (name.isEmpty()) getString(R.string.error_required) else null)
                val image = pickedImage
                formBinding.imageError.visibleIf(image == null)
                if (!nameOk || image == null) return@setOnClickListener
                session.addInvoice(name, formBinding.descriptionLayout.textValue, image)
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener { form = null }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
