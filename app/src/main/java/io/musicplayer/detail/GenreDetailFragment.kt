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
 
package io.musicplayer.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.transition.MaterialSharedAxis
import io.musicplayer.R
import io.musicplayer.databinding.FragmentDetailBinding
import io.musicplayer.detail.recycler.DetailAdapter
import io.musicplayer.detail.recycler.GenreDetailAdapter
import io.musicplayer.music.Album
import io.musicplayer.music.Artist
import io.musicplayer.music.Genre
import io.musicplayer.music.Music
import io.musicplayer.music.MusicParent
import io.musicplayer.music.Song
import io.musicplayer.playback.state.PlaybackMode
import io.musicplayer.settings.Settings
import io.musicplayer.ui.Sort
import io.musicplayer.ui.fragment.MenuFragment
import io.musicplayer.ui.recycler.Item
import io.musicplayer.util.collect
import io.musicplayer.util.collectImmediately
import io.musicplayer.util.context
import io.musicplayer.util.logD
import io.musicplayer.util.showToast
import io.musicplayer.util.unlikelyToBeNull

/**
 * A fragment that shows information for a particular [Genre].
 * @author OxygenCobalt
 */
class GenreDetailFragment :
    MenuFragment<FragmentDetailBinding>(), Toolbar.OnMenuItemClickListener, DetailAdapter.Listener {
    private val detailModel: DetailViewModel by activityViewModels()

    private val args: GenreDetailFragmentArgs by navArgs()
    private val detailAdapter = GenreDetailAdapter(this)
    private val settings: Settings by lifecycleObject { binding -> Settings(binding.context) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateBinding(inflater: LayoutInflater) = FragmentDetailBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentDetailBinding, savedInstanceState: Bundle?) {
        detailModel.setGenreId(args.genreId)

        binding.detailToolbar.apply {
            inflateMenu(R.menu.menu_genre_artist_detail)
            setNavigationOnClickListener { findNavController().navigateUp() }
            setOnMenuItemClickListener(this@GenreDetailFragment)
        }

        binding.detailRecycler.adapter = detailAdapter

        // --- VIEWMODEL SETUP ---

        collectImmediately(detailModel.currentGenre, ::handleItemChange)
        collectImmediately(detailModel.genreData, detailAdapter::submitList)
        collectImmediately(
            playbackModel.song, playbackModel.parent, playbackModel.isPlaying, ::updatePlayback)
        collect(navModel.exploreNavigationItem, ::handleNavigation)
    }

    override fun onDestroyBinding(binding: FragmentDetailBinding) {
        super.onDestroyBinding(binding)
        binding.detailToolbar.setOnMenuItemClickListener(null)
        binding.detailRecycler.adapter = null
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_play_next -> {
                playbackModel.playNext(unlikelyToBeNull(detailModel.currentGenre.value))
                requireContext().showToast(R.string.lng_queue_added)
                true
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(unlikelyToBeNull(detailModel.currentGenre.value))
                requireContext().showToast(R.string.lng_queue_added)
                true
            }
            else -> false
        }
    }

    override fun onItemClick(item: Item) {
        when (item) {
            is Song ->
                playbackModel.play(item, settings.detailPlaybackMode ?: PlaybackMode.IN_GENRE)
            is Album ->
                findNavController()
                    .navigate(ArtistDetailFragmentDirections.actionShowAlbum(item.id))
        }
    }

    override fun onOpenMenu(item: Item, anchor: View) {
        if (item is Song) {
            musicMenu(anchor, R.menu.menu_song_actions, item)
        }

        error("Unexpected datatype when opening menu: ${item::class.java}")
    }

    override fun onPlayParent() {
        playbackModel.play(unlikelyToBeNull(detailModel.currentGenre.value), false)
    }

    override fun onShuffleParent() {
        playbackModel.play(unlikelyToBeNull(detailModel.currentGenre.value), true)
    }

    override fun onShowSortMenu(anchor: View) {
        menu(anchor, R.menu.menu_genre_sort) {
            val sort = detailModel.genreSort
            unlikelyToBeNull(menu.findItem(sort.mode.itemId)).isChecked = true
            unlikelyToBeNull(menu.findItem(R.id.option_sort_asc)).isChecked = sort.isAscending
            setOnMenuItemClickListener { item ->
                item.isChecked = !item.isChecked
                detailModel.genreSort =
                    if (item.itemId == R.id.option_sort_asc) {
                        sort.withAscending(item.isChecked)
                    } else {
                        sort.withMode(unlikelyToBeNull(Sort.Mode.fromItemId(item.itemId)))
                    }
                true
            }
        }
    }

    private fun handleItemChange(genre: Genre?) {
        if (genre == null) {
            findNavController().navigateUp()
            return
        }

        requireBinding().detailToolbar.title = genre.resolveName(requireContext())
    }

    private fun handleNavigation(item: Music?) {
        when (item) {
            is Song -> {
                logD("Navigating to another song")
                findNavController()
                    .navigate(GenreDetailFragmentDirections.actionShowAlbum(item.album.id))
            }
            is Album -> {
                logD("Navigating to another album")
                findNavController().navigate(GenreDetailFragmentDirections.actionShowAlbum(item.id))
            }
            is Artist -> {
                logD("Navigating to another artist")
                findNavController()
                    .navigate(GenreDetailFragmentDirections.actionShowArtist(item.id))
            }
            is Genre -> {
                navModel.finishExploreNavigation()
            }
            null -> {}
        }
    }

    private fun updatePlayback(song: Song?, parent: MusicParent?, isPlaying: Boolean) {
        if (parent is Genre && parent.id == unlikelyToBeNull(detailModel.currentGenre.value).id) {
            detailAdapter.updateIndicator(song, isPlaying)
        } else {
            // Ignore song playback not from the genre
            detailAdapter.updateIndicator(null, isPlaying)
        }
    }
}
