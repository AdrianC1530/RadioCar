package com.radiocar.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class AppGridAdapter(private val inflater: LayoutInflater) : BaseAdapter() {

    var items: List<AppEntry> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private class Holder(view: View) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val label: TextView = view.findViewById(R.id.app_label)
    }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_app, parent, false).also {
            it.tag = Holder(it)
        }
        val holder = view.tag as Holder
        val entry = items[position]
        holder.icon.setImageDrawable(entry.icon)
        holder.label.text = entry.label
        return view
    }
}
