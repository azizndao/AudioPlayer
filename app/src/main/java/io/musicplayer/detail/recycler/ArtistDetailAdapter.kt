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
 
package io.musicplayer.detail.recycler

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.musicplayer.IntegerTable
import io.musicplayer.R
import io.musicplayer.databinding.ItemDetailBinding
import io.musicplayer.databinding.ItemParentBinding
import io.musicplayer.databinding.ItemSongBinding
import io.musicplayer.music.Album
import io.musicplayer.music.Artist
import io.musicplayer.music.Genre
import io.musicplayer.music.Song
import io.musicplayer.music.resolveYear
import io.musicplayer.ui.recycler.ArtistViewHolder
import io.musicplayer.ui.recycler.IndicatorAdapter
import io.musicplayer.ui.recycler.Item
import io.musicplayer.ui.recycler.MenuItemListener
import io.musicplayer.ui.recycler.SimpleItemCallback
import io.musicplayer.util.context
import io.musicplayer.util.getPlural
import io.musicplayer.util.inflater

/**
 * An adapter for displaying [Artist] information and it's children. Unlike the other adapters, this
 * one actually contains both album information and song information.
 * @author OxygenCobalt
 */
class ArtistDetailAdapter(private val listener: Listener) :
    DetailAdapter<DetailAdapter.Listener>(listener, DIFFER) {

    override fun getItemViewType(position: Int) =
        when (differ.currentList[position]) {
            is Artist -> ArtistDetailViewHolder.VIEW_TYPE
            is Album -> ArtistAlbumViewHolder.VIEW_TYPE
            is Song -> ArtistSongViewHolder.VIEW_TYPE
            else -> super.getItemViewType(position)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        when (viewType) {
            ArtistDetailViewHolder.VIEW_TYPE -> ArtistDetailViewHolder.new(parent)
            ArtistAlbumViewHolder.VIEW_TYPE -> ArtistAlbumViewHolder.new(parent)
            ArtistSongViewHolder.VIEW_TYPE -> ArtistSongViewHolder.new(parent)
            else -> super.onCreateViewHolder(parent, viewType)
        }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        super.onBindViewHolder(holder, position, payloads)

        if (payloads.isEmpty()) {
            when (val item = differ.currentList[position]) {
                is Artist -> (holder as ArtistDetailViewHolder).bind(item, listener)
                is Album -> (holder as ArtistAlbumViewHolder).bind(item, listener)
                is Song -> (holder as ArtistSongViewHolder).bind(item, listener)
            }
        }
    }

    override fun isItemFullWidth(position: Int): Boolean {
        val item = differ.currentList[position]
        return super.isItemFullWidth(position) || item is Artist
    }

    companion object {
        private val DIFFER =
            object : SimpleItemCallback<Item>() {
                override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
                    return when {
                        oldItem is Artist && newItem is Artist ->
                            ArtistDetailViewHolder.DIFFER.areItemsTheSame(oldItem, newItem)
                        oldItem is Album && newItem is Album ->
                            ArtistAlbumViewHolder.DIFFER.areItemsTheSame(oldItem, newItem)
                        oldItem is Song && newItem is Song ->
                            ArtistSongViewHolder.DIFFER.areItemsTheSame(oldItem, newItem)
                        else -> DetailAdapter.DIFFER.areItemsTheSame(oldItem, newItem)
                    }
                }
            }
    }
}

private class ArtistDetailViewHolder private constructor(private val binding: ItemDetailBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: Artist, listener: DetailAdapter.Listener) {
        binding.detailCover.bind(item)
        binding.detailType.text = binding.context.getString(R.string.lbl_artist)
        binding.detailName.text = item.resolveName(binding.context)

        // Get the genre that corresponds to the most songs in this artist, which would be
        // the most "Prominent" genre.
        val genresByAmount = mutableMapOf<Genre, Int>()
        for (song in item.songs) {
            for (genre in song.genres) {
                genresByAmount[genre] = genresByAmount[genre]?.inc() ?: 1
            }
        }

        binding.detailSubhead.text =
            genresByAmount.maxByOrNull { it.value }?.key?.resolveName(binding.context)
                ?: binding.context.getString(R.string.def_genre)

        binding.detailInfo.text =
            binding.context.getString(
                R.string.fmt_two,
                binding.context.getPlural(R.plurals.fmt_album_count, item.albums.size),
                binding.context.getPlural(R.plurals.fmt_song_count, item.songs.size))

        binding.detailPlayButton.setOnClickListener { listener.onPlayParent() }
        binding.detailShuffleButton.setOnClickListener { listener.onShuffleParent() }
    }

    companion object {
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_ARTIST_DETAIL

        fun new(parent: View) =
            ArtistDetailViewHolder(ItemDetailBinding.inflate(parent.context.inflater))

        val DIFFER = ArtistViewHolder.DIFFER
    }
}

private class ArtistAlbumViewHolder
private constructor(
    private val binding: ItemParentBinding,
) : IndicatorAdapter.ViewHolder(binding.root) {
    fun bind(item: Album, listener: MenuItemListener) {
        binding.parentImage.bind(item)
        binding.parentName.text = item.resolveName(binding.context)
        binding.parentInfo.text = item.date.resolveYear(binding.context)
        // binding.parentMenu.setOnClickListener { listener.onOpenMenu(item, it) }
        binding.root.setOnLongClickListener {
            listener.onOpenMenu(item, it)
            true
        }
        binding.root.setOnClickListener { listener.onItemClick(item) }
    }

    override fun updateIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.root.isActivated = isActive
        binding.parentImage.isPlaying = isPlaying
    }

    companion object {
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_ARTIST_ALBUM

        fun new(parent: View) =
            ArtistAlbumViewHolder(ItemParentBinding.inflate(parent.context.inflater))

        val DIFFER =
            object : SimpleItemCallback<Album>() {
                override fun areItemsTheSame(oldItem: Album, newItem: Album) =
                    oldItem.rawName == newItem.rawName && oldItem.date == newItem.date
            }
    }
}

private class ArtistSongViewHolder
private constructor(
    private val binding: ItemSongBinding,
) : IndicatorAdapter.ViewHolder(binding.root) {
    fun bind(item: Song, listener: MenuItemListener) {
        binding.songAlbumCover.bind(item)
        binding.songName.text = item.resolveName(binding.context)
        binding.songInfo.text = item.album.resolveName(binding.context)
        // binding.songMenu.setOnClickListener { listener.onOpenMenu(item, it) }
        binding.root.setOnLongClickListener {
            listener.onOpenMenu(item, it)
            true
        }
        binding.root.setOnClickListener { listener.onItemClick(item) }
    }

    override fun updateIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.root.isActivated = isActive
        binding.songAlbumCover.isPlaying = isPlaying
    }

    companion object {
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_ARTIST_SONG

        fun new(parent: View) =
            ArtistSongViewHolder(ItemSongBinding.inflate(parent.context.inflater))

        val DIFFER =
            object : SimpleItemCallback<Song>() {
                override fun areItemsTheSame(oldItem: Song, newItem: Song) =
                    oldItem.rawName == newItem.rawName &&
                        oldItem.album.rawName == newItem.album.rawName
            }
    }
}
