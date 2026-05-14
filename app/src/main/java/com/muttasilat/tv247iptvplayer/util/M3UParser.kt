package com.muttasilat.tv247iptvplayer.util

import android.util.Log
import com.muttasilat.tv247iptvplayer.model.Channel

object M3UParser {
    private const val TAG = "M3UParser"

    fun parseM3U(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var currentId = 0L
        val lines = content.split("\n")
        
        var currentName = ""
        var currentGroup = ""
        var currentUrl = ""
        
        for (line in lines) {
            when {
                line.startsWith("#EXTINF:") -> {
                    // Parse channel name and group
                    currentName = line.substringAfterLast(",").trim()
                    currentGroup = try {
                        line.substringAfter("group-title=\"")
                            .substringBefore("\"")
                    } catch (e: Exception) {
                        "Default"
                    }
                }
                line.trim().startsWith("http") -> {
                    // Found URL, create channel
                    currentUrl = line.trim()
                    if (currentName.isNotEmpty() && currentUrl.isNotEmpty()) {
                        channels.add(
                            Channel(
                                id = currentId++,
                                name = currentName,
                                streamUrl = currentUrl,
                                logoUrl = null,
                                groupTitle = currentGroup,
                                group = currentGroup
                            )
                        )
                        currentName = ""
                        currentGroup = ""
                        currentUrl = ""
                    }
                }
            }
        }
        
        Log.d(TAG, "Parsed ${channels.size} channels")
        return channels
    }
} 