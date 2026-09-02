package com.example.pharmasync

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.pharmasync.databinding.CustomprogressBinding
import com.example.pharmasync.databinding.FragmentMedicinesBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference


class Medicines : Fragment() {
    lateinit var binding: FragmentMedicinesBinding
    lateinit var previewDialog: BottomSheetDialog
    private lateinit var auth: FirebaseAuth
    private lateinit var fs: FirebaseFirestore
    lateinit private var filter: ArrayList<String>
    lateinit private var filter_name: ArrayList<String>
    private lateinit var sr: StorageReference
    lateinit var medicineList: ArrayList<medicine>
    var imageUri: Uri = android.net.Uri.EMPTY
    private var m_img: ImageView? = null
    private var medicineNameInput: TextInputEditText? = null

    private val barcodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCAN_RESULT")
            medicineNameInput?.setText(barcode)
        }
    }

    private var galleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
//                    galleryprofile()
                val dialog = Dialog(requireContext())
                val layout = CustomprogressBinding.inflate(layoutInflater)
                dialog.setContentView(layout.root)
                dialog.setCanceledOnTouchOutside(false)
                dialog.show()
                imageUri = result?.data!!.data!!
                m_img?.setImageURI(result.data!!.data)
                sr = FirebaseStorage.getInstance()
                    .getReference("Medicines/" + auth.currentUser?.uid)
                    .child("Shop${auth.currentUser?.uid}_med${result.data?.data?.lastPathSegment}")
                sr.putFile(result.data?.data!!).addOnSuccessListener {
                    Log.d("D_CHECK", "Medicine Image Uploaded ")
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
            search()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentMedicinesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        fs = FirebaseFirestore.getInstance()
        medicineList = arrayListOf()
        val recyclerView = binding.rvMedicine
        previewDialog = BottomSheetDialog(requireContext())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        get_data()
        binding.addMedicine.setOnClickListener {
            val view = LayoutInflater.from(requireContext())
                .inflate(R.layout.medicine_add, null, false)
            m_img = view.findViewById<ImageView>(R.id.M_img)
            medicineNameInput = view.findViewById<TextInputEditText>(R.id.medicine_name)
            
            val dialog = MaterialAlertDialogBuilder(
                requireContext()
            )
                .setView(view)
                .create()
            
            view.findViewById<TextInputLayout>(R.id.layout_1).setEndIconOnClickListener {
                val intent = Intent(requireContext(), BarcodeScannerActivity::class.java)
                barcodeLauncher.launch(intent)
            }

            view.findViewById<ImageView>(R.id.M_img).setOnClickListener {
                requestpermission()
            }
            view.findViewById<Button>(R.id.submit_btn).setOnClickListener {

                if (view.findViewById<TextInputEditText>(R.id.medicine_name).text.toString()
                        .isNotEmpty() && view.findViewById<TextInputEditText>(R.id.priceper_unit).text.toString()
                        .isNotEmpty() && view.findViewById<TextInputEditText>(R.id.stock).text.toString()
                        .isNotEmpty() && view.findViewById<TextInputEditText>(R.id.category).text.toString()
                        .isNotEmpty() && view.findViewById<TextInputEditText>(R.id.description).text.toString()
                        .isNotEmpty()
                ) {


                    val inventoryInfo = hashMapOf(
                        "Medicine" to view.findViewById<TextInputEditText>(R.id.medicine_name).text.toString(),
                        "Description" to view.findViewById<TextInputEditText>(R.id.description).text.toString(),
                        "Stock" to view.findViewById<TextInputEditText>(R.id.stock).text.toString(),
                        "PricePerUnit" to view.findViewById<TextInputEditText>(R.id.priceper_unit).text.toString(),
                        "Category" to view.findViewById<TextInputEditText>(R.id.category).text.toString(),
                        "CreatedAt" to Timestamp.now().toDate(),
                        "LowStock" to "-1",
                        "MedicineId" to imageUri.lastPathSegment.toString(),
                        "UserID" to auth.currentUser?.uid!!,
                        "ImageUri" to "Shop${auth.currentUser?.uid}_med${imageUri.lastPathSegment.toString()}"
                    )
                    fs.collection("Medical-Store").document(auth.currentUser?.uid!!).update(
                        "MedicineID",
                        FieldValue.arrayUnion(imageUri.lastPathSegment.toString())
                    ).addOnSuccessListener {
                        Log.d("Hello", "onViewCreated: Addedddd")
                    }
                    fs.collection("Medicines").document(auth.currentUser?.uid!!)
                        .collection("MyMedicines").document().set(inventoryInfo)
                        .addOnCompleteListener {
                            if (it.isSuccessful) {
                                custom_snackbar("Medicine Added")
//                                checkUser()
                                get_data()

                            } else {
                                custom_snackbar("Error")
                            }
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
                fs.collection("Medicines").document(auth.currentUser?.uid!!)
                    .collection("MyMedicines")
                    .whereEqualTo("Medicine", binding.search.text.toString()).get()
                    .addOnSuccessListener {
                        medicineList.clear()
                        for (data in it) {
                            val r = data.toObject(medicine::class.java)
                            Log.d("D_CHECK", "getInventory: $r")
                            medicineList.add(r)
                        }
                        medicineList.sortBy { it.CreatedAt }
                        Log.d("D_CHECK", "load_data: ${medicineList.size}++++")

                        load_data(medicineList)
                    }
            }
        }

        binding.search.addTextChangedListener(textWatcher)



    }

    fun get_data() {
        fs = FirebaseFirestore.getInstance()
        fs.collection("Medicines").document(auth.currentUser?.uid!!).collection("MyMedicines").get()
            .addOnSuccessListener {
                medicineList.clear()
                for (data in it) {
                    val r = data.toObject(medicine::class.java)
                    Log.d("D_CHECK", "getInventory: $r")
                    medicineList.add(r)
                }
                medicineList.sortBy { it.CreatedAt }
                Log.d("D_CHECK", "load_data: ${medicineList.size}++++")

                load_data(medicineList)
            }
    }

    fun load_data(medicine: ArrayList<medicine>) {
        simulateDataLoading()
        val adapter = medicineAdapter(requireContext(), medicine)
        binding.rvMedicine.adapter = adapter
        if (medicine.size == 0) {
            binding.msg.visibility = View.VISIBLE
        }else{
            binding.msg.visibility = View.GONE

        }
        adapter.onEdit(object : medicineAdapter.EditClick {
            override fun onEditClick(position: Int) {
                val view =
                    View.inflate(requireContext(), R.layout.edit_dialog, null)

                view.findViewById<TextView>(R.id.textView).text = medicineList[position].Medicine
                view.findViewById<TextInputEditText>(R.id.m_qty).text =
                    Editable.Factory.getInstance().newEditable(medicineList[position].Stock)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setPositiveButton("Save") { dialog, which ->
                        fs.collection("Medicines").document(auth.currentUser?.uid!!)
                            .collection("MyMedicines")
                            .whereEqualTo("MedicineId", medicineList[position].MedicineId).get()
                            .addOnSuccessListener {
                                for (doc in it) {
                                    val docRef = doc.reference
                                    docRef.update(
                                        "Stock",
                                        view.findViewById<TextInputEditText>(R.id.m_qty).text.toString()
                                    ).addOnSuccessListener {
                                        get_data()
                                        monitorStock()
                                        custom_snackbar("${medicineList[position].Medicine} Updated Successfully")
                                        Log.d("D_CHECK", "onItemLongClick: Updated")
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
                }.show()
            }

        })

        adapter.onItem(object : medicineAdapter.onitemclick {
            override fun itemClickListener(position: Int) {
                val view = View.inflate(requireContext(), R.layout.preview_dialog, null)
                previewDialog.setContentView(view)
                previewDialog.show()
                previewDialog.setCancelable(true)
                previewDialog.setCanceledOnTouchOutside(true)
                val img = view.findViewById<ImageView>(R.id.M_img)
                sr = FirebaseStorage.getInstance()
                    .getReference("Medicines/" + auth.currentUser?.uid)
                    .child(medicineList[position].ImageUri.toString())

                sr.downloadUrl.addOnSuccessListener {
                    Glide.with(requireContext())
                        .load(it)
                        .into(img)
                }
                view.findViewById<TextView>(R.id.med_name).text =
                    medicineList[position].Medicine.toString()
                view.findViewById<TextView>(R.id.product_unit).text =
                    medicineList[position].Stock.toString()
                view.findViewById<TextView>(R.id.category).text = medicineList[position].Category
                view.findViewById<TextView>(R.id.pp_unit).text =
                    medicineList[position].PricePerUnit.toString() + "/-"
                view.findViewById<TextView>(R.id.m_id).text = medicineList[position].MedicineId
                val notifyBtn = view.findViewById<Button>(R.id.notify)
                val deleteBtn = view.findViewById<Button>(R.id.delete)
                deleteBtn.setOnClickListener {
                    MaterialAlertDialogBuilder(
                        requireContext(),
                    )
                        .setTitle("Remove Product")
                        .setIcon(R.drawable.baseline_medical_services_24)
                        .setMessage("Are you sure you want to remove ${medicineList[position].Medicine}?")
                        .setPositiveButton("Yes") { dialog, which ->
                            sr = FirebaseStorage.getInstance()
                                .getReference("Product/" + auth.currentUser?.uid!!)
                                .child(medicineList[position].ImageUri.toString())
                            sr.delete()

//                        val product_ID = product[position]
                            fs.collection("Medicines").document(auth.currentUser?.uid!!)
                                .collection("MyMedicines")
                                .whereEqualTo("MedicineId", medicineList[position].MedicineId).get()
                                .addOnSuccessListener {
                                    for (doc in it) {
                                        val docRef = doc.reference
                                        docRef.delete().addOnSuccessListener {
                                            get_data()
                                            previewDialog.dismiss()
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

                val notifyview =
                    View.inflate(requireContext(), R.layout.edit_dialog, null)
                notifyBtn.setOnClickListener {
                    MaterialAlertDialogBuilder(requireContext()).apply {
                        setView(notifyview)
                        notifyview.findViewById<TextView>(R.id.tvmsg).text = "Notify Low Stock"
                        notifyview.findViewById<TextView>(R.id.textView).text =
                            "Stock Alert For ${medicineList[position].Medicine}"
                        notifyview.findViewById<TextInputLayout>(R.id.layout_3).hint =
                            "Alert Stock"
                        notifyview.findViewById<TextInputEditText>(R.id.m_qty).text =
                            Editable.Factory.getInstance()
                                .newEditable(medicineList[position].LowStock)
                        previewDialog.dismiss()
                        setPositiveButton("Save") { dialog, which ->
                            fs.collection("Medicines").document(auth.currentUser?.uid!!)
                                .collection("MyMedicines")
                                .whereEqualTo("MedicineId", medicineList[position].MedicineId).get()
                                .addOnSuccessListener {
                                    for (doc in it) {
                                        val docRef = doc.reference
                                        val qty =
                                            notifyview.findViewById<TextInputEditText>(R.id.m_qty).text.toString()
                                                .toInt()
                                        docRef.update(
                                            "LowStock",
                                            notifyview.findViewById<TextInputEditText>(R.id.m_qty).text.toString()
                                        ).addOnSuccessListener {
                                            get_data()
                                            custom_snackbar("Stock Alert For ${medicineList[position].Medicine} Updated Successfully")
                                            monitorStock()
                                            Log.d("D_CHECK", "onItemLongClick: Updated")
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
                    }.show()
                }

                if (medicineList[position].LowStock?.toInt() != -1) {
                    view.findViewById<Button>(R.id.remove_notify).visibility = View.VISIBLE
                }
                view.findViewById<Button>(R.id.remove_notify).setOnClickListener {
                    fs.collection("Medicines").document(auth.currentUser?.uid!!)
                        .collection("MyMedicines")
                        .whereEqualTo("MedicineId", medicineList[position].MedicineId).get()
                        .addOnSuccessListener {
                            for (doc in it) {
                                val docRef = doc.reference

                                docRef.update(
                                    "LowStock",
                                    "-1"
                                ).addOnSuccessListener {
//                                    s_getdata()
//                                    checkUser()
                                    get_data()
                                    custom_snackbar("Stock Alert For ${medicineList[position].Medicine} Updated Successfully")
                                    monitorStock()
                                    Log.d("D_CHECK", "onItemLongClick: Updated")
                                    previewDialog.dismiss()
                                }.addOnFailureListener {
                                    Log.d("D_CHECK", "onItemLongClick: ${it.message}")
                                }
                            }
                            Log.d("D_CHECK", "onItemLongClick: ${it}")
                        }.addOnFailureListener {
                            Log.d("D_CHECK", "onItemLongClick: ${it.message}")
                        }
                }
            }

        })


        binding.rvMedicine.viewTreeObserver.addOnGlobalLayoutListener {
            binding.ProgressBar.visibility = View.GONE

        }
    }

    private fun simulateDataLoading() {
        binding.ProgressBar.postDelayed({
            binding.rvMedicine.visibility = View.VISIBLE

        }, 2000)
    }

    private fun custom_snackbar(message: String) {
        val bar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        bar.setBackgroundTint(resources.getColor(R.color.blue))
        bar.setActionTextColor(resources.getColor(R.color.white))
        bar.setTextColor(Color.WHITE)
        bar.setAction("OK") {
            bar.dismiss()

        }
        bar.show()
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

    private fun monitorStock() {
        val stockRef =
            fs.collection("Medicines").document(auth.currentUser?.uid!!).collection("MyMedicines")

        stockRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w("D_CHECK", "Listen failed", e)
                return@addSnapshotListener
            }
            if (snapshot != null && !snapshot.isEmpty) {
                for (doc in snapshot.documents) {
                    val stock = doc.toObject(medicine::class.java)
                    if (stock != null && stock.Stock!!.toInt() < stock.LowStock!!.toInt()) {
                        // If stock is below 5, call showNotification function
                        showNotification(
                            "Low Stock Alert",
                            "Stock for ${stock.Medicine} is below ${stock.LowStock}"
                        )
                    }
                }
            } else {
                Log.d("Hello", "No stock data")
            }
        }
    }

    // Notification Function
    private fun showNotification(title: String, message: String) {
        val notificationManager =
            requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        // Create an intent for the activity you want to open when the notification is clicked
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        // You can add extras to the intent if you need to pass data to the activity

        val pendingIntent = PendingIntent.getActivity(
            requireContext(),
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "my_channel_id"
            val channel =
                NotificationChannel(
                    channelId,
                    "My Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            notificationManager.createNotificationChannel(channel)

            val notification = NotificationCompat.Builder(requireContext(), channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.baseline_medical_services_24)
                .setContentIntent(pendingIntent) // Attach the pending intent to the notification
                .build()

            notificationManager.notify(notificationId, notification)
        } else {
            val notification = NotificationCompat.Builder(requireContext())
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.baseline_medical_services_24)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent) // Attach the pending intent to the notification
                .build()

            notificationManager.notify(notificationId, notification)
        }
    }

    fun search() {
        if (binding.search.text.toString().isNotEmpty()) {
            val filterList = medicineList.filter {
                it.Medicine?.startsWith(
                    binding.search.text.toString(),
                    ignoreCase = true
                )!!
            }
            load_data(java.util.ArrayList(filterList))
        } else {
            load_data(medicineList)
        }

    }


}
