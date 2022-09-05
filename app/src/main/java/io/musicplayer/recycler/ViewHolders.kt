/*
 * Copyright (c) 2022 Auxio Project
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
 
package io.musicplayer.ui.recycler

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.musicplayer.IntegerTable
import io.musicplayer.R
import io.musicplayer.databinding.ItemHeaderBinding
import io.musicplayer.databinding.ItemParentBinding
import io.musicplayer.databinding.ItemSongBinding
import io.musicplayer.music.Album
import io.musicplayer.music.Artist
import io.musicplayer.music.Genre
import io.musicplayer.music.Song
import io.musicplayer.util.context
import io.musicplayer.util.getPlural
import io.musicplayer.util.inflater

/**
 * The shared ViewHolder for a [Song].
 * @author OxygenCobalt
 */
class SongViewHolder private constructor(private val binding: ItemSongBinding) :
    IndicatorAdapter.ViewHolder(binding.root) {
    fun bind(item: Song, listener: MenuItemListener) {
        binding.songAlbumCover.bind(item)
        binding.songName.text = item.resolveName(binding.context)
        binding.songInfo.text = item.resolveIndividualArtistName(binding.context)
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
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_SONG

        fun new(parent: View) = SongViewHolder(ItemSongBinding.inflate(parent.context.inflater))

        val DIFFER =
            object : SimpleItemCallback<Song>() {
                override fun areItemsTheSame(oldItem: Song, newItem: Song) =
                    oldItem.rawName == newItem.rawName &&
                        oldItem.individualArtistRawName == oldItem.individualArtistRawName
            }
    }
}

/**
 * The Shared ViewHolder for a [Album].
 * @author OxygenCobalt
 */
class AlbumViewHolder
private constructor(
    private val binding: ItemParentBinding,
) : IndicatorAdapter.ViewHolder(binding.root) {

    fun bind(item: Album, listener: MenuItemListener) {
        binding.parentImage.bind(item)
        binding.parentName.text = item.resolveName(binding.context)
        binding.parentInfo.text = item.artist.resolveName(binding.context)
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
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_ALBUM

        fun new(parent: View) = AlbumViewHolder(ItemParentBinding.inflate(parent.context.inflater))

        val DIFFER =
            object : SimpleItemCallback<Album>() {
                override fun areItemsTheSame(oldItem: Album, newItem: Album) =
                    oldItem.rawName == newItem.rawName &&
                        oldItem.artist.rawName == newItem.artist.rawName &&
                        oldItem.releaseType == newItem.releaseType
            }
    }
}

/**
 * The Shared ViewHolder for a [Artist].
 * @author OxygenCobalt
 */
class ArtistViewHolder private constructor(private val binding: ItemParentBinding) :
    IndicatorAdapter.ViewHolder(binding.root) {

    fun bind(item: Artist, listener: MenuItemListener) {
        binding.parentImage.bind(item)
        binding.parentName.text = item.resolveName(binding.context)
        binding.parentInfo.text =
            binding.context.getString(
                R.string.fmt_two,
                binding.context.getPlural(R.plurals.fmt_album_count, item.albums.size),
                binding.context.getPlural(R.plurals.fmt_song_count, item.songs.size))
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
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_ARTIST

        fun new(parent: View) = ArtistViewHolder(ItemParentBinding.inflate(parent.context.inflater))

        val DIFFER =
            object : SimpleItemCallback<Artist>() {
                override fun areItemsTheSame(oldItem: Artist, newItem: Artist) =
                    oldItem.rawName == newItem.rawName &&
                        oldItem.albums.size == newItem.albums.size &&
                        oldItem.songs.size == newItem.songs.size
            }
    }
}

/**
 * The Shared ViewHolder for a [Genre].
 * @author OxygenCobalt
 */
class GenreViewHolder
private constructor(
    private val binding: ItemParentBinding,
) : IndicatorAdapter.ViewHolder(binding.root) {

    fun bind(item: Genre, listener: MenuItemListener) {
        binding.parentImage.bind(item)
        binding.parentName.text = item.resolveName(binding.context)
        binding.parentInfo.text =
            binding.context.getPlural(R.plurals.fmt_song_count, item.songs.size)
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
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_GENRE

        fun new(parent: View) = GenreViewHolder(ItemParentBinding.inflate(parent.context.inflater))

        val DIFFER =
            object : SimpleItemCallback<Genre>() {
                override fun areItemsTheSame(oldItem: Genre, newItem: Genre): Boolean =
                    oldItem.rawName == newItem.rawName && oldItem.songs.size == newItem.songs.size
            }
    }
}

/**
 * The Shared ViewHolder for a [Header].
 * @author OxygenCobalt
 */
class HeaderViewHolder private constructor(private val binding: ItemHeaderBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: Header) {
        binding.title.text = binding.context.getString(item.string)
    }

    companion object {
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_HEADER

        fun new(parent: View) = HeaderViewHolder(ItemHeaderBinding.inflate(parent.context.inflater))

        val DIFFER =
            object : SimpleItemCallback<Header>() {
                override fun areItemsTheSame(oldItem: Header, newItem: Header): Boolean =
                    oldItem.string == newItem.string
            }
    }
}
