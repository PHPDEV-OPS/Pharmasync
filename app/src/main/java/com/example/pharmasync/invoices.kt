package com.example.pharmasync

import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.pharmasync.databinding.CustomprogressBinding
import com.example.pharmasync.databinding.FragmentInvoicesBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale


class invoices : Fragment() {

    lateinit var binding: FragmentInvoicesBinding
    lateinit var previewDialog: BottomSheetDialog
    private lateinit var auth: FirebaseAuth
    private lateinit var fs: FirebaseFirestore
    private lateinit var sr: StorageReference
    lateinit var invoiceList: ArrayList<invoice>
    var imageUri: Uri = android.net.Uri.EMPTY
    private var m_img: ImageView? = null
    private var galleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
//                    galleryprofile()
                val dialog = Dialog(requireContext())
                val layout = CustomprogressBinding.inflate(layoutInflater)
                dialog.setContentView(layout.root)
                dialog.setCanceledOnTouchOutside(false)
                dialog.show()
                imageUri = result?.data!!.data!!
                m_img?.setImageURI(result.data!!.data)
                sr = FirebaseStorage.getInstance()
                    .getReference("Invoices/" + auth.currentUser?.uid)
                    .child("Invoice${auth.currentUser?.uid}_med${result.data?.data?.lastPathSegment}")
                sr.putFile(result.data?.data!!).addOnSuccessListener {
                    Log.d("D_CHECK", "Invoice Image Uploaded ")
                    dialog.dismiss()

                }.addOnFailureListener {
                    Log.d("D_CHECK", "Medicine Image Not Uploaded ")
                    dialog.dismiss()
                }
            }
        }
    var textWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            // this function is called before text is edited
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            // this function is called when text is edited
            search()
        }

        override fun afterTextChanged(s: Editable) {
            // this function is called after text is edited
        }
    }



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentInvoicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        fs = FirebaseFirestore.getInstance()
        invoiceList= arrayListOf()
        val recyclerView = binding.rvInvoice
        previewDialog = BottomSheetDialog(requireContext())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        get_data()
        binding.addMedicine.setOnClickListener {
            val view = LayoutInflater.from(requireContext())
                .inflate(R.layout.invoice_add, null, false)
            m_img = view.findViewById<ImageView>(R.id.M_img)
            val dialog = MaterialAlertDialogBuilder(
                requireContext()
            )
                .setView(view)
                .create()
            view.findViewById<ImageView>(R.id.M_img).setOnClickListener {
                requestpermission()
            }
            view.findViewById<Button>(R.id.submit_btn).setOnClickListener {

                if (view.findViewById<TextInputEditText>(R.id.inv_name).text.toString()
                        .isNotEmpty() && view.findViewById<TextInputEditText>(R.id.description).text.toString()
                        .isNotEmpty()
                ) {
                    val inv = hashMapOf(
                        "InvoiceName" to view.findViewById<TextInputEditText>(R.id.inv_name).text.toString(),
                        "Description" to view.findViewById<TextInputEditText>(R.id.description).text.toString(),
                        "ImageUri" to "Invoice${auth.currentUser?.uid}_med${imageUri.lastPathSegment.toString()}",
                        "time" to Timestamp.now(),
                        "InvoiceId" to imageUri.lastPathSegment.toString()
                    )
                    fs.collection("Invoice").document(auth.currentUser?.uid!!).collection("MyInvoice").document().set(inv)
                        .addOnSuccessListener {
                            Log.d("Hello", "onViewCreated: Added")
                            get_data()
                        }
                    Log.d("hello", "itemClickListener: $} ")
                    dialog.dismiss()
                } else {
                    custom_snackbar("Enter Proper Detail")
                }
            }
            dialog.show()
        }

        binding.searchBtn.setOnClickListener {
            if (binding.search.text.toString().isNotEmpty()) {
                fs.collection("Invoice").document(auth.currentUser?.uid!!)
                    .collection("MyInvoice")
                    .whereEqualTo("InvoiceName", binding.search.text.toString()).get()
                    .addOnSuccessListener {
                        invoiceList.clear()
                        for (data in it) {
                            val r = data.toObject(invoice::class.java)
                            Log.d("D_CHECK", "getInventory: $r")
                            invoiceList.add(r)
                        }
                        invoiceList.sortBy { it.time }
                        load_data(invoiceList)
                    }
            }
        }

        binding.search.addTextChangedListener(textWatcher)
    }

    private fun custom_snackbar(message: String) {
        val bar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        bar.setBackgroundTint(resources.getColor(R.color.blue))
        bar.setAction("OK") {
            bar.dismiss()
        }
        bar.setActionTextColor(resources.getColor(R.color.white))
        bar.show()
    }

    fun get_data() {
        fs = FirebaseFirestore.getInstance()
        fs.collection("Invoice").document(auth.currentUser?.uid!!).collection("MyInvoice").get()
            .addOnSuccessListener {
                invoiceList.clear()
                for (data in it) {
                    val r = data.toObject(invoice::class.java)
                    Log.d("D_CHECK", "getInventory: $r")
                    invoiceList.add(r)
                }
                invoiceList.sortBy { it.time }

                load_data(invoiceList)
            }
    }
    fun load_data(medicine: ArrayList<invoice>) {
        Log.d("D_CHECK", "load_data: ${medicine}")
        simulateDataLoading()
        val adapter = invoiceAdapter(requireContext(), medicine)
        binding.rvInvoice.adapter = adapter
//        if (medicine.size == 0) {
//            binding.msg.visibility = View.VISIBLE
//        }


        adapter.onItem(object : invoiceAdapter.onitemclick {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun itemClickListener(position: Int) {
                val view = View.inflate(requireContext(), R.layout.invoice_preview, null)
                previewDialog.setContentView(view)
                previewDialog.show()
                previewDialog.setCancelable(true)
                previewDialog.setCanceledOnTouchOutside(true)
                val img = view.findViewById<ImageView>(R.id.I_img)
                sr = FirebaseStorage.getInstance()
                    .getReference("Invoices/" + auth.currentUser?.uid)
                    .child(invoiceList[position].ImageUri.toString())

                sr.downloadUrl.addOnSuccessListener {
                    Glide.with(requireContext())
                        .load(it)
                        .into(img)
                }
                val date = invoiceList[position].time
                val obj = date?.toDate()
                val timeZone = ZoneId.of("UTC")
                val localDateTime =
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(obj?.time!!), timeZone)
                val year = localDateTime.year.toString()
                val month = localDateTime.month.getDisplayName(
                    TextStyle.SHORT,
                    Locale.getDefault()
                ).toString()
                val day = localDateTime.dayOfMonth.toString()

                val time = "$day $month $year"
                view.findViewById<TextView>(R.id.inv_name).text =
                    invoiceList[position].InvoiceName.toString()
                view.findViewById<TextView>(R.id.date).text =time
                view.findViewById<TextView>(R.id.description).text = invoiceList[position].Description

//                view.findViewById<TextView>(R.id.date).text = invoiceList[position].Description

                val downloadbtn = view.findViewById<MaterialButton>(R.id.download)
                val localFile = File.createTempFile("image", "jpg")
                downloadbtn.setOnClickListener {
                    sr.getFile(localFile).addOnSuccessListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            createPdfFromLargeImage(requireContext(), localFile,invoiceList[position].InvoiceName.toString())
                        }
                    }
                }

                val notifyBtn = view.findViewById<Button>(R.id.notify)
                val deleteBtn = view.findViewById<Button>(R.id.delete)
                deleteBtn.setOnClickListener {
                    MaterialAlertDialogBuilder(
                        requireContext(),
                    )
                        // final minor changes
                        .setTitle("Delete Invoice")
                        .setIcon(R.drawable.baseline_medical_services_24)
                        .setMessage("Are you sure you want to remove ${invoiceList[position].InvoiceName}?")
                        .setPositiveButton("Yes") { dialog, which ->
                            sr = FirebaseStorage.getInstance()
                                .getReference("Invoices/" + auth.currentUser?.uid!!)
                                .child(invoiceList[position].ImageUri.toString())
                            sr.delete()

//                        val product_ID = product[position]
                            fs.collection("Invoice").document(auth.currentUser?.uid!!)
                                .collection("MyInvoice")
                                .whereEqualTo("InvoiceId", invoiceList[position].InvoiceId).get()
                                .addOnSuccessListener {
                                    for (doc in it) {
                                        val docRef = doc.reference
                                        docRef.delete().addOnSuccessListener {
                                            previewDialog.dismiss()
                                            get_data()
                                            Log.d("D_CHECK", "onItemLongClick: Deleted")
                                        }.addOnFailureListener {
                                            Log.d("D_CHECK", "onItemLongClick: ${it.message}")
                                        }
                                    }
                                    Log.d("D_CHECK", "onItemLongClick: ${it}")
                                }.addOnFailureListener {
                                    Log.d("D_CHECK", "onItemLongClick: ${it.message}")
                                }

                            dialog.dismiss()
                        }
                        .setNegativeButton("No") { dialog, which ->
                            dialog.dismiss()
                        }
                        .show();
                }

            }

        })


        binding.rvInvoice.viewTreeObserver.addOnGlobalLayoutListener {
            binding.ProgressBar.visibility = View.GONE

        }
    }

    private fun checkpermissionRead() = ActivityCompat.checkSelfPermission(
        requireContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkpermissionReadImages() = ActivityCompat.checkSelfPermission(
        requireContext(), android.Manifest.permission.READ_MEDIA_IMAGES
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestpermission() {
        val permissiontoRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 33) {
            if (!checkpermissionReadImages()) {
                permissiontoRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                val galleryIntent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                galleryLauncher.launch(galleryIntent)
            }

            if (permissiontoRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    requireContext() as Activity, permissiontoRequest.toTypedArray(), 0
                )
            }
        } else {
            if (!checkpermissionRead()) {
                permissiontoRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                val galleryIntent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                galleryLauncher.launch(galleryIntent)
////                profileImage()
//                profile()
            }

            if (permissiontoRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    requireContext() as Activity, permissiontoRequest.toTypedArray(), 0
                )
            }
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0 && grantResults.isNotEmpty()) {
            for (i in grantResults.indices) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("hello", "onRequestPermissionsResult: Done")

                }
            }
        }
    }
    private fun simulateDataLoading() {
        binding.ProgressBar.postDelayed({
            binding.rvInvoice.visibility = View.VISIBLE

        }, 2000)
    }
    fun search() {
        if (binding.search.text.toString().isNotEmpty()) {
            val filterList = invoiceList.filter {
                it.InvoiceName?.startsWith(
                    binding.search.text.toString(),
                    ignoreCase = true
                )!!
            }
            Log.d("D_CHECK", "search: ${filterList}")
            load_data(filterList as ArrayList<invoice>)
        } else {
            load_data(invoiceList)
        }

    }
    @RequiresApi(Build.VERSION_CODES.Q)
    fun createPdfFromLargeImage(context: Context, imageFile: File,filename:String) {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        // Get the image dimensions
        BitmapFactory.decodeFile(imageFile.absolutePath, options)

        // Scale down the image if it's too large
        options.inSampleSize = calculateInSampleSize(options, 1000, 1000) // Customize this as needed
        options.inJustDecodeBounds = false

        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)

        // Step 2: Create a PDF from the Bitmap
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        val canvas = page.canvas
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        pdfDocument.finishPage(page)

        // Step 3: Save the PDF to the Downloads folder using MediaStore
        val contentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "${filename}.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) // Set the folder path to Downloads
        }

        // Insert the file into the Downloads collection
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }

                pdfDocument.close()

                Log.d("PDF", "PDF saved to Downloads: $uri")
                previewDialog.dismiss()
                custom_snackbar("PDF saved to Downloads")

            } catch (e: IOException) {
                pdfDocument.close()
                Log.e("PDF", "Error saving PDF to Downloads", e)
            }
        } else {
            Log.e("PDF", "Failed to create URI for PDF")
        }
    }

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}