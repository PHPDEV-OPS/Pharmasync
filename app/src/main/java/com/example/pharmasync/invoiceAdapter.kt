package com.example.pharmasync

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class invoiceAdapter(private val context: Context, private val items: ArrayList<invoice>):  RecyclerView.Adapter<invoiceAdapter.inv_ViewHolder>()   {
    lateinit var fs: FirebaseFirestore
    lateinit var auth: FirebaseAuth
    lateinit var sr : StorageReference
    lateinit var mylistener: invoiceAdapter.onitemclick
    interface onitemclick {
        fun itemClickListener(position: Int)
    }

    fun onItem(listener: onitemclick) {
        mylistener = listener
    }
    class inv_ViewHolder(view: View,  mylistener: invoiceAdapter.onitemclick
    ) : RecyclerView.ViewHolder(view) {
        val invoicename: TextView = view.findViewById(R.id.m_name)
        val time: TextView = view.findViewById(R.id.time)
        val image: de.hdodenhof.circleimageview.CircleImageView = view.findViewById(R.id.m_image)
        val sr: TextView = view.findViewById(R.id.sr)

        init {
            view.setOnClickListener {
                mylistener.itemClickListener(adapterPosition)
            }
       
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): inv_ViewHolder {
        fs = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val view = LayoutInflater.from(parent.context).inflate(R.layout.invoice_rv, parent, false)
        return invoiceAdapter.inv_ViewHolder(view, mylistener)
    }

    override fun getItemCount(): Int {
       return items.size
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: inv_ViewHolder, position: Int) {
        val item = items[position]
        val p_no = position + 1
        val date = items[position].time
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

        holder.invoicename.text = "Invoice: "+item.InvoiceName
        holder.time.text = "Time: "+time
        holder.sr.text = p_no.toString() + "."
        sr = FirebaseStorage.getInstance()
            .getReference("Invoices/" + auth.currentUser?.uid)
            .child(item.ImageUri.toString())

        sr.downloadUrl.addOnSuccessListener {
            Glide.with(context)
                .load(it)
                .into(holder.image)
        }
    }
}