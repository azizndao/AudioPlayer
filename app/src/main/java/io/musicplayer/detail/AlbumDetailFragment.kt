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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearSmoothScroller
import com.google.android.material.transition.MaterialSharedAxis
import io.musicplayer.R
import io.musicplayer.databinding.FragmentDetailBinding
import io.musicplayer.detail.recycler.AlbumDetailAdapter
import io.musicplayer.music.*
import io.musicplayer.playback.state.PlaybackMode
import io.musicplayer.settings.Settings
import io.musicplayer.ui.Sort
import io.musicplayer.ui.fragment.MenuFragment
import io.musicplayer.ui.recycler.Item
import io.musicplayer.util.*

/**
 * A fragment that shows information for a particular [Album].
 * @author OxygenCobalt
 */
class AlbumDetailFragment :
    MenuFragment<FragmentDetailBinding>(),
    Toolbar.OnMenuItemClickListener,
    AlbumDetailAdapter.Listener {
    private val detailModel: DetailViewModel by activityViewModels()

    private val args: AlbumDetailFragmentArgs by navArgs()
    private val detailAdapter = AlbumDetailAdapter(this)
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
        detailModel.setAlbumId(args.albumId)

        binding.detailToolbar.apply {
            inflateMenu(R.menu.menu_album_detail)
            setNavigationOnClickListener { findNavController().navigateUp() }
            setOnMenuItemClickListener(this@AlbumDetailFragment)
        }

        binding.detailRecycler.adapter = detailAdapter

        // -- VIEWMODEL SETUP ---

        collectImmediately(detailModel.currentAlbum, ::handleItemChange)
        collectImmediately(detailModel.albumData, detailAdapter::submitList)
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
                playbackModel.playNext(unlikelyToBeNull(detailModel.currentAlbum.value))
                requireContext().showToast(R.string.lng_queue_added)
                true
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(unlikelyToBeNull(detailModel.currentAlbum.value))
                requireContext().showToast(R.string.lng_queue_added)
                true
            }
            R.id.action_go_artist -> {
                navModel.exploreNavigateTo(unlikelyToBeNull(detailModel.currentAlbum.value).artist)
                true
            }
            else -> false
        }
    }

    override fun onItemClick(item: Item) {
        if (item is Song) {
            playbackModel.play(item, settings.detailPlaybackMode ?: PlaybackMode.IN_ALBUM)
        }
    }

    override fun onOpenMenu(item: Item, anchor: View) {
        if (item is Song) {
            musicMenu(anchor, R.menu.menu_album_song_actions, item)
            return
        }

        error("Unexpected datatype when opening menu: ${item::class.java}")
    }

    override fun onPlayParent() {
        playbackModel.play(unlikelyToBeNull(detailModel.currentAlbum.value), false)
    }

    override fun onShuffleParent() {
        playbackModel.play(unlikelyToBeNull(detailModel.currentAlbum.value), true)
    }

    override fun onShowSortMenu(anchor: View) {
        menu(anchor, R.menu.menu_album_sort) {
            val sort = detailModel.albumSort
            unlikelyToBeNull(menu.findItem(sort.mode.itemId)).isChecked = true
            unlikelyToBeNull(menu.findItem(R.id.option_sort_asc)).isChecked = sort.isAscending
            setOnMenuItemClickListener { item ->
                item.isChecked = !item.isChecked
                detailModel.albumSort =
                    if (item.itemId == R.id.option_sort_asc) {
                        sort.withAscending(item.isChecked)
                    } else {
                        sort.withMode(unlikelyToBeNull(Sort.Mode.fromItemId(item.itemId)))
                    }
                true
            }
        }
    }

    override fun onNavigateToArtist() {
        findNavController()
            .navigate(
                AlbumDetailFragmentDirections.actionShowArtist(
                    unlikelyToBeNull(detailModel.currentAlbum.value).artist.id))
    }

    private fun handleItemChange(album: Album?) {
        if (album == null) {
            findNavController().navigateUp()
            return
        }

        requireBinding().detailToolbar.title = album.resolveName(requireContext())
    }

    private fun handleNavigation(item: Music?) {
        val binding = requireBinding()
        when (item) {
            // Songs should be scrolled to if the album matches, or a new detail
            // fragment should be launched otherwise.
            is Song -> {
                if (unlikelyToBeNull(detailModel.currentAlbum.value).id == item.album.id) {
                    logD("Navigating to a song in this album")
                    scrollToItem(item.id)
                    navModel.finishExploreNavigation()
                } else {
                    logD("Navigating to another album")
                    findNavController()
                        .navigate(AlbumDetailFragmentDirections.actionShowAlbum(item.album.id))
                }
            }

            // If the album matches, no need to do anything. Otherwise launch a new
            // detail fragment.
            is Album -> {
                if (unlikelyToBeNull(detailModel.currentAlbum.value).id == item.id) {
                    logD("Navigating to the top of this album")
                    binding.detailRecycler.scrollToPosition(0)
                    navModel.finishExploreNavigation()
                } else {
                    logD("Navigating to another album")
                    findNavController()
                        .navigate(AlbumDetailFragmentDirections.actionShowAlbum(item.id))
                }
            }

            // Always launch a new ArtistDetailFragment.
            is Artist -> {
                logD("Navigating to another artist")
                findNavController()
                    .navigate(AlbumDetailFragmentDirections.actionShowArtist(item.id))
            }
            null -> {}
            else -> error("Unexpected navigation item ${item::class.java}")
        }
    }

    /** Scroll to an song using its [id]. */
    private fun scrollToItem(id: Long) {
        // Calculate where the item for the currently played song is
        val pos = detailModel.albumData.value.indexOfFirst { it.id == id && it is Song }

        if (pos != -1) {
            val binding = requireBinding()
            binding.detailRecycler.post {
                // Make sure to increment the position to make up for the detail header
                binding.detailRecycler.layoutManager?.startSmoothScroll(
                    CenterSmoothScroller(requireContext(), pos))

                // If the recyclerview can scroll, its certain that it will have to scroll to
                // correctly center the playing item, so make sure that the Toolbar is lifted in
                // that case.
                binding.detailAppbar.isLifted = binding.detailRecycler.canScroll()
            }
        }
    }

    private fun updatePlayback(song: Song?, parent: MusicParent?, isPlaying: Boolean) {
        val binding = requireBinding()

        for (item in binding.detailToolbar.menu.children) {
            // If there is no playback going in, any queue additions will be wiped as soon as
            // something is played. Disable these actions when playback is going on so that
            // it isn't possible to add anything during that time.
            if (item.itemId == R.id.action_play_next || item.itemId == R.id.action_queue_add) {
                item.isEnabled = song != null
            }
        }

        if (parent is Album && parent.id == unlikelyToBeNull(detailModel.currentAlbum.value).id) {
            detailAdapter.updateIndicator(song, isPlaying)
        } else {
            // Clear the ViewHolders if the mode isn't ALL_SONGS
            detailAdapter.updateIndicator(null, isPlaying)
        }
    }

    /**
     * [LinearSmoothScroller] subclass that centers the item on the screen instead of snapping to
     * the top or bottom.
     */
    private class CenterSmoothScroller(context: Context, target: Int) :
        LinearSmoothScroller(context) {
        init {
            targetPosition = target
        }

        override fun calculateDtToFit(
            viewStart: Int,
            viewEnd: Int,
            boxStart: Int,
            boxEnd: Int,
            snapPreference: Int
        ): Int {
            return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
        }
    }
}
