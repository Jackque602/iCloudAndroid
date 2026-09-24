package com.icloudandroid

import android.content.Context
import com.icloudandroid.auth.AuthRepository
import com.icloudandroid.photos.PhotosRepository

/** Minimal hand-rolled DI: one shared AuthRepository, and a PhotosRepository built on demand once we have a service URL. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val authRepository: AuthRepository by lazy { AuthRepository(appContext) }

    fun photosRepository(photosServiceUrl: String): PhotosRepository =
        PhotosRepository(appContext, photosServiceUrl)
}
