package com.example.pharmasync

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class allmedicinesAdapter (private val context: Context, private val items: ArrayList<medicine>):  RecyclerView.Adapter<allmedicinesAdapter.allmed_ViewHolder>()  {
    lateinit var fs: FirebaseFirestore
    lateinit var auth: FirebaseAuth
    lateinit var sr : StorageReference
    class allmed_ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val medicinename: TextView = view.findViewById(R.id.m_name)
        val image: de.hdodenhof.circleimageview.CircleImageView = view.findViewById(R.id.m_image)
        val shopname : TextView = view.findViewById(R.id.category)
        val sr: TextView = view.findViewById(R.id.sr)
        val stock : TextView = view.findViewById(R.id.m_stock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): allmed_ViewHolder {
        fs = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val view = LayoutInflater.from(parent.context).inflate(R.layout.allmed_rv, parent, false)
        return allmed_ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return  items.size
    }

    override fun onBindViewHolder(holder: allmed_ViewHolder, position: Int) {
        val item = items[position]
        val p_no = position + 1
        holder.medicinename.text = "Name: "+item.Medicine
        holder.stock.text = "Price:\n" + item.PricePerUnit.toString()+"/-"
        holder.sr.text = p_no.toString() + "."

        fs.collection("Users").document(item.UserID!!).get().addOnSuccessListener {
            holder.shopname.text ="Shopname: "+ it["Shop-Name"].toString()
        }
        sr = FirebaseStorage.getInstance()
            .getReference("Medicines/" +item.UserID)
            .child(item.ImageUri.toString())

        sr.downloadUrl.addOnSuccessListener {
            Glide.with(context)
                .load(it)
                .into(holder.image)
        }
    }
}