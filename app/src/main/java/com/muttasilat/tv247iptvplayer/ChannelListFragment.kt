package com.muttasilat.tv247iptvplayer

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import androidx.leanback.widget.*
import com.muttasilat.tv247iptvplayer.model.Channel
import com.muttasilat.tv247iptvplayer.presenter.ChannelPresenter
import com.muttasilat.tv247iptvplayer.presenter.HeaderPresenter
import android.graphics.Color
import android.app.AlertDialog
import com.muttasilat.tv247iptvplayer.manager.ChannelManager
import android.view.KeyEvent
import android.widget.TextView
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import com.muttasilat.tv247iptvplayer.R

class ChannelListFragment : Fragment() {
    private lateinit var channelManager: ChannelManager
    private var channelSelectedListener: OnChannelSelectedListener? = null
    private var channels: List<Channel> = emptyList()
    private var currentChannelIndex: Int = 0
    private lateinit var mAdapter: ArrayObjectAdapter
    private lateinit var categoryTitle: TextView
    private lateinit var gridView: VerticalGridView
    
    private var lastClickTime = 0L
    private val DOUBLE_CLICK_TIME_DELTA = 300L // milliseconds

    companion object {
        private const val TAG = "ChannelListFragment"
    }

    interface OnChannelSelectedListener {
        fun onChannelSelected(channel: Channel)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_channel_list, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            channelManager = ChannelManager(requireContext())

            // Initialize adapter with presenter selector
            val presenterSelector = ClassPresenterSelector().apply {
                addClassPresenter(HeaderItem::class.java, HeaderPresenter())
                addClassPresenter(Channel::class.java, ChannelPresenter())
            }
            mAdapter = ArrayObjectAdapter(presenterSelector)

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            // Set up views
            categoryTitle = view.findViewById(R.id.category_title)
            gridView = view.findViewById(R.id.channels_grid)
            
            // Hide the category title since we're using headers
            categoryTitle.visibility = View.GONE
            
            // Configure grid
            gridView.apply {
                setItemViewCacheSize(20)
                setNumColumns(1)
                
                // Enable focus and selection
                isFocusable = true
                isFocusableInTouchMode = true
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

                // Set up adapter with ItemBridgeAdapter
                val bridgeAdapter = ItemBridgeAdapter(mAdapter).apply {
                    setAdapterListener(object : ItemBridgeAdapter.AdapterListener() {
                        override fun onCreate(viewHolder: ItemBridgeAdapter.ViewHolder) {
                            viewHolder.itemView.apply {
                                isFocusable = true
                                isFocusableInTouchMode = true
                                
                                // Add click listener
                                setOnClickListener {
                                    val item = viewHolder.item
                                    if (item is Channel) {
                                        channelSelectedListener?.onChannelSelected(item)
                                    }
                                }
                                
                                // Add long click listener
                                setOnLongClickListener {
                                    val item = viewHolder.item
                                    if (item is Channel) {
                                        showChannelOptionsDialog(item)
                                    }
                                    true
                                }
                                
                                // Add focus change listener for visual feedback
                                setOnFocusChangeListener { v, hasFocus ->
                                    v.setBackgroundColor(
                                        if (hasFocus) Color.parseColor("#44FFFFFF")
                                        else Color.TRANSPARENT
                                    )
                                    if (hasFocus && viewHolder.item is Channel) {
                                        currentChannelIndex = gridView.getChildAdapterPosition(v)
                                    }
                                }
                            }
                        }
                    })
                }
                adapter = bridgeAdapter

                // Handle keyboard navigation
                setOnKeyListener { _, keyCode, event ->
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            val focusedChild = findFocus()
                            focusedChild?.performClick()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (selectedPosition > 0) {
                                smoothScrollToPosition(selectedPosition - 1)
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (selectedPosition < mAdapter.size() - 1) {
                                smoothScrollToPosition(selectedPosition + 1)
                                true
                            } else false
                        }
                        else -> false
                    }
                }
            }
            
            // Initial refresh and focus
            refreshList()
            view.postDelayed({
                gridView.requestFocus()
                if (mAdapter.size() > 0) {
                    gridView.selectedPosition = 0
                }
            }, 100)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated", e)
        }
    }

    override fun onResume() {
        super.onResume()
        view?.postDelayed({
            gridView.requestFocus()
            if (currentChannelIndex >= 0) {
                gridView.selectedPosition = currentChannelIndex
            }
        }, 100)
    }

    private fun showChannelOptionsDialog(channel: Channel) {

        try {
            val isFavorite = channelManager.isFavorite(channel)

            AlertDialog.Builder(requireContext())
                .setTitle(channel.name)
                .setItems(
                    arrayOf(if (isFavorite) "Remove from Favorites" else "Add to Favorites")
                ) { _, which ->
                    when (which) {
                        0 -> {
                            if (isFavorite) {
                                channelManager.removeFromFavorites(channel)
                            } else {
                                channelManager.addToFavorites(channel)
                            }
                            refreshList()
                        }
                    }
                }
                .show()

        } catch (e: Exception) {
            Log.e(TAG, "Error showing options dialog", e)
        }

    }

    fun setChannels(newChannels: List<Channel>) {
        try {
            Log.d(TAG, "Setting ${newChannels.size} channels")
            channels = newChannels
            refreshList()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting channels", e)
        }
    }

    fun refreshList() {
        try {
            Log.d(TAG, "Refreshing channel list")
            mAdapter.clear()
            
            // Add all categories
            val favoriteChannels = channelManager.getFavoriteChannels(channels)
            val recentChannels = channelManager.getRecentChannels(channels)
            
            // Add header for Favorites if exists
            if (favoriteChannels.isNotEmpty()) {
                mAdapter.add(HeaderItem("Favorites"))
                mAdapter.addAll(mAdapter.size(), favoriteChannels)
            }
            
            // Add header for Recent if exists
            if (recentChannels.isNotEmpty()) {
                mAdapter.add(HeaderItem("Recent"))
                mAdapter.addAll(mAdapter.size(), recentChannels)
            }
            
            // Always add All Channels
            mAdapter.add(HeaderItem("All Channels"))
            mAdapter.addAll(mAdapter.size(), channels)
            
            Log.d(TAG, "Channel list refreshed with categories")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing list", e)
        }
    }

    private fun getCurrentSelectedChannel(): Channel? {
        return channels.getOrNull(gridView.selectedPosition)
    }

    fun updateSelectedPosition(position: Int) {
        try {
            Log.d(TAG, "Updating selected position to: $position")
            currentChannelIndex = position
            gridView.selectedPosition = position
        } catch (e: Exception) {
            Log.e(TAG, "Error updating position", e)
        }
    }

    fun setOnChannelSelectedListener(listener: OnChannelSelectedListener) {
        channelSelectedListener = listener
    }
} 