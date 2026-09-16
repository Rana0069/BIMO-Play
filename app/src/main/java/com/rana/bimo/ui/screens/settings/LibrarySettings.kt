/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.rana.bimo.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rana.bimo.R
import com.rana.bimo.constants.FlatSubfoldersKey
import com.rana.bimo.constants.ProxyEnabledKey
import com.rana.bimo.constants.ProxyTypeKey
import com.rana.bimo.constants.ProxyUrlKey
import com.rana.bimo.constants.ShowLikedAndDownloadedPlaylist
import com.rana.bimo.constants.TopBarInsets
import com.rana.bimo.ui.component.ColumnWithContentPadding
import com.rana.bimo.ui.component.EditTextPreference
import com.rana.bimo.ui.component.IconButton
import com.rana.bimo.ui.component.ListPreference
import com.rana.bimo.ui.component.PreferenceEntry
import com.rana.bimo.ui.component.PreferenceGroupTitle
import com.rana.bimo.ui.component.SettingsClickToReveal
import com.rana.bimo.ui.component.SwitchPreference
import com.rana.bimo.ui.screens.settings.fragments.ListenHistoryFrag
import com.rana.bimo.ui.screens.settings.fragments.SearchHistoryFrag
import com.rana.bimo.ui.utils.backToMain
import com.rana.bimo.utils.rememberEnumPreference
import com.rana.bimo.utils.rememberPreference
import java.net.Proxy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (showLikedAndDownloadedPlaylist, onShowLikedAndDownloadedPlaylistChange) = rememberPreference(
        key = ShowLikedAndDownloadedPlaylist,
        defaultValue = true
    )
    val (flatSubfolders, onFlatSubfoldersChange) = rememberPreference(FlatSubfoldersKey, defaultValue = true)

    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyUrl, onProxyUrlChange) = rememberPreference(key = ProxyUrlKey, defaultValue = "host:port")


    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.content)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.local_player_settings_title)) },
                icon = { Icon(Icons.Rounded.SdCard, null) },
                onClick = { navController.navigate("settings/local") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.lyrics_settings_title)) },
                icon = { Icon(Icons.Rounded.Lyrics, null) },
                onClick = { navController.navigate("settings/library/lyrics") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.storage)) },
                icon = { Icon(Icons.Rounded.Storage, null) },
                onClick = { navController.navigate("settings/storage") }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.privacy)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            ListenHistoryFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            SearchHistoryFrag()
        }

        SettingsClickToReveal(stringResource(R.string.advanced)) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.show_liked_and_downloaded_playlist)) },
                    icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) },
                    checked = showLikedAndDownloadedPlaylist,
                    onCheckedChange = onShowLikedAndDownloadedPlaylistChange
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.flat_subfolders_title)) },
                    description = stringResource(R.string.flat_subfolders_description),
                    icon = { Icon(Icons.Rounded.FolderCopy, null) },
                    checked = flatSubfolders,
                    onCheckedChange = onFlatSubfoldersChange
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_proxy)) },
                    checked = proxyEnabled,
                    onCheckedChange = onProxyEnabledChange
                )

                AnimatedVisibility(proxyEnabled) {
                    Column {
                        ListPreference(
                            title = { Text(stringResource(R.string.proxy_type)) },
                            selectedValue = proxyType,
                            values = listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS),
                            valueText = { it.name },
                            onValueSelected = onProxyTypeChange
                        )
                        EditTextPreference(
                            title = { Text(stringResource(R.string.proxy_url)) },
                            value = proxyUrl,
                            onValueChange = onProxyUrlChange
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }


    TopAppBar(
        title = { Text(stringResource(R.string.grp_library_and_content)) },
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
}
