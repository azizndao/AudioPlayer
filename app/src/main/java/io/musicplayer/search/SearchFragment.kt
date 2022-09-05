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
 
package io.musicplayer.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isInvisible
import androidx.core.view.postDelayed
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.MaterialSharedAxis
import io.musicplayer.R
import io.musicplayer.databinding.FragmentSearchBinding
import io.musicplayer.music.Album
import io.musicplayer.music.Artist
import io.musicplayer.music.Genre
import io.musicplayer.music.Music
import io.musicplayer.music.MusicParent
import io.musicplayer.music.Song
import io.musicplayer.settings.Settings
import io.musicplayer.ui.fragment.MenuFragment
import io.musicplayer.ui.recycler.Item
import io.musicplayer.ui.recycler.MenuItemListener
import io.musicplayer.util.androidViewModels
import io.musicplayer.util.collect
import io.musicplayer.util.collectImmediately
import io.musicplayer.util.context
import io.musicplayer.util.getSystemServiceCompat
import io.musicplayer.util.logW

/**
 * A [Fragment] that allows for the searching of the entire music library.
 * @author OxygenCobalt
 */
class SearchFragment :
    MenuFragment<FragmentSearchBinding>(), MenuItemListener, Toolbar.OnMenuItemClickListener {

    // SearchViewModel is only scoped to this Fragment
    private val searchModel: SearchViewModel by androidViewModels()

    private val searchAdapter = SearchAdapter(this)
    private val settings: Settings by lifecycleObject { binding -> Settings(binding.context) }
    private val imm: InputMethodManager by lifecycleObject { binding ->
        binding.context.getSystemServiceCompat(InputMethodManager::class)
    }

    private var launchedKeyboard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateBinding(inflater: LayoutInflater) = FragmentSearchBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentSearchBinding, savedInstanceState: Bundle?) {
        binding.searchToolbar.apply {
            menu.findItem(searchModel.filterMode?.itemId ?: R.id.option_filter_all).isChecked = true

            setNavigationOnClickListener {
                imm.hide()
                findNavController().navigateUp()
            }

            setOnMenuItemClickListener(this@SearchFragment)
        }

        binding.searchEditText.apply {
            addTextChangedListener { text ->
                // Run the search with the updated text as the query
                searchModel.search(text?.toString())
            }

            if (!launchedKeyboard) {
                // Auto-open the keyboard when this view is shown
                requestFocus()
                postDelayed(200) { imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }

                launchedKeyboard = true
            }
        }

        binding.searchRecycler.adapter = searchAdapter

        // --- VIEWMODEL SETUP ---

        collectImmediately(searchModel.searchResults, ::handleResults)
        collectImmediately(
            playbackModel.song, playbackModel.parent, playbackModel.isPlaying, ::handlePlayback)
        collect(navModel.exploreNavigationItem, ::handleNavigation)
    }

    override fun onDestroyBinding(binding: FragmentSearchBinding) {
        binding.searchToolbar.setOnMenuItemClickListener(null)
        binding.searchRecycler.adapter = null
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.submenu_filtering -> {}
            else -> {
                if (item.itemId != R.id.submenu_filtering) {
                    searchModel.updateFilterModeWithId(item.itemId)
                    item.isChecked = true
                }
            }
        }

        return true
    }

    override fun onItemClick(item: Item) {
        when (item) {
            is Song -> playbackModel.play(item, settings.libPlaybackMode)
            is MusicParent -> navModel.exploreNavigateTo(item)
        }
    }

    override fun onOpenMenu(item: Item, anchor: View) {
        when (item) {
            is Song -> musicMenu(anchor, R.menu.menu_song_actions, item)
            is Album -> musicMenu(anchor, R.menu.menu_album_actions, item)
            is Artist -> musicMenu(anchor, R.menu.menu_genre_artist_actions, item)
            is Genre -> musicMenu(anchor, R.menu.menu_genre_artist_actions, item)
            else -> logW("Unexpected datatype when opening menu: ${item::class.java}")
        }
    }

    private fun handleResults(results: List<Item>) {
        val binding = requireBinding()

        searchAdapter.submitList(results.toMutableList()) {
            // I would make it so that the position is only scrolled back to the top when
            // the query actually changes instead of once every re-creation event, but sadly
            // that doesn't seem possible.
            binding.searchRecycler.scrollToPosition(0)
        }

        binding.searchRecycler.isInvisible = results.isEmpty()
    }

    private fun handlePlayback(song: Song?, parent: MusicParent?, isPlaying: Boolean) {
        searchAdapter.updateIndicator(parent ?: song, isPlaying)
    }

    private fun handleNavigation(item: Music?) {
        findNavController()
            .navigate(
                when (item) {
                    is Song -> SearchFragmentDirections.actionShowAlbum(item.album.id)
                    is Album -> SearchFragmentDirections.actionShowAlbum(item.id)
                    is Artist -> SearchFragmentDirections.actionShowArtist(item.id)
                    is Genre -> SearchFragmentDirections.actionShowGenre(item.id)
                    else -> return
                })

        imm.hide()
    }

    private fun InputMethodManager.hide() {
        hideSoftInputFromWindow(requireView().windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }
}
