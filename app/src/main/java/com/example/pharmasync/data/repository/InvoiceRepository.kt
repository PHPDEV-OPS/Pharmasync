package com.example.pharmasync.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.net.Uri
import com.bumptech.glide.Glide
import com.example.pharmasync.data.firestore.Fs
import com.example.pharmasync.data.firestore.mirrorInto
import com.example.pharmasync.data.firestore.toFirestore
import com.example.pharmasync.data.firestore.toInvoice
import com.example.pharmasync.data.local.InvoiceDao
import com.example.pharmasync.data.local.InvoiceEntity
import com.example.pharmasync.data.model.Invoice
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class InvoiceRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val dao: InvoiceDao,
    private val storage: StorageRepository,
) {
    fun observe(ownerId: String): Flow<List<Invoice>> =
        dao.observe(ownerId).map { rows -> rows.map(InvoiceEntity::toModel) }.distinctUntilChanged()

    fun sync(ownerId: String, scope: CoroutineScope, onError: (Exception) -> Unit, onSynced: () -> Unit): ListenerRegistration =
        Fs.invoices(firestore, ownerId).mirrorInto(scope, onError, onSynced) { snapshot ->
            dao.replaceAll(ownerId, snapshot.documents.map { InvoiceEntity.from(it.toInvoice()) })
        }

    suspend fun add(ownerId: String, name: String, description: String, image: Uri): Invoice {
        val ref = Fs.invoices(firestore, ownerId).document()
        val url = storage.uploadJpeg("invoice_images/$ownerId/${ref.id}.jpg", image, maxDimension = 2000)
        val invoice = Invoice(ref.id, ownerId, name, description, url, System.currentTimeMillis())
        dao.upsert(listOf(InvoiceEntity.from(invoice)))
        ref.set(invoice.toFirestore()).addOnFailureListener { Log.w(TAG, "Invoice not saved", it) }
        return invoice
    }

    suspend fun delete(invoice: Invoice) {
        dao.delete(invoice.id)
        Fs.invoices(firestore, invoice.ownerId).document(invoice.id).delete()
        storage.deleteQuietly(invoice.imageRef)
    }

    /** Renders the invoice image into a single-page PDF in the public Downloads folder. */
    suspend fun exportPdf(invoice: Invoice): String = withContext(Dispatchers.IO) {
        val url = if (invoice.imageRef.startsWith("http")) {
            invoice.imageRef
        } else {
            FirebaseStorage.getInstance().reference.child(invoice.imageRef).downloadUrl.await().toString()
        }
        val bitmap: Bitmap = Glide.with(context).asBitmap().load(url).submit(1654, 2339).get()

        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create())
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdf.finishPage(page)

            val fileName = invoice.name.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "invoice" } + ".pdf"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Could not create download")
                context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
                    ?: throw IOException("Could not open download")
            } else {
                // Android 8–9: write to the app's own Downloads folder (no storage permission needed).
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                FileOutputStream(File(dir, fileName)).use { pdf.writeTo(it) }
            }
            fileName
        } finally {
            pdf.close()
        }
    }

    private companion object {
        const val TAG = "InvoiceRepository"
    }
}
