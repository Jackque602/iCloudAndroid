package com.icloudandroid.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icloudandroid.photos.PendingUpload
import com.icloudandroid.photos.PhotoItem
import com.icloudandroid.photos.PhotosRepository
import com.icloudandroid.photos.UploadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GalleryUiState(
    val items: List<PhotoItem> = emptyList(),
    val isLoadingInitial: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
)

class GalleryViewModel(private val repository: PhotosRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    private var nextOffset = 0
    private var isFetching = false

    init {
        loadMore()
    }

    fun retry() {
        _uiState.value = GalleryUiState()
        nextOffset = 0
        loadMore()
    }

    fun loadMore() {
        val current = _uiState.value
        if (isFetching || !current.hasMore) return
        isFetching = true

        val loadingInitial = current.items.isEmpty()
        _uiState.value = current.copy(
            isLoadingInitial = loadingInitial,
            isLoadingMore = !loadingInitial,
            errorMessage = null,
        )

        viewModelScope.launch {
            repository.loadPage(nextOffset)
                .onSuccess { page ->
                    nextOffset = page.nextOffset
                    _uiState.value = _uiState.value.copy(
                        items = _uiState.value.items + page.items,
                        isLoadingInitial = false,
                        isLoadingMore = false,
                        hasMore = page.hasMore,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingInitial = false,
                        isLoadingMore = false,
                        errorMessage = error.message ?: "Failed to load photos.",
                    )
                }
            isFetching = false
        }
    }

    /** Experimental native upload path; see [com.icloudandroid.photos.UploadApi]. */
    fun uploadPhotos(uploads: List<PendingUpload>) {
        if (uploads.isEmpty()) return
        viewModelScope.launch {
            var failures = 0
            uploads.forEachIndexed { index, upload ->
                _uploadState.value = UploadState.Uploading(index, uploads.size)
                repository.uploadPhoto(upload).onFailure { failures++ }
            }
            _uploadState.value = if (failures == 0) {
                UploadState.Success
            } else {
                UploadState.Failed("$failures of ${uploads.size} upload(s) failed.")
            }
            retry()
        }
    }

    fun dismissUploadState() {
        _uploadState.value = UploadState.Idle
    }
}
