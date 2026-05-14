package com.muttasilat.tv247iptvplayer.presenter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.Presenter
import android.graphics.Color

class HeaderPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(32, 24, 24, 8)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        (item as? HeaderItem)?.let { header ->
            (viewHolder.view as TextView).text = header.name
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // Nothing to clean up
    }
} 