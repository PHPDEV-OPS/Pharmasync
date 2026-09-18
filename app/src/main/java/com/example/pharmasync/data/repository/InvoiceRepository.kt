package com.example.pharmasync.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.example.pharmasync.R
import com.example.pharmasync.data.local.InvoiceDao
import com.example.pharmasync.data.local.InvoiceEntity
import com.example.pharmasync.data.model.Invoice
import com.example.pharmasync.data.remote.CreateInvoiceRequest
import com.example.pharmasync.data.remote.PharmasyncApi
import com.example.pharmasync.data.remote.apiCall
import com.example.pharmasync.data.remote.toInvoice
import com.example.pharmasync.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Invoices live in Neon Postgres; their images sit in the private Neon Object Storage bucket and
 * are read through short-lived signed URLs returned by the backend.
 */
class InvoiceRepository(
    private val context: Context,
    private val api: PharmasyncApi,
    private val dao: InvoiceDao,
    private val storage: StorageRepository,
) {
    fun observe(ownerId: String): Flow<List<Invoice>> =
        dao.observe(ownerId).map { rows -> rows.map(InvoiceEntity::toModel) }.distinctUntilChanged()

    suspend fun refresh(ownerId: String) {
        val invoices = apiCall { api.invoices() }.map { it.toInvoice() }
        dao.replaceAll(ownerId, invoices.map(InvoiceEntity::from))
    }

    suspend fun add(ownerId: String, name: String, description: String, image: Uri): Invoice {
        val uploaded = storage.upload(ImageKind.INVOICE, image)
        val invoice = apiCall {
            api.createInvoice(CreateInvoiceRequest(UUID.randomUUID().toString(), name, description, uploaded.key))
        }.toInvoice()
        dao.upsert(listOf(InvoiceEntity.from(invoice)))
        return invoice
    }

    suspend fun delete(invoice: Invoice) {
        apiCall { api.deleteInvoice(invoice.id) }
        dao.delete(invoice.id)
    }

    /** Signed image links expire, so the preview screen fetches a fresh copy before rendering. */
    suspend fun find(ownerId: String, invoiceId: String): Invoice? {
        val invoices = apiCall { api.invoices() }.map { it.toInvoice() }
        dao.replaceAll(ownerId, invoices.map(InvoiceEntity::from))
        return invoices.firstOrNull { it.id == invoiceId }
    }

    // region PDF

    /**
     * Renders [invoice] into an A4 PDF in the app cache. The invoice image is included when it can
     * be loaded; otherwise the PDF still contains the invoice details and a note.
     */
    suspend fun renderPdf(invoice: Invoice): File = withContext(Dispatchers.IO) {
        val image = runCatching { loadImage(invoice.imageRef) }
            .onFailure { Log.w(TAG, "Invoice image unavailable for PDF", it) }
            .getOrNull()

        val dir = File(context.cacheDir, PDF_DIR).apply { mkdirs() }
        val file = File(dir, fileName(invoice))
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            drawPage(page.canvas, invoice, image)
            pdf.finishPage(page)
            FileOutputStream(file).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
            image?.recycle()
        }
        file
    }

    /** First page of [pdf] rendered to a bitmap [width] pixels wide, for on-screen preview. */
    suspend fun renderPreview(pdf: File, width: Int): Bitmap = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val height = (width.toFloat() * page.height / page.width).toInt()
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }

    /** Copies a rendered PDF into the public Downloads folder. Returns a viewable Uri. */
    suspend fun saveToDownloads(pdf: File): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, pdf.name)
                put(MediaStore.Downloads.MIME_TYPE, PDF_MIME)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Pharmasync")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create a file in Downloads")
            try {
                resolver.openOutputStream(uri)?.use { out -> FileInputStream(pdf).use { it.copyTo(out) } }
                    ?: throw IOException("Could not write to Downloads")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            uri
        } else {
            // Android 8–9: the app's own Downloads folder needs no storage permission.
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val target = File(dir, pdf.name)
            pdf.copyTo(target, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        }
    }

    fun shareUri(pdf: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)

    private suspend fun loadImage(url: String): Bitmap? {
        if (url.isBlank()) return null
        val bytes = storage.download(url)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun drawPage(canvas: Canvas, invoice: Invoice, image: Bitmap?) {
        val margin = 40f
        val contentWidth = PAGE_WIDTH - 2 * margin
        canvas.drawColor(Color.WHITE)

        // Brand header band
        val brand = context.getColor(R.color.brand_primary)
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 86f, Paint().apply { color = brand })
        val white = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawText(context.getString(R.string.app_name), margin, 40f, white.apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD })
        canvas.drawText(context.getString(R.string.pdf_invoice_record), margin, 64f, TextPaint(white).apply { textSize = 11f; typeface = Typeface.DEFAULT })

        var y = 120f
        val dark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(26, 27, 33) }
        val muted = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(91, 94, 110); textSize = 11f }

        y = drawWrapped(canvas, invoice.name, TextPaint(dark).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD }, margin, y, contentWidth) + 6f
        canvas.drawText(Formatters.dateTime(invoice.createdAt), margin, y + 11f, muted)
        y += 24f
        if (invoice.description.isNotBlank()) {
            y = drawWrapped(canvas, invoice.description, TextPaint(dark).apply { textSize = 12f }, margin, y, contentWidth) + 12f
        }
        canvas.drawLine(margin, y, PAGE_WIDTH - margin, y, Paint().apply { color = Color.rgb(225, 227, 237); strokeWidth = 1f })
        y += 16f

        val footerTop = PAGE_HEIGHT - 40f
        if (image != null) {
            val available = RectF(margin, y, PAGE_WIDTH - margin, footerTop - 12f)
            val scale = minOf(available.width() / image.width, available.height() / image.height)
            val w = image.width * scale
            val h = image.height * scale
            val left = available.left + (available.width() - w) / 2
            canvas.drawBitmap(image, Rect(0, 0, image.width, image.height), RectF(left, y, left + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawText(context.getString(R.string.pdf_image_unavailable), margin, y + 14f, muted)
        }

        canvas.drawText(
            context.getString(R.string.pdf_generated_on, Formatters.dateTime(System.currentTimeMillis())),
            margin, footerTop + 16f, TextPaint(muted).apply { textSize = 9f },
        )
    }

    @Suppress("DEPRECATION")
    private fun drawWrapped(canvas: Canvas, text: String, paint: TextPaint, x: Float, y: Float, width: Float): Float {
        val layout = StaticLayout(text, paint, width.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }

    private fun fileName(invoice: Invoice): String {
        val base = invoice.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_').ifBlank { "invoice" }
        return "${base}_${invoice.id.take(6)}.pdf"
    }

    // endregion

    companion object {
        private const val TAG = "InvoiceRepository"
        private const val PDF_DIR = "invoices"
        const val PDF_MIME = "application/pdf"

        // A4 in PostScript points.
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
    }
}
