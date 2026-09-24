package com.icloudandroid.photos

import android.content.Context
import com.icloudandroid.network.HttpClientProvider

/**
 * Thin pagination wrapper around [PhotosApi]. Each call fetches the next page
 * starting where the previous one left off; callers (the gallery ViewModel)
 * keep the accumulated list and track [hasMore] themselves.
 */
class PhotosRepository(context: Context, photosServiceUrl: String) {

    private val api = PhotosApi(HttpClientProvider.client(context.applicationContext), photosServiceUrl)
    private val uploadApi = UploadApi(HttpClientProvider.client(context.applicationContext), photosServiceUrl)

    suspend fun loadPage(offset: Int, pageSize: Int = DEFAULT_PAGE_SIZE): Result<PhotoPage> =
        api.queryPage(offset, pageSize)

    suspend fun uploadPhoto(upload: PendingUpload): Result<Unit> = uploadApi.uploadPhoto(
        bytes = upload.bytes,
        filename = upload.filename,
        uti = upload.uti,
        width = upload.width,
        height = upload.height,
    )

    companion object {
        const val DEFAULT_PAGE_SIZE = 90
    }
}
