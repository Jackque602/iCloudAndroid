package com.icloudandroid.photos

/** A photo read off the device, ready to hand to [UploadApi]. */
data class PendingUpload(
    val bytes: ByteArray,
    val filename: String,
    val uti: String,
    val width: Int,
    val height: Int,
)

/** Progress of an experimental native upload batch, surfaced by GalleryViewModel. */
sealed interface UploadState {
    data object Idle : UploadState
    data class Uploading(val done: Int, val total: Int) : UploadState
    data object Success : UploadState
    data class Failed(val message: String) : UploadState
}
