package com.muttasilat.tv247iptvplayer.model

enum class ChannelCategory {
    FAVORITES,
    RECENT,
    ALL
}

data class ChannelGroup(
    val category: ChannelCategory,
    val channels: List<Channel>
) 