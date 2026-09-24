package com.icloudandroid.ui.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.icloudandroid.R
import com.icloudandroid.photos.PhotoItem
import com.icloudandroid.photos.UploadState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onSignOut: () -> Unit,
    onOpenPhoto: (index: Int) -> Unit,
    onOpenUploader: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    val gridState = rememberLazyGridState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExperimentalWarning by remember { mutableStateOf(false) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val uploads = uris.mapNotNull { readPendingUpload(context, it) }
            viewModel.uploadPhotos(uploads)
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 12
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    LaunchedEffect(uploadState) {
        when (val state = uploadState) {
            is UploadState.Success -> {
                snackbarHostState.showSnackbar(context.getString(R.string.upload_success))
                viewModel.dismissUploadState()
            }
            is UploadState.Failed -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.dismissUploadState()
            }
            else -> Unit
        }
    }

    if (showExperimentalWarning) {
        AlertDialog(
            onDismissRequest = { showExperimentalWarning = false },
            title = { Text(stringResource(R.string.experimental_upload_warning_title)) },
            text = { Text(stringResource(R.string.experimental_upload_warning_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showExperimentalWarning = false
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExperimentalWarning = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.gallery_title)) },
                    actions = {
                        IconButton(onClick = onOpenUploader) {
                            Icon(Icons.Default.CloudUpload, contentDescription = stringResource(R.string.action_add_photos))
                        }
                        IconButton(onClick = { showExperimentalWarning = true }) {
                            Icon(Icons.Default.Science, contentDescription = stringResource(R.string.action_experimental_upload))
                        }
                        IconButton(onClick = onSignOut) {
                            Icon(Icons.Default.Logout, contentDescription = stringResource(R.string.action_sign_out))
                        }
                    },
                )
                val uploading = uploadState as? UploadState.Uploading
                if (uploading != null) {
                    LinearProgressIndicator(
                        progress = { if (uploading.total == 0) 0f else uploading.done / uploading.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    ) { padding ->
        when {
            uiState.isLoadingInitial -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            uiState.errorMessage != null && uiState.items.isEmpty() -> ErrorState(
                message = uiState.errorMessage!!,
                onRetry = viewModel::retry,
                padding = padding,
            )

            uiState.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.empty_gallery))
            }

            else -> PhotoGrid(
                items = uiState.items,
                isLoadingMore = uiState.isLoadingMore,
                gridState = gridState,
                padding = padding,
                onOpenPhoto = onOpenPhoto,
            )
        }
    }
}

@Composable
private fun PhotoGrid(
    items: List<PhotoItem>,
    isLoadingMore: Boolean,
    gridState: LazyGridState,
    padding: PaddingValues,
    onOpenPhoto: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 2.dp,
            bottom = padding.calculateBottomPadding() + 2.dp,
            start = 2.dp,
            end = 2.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items.size, key = { items[it].recordName }) { index ->
            PhotoThumbnail(item = items[index], onClick = { onOpenPhoto(index) })
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(item: PhotoItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(MaterialTheme.shapes.extraSmall),
    ) {
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = item.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(onClick = onClick),
        )
        if (item.isVideo) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}
