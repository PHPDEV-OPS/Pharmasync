package com.example.pharmasync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class exploreAdapter(private val items: ArrayList<MedicalStore>): RecyclerView.Adapter<exploreAdapter.explore_ViewHolder>(){


    lateinit var editlistener: exploreAdapter.EditClick
    interface EditClick {
        fun onEditClick(position: Int)
    }

    fun onEdit(listener: EditClick) {
        editlistener = listener
    }
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): exploreAdapter.explore_ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.explore_rv, parent, false)
        return explore_ViewHolder(view,editlistener)
    }

    override fun onBindViewHolder(holder: exploreAdapter.explore_ViewHolder, position: Int) {
        val item = items[position]
        val p_no = position + 1
        holder.shopname.text = "Name: "+item.ShopName
        holder.address.text = "Address: "+ if (item.Address.toString().length>35){item.Address.toString().substring(0,35)+"..."}else{item.Address.toString()}
        holder.sr.text = p_no.toString() + "."
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class explore_ViewHolder(view: View, editlistener: EditClick) : RecyclerView.ViewHolder(view) {

        val shopname: TextView = view.findViewById(R.id.s_name)
        val address: TextView = view.findViewById(R.id.s_address)
        val sr: TextView = view.findViewById(R.id.sr)
        val locate: ImageButton = view.findViewById(R.id.locate_btn)
        val img : ImageView = view.findViewById(R.id.s_image)

        init {
            locate.setOnClickListener {
                editlistener.onEditClick(adapterPosition)
            }
        }
    }
}
