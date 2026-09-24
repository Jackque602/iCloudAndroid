package com.icloudandroid.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.icloudandroid.R
import com.icloudandroid.photos.PhotoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onSignOut: () -> Unit,
    onOpenPhoto: (index: Int) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery_title)) },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, contentDescription = stringResource(R.string.action_sign_out))
                    }
                },
            )
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
