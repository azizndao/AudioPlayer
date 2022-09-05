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
import android.view.View
import android.view.ViewGroup
import io.musicplayer.R
import io.musicplayer.databinding.FragmentHomeListBinding
import io.musicplayer.music.Genre
import io.musicplayer.music.Music
import io.musicplayer.music.MusicParent
import io.musicplayer.ui.DisplayMode
import io.musicplayer.ui.Sort
import io.musicplayer.ui.recycler.GenreViewHolder
import io.musicplayer.ui.recycler.IndicatorAdapter
import io.musicplayer.ui.recycler.Item
import io.musicplayer.ui.recycler.MenuItemListener
import io.musicplayer.ui.recycler.SyncListDiffer
import io.musicplayer.util.collectImmediately
import io.musicplayer.util.formatDurationMs

/**
 * A [HomeListFragment] for showing a list of [Genre]s.
 * @author OxygenCobalt
 */
class GenreListFragment : HomeListFragment<Genre>() {
    private val homeAdapter = GenreAdapter(this)

    override fun onBindingCreated(binding: FragmentHomeListBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.homeRecycler.apply {
            id = R.id.home_genre_list
            adapter = homeAdapter
        }

        collectImmediately(homeModel.genres, homeAdapter::replaceList)
        collectImmediately(playbackModel.parent, playbackModel.isPlaying, ::handlePlayback)
    }

    override fun getPopup(pos: Int): String? {
        val genre = homeModel.genres.value[pos]

        // Change how we display the popup depending on the mode.
        return when (homeModel.getSortForDisplay(DisplayMode.SHOW_GENRES).mode) {
            // By Name -> Use Name
            is Sort.Mode.ByName -> genre.sortName?.run { first().uppercase() }

            // Duration -> Use formatted duration
            is Sort.Mode.ByDuration -> genre.durationMs.formatDurationMs(false)

            // Count -> Use song count
            is Sort.Mode.ByCount -> genre.songs.size.toString()

            // Unsupported sort, error gracefully
            else -> null
        }
    }

    override fun onItemClick(item: Item) {
        check(item is Music)
        navModel.exploreNavigateTo(item)
    }

    override fun onOpenMenu(item: Item, anchor: View) {
        when (item) {
            is Genre -> musicMenu(anchor, R.menu.menu_genre_artist_actions, item)
            else -> error("Unexpected datatype when opening menu: ${item::class.java}")
        }
    }

    private fun handlePlayback(parent: MusicParent?, isPlaying: Boolean) {
        if (parent is Genre) {
            homeAdapter.updateIndicator(parent, isPlaying)
        } else {
            // Ignore playback not from genres
            homeAdapter.updateIndicator(null, isPlaying)
        }
    }

    private class GenreAdapter(private val listener: MenuItemListener) :
        IndicatorAdapter<GenreViewHolder>() {
        private val differ = SyncListDiffer(this, GenreViewHolder.DIFFER)

        override val currentList: List<Item>
            get() = differ.currentList

        override fun getItemCount() = differ.currentList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            GenreViewHolder.new(parent)

        override fun onBindViewHolder(holder: GenreViewHolder, position: Int, payloads: List<Any>) {
            super.onBindViewHolder(holder, position, payloads)

            if (payloads.isEmpty()) {
                holder.bind(differ.currentList[position], listener)
            }
        }

        fun replaceList(newList: List<Genre>) {
            differ.replaceList(newList)
        }
    }
}
