package com.example.pharmasync.ui.pharmacist

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pharmasync.R
import com.example.pharmasync.appContainer
import com.example.pharmasync.data.model.Invoice
import com.example.pharmasync.data.repository.InvoiceRepository
import com.example.pharmasync.databinding.ActivityInvoicePreviewBinding
import com.example.pharmasync.ui.common.Dialogs
import com.example.pharmasync.util.Formatters
import com.example.pharmasync.util.showMessage
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** Shows the exact PDF that will be downloaded, with download, share and delete actions. */
class InvoicePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInvoicePreviewBinding
    private val repository get() = appContainer.invoiceRepository
    private var invoice: Invoice? = null
    private var pdf: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInvoicePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding.header) {
            headerBack.visibility = View.VISIBLE
            headerBack.setOnClickListener { finish() }
            headerTitle.setText(R.string.invoice_detail)
            headerAction.visibility = View.VISIBLE
            headerAction.setIconResource(R.drawable.ic_delete)
            headerAction.contentDescription = getString(R.string.action_delete)
            headerAction.setOnClickListener { confirmDelete() }
        }
        binding.btnDownload.setOnClickListener { download() }
        binding.btnShare.setOnClickListener { share() }

        load()
    }

    private fun load() {
        val id = intent.getStringExtra(EXTRA_INVOICE_ID)
        val uid = appContainer.auth.currentUser?.uid
        if (id == null || uid == null) {
            finish()
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            try {
                val item = repository.find(uid, id)
                if (item == null) {
                    finish()
                    return@launch
                }
                invoice = item
                binding.header.headerTitle.text = item.name
                binding.header.headerSubtitle.visibility = View.VISIBLE
                binding.header.headerSubtitle.text = Formatters.dateTime(item.createdAt)

                val file = repository.renderPdf(item)
                pdf = file
                val width = resources.displayMetrics.widthPixels.coerceIn(720, 1440)
                binding.page.setImageBitmap(repository.renderPreview(file, width))
                binding.btnDownload.isEnabled = true
                binding.btnShare.isEnabled = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Preview failed", e)
                binding.previewError.visibility = View.VISIBLE
                binding.previewError.text = getString(R.string.error_pdf_preview)
            } finally {
                setBusy(false)
            }
        }
    }

    private fun download() {
        val file = pdf ?: return
        setBusy(true)
        lifecycleScope.launch {
            try {
                val uri = repository.saveToDownloads(file)
                Snackbar.make(binding.root, getString(R.string.msg_pdf_saved, file.name), Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.btnDownload)
                    .setAction(R.string.action_open) { open(uri) }
                    .show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                binding.root.showMessage(getString(R.string.error_pdf_save))
            } finally {
                setBusy(false)
            }
        }
    }

    private fun share() {
        val file = pdf ?: return
        val intent = Intent(Intent.ACTION_SEND)
            .setType(InvoiceRepository.PDF_MIME)
            .putExtra(Intent.EXTRA_STREAM, repository.shareUri(file))
            .putExtra(Intent.EXTRA_SUBJECT, invoice?.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    private fun open(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, InvoiceRepository.PDF_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            binding.root.showMessage(getString(R.string.error_no_pdf_app))
        }
    }

    private fun confirmDelete() {
        val item = invoice ?: return
        Dialogs.confirm(
            this,
            title = getString(R.string.dialog_delete_title, item.name),
            message = getString(R.string.dialog_delete_message),
            confirmText = getString(R.string.action_delete),
        ) {
            lifecycleScope.launch {
                repository.delete(item)
                finish()
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.INVISIBLE
        if (busy) {
            binding.btnDownload.isEnabled = false
        } else if (pdf != null) {
            binding.btnDownload.isEnabled = true
        }
    }

    companion object {
        private const val TAG = "InvoicePreview"
        private const val EXTRA_INVOICE_ID = "invoice_id"

        fun intent(context: Context, invoiceId: String): Intent =
            Intent(context, InvoicePreviewActivity::class.java).putExtra(EXTRA_INVOICE_ID, invoiceId)
    }
}
