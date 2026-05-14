package com.muttasilat.tv247iptvplayer.presenter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.muttasilat.tv247iptvplayer.model.Channel
import com.muttasilat.tv247iptvplayer.R


class ChannelPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        
        // Make view focusable
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val channel = item as Channel
        val textView = viewHolder.view as TextView
        
        // Set channel name
        textView.text = channel.name
        
        // Make sure the view is focusable
        textView.isFocusable = true
        textView.isFocusableInTouchMode = true
        
        // Set minimum height for better touch targets
        textView.minHeight = textView.resources.getDimensionPixelSize(R.dimen.channel_item_height)
        
        // Add focus highlight
        textView.setBackgroundResource(R.drawable.channel_item_background)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // Nothing to clean up
    }
}