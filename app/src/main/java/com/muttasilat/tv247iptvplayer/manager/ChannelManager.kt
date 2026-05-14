package com.muttasilat.tv247iptvplayer.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.muttasilat.tv247iptvplayer.model.Channel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.ArrayDeque

class
ChannelManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private var favoriteChannels: MutableSet<String> = loadFavorites()
    private var recentChannels: ArrayDeque<String> = loadRecents()
    
    companion object {
        private const val TAG = "ChannelManager"
        private const val PREFS_NAME = "channel_preferences"
        private const val KEY_FAVORITES = "favorite_channels"
        private const val KEY_RECENTS = "recent_channels"
        private const val MAX_RECENT_CHANNELS = 5
    }

    fun addToFavorites(channel: Channel) {
        favoriteChannels.add(channel.streamUrl)
        saveFavorites()
        Log.d(TAG, "Added to favorites: ${channel.name}")
    }

    fun removeFromFavorites(channel: Channel) {
        favoriteChannels.remove(channel.streamUrl)
        saveFavorites()
        Log.d(TAG, "Removed from favorites: ${channel.name}")
    }

    fun isFavorite(channel: Channel): Boolean {
        return favoriteChannels.contains(channel.streamUrl)
    }

    fun addToRecent(channel: Channel) {
        // Remove if already exists to avoid duplicates
        recentChannels.removeIf { it == channel.streamUrl }
        
        // Add to front
        recentChannels.addFirst(channel.streamUrl)
        
        // Maintain max size
        while (recentChannels.size > MAX_RECENT_CHANNELS) {
            recentChannels.removeLast()
        }
        
        saveRecents()
        Log.d(TAG, "Added to recents: ${channel.name}")
    }

    fun getFavoriteChannels(allChannels: List<Channel>): List<Channel> {
        return allChannels.filter { isFavorite(it) }
    }

    fun getRecentChannels(allChannels: List<Channel>): List<Channel> {
        return recentChannels.mapNotNull { url ->
            allChannels.find { it.streamUrl == url }
        }
    }

    private fun loadFavorites(): MutableSet<String> {
        val favoritesJson = prefs.getString(KEY_FAVORITES, null)
        return if (favoritesJson != null) {
            try {
                gson.fromJson(favoritesJson, object : TypeToken<MutableSet<String>>() {}.type)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading favorites", e)
                mutableSetOf()
            }
        } else {
            mutableSetOf()
        }
    }

    private fun saveFavorites() {
        prefs.edit().putString(KEY_FAVORITES, gson.toJson(favoriteChannels)).apply()
    }

    private fun loadRecents(): ArrayDeque<String> {
        val recentsJson = prefs.getString(KEY_RECENTS, null)
        return if (recentsJson != null) {
            try {
                gson.fromJson(recentsJson, object : TypeToken<ArrayDeque<String>>() {}.type)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recents", e)
                ArrayDeque()
            }
        } else {
            ArrayDeque()
        }
    }

    private fun saveRecents() {
        prefs.edit().putString(KEY_RECENTS, gson.toJson(recentChannels)).apply()
    }
} 