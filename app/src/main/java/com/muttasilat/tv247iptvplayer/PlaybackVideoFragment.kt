package com.muttasilat.tv247iptvplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.appcompat.app.AlertDialog
import java.util.*
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.Gravity
import android.view.ViewGroup
import androidx.media3.common.AudioAttributes

/** Handles video playback with media controls. */
@UnstableApi
class PlaybackVideoFragment : Fragment() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var channelNameView: TextView
    private var channelNameHandler = Handler(Looper.getMainLooper())
    private var controlsContainer: View? = null
    
    private val hideChannelNameRunnable = Runnable {
        hideChannelName()
    }

    interface ChannelNavigationListener {
        fun onNextChannel()
        fun onPreviousChannel()
        fun onToggleChannelList()
        fun onToggleFullscreen()
    }

    private var navigationListener: ChannelNavigationListener? = null
    private var lastTapTime: Long = 0
    private var aspectRatioButton: ImageButton? = null
    private var audioTrackButton: ImageButton? = null
    private var currentAudioTrackIndex = 0
    private var settingsButton: ImageButton? = null

    // Add a runnable for auto-hiding controls
    private val hideControlsRunnable = Runnable {
        hideControls()
    }

    // Add this property to track if controls are visible
    private var controlsVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_playback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize player view
        playerView = view.findViewById(R.id.player_view)
        
        // Initialize controls
        controlsContainer = view.findViewById(R.id.controls_container)
        channelNameView = view.findViewById(R.id.channel_name)
        
        // Initialize buttons
        val exitButton = view.findViewById<ImageButton>(R.id.btn_exit)
        val previousButton = view.findViewById<ImageButton>(R.id.btn_previous)
        val nextButton = view.findViewById<ImageButton>(R.id.btn_next)
        aspectRatioButton = view.findViewById<ImageButton>(R.id.btn_aspect_ratio)
        settingsButton = view.findViewById<ImageButton>(R.id.btn_audio_track)
        val channelsButton = view.findViewById<ImageButton>(R.id.btn_channels)
        
        // Set up button click listeners
        exitButton?.setOnClickListener {
            activity?.finishAffinity() // Close the app completely
        }
        
        previousButton?.setOnClickListener {
            navigationListener?.onPreviousChannel()
        }
        
        nextButton?.setOnClickListener {
            navigationListener?.onNextChannel()
        }
        
        aspectRatioButton?.setOnClickListener {
            cycleAspectRatio()
        }
        
        settingsButton?.setOnClickListener {
            showSettingsDialog()
        }
        
        channelsButton?.setOnClickListener {
            navigationListener?.onToggleChannelList()
        }
        
        // Add focus listeners to buttons
        setupFocusAnimations(exitButton)
        setupFocusAnimations(previousButton)
        setupFocusAnimations(settingsButton)
        setupFocusAnimations(aspectRatioButton)
        setupFocusAnimations(channelsButton)
        setupFocusAnimations(nextButton)
        
        // Set up next/previous focus for buttons to ensure proper navigation
        exitButton?.nextFocusRightId = previousButton?.id ?: 0
        previousButton?.nextFocusLeftId = exitButton?.id ?: 0
        previousButton?.nextFocusRightId = settingsButton?.id ?: 0
        settingsButton?.nextFocusLeftId = previousButton?.id ?: 0
        settingsButton?.nextFocusRightId = aspectRatioButton?.id ?: 0
        aspectRatioButton?.nextFocusLeftId = settingsButton?.id ?: 0
        aspectRatioButton?.nextFocusRightId = channelsButton?.id ?: 0
        channelsButton?.nextFocusLeftId = aspectRatioButton?.id ?: 0
        channelsButton?.nextFocusRightId = nextButton?.id ?: 0
        nextButton?.nextFocusLeftId = channelsButton?.id ?: 0
        
        // Set initial focus to the next button (right-most)
        nextButton?.requestFocus()
        
        // Get URL and title from arguments
        arguments?.getString("url")?.let { url ->
            arguments?.getString("title")?.let { title ->
                Log.d(TAG, "Starting playback of URL: $url")
                setupPlayer(url)
                showChannelName(title)
            }
        }

        // Set click listener on the root view for showing/hiding controls
        playerView.setOnClickListener {
            toggleControlsVisibility()
        }

        // Handle key events for TV remote
        playerView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (controlsVisible) {
                    // When controls are visible, handle navigation differently
                    when (keyCode) {
                        KeyEvent.KEYCODE_BACK -> {
                            hideControls()
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            // Let the focused button handle the click
                            return@setOnKeyListener false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, 
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            // Reset auto-hide timer when navigating between controls
                            channelNameHandler.removeCallbacks(hideControlsRunnable)
                            channelNameHandler.postDelayed(hideControlsRunnable, 5000)
                            
                            // IMPORTANT: Return true to prevent the activity from showing channel list
                            return@setOnKeyListener true
                        }
                        else -> return@setOnKeyListener false
                    }
                } else {
                    // When controls are not visible, handle navigation for channel switching
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            // Show controls on OK button press
                            showControls()
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            navigationListener?.onPreviousChannel()
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            // Fix for right arrow key - change channel instead of showing list
                            navigationListener?.onNextChannel()
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            // Let the back button be handled by the activity
                            // (which will show the channel list)
                            return@setOnKeyListener false
                        }
                        else -> return@setOnKeyListener false
                    }
                }
            }
            false
        }
        
        // Make sure playerView is focusable for TV navigation
        playerView.isFocusable = true
        playerView.isFocusableInTouchMode = true
        playerView.requestFocus()

        // Make sure controls are initially hidden
        controlsContainer?.visibility = View.GONE

        // Set initial aspect ratio mode
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    private fun setupPlayer(url: String) {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC) // Use MUSIC instead of MOVIE for better compatibility
                .build()

            // Force software decoding for better compatibility
            val renderersFactory = DefaultRenderersFactory(requireContext())
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true) // Enable decoder fallback
                .setEnableAudioTrackPlaybackParams(false) // Disable audio playback params for compatibility
                .setAllowedVideoJoiningTimeMs(5000) // Increase joining time

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    32 * 1024,  // Min buffer
                    64 * 1024,  // Max buffer
                    1024,       // Buffer for playback
                    1024        // Buffer for rebuffer
                )
                .build()

            // Create media source without error handling policy
            val dataSourceFactory = DefaultDataSource.Factory(requireContext())
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            // Add track selector configuration with forced software decoding
            val trackSelector = DefaultTrackSelector(requireContext()).apply {
                parameters = DefaultTrackSelector.ParametersBuilder(requireContext())
                    .setPreferredAudioLanguage(null) // Allow all languages
                    .setForceHighestSupportedBitrate(false) // Don't force highest bitrate
                    .setExceedRendererCapabilitiesIfNecessary(true) // Try to play even if capabilities are exceeded
                    .setTunnelingEnabled(false) // Disable tunneling for better compatibility
                    .build()
            }

            // Create player with extended configuration
            player = ExoPlayer.Builder(requireContext())
                .setTrackSelector(trackSelector)
                .setRenderersFactory(renderersFactory)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()

            // Configure player
            player?.apply {
                setMediaItem(MediaItem.fromUri(url))
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING // Default to stretch
                prepare()
            }

            // Configure player view with stretch aspect ratio
            playerView.apply {
                player = this@PlaybackVideoFragment.player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL // Set to stretch by default
            }

            // Add player listener for error handling
            player?.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player error: ${error.message}", error)
                    
                    // Always try alternative audio config on error
                    retryPlaybackWithAlternativeAudioConfig()
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up player", e)
            Toast.makeText(context, "Error playing stream", Toast.LENGTH_SHORT).show()
        }
    }

    private fun retryPlaybackWithAlternativeAudioConfig() {
        try {
            // Store current player reference
            val oldPlayer = player

            // Create alternative audio attributes
            val alternativeAudioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC) // Try different content type
                .build()

            // Create new player with alternative audio config
            val newPlayer = ExoPlayer.Builder(requireContext())
                .setAudioAttributes(alternativeAudioAttributes, true)
                .build()

            // Release old player after creating new one
            oldPlayer?.release()

            // Assign new player
            player = newPlayer

            // Restart playback with new configuration
            setupPlayer(arguments?.getString("url") ?: return)
            
            Toast.makeText(context, "Trying alternative audio configuration", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in alternative audio config", e)
        }
    }

    private fun retryPlaybackWithSoftwareDecoding() {
        try {
            // Store the current player reference
            val oldPlayer = player
            
            // Create new player with software decoding
            val renderersFactory = DefaultRenderersFactory(requireContext())
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)
                // Force software rendering by setting the right parameters
            
            // Create track selector with software decoding parameters
            val trackSelector = DefaultTrackSelector(requireContext()).apply {
                parameters = DefaultTrackSelector.ParametersBuilder(requireContext())
                    .setPreferredAudioLanguage(null)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setAllowAudioMixedDecoderSupportAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setTunnelingEnabled(false)
                    .build()
            }

            // Create new player instance
            val newPlayer = ExoPlayer.Builder(requireContext())
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .build()

            // Release old player after creating new one
            oldPlayer?.release()
            
            // Assign new player
            player = newPlayer
            
            // Restart playback
            setupPlayer(arguments?.getString("url") ?: return)
            
            Toast.makeText(context, "Trying software decoding", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in software decoding fallback", e)
        }
    }

    private fun showChannelName(name: String) {
        channelNameView.text = name
        channelNameView.visibility = View.VISIBLE
        channelNameHandler.removeCallbacks(hideChannelNameRunnable)
        channelNameHandler.postDelayed(hideChannelNameRunnable, 3000)
    }

    private fun hideChannelName() {
        channelNameView.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                channelNameView.visibility = View.GONE
            }
    }

    private fun toggleControlsVisibility() {
        controlsContainer?.let { container ->
            if (container.visibility == View.VISIBLE) {
                hideControls()
            } else {
                showControls()
            }
        }
    }

    private fun showControls() {
        controlsContainer?.apply {
            visibility = View.VISIBLE
            controlsVisible = true
            
            // Make sure the first control button gets focus for TV navigation
            aspectRatioButton?.requestFocus()
            
            // Auto-hide controls after a delay
            channelNameHandler.removeCallbacks(hideControlsRunnable)
            channelNameHandler.postDelayed(hideControlsRunnable, 5000)
        }
    }

    private fun hideControls() {
        controlsContainer?.apply {
            visibility = View.GONE
        }
        controlsVisible = false
        channelNameHandler.removeCallbacks(hideControlsRunnable)
        
        // Return focus to player view
        playerView?.requestFocus()
    }

    fun setNavigationListener(listener: ChannelNavigationListener) {
        navigationListener = listener
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private fun cycleAspectRatio() {
        playerView.resizeMode = when (playerView.resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        showAspectRatioToast()
    }

    private fun showAspectRatioToast() {
        val message = when (playerView.resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Original (Fit)"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
            else -> "Original (Fit)"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun showSettingsDialog() {
        try {
            // Create a simple dialog
            val dialog = Dialog(requireContext())
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_player_settings)
            
            // Set dialog width and position
            dialog.window?.let { window ->
                val params = window.attributes
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                params.gravity = Gravity.CENTER
                window.attributes = params
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
            
            // Set up buttons
            dialog.findViewById<TextView>(R.id.btn_audio_tracks).setOnClickListener {
                dialog.dismiss()
                showAudioTrackSelector()
            }
            
            dialog.findViewById<TextView>(R.id.btn_software_decoding).setOnClickListener {
                dialog.dismiss()
                Toast.makeText(context, "Switching to software decoding...", Toast.LENGTH_SHORT).show()
                retryPlaybackWithSoftwareDecoding()
            }
            
            dialog.findViewById<TextView>(R.id.btn_hardware_decoding).setOnClickListener {
                dialog.dismiss()
                Toast.makeText(context, "Switching to hardware decoding...", Toast.LENGTH_SHORT).show()
                retryPlaybackWithHardwareDecoding()
            }
            
            dialog.findViewById<TextView>(R.id.btn_alternative_audio).setOnClickListener {
                dialog.dismiss()
                Toast.makeText(context, "Trying alternative audio config...", Toast.LENGTH_SHORT).show()
                retryPlaybackWithAlternativeAudioConfig()
            }
            
            dialog.findViewById<TextView>(R.id.btn_reset_player).setOnClickListener {
                dialog.dismiss()
                Toast.makeText(context, "Resetting player...", Toast.LENGTH_SHORT).show()
                resetPlayer()
            }
            
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing settings dialog", e)
            Toast.makeText(context, "Error showing settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun retryPlaybackWithHardwareDecoding() {
        try {
            // Store the current player reference
            val oldPlayer = player
            
            // Create new player with hardware decoding
            val renderersFactory = DefaultRenderersFactory(requireContext())
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            
            // Create track selector with hardware decoding parameters
            val trackSelector = DefaultTrackSelector(requireContext()).apply {
                parameters = DefaultTrackSelector.ParametersBuilder(requireContext())
                    .setTunnelingEnabled(true) // Enable tunneling for hardware acceleration
                    .build()
            }

            // Create new player instance
            val newPlayer = ExoPlayer.Builder(requireContext())
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .build()

            // Release old player after creating new one
            oldPlayer?.release()
            
            // Assign new player
            player = newPlayer
            
            // Restart playback
            setupPlayer(arguments?.getString("url") ?: return)
        } catch (e: Exception) {
            Log.e(TAG, "Error in hardware decoding setup", e)
        }
    }

    private fun resetPlayer() {
        try {
            // Release current player
            player?.release()
            player = null
            
            // Restart playback with default settings
            setupPlayer(arguments?.getString("url") ?: return)
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting player", e)
        }
    }

    private fun showAudioTrackSelector() {
        try {
            player?.let { exoPlayer ->
                val tracks = exoPlayer.currentTracks
                val audioTracks = mutableListOf<Pair<String, Tracks.Group>>()
                
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (trackIndex in 0 until group.length) {
                            if (group.isTrackSupported(trackIndex)) {
                                val format = group.getTrackFormat(trackIndex)
                                val label = buildAudioTrackLabel(format)
                                if (!label.isNullOrEmpty()) {
                                    audioTracks.add(label to group)
                                }
                            }
                        }
                    }
                }

                if (audioTracks.isEmpty()) {
                    Toast.makeText(context, "No audio tracks available", Toast.LENGTH_SHORT).show()
                    return
                }

                // Create string array of track names
                val trackNames = audioTracks.map { it.first }.toTypedArray()

                // Create a simple dialog without custom layouts
                val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Select Audio Track")
                    .setSingleChoiceItems(trackNames, currentAudioTrackIndex) { dialog, which ->
                        try {
                            currentAudioTrackIndex = which
                            val group = audioTracks[which].second
                            
                            // Update track selection
                            val trackSelectionParameters = player?.trackSelectionParameters
                                ?.buildUpon()
                                ?.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                ?.addOverride(
                                    TrackSelectionOverride(
                                        group.mediaTrackGroup,
                                        listOf(which)
                                    )
                                )
                                ?.build()
                            
                            trackSelectionParameters?.let { params ->
                                player?.trackSelectionParameters = params
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error selecting audio track", e)
                            Toast.makeText(context, "Error selecting audio track", Toast.LENGTH_SHORT).show()
                        }
                        
                        dialog.dismiss()
                    }
                    .create()
                
                // Set dialog properties for better visibility on TV
                dialog.window?.attributes?.apply {
                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    gravity = Gravity.CENTER
                }
                
                dialog.show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing audio track selector", e)
            Toast.makeText(context, "Error showing audio options", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildAudioTrackLabel(format: Format): String {
        val parts = mutableListOf<String>()
        
        // Add codec type based on mime type
        val codecType = when (format.sampleMimeType) {
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_AC3 -> "AC3"
            MimeTypes.AUDIO_AC4 -> "AC4"
            MimeTypes.AUDIO_DTS -> "DTS"
            MimeTypes.AUDIO_E_AC3 -> "E-AC3"  // Corrected from AUDIO_EAC3
            MimeTypes.AUDIO_MPEG -> "MP3"     // Corrected from AUDIO_MP3
            else -> format.sampleMimeType?.substringAfterLast('/') ?: "Unknown"
        }
        parts.add(codecType)
        
        // Add language if available
        format.language?.let { lang ->
            if (lang.isNotEmpty() && lang != "und") {
                try {
                    parts.add(Locale(lang).displayLanguage)
                } catch (e: Exception) {
                    parts.add(lang)
                }
            }
        }
        
        // Add bitrate if available
        if (format.bitrate > 0) {
            parts.add("${format.bitrate / 1000} kbps")
        }
        
        // Add channel count if available
        if (format.channelCount > 0) {
            parts.add("${format.channelCount} ch")
        }
        
        return parts.joinToString(" - ")
    }

    private fun setupFocusAnimations(button: ImageButton?) {
        button?.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(200)
                    .start()
                
                // Reset auto-hide timer when focus changes
                channelNameHandler.removeCallbacks(hideControlsRunnable)
                channelNameHandler.postDelayed(hideControlsRunnable, 5000)
            } else {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .start()
            }
        }
    }

    // Update the handleKeyEvent method to properly handle right arrow when controls are visible
    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (controlsVisible) {
                // When controls are visible, handle navigation differently
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        hideControls()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        // Let the focused button handle the click
                        return false
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, 
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Reset auto-hide timer when navigating between controls
                        channelNameHandler.removeCallbacks(hideControlsRunnable)
                        channelNameHandler.postDelayed(hideControlsRunnable, 5000)
                        
                        // IMPORTANT: Return true to prevent the activity from showing channel list
                        return true
                    }
                    else -> return false
                }
            } else {
                // When controls are not visible, handle navigation for channel switching
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        navigationListener?.onPreviousChannel()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // This is the critical part - make sure we're handling the right key
                        navigationListener?.onNextChannel()
                        return true  // Return true to indicate we've handled this key
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        // Let the back button be handled by the activity
                        return false
                    }
                    else -> return false
                }
            }
        }
        return false
    }

    companion object {
        private const val TAG = "PlaybackVideoFragment"
    }
}