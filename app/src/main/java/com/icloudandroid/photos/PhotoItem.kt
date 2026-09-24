package com.icloudandroid.photos

/** One photo or video pulled from the iCloud Photos library. */
data class PhotoItem(
    val recordName: String,
    val filename: String,
    val isVideo: Boolean,
    val dateMillis: Long,
    val thumbnailUrl: String?,
    val fullResUrl: String?,
    val width: Int,
    val height: Int,
)

/** One page of results plus whether another page is worth requesting. */
data class PhotoPage(
    val items: List<PhotoItem>,
    val nextOffset: Int,
    val hasMore: Boolean,
)
