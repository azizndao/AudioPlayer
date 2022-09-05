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
 
package io.musicplayer.home.list

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import io.musicplayer.R
import io.musicplayer.databinding.FragmentHomeListBinding
import io.musicplayer.music.MusicParent
import io.musicplayer.music.Song
import io.musicplayer.settings.Settings
import io.musicplayer.ui.DisplayMode
import io.musicplayer.ui.Sort
import io.musicplayer.ui.recycler.*
import io.musicplayer.util.collectImmediately
import io.musicplayer.util.context
import io.musicplayer.util.formatDurationMs
import io.musicplayer.util.secsToMs
import java.util.*

/**
 * A [HomeListFragment] for showing a list of [Song]s.
 * @author OxygenCobalt
 */
class SongListFragment : HomeListFragment<Song>() {
    private val homeAdapter = SongAdapter(this)
    private val settings: Settings by lifecycleObject { binding -> Settings(binding.context) }
    private val formatterSb = StringBuilder(50)
    private val formatter = Formatter(formatterSb)

    override fun onBindingCreated(binding: FragmentHomeListBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.homeRecycler.apply {
            id = R.id.home_song_list
            adapter = homeAdapter
        }

        collectImmediately(homeModel.songs, homeAdapter::replaceList)
        collectImmediately(
            playbackModel.song, playbackModel.parent, playbackModel.isPlaying, ::handlePlayback)
    }

    override fun getPopup(pos: Int): String? {
        val song = homeModel.songs.value[pos]

        // Change how we display the popup depending on the mode.
        // Note: We don't use the more correct individual artist name here, as sorts are largely
        // based off the names of the parent objects and not the child objects.
        return when (homeModel.getSortForDisplay(DisplayMode.SHOW_SONGS).mode) {
            // Name -> Use name
            is Sort.Mode.ByName -> song.sortName?.run { first().uppercase() }

            // Artist -> Use Artist Name
            is Sort.Mode.ByArtist -> song.album.artist.sortName?.run { first().uppercase() }

            // Album -> Use Album Name
            is Sort.Mode.ByAlbum -> song.album.sortName?.run { first().uppercase() }

            // Year -> Use Full Year
            is Sort.Mode.ByYear -> song.album.date?.resolveYear(requireContext())

            // Duration -> Use formatted duration
            is Sort.Mode.ByDuration -> song.durationMs.formatDurationMs(false)

            // Last added -> Format as date
            is Sort.Mode.ByDateAdded -> {
                val dateAddedMillis = song.dateAdded.secsToMs()
                formatterSb.setLength(0)
                DateUtils.formatDateRange(
                        context,
                        formatter,
                        dateAddedMillis,
                        dateAddedMillis,
                        DateUtils.FORMAT_ABBREV_ALL)
                    .toString()
            }

            // Unsupported sort, error gracefully
            else -> null
        }
    }

    override fun onItemClick(item: Item) {
        check(item is Song)
        playbackModel.play(item, settings.libPlaybackMode)
    }

    override fun onOpenMenu(item: Item, anchor: View) {
        when (item) {
            is Song -> musicMenu(anchor, R.menu.menu_song_actions, item)
            else -> error("Unexpected datatype when opening menu: ${item::class.java}")
        }
    }

    private fun handlePlayback(song: Song?, parent: MusicParent?, isPlaying: Boolean) {
        if (parent == null) {
            homeAdapter.updateIndicator(song, isPlaying)
        } else {
            // Ignore playback that is not from all songs
            homeAdapter.updateIndicator(null, isPlaying)
        }
    }

    private class SongAdapter(private val listener: MenuItemListener) :
        IndicatorAdapter<SongViewHolder>() {
        private val differ = SyncListDiffer(this, SongViewHolder.DIFFER)

        override val currentList: List<Item>
            get() = differ.currentList

        override fun getItemCount() = differ.currentList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            SongViewHolder.new(parent)

        override fun onBindViewHolder(holder: SongViewHolder, position: Int, payloads: List<Any>) {
            super.onBindViewHolder(holder, position, payloads)

            if (payloads.isEmpty()) {
                holder.bind(differ.currentList[position], listener)
            }
        }

        fun replaceList(newList: List<Song>) {
            differ.replaceList(newList)
        }
    }
}
