package com.example.pharmasync.ui.common

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.pharmasync.R
import com.example.pharmasync.appContainer
import com.example.pharmasync.data.model.CatalogDrug
import com.example.pharmasync.data.model.Medicine
import com.example.pharmasync.databinding.DialogMedicineFormBinding
import com.example.pharmasync.ui.scanner.BarcodeScannerActivity
import com.example.pharmasync.util.ImageLoader
import com.example.pharmasync.util.showMessage
import com.example.pharmasync.util.textValue
import com.example.pharmasync.util.validate
import com.example.pharmasync.util.visibleIf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Add/edit form for inventory and catalog items, with photo picking, barcode scanning and
 * openFDA name suggestions. Must be created while the fragment is initialising (it registers
 * activity-result launchers).
 */
class MedicineEditor(
    private val fragment: Fragment,
    private val onSave: (Medicine, Uri?) -> Unit,
) {
    private var binding: DialogMedicineFormBinding? = null
    private var pickedImage: Uri? = null
    private var suggestions: List<CatalogDrug> = emptyList()
    private var suggestJob: Job? = null
    private var ignoreNextNameChange = false

    private val pickImage = fragment.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val form = binding ?: return@registerForActivityResult
        if (uri != null) {
            pickedImage = uri
            showImage(form) { Glide.with(form.image).load(uri).centerCrop().into(form.image) }
        }
    }

    private val scanBarcode = fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_RESULT)
        if (result.resultCode == Activity.RESULT_OK && code != null) lookupBarcode(code)
    }

    fun open(existing: Medicine?, ownerId: String, newId: () -> String, knownCategories: List<String>) {
        val context = fragment.requireContext()
        val form = DialogMedicineFormBinding.inflate(LayoutInflater.from(context))
        binding = form
        pickedImage = null

        existing?.let { item ->
            form.nameInput.setText(item.name, false)
            form.categoryInput.setText(item.category, false)
            form.priceInput.setText(if (item.price > 0) item.price.toString() else "")
            form.stockInput.setText(item.stock.toString())
            if (item.lowStockThreshold > 0) form.lowStockInput.setText(item.lowStockThreshold.toString())
            form.manufacturerInput.setText(item.manufacturer)
            form.descriptionInput.setText(item.description)
            if (item.imageRef.isNotBlank()) showImage(form) { ImageLoader.load(form.image, item.imageRef) }
        }

        val categories = (knownCategories + DEFAULT_CATEGORIES).distinctBy { it.lowercase() }.sorted()
        form.categoryInput.setSimpleItems(categories.toTypedArray())
        form.btnPhoto.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        form.image.setOnClickListener { form.btnPhoto.performClick() }
        form.btnScan.setOnClickListener {
            scanBarcode.launch(Intent(context, BarcodeScannerActivity::class.java))
        }
        setUpSuggestions(form)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) R.string.medicine_form_add_title else R.string.medicine_form_edit_title)
            .setView(form.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val medicine = readForm(form, existing, ownerId, newId) ?: return@setOnClickListener
                onSave(medicine, pickedImage)
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener {
            suggestJob?.cancel()
            binding = null
        }
        dialog.show()
    }

    private fun readForm(form: DialogMedicineFormBinding, existing: Medicine?, ownerId: String, newId: () -> String): Medicine? {
        val context = form.root.context
        val required = context.getString(R.string.error_required)
        val invalid = context.getString(R.string.error_invalid_number)

        val name = form.nameLayout.textValue
        val priceText = form.priceLayout.textValue
        val stockText = form.stockLayout.textValue
        val lowText = form.lowStockLayout.textValue
        val price = priceText.toDoubleOrNull()
        val stock = stockText.toIntOrNull()
        val low = if (lowText.isEmpty()) 0 else lowText.toIntOrNull()

        val valid = listOf(
            form.nameLayout.validate(if (name.isEmpty()) required else null),
            form.priceLayout.validate(if (priceText.isEmpty()) required else if (price == null || price < 0) invalid else null),
            form.stockLayout.validate(if (stockText.isEmpty()) required else if (stock == null || stock < 0) invalid else null),
            form.lowStockLayout.validate(if (low == null || low < 0) invalid else null),
        ).all { it }
        if (!valid) return null

        return Medicine(
            id = existing?.id ?: newId(),
            ownerId = ownerId,
            name = name,
            description = form.descriptionLayout.textValue,
            category = form.categoryLayout.textValue.ifBlank { Medicine.DEFAULT_CATEGORY },
            price = price!!,
            stock = stock!!,
            lowStockThreshold = low!!,
            imageRef = existing?.imageRef.orEmpty(),
            manufacturer = form.manufacturerLayout.textValue,
            ndc = existing?.ndc.orEmpty(),
            createdAt = existing?.createdAt ?: 0L,
        )
    }

    private fun setUpSuggestions(form: DialogMedicineFormBinding) {
        val catalog = fragment.requireContext().appContainer.drugCatalogRepository
        form.nameInput.doAfterTextChanged { text ->
            if (ignoreNextNameChange) {
                ignoreNextNameChange = false
                return@doAfterTextChanged
            }
            val term = text?.toString().orEmpty()
            suggestJob?.cancel()
            if (term.trim().length < 3) return@doAfterTextChanged
            suggestJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
                delay(400)
                val results = try {
                    catalog.suggest(term)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return@launch // Suggestions are optional; stay silent when offline.
                }
                if (binding !== form) return@launch
                suggestions = results
                form.nameInput.setAdapter(
                    ArrayAdapter(form.root.context, android.R.layout.simple_list_item_1, results.map { it.displayName })
                )
                if (results.isNotEmpty() && form.nameInput.hasFocus()) form.nameInput.showDropDown()
            }
        }
        form.nameInput.setOnItemClickListener { _, _, position, _ ->
            suggestions.getOrNull(position)?.let { applyDrug(form, it, overwriteName = false) }
        }
    }

    private fun lookupBarcode(code: String) {
        val form = binding ?: return
        val context = form.root.context
        form.lookupProgress.visibleIf(true)
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val drug = runCatching { context.appContainer.drugCatalogRepository.lookupBarcode(code) }.getOrNull()
            if (binding !== form) return@launch
            form.lookupProgress.visibleIf(false)
            if (drug != null) {
                applyDrug(form, drug, overwriteName = true)
                form.root.showMessage(context.getString(R.string.msg_barcode_found, drug.displayName))
            } else {
                form.root.showMessage(context.getString(R.string.msg_barcode_not_found, code))
            }
        }
    }

    private fun applyDrug(form: DialogMedicineFormBinding, drug: CatalogDrug, overwriteName: Boolean) {
        if (overwriteName || form.nameLayout.textValue.isEmpty()) {
            ignoreNextNameChange = true
            form.nameInput.setText(drug.displayName, false)
        }
        form.categoryInput.setText(drug.category, false)
        if (form.manufacturerLayout.textValue.isEmpty()) form.manufacturerInput.setText(drug.manufacturer)
        if (form.descriptionLayout.textValue.isEmpty()) form.descriptionInput.setText(drug.description)
        form.priceInput.requestFocus()
    }

    private fun showImage(form: DialogMedicineFormBinding, load: () -> Unit) {
        form.image.setPadding(0, 0, 0, 0)
        form.image.imageTintList = null
        form.btnPhoto.setText(R.string.action_change_photo)
        load()
    }

    private companion object {
        val DEFAULT_CATEGORIES = listOf(
            "Analgesic", "Antibiotic", "Antihistamine", "Antihypertensive", "Antidiabetic",
            "Antifungal", "Antiseptic", "Cough & Cold", "Gastrointestinal", "Supplements",
            "Vitamins", "First Aid", "Medical Devices", Medicine.DEFAULT_CATEGORY,
        )
    }
}
