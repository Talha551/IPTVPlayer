package com.muttasilat.tv247iptvplayer

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.muttasilat.tv247iptvplayer.model.Channel
import com.muttasilat.tv247iptvplayer.util.M3UParser
import android.graphics.Color
import android.os.Build
import com.muttasilat.tv247iptvplayer.manager.ChannelManager
import com.muttasilat.tv247iptvplayer.util.PlaylistDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleOwner
import android.view.WindowInsets
import android.view.WindowInsetsController

/**
 * Loads [MainFragment].
 */
class MainActivity : FragmentActivity(),
    ChannelListFragment.OnChannelSelectedListener,
    PlaybackVideoFragment.ChannelNavigationListener,
    LifecycleOwner {
    private var channels: List<Channel> = emptyList()
    private var currentChannelIndex = 0
    private lateinit var channelListFragment: ChannelListFragment
    private var playbackFragment: PlaybackVideoFragment? = null
    private var isFullscreen = false
    private lateinit var channelManager: ChannelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Force fullscreen mode - moved after setContentView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        channelManager = ChannelManager(this)

        // Initialize fragments
        channelListFragment = ChannelListFragment().apply {
            setOnChannelSelectedListener(this@MainActivity)
        }
        
        // Add channel list fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.channel_list_container, channelListFragment)
            .commitNow()  // Use commitNow to ensure immediate execution

        // Load channels
        loadChannels()
    }

    private fun loadChannels() {
        try {
            Log.d(TAG, "Starting loadChannels")
            lifecycleScope.launch {
                try {
                    // Show loading indicator
                    showLoading(true)
                    
                    // Download playlist using the new API
                    Log.d(TAG, "Attempting to download playlist")
                    val content = PlaylistDownloader.Companion.downloadPlaylist(this@MainActivity)
                    Log.d(TAG, "Successfully downloaded playlist, content size: ${content.length}")
                    
                    // Parse channels
                    Log.d(TAG, "Starting to parse M3U content")
                    channels = M3UParser.parseM3U(content)
                    Log.i(TAG, "Successfully parsed ${channels.size} channels")
                    
                    // Log first few channels for debugging
                    channels.take(3).forEach { channel ->
                        Log.d(TAG, "Channel: ${channel.name}, URL: ${channel.streamUrl}")
                    }
                    
                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "Updating UI with parsed channels")
                        // Hide loading indicator
                        showLoading(false)
                        
                        // Setup fragments after channels are loaded
                        setupFragments()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading channels", e)
                    Log.e(TAG, "Full stack trace: ${e.stackTraceToString()}")
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(this@MainActivity, 
                            "Error loading channels: ${e.message}", 
                            Toast.LENGTH_LONG).show()
                    }
                    channels = emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadChannels", e)
            Log.e(TAG, "Full stack trace: ${e.stackTraceToString()}")
            Toast.makeText(this, "Error loading channels: ${e.message}", Toast.LENGTH_LONG).show()
            channels = emptyList()
        }
    }

    private fun setupFragments() {
        // Wait for fragment transaction to complete
        supportFragmentManager.executePendingTransactions()

        // Update UI after fragments are ready
        if (channels.isNotEmpty()) {
            // Set channels in list
            channelListFragment.setChannels(channels)
            // Play first channel
            playChannel(channels[currentChannelIndex])
        } else {
            Toast.makeText(this, "No channels available", Toast.LENGTH_LONG).show()
        }
    }

    private fun showLoading(show: Boolean) {
        findViewById<View>(R.id.loading_indicator)?.visibility =
            if (show) View.VISIBLE else View.GONE
    }

    private fun playChannel(channel: Channel) {
        // Add to recent channels
        channelManager.addToRecent(channel)
        
        // Hide channel list
        findViewById<View>(R.id.channel_list_container).visibility = View.GONE
        
        // Update current channel index
        currentChannelIndex = channels.indexOf(channel)
        
        // Create and switch to new playback fragment
        playbackFragment = PlaybackVideoFragment().apply {
            arguments = Bundle().apply {
                putString("url", channel.streamUrl)
                putString("title", channel.name)
            }
            setNavigationListener(this@MainActivity)
        }
        
        // Replace fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.video_container, playbackFragment!!)
            .commit()
    }

    override fun onChannelSelected(channel: Channel) {
        // Update current channel index
        currentChannelIndex = channels.indexOf(channel)
        
        // Add to recent channels
        channelManager.addToRecent(channel)
        
        // Hide channel list
        findViewById<View>(R.id.channel_list_container).visibility = View.GONE
        
        // Start playback
        playChannel(channel)
        
        // Update channel list selection
        channelListFragment.updateSelectedPosition(currentChannelIndex)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Skip handling keys if we're in playback mode - let dispatchKeyEvent handle it
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)
        if (currentFragment is PlaybackVideoFragment) {
            return super.onKeyDown(keyCode, event)
        }
        
        // Original key handling for non-playback fragments
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                val channelListContainer = findViewById<View>(R.id.channel_list_container)
                if (channelListContainer.visibility == View.VISIBLE) {
                    // If channel list is visible, hide it
                    channelListContainer.visibility = View.GONE
                    true
                } else {
                    // Show channel list and give it focus
                    channelListContainer.visibility = View.VISIBLE
                    channelListFragment.apply {
                        view?.postDelayed({
                            view?.requestFocus()
                            // Calculate actual position considering headers and recent items
                            val recentChannels = channelManager.getRecentChannels(channels)
                            val favoriteChannels = channelManager.getFavoriteChannels(channels)
                            // Add 2 for "Recent" and "All Channels" headers
                            val actualPosition = currentChannelIndex + 
                                (if (recentChannels.isNotEmpty()) recentChannels.size + 1 else 0) +
                                (if (favoriteChannels.isNotEmpty()) favoriteChannels.size + 1 else 0) +
                                1 // Add 1 for "All Channels" header
                            
                            Log.d(TAG, "Calculated list position: $actualPosition (channel index: $currentChannelIndex)")
                            updateSelectedPosition(actualPosition)
                        }, 100)
                    }
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (findViewById<View>(R.id.channel_list_container).visibility != View.VISIBLE) {
                    // Only change channel if list is not visible
                    changeChannel(false)
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (findViewById<View>(R.id.channel_list_container).visibility != View.VISIBLE) {
                    // Only change channel if list is not visible
                    changeChannel(true)
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (findViewById<View>(R.id.channel_list_container).visibility == View.VISIBLE) {
                    // Move focus to video when pressing left from channel list
                    findViewById<View>(R.id.channel_list_container).visibility = View.GONE
                    playbackFragment?.view?.requestFocus()
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }

            KeyEvent.KEYCODE_DPAD_CENTER, 
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                if (findViewById<View>(R.id.channel_list_container).visibility != View.VISIBLE) {
                    // Only toggle list if not already visible
                    onToggleChannelList()
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun showChannelList() {
        Log.d(TAG, "Showing channel list at index: $currentChannelIndex")
        val channelListContainer = findViewById<View>(R.id.channel_list_container)
        
        // First update the position
        channelListFragment.updateSelectedPosition(currentChannelIndex)
        
        // Then make the container visible
        channelListContainer.apply {
            visibility = View.VISIBLE
            setBackgroundColor(Color.parseColor("#CC000000"))
            bringToFront()
        }
        
        // Finally refresh the list
        channelListFragment.refreshList()
    }

    private fun hideChannelList() {
        Log.d(TAG, "Hiding channel list")
        val channelListContainer = findViewById<View>(R.id.channel_list_container)
        channelListContainer.visibility = View.GONE
        playbackFragment?.view?.requestFocus()
    }

    override fun onToggleChannelList() {
        val channelListContainer = findViewById<View>(R.id.channel_list_container)
        if (channelListContainer.visibility == View.VISIBLE) {
            channelListContainer.visibility = View.GONE
        } else {
            channelListContainer.visibility = View.VISIBLE
            channelListFragment.view?.requestFocus()
        }
    }

    private fun changeChannel(up: Boolean) {
        if (channels.isEmpty()) return

        val oldIndex = currentChannelIndex
        currentChannelIndex = when {
            up -> (currentChannelIndex + 1) % channels.size
            else -> if (currentChannelIndex > 0) currentChannelIndex - 1 else channels.size - 1
        }
        
        Log.d(TAG, "Channel change: $oldIndex -> $currentChannelIndex (${if (up) "UP" else "DOWN"})")
        
        // Update channel list selection even in fullscreen
        channelListFragment.updateSelectedPosition(currentChannelIndex)
        
        // Create and switch to new playback fragment
        playChannel(channels[currentChannelIndex])
    }

    override fun onToggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            hideSystemUI()
        } else {
            showSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 and above
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // For Android 10 and below
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 and above
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.let { controller ->
                controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            // For Android 10 and below
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    // Implement ChannelNavigationListener
    override fun onNextChannel() {
        Log.d(TAG, "Next channel requested, current: $currentChannelIndex")
        changeChannel(true)
    }

    override fun onPreviousChannel() {
        Log.d(TAG, "Previous channel requested, current: $currentChannelIndex")
        changeChannel(false)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Check if we're in playback mode
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)
        if (currentFragment is PlaybackVideoFragment) {
            // Let the playback fragment handle ALL key events when it's active
            val handled = currentFragment.handleKeyEvent(event.keyCode, event)
            
            // If the fragment didn't handle it, we'll handle it here but ONLY for non-navigation keys
            if (!handled && event.action == KeyEvent.ACTION_DOWN) {
                // Handle non-navigation keys that the fragment didn't handle
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        // Let the system handle back button
                        return super.dispatchKeyEvent(event)
                    }
                    // Explicitly prevent right key from showing channel list when in playback
                    KeyEvent.KEYCODE_DPAD_RIGHT, 
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Consume these keys to prevent onKeyDown from handling them
                        return true
                    }
                    else -> {
                        // Let the system handle other keys
                        return super.dispatchKeyEvent(event)
                    }
                }
            }
            
            // If the fragment handled it, we're done
            return handled || super.dispatchKeyEvent(event)
        }
        
        // Not in playback mode, let the system handle it
        return super.dispatchKeyEvent(event)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}