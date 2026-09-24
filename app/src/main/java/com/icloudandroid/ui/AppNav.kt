package com.icloudandroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.icloudandroid.IcloudAndroidApp
import com.icloudandroid.ui.gallery.GalleryScreen
import com.icloudandroid.ui.gallery.GalleryViewModel
import com.icloudandroid.ui.login.LoginScreen
import com.icloudandroid.ui.login.LoginViewModel
import com.icloudandroid.ui.upload.UploadWebViewScreen
import com.icloudandroid.ui.viewer.PhotoViewerScreen

private sealed interface Screen {
    data object Login : Screen
    data class Gallery(val photosServiceUrl: String) : Screen
    data class Viewer(val photosServiceUrl: String, val index: Int) : Screen
    data class Uploader(val photosServiceUrl: String) : Screen
}

@Composable
fun AppNav() {
    val container = (LocalContext.current.applicationContext as IcloudAndroidApp).container
    var screen by remember { mutableStateOf<Screen>(Screen.Login) }

    when (val current = screen) {
        is Screen.Login -> {
            val loginViewModel: LoginViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { LoginViewModel(container.authRepository) }
                }
            )
            LoginScreen(
                viewModel = loginViewModel,
                onSignedIn = { photosServiceUrl -> screen = Screen.Gallery(photosServiceUrl) },
            )
        }

        is Screen.Gallery -> {
            val galleryViewModel: GalleryViewModel = viewModel(
                key = current.photosServiceUrl,
                factory = viewModelFactory {
                    initializer { GalleryViewModel(container.photosRepository(current.photosServiceUrl)) }
                }
            )
            GalleryScreen(
                viewModel = galleryViewModel,
                onSignOut = {
                    container.authRepository.signOut()
                    screen = Screen.Login
                },
                onOpenPhoto = { index -> screen = Screen.Viewer(current.photosServiceUrl, index) },
                onOpenUploader = { screen = Screen.Uploader(current.photosServiceUrl) },
            )
        }

        is Screen.Viewer -> {
            val galleryViewModel: GalleryViewModel = viewModel(
                key = current.photosServiceUrl,
                factory = viewModelFactory {
                    initializer { GalleryViewModel(container.photosRepository(current.photosServiceUrl)) }
                }
            )
            val galleryUiState by galleryViewModel.uiState.collectAsState()
            PhotoViewerScreen(
                items = galleryUiState.items,
                initialIndex = current.index,
                onBack = { screen = Screen.Gallery(current.photosServiceUrl) },
            )
        }

        is Screen.Uploader -> {
            UploadWebViewScreen(
                cookieJar = container.cookieJar,
                onBack = { screen = Screen.Gallery(current.photosServiceUrl) },
            )
        }
    }
}
