package com.smarterz.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class MediaAdapter(
    private var items: List<MediaItem>,
    private val showRemoveButton: Boolean = false,
    private val onItemClick: (MediaItem) -> Unit,
    private val onRemoveClick: ((MediaItem) -> Unit)? = null
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    inner class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.cardPoster)
        val title: TextView = view.findViewById(R.id.cardTitle)
        val detail: TextView = view.findViewById(R.id.cardDetail)
        val removeBtn: ImageButton = view.findViewById(R.id.removeBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_card, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val item = items[position]

        holder.title.text = item.title
        holder.detail.text = item.detail

        Glide.with(holder.poster.context)
            .load(item.poster)
            .placeholder(R.drawable.placeholder_poster)
            .error(R.drawable.placeholder_poster)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.poster)

        holder.removeBtn.visibility = if (showRemoveButton) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.removeBtn.setOnClickListener { onRemoveClick?.invoke(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<MediaItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
