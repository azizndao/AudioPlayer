/*
 * Copyright (c) 2021 Auxio Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package io.musicplayer.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.musicplayer.R
import io.musicplayer.home.tabs.Tab
import io.musicplayer.music.Album
import io.musicplayer.music.Artist
import io.musicplayer.music.Genre
import io.musicplayer.music.MusicStore
import io.musicplayer.music.Song
import io.musicplayer.settings.Settings
import io.musicplayer.ui.DisplayMode
import io.musicplayer.ui.Sort
import io.musicplayer.util.application
import io.musicplayer.util.logD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The ViewModel for managing [HomeFragment]'s data, sorting modes, and tab state.
 * @author OxygenCobalt
 */
class HomeViewModel(application: Application) :
    AndroidViewModel(application), Settings.Callback, MusicStore.Callback {
    private val musicStore = MusicStore.getInstance()
    private val settings = Settings(application, this)

    private val _songs = MutableStateFlow(listOf<Song>())
    val songs: StateFlow<List<Song>>
        get() = _songs

    private val _albums = MutableStateFlow(listOf<Album>())
    val albums: StateFlow<List<Album>>
        get() = _albums

    private val _artists = MutableStateFlow(listOf<Artist>())
    val artists: MutableStateFlow<List<Artist>>
        get() = _artists

    private val _genres = MutableStateFlow(listOf<Genre>())
    val genres: StateFlow<List<Genre>>
        get() = _genres

    var tabs: List<DisplayMode> = visibleTabs
        private set

    /** Internal getter for getting the visible library tabs */
    private val visibleTabs: List<DisplayMode>
        get() = settings.libTabs.filterIsInstance<Tab.Visible>().map { it.mode }

    private val _currentTab = MutableStateFlow(tabs[0])
    val currentTab: StateFlow<DisplayMode> = _currentTab

    /**
     * Marker to recreate all library tabs, usually initiated by a settings change. When this flag
     * is set, all tabs (and their respective viewpager fragments) will be recreated from scratch.
     */
    private val _shouldRecreateTabs = MutableStateFlow(false)
    val recreateTabs: StateFlow<Boolean> = _shouldRecreateTabs

    private val _isFastScrolling = MutableStateFlow(false)
    val isFastScrolling: StateFlow<Boolean> = _isFastScrolling

    init {
        musicStore.addCallback(this)
    }

    /** Update the current tab based off of the new ViewPager position. */
    fun updateCurrentTab(pos: Int) {
        logD("Updating current tab to ${tabs[pos]}")
        _currentTab.value = tabs[pos]
    }

    fun finishRecreateTabs() {
        _shouldRecreateTabs.value = false
    }

    /** Get the specific sort for the given [DisplayMode]. */
    fun getSortForDisplay(displayMode: DisplayMode) =
        when (displayMode) {
            DisplayMode.SHOW_SONGS -> settings.libSongSort
            DisplayMode.SHOW_ALBUMS -> settings.libAlbumSort
            DisplayMode.SHOW_ARTISTS -> settings.libArtistSort
            DisplayMode.SHOW_GENRES -> settings.libGenreSort
        }

    /** Update the currently displayed item's [Sort]. */
    fun updateCurrentSort(sort: Sort) {
        logD("Updating ${_currentTab.value} sort to $sort")
        when (_currentTab.value) {
            DisplayMode.SHOW_SONGS -> {
                settings.libSongSort = sort
                _songs.value = sort.songs(_songs.value)
            }
            DisplayMode.SHOW_ALBUMS -> {
                settings.libAlbumSort = sort
                _albums.value = sort.albums(_albums.value)
            }
            DisplayMode.SHOW_ARTISTS -> {
                settings.libArtistSort = sort
                _artists.value = sort.artists(_artists.value)
            }
            DisplayMode.SHOW_GENRES -> {
                settings.libGenreSort = sort
                _genres.value = sort.genres(_genres.value)
            }
        }
    }

    /**
     * Update the fast scroll state. This is used to control the FAB visibility whenever the user
     * begins to fast scroll.
     */
    fun updateFastScrolling(scrolling: Boolean) {
        logD("Updating fast scrolling state: $scrolling")
        _isFastScrolling.value = scrolling
    }

    // --- OVERRIDES ---

    override fun onLibraryChanged(library: MusicStore.Library?) {
        if (library != null) {
            _songs.value = settings.libSongSort.songs(library.songs)
            _albums.value = settings.libAlbumSort.albums(library.albums)
            _artists.value = settings.libArtistSort.artists(library.artists)
            _genres.value = settings.libGenreSort.genres(library.genres)
        }
    }

    override fun onSettingChanged(key: String) {
        if (key == application.getString(R.string.set_key_lib_tabs)) {
            tabs = visibleTabs
            _shouldRecreateTabs.value = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicStore.removeCallback(this)
        settings.release()
    }
}
