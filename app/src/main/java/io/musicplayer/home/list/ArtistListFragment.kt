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
import io.musicplayer.music.Artist
import io.musicplayer.music.Music
import io.musicplayer.music.MusicParent
import io.musicplayer.ui.DisplayMode
import io.musicplayer.ui.Sort
import io.musicplayer.ui.recycler.ArtistViewHolder
import io.musicplayer.ui.recycler.IndicatorAdapter
import io.musicplayer.ui.recycler.Item
import io.musicplayer.ui.recycler.MenuItemListener
import io.musicplayer.ui.recycler.SyncListDiffer
import io.musicplayer.util.collectImmediately
import io.musicplayer.util.formatDurationMs

/**
 * A [HomeListFragment] for showing a list of [Artist]s.
 * @author OxygenCobalt
 */
class ArtistListFragment : HomeListFragment<Artist>() {
    private val homeAdapter = ArtistAdapter(this)

    override fun onBindingCreated(binding: FragmentHomeListBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.homeRecycler.apply {
            id = R.id.home_artist_list
            adapter = homeAdapter
        }

        collectImmediately(homeModel.artists, homeAdapter::replaceList)
        collectImmediately(playbackModel.parent, playbackModel.isPlaying, ::handleParent)
    }

    override fun getPopup(pos: Int): String? {
        val artist = homeModel.artists.value[pos]

        // Change how we display the popup depending on the mode.
        return when (homeModel.getSortForDisplay(DisplayMode.SHOW_ARTISTS).mode) {
            // By Name -> Use Name
            is Sort.Mode.ByName -> artist.sortName?.run { first().uppercase() }

            // Duration -> Use formatted duration
            is Sort.Mode.ByDuration -> artist.durationMs.formatDurationMs(false)

            // Count -> Use song count
            is Sort.Mode.ByCount -> artist.songs.size.toString()

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
            is Artist -> musicMenu(anchor, R.menu.menu_genre_artist_actions, item)
            else -> error("Unexpected datatype when opening menu: ${item::class.java}")
        }
    }

    private fun handleParent(parent: MusicParent?, isPlaying: Boolean) {
        if (parent is Artist) {
            homeAdapter.updateIndicator(parent, isPlaying)
        } else {
            // Ignore playback not from artists
            homeAdapter.updateIndicator(null, isPlaying)
        }
    }

    private class ArtistAdapter(private val listener: MenuItemListener) :
        IndicatorAdapter<ArtistViewHolder>() {
        private val differ = SyncListDiffer(this, ArtistViewHolder.DIFFER)

        override val currentList: List<Item>
            get() = differ.currentList

        override fun getItemCount() = differ.currentList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ArtistViewHolder.new(parent)

        override fun onBindViewHolder(
            holder: ArtistViewHolder,
            position: Int,
            payloads: List<Any>
        ) {
            super.onBindViewHolder(holder, position, payloads)

            if (payloads.isEmpty()) {
                holder.bind(differ.currentList[position], listener)
            }
        }

        fun replaceList(newList: List<Artist>) {
            differ.replaceList(newList)
        }
    }
}
