package com.rana.bimo.ui.screens.artist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.rana.bimo.LocalMenuState
import com.rana.bimo.LocalPlayerAwareWindowInsets
import com.rana.bimo.LocalPlayerConnection
import com.rana.bimo.LocalSnackbarHostState
import com.rana.bimo.R
import com.rana.bimo.constants.ArtistSongSortDescendingKey
import com.rana.bimo.constants.ArtistSongSortType
import com.rana.bimo.constants.ArtistSongSortTypeKey
import com.rana.bimo.constants.CONTENT_TYPE_HEADER
import com.rana.bimo.constants.TopBarInsets
import com.rana.bimo.models.toMediaMetadata
import com.rana.bimo.playback.queues.ListQueue
import com.rana.bimo.ui.component.FloatingFooter
import com.rana.bimo.ui.component.HideOnScrollFAB
import com.rana.bimo.ui.component.IconButton
import com.rana.bimo.ui.component.SelectHeader
import com.rana.bimo.ui.component.SongListItem
import com.rana.bimo.ui.component.SortHeader
import com.rana.bimo.ui.utils.backToMain
import com.rana.bimo.utils.rememberEnumPreference
import com.rana.bimo.utils.rememberPreference
import com.rana.bimo.viewmodels.ArtistSongsViewModel
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSongsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistSongsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val (sortType, onSortTypeChange) = rememberEnumPreference(ArtistSongSortTypeKey, ArtistSongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSongSortDescendingKey, true)

    val artist by viewModel.artist.collectAsState()
    val songs by viewModel.songs.collectAsState()

    val lazyListState = rememberLazyListState()

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val snackbarHostState = LocalSnackbarHostState.current

    Box(
        modifier = Modifier.fillMaxSize()
            .padding(bottom = 32.dp)
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.padding(bottom = if (inSelectMode) 64.dp else 0.dp)
        ) {
            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    SortHeader(
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = onSortTypeChange,
                        onSortDescendingChange = onSortDescendingChange,
                        sortTypeText = { sortType ->
                            when (sortType) {
                                ArtistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                ArtistSongSortType.NAME -> R.string.sort_by_name
                                ArtistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                            }
                        }
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, item -> item.id }
            ) { index, song ->
                SongListItem(
                    song = song,
                    onPlay = {
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            val playlistId = YouTube.artist(artist?.id!!).getOrNull()
                                ?.artist?.shuffleEndpoint?.playlistId

                            // for some reason this get called on the wrong thread and crashes, use main
                            CoroutineScope(Dispatchers.Main).launch {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = artist?.artist?.name,
                                        items = songs.map { it.toMediaMetadata() },
                                        startIndex = index,
                                        playlistId = playlistId
                                    )
                                )
                            }
                        }
                    },
                    onSelectedChange = {
                        inSelectMode = true
                        if (it) {
                            selection.add(song.id)
                        } else {
                            selection.remove(song.id)
                        }
                    },
                    inSelectMode = inSelectMode,
                    isSelected = selection.contains(song.id),
                    navController = navController,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                )
            }
        }

        TopAppBar(
            title = { Text(artist?.artist?.name.orEmpty()) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null
                    )
                }
            },
            windowInsets = TopBarInsets,
            scrollBehavior = scrollBehavior
        )

        HideOnScrollFAB(
            lazyListState = lazyListState,
            icon = Icons.Rounded.Shuffle,
            onClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = artist?.artist?.name,
                        items = songs.map { it.toMediaMetadata() },
                        startShuffled = true,
                        playlistId = null,
                    )
                )
            }
        )

        FloatingFooter(inSelectMode) {
            SelectHeader(
                navController = navController,
                selectedItems = selection.mapNotNull { songId ->
                    songs.find { it.id == songId }
                }.map { it.toMediaMetadata() },
                totalItemCount = songs.size,
                onSelectAll = {
                    selection.clear()
                    selection.addAll(songs.map { it.id })
                },
                onDeselectAll = { selection.clear() },
                menuState = menuState,
                onDismiss = onExitSelectionMode
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }
}