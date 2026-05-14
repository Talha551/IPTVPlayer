package com.muttasilat.tv247iptvplayer.util

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaylistDownloader {
    companion object {
        private const val TAG = "PlaylistDownloader"
        private const val PLAYLIST_URL = "https://pace-tel.com/playlist2.m3u"
        
        suspend fun downloadPlaylist(context: Context): String {
            return try {
                // Try downloading from URL first
                val response = withContext(Dispatchers.IO) {
                    URL(PLAYLIST_URL).readText()
                }
                
                // Save the downloaded playlist to local storage
                savePlaylistToLocal(context, response)
                
                response
            } catch (e: Exception) {
                // If download fails, try reading from local storage
                readPlaylistFromLocal(context) ?: ""
            }
        }

        private fun savePlaylistToLocal(context: Context, playlist: String) {
            try {
                context.openFileOutput("channels.m3u8", Context.MODE_PRIVATE).use {
                    it.write(playlist.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun readPlaylistFromLocal(context: Context): String? {
            return try {
                context.openFileInput("channels.m3u8").bufferedReader().use {
                    it.readText()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
} 