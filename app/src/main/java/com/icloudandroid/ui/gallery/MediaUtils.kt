package com.icloudandroid.ui.gallery

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.icloudandroid.photos.PendingUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads a picked content Uri fully into memory for the experimental upload path. */
suspend fun readPendingUpload(context: Context, uri: Uri): PendingUpload? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    val mimeType = resolver.getType(uri) ?: "image/jpeg"
    val filename = queryDisplayName(resolver, uri) ?: "IMG_${System.currentTimeMillis()}.jpg"

    PendingUpload(
        bytes = bytes,
        filename = filename,
        uti = mimeToUti(mimeType),
        width = bounds.outWidth.coerceAtLeast(0),
        height = bounds.outHeight.coerceAtLeast(0),
    )
}

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) cursor.getString(index) else null
    }

private fun mimeToUti(mimeType: String): String = when (mimeType) {
    "image/png" -> "public.png"
    "image/heic" -> "public.heic"
    "image/gif" -> "com.compuserve.gif"
    else -> "public.jpeg"
}
