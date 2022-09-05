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
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import io.musicplayer.IntegerTable
import io.musicplayer.R
import io.musicplayer.databinding.ItemAlbumSongBinding
import io.musicplayer.databinding.ItemDetailBinding
import io.musicplayer.databinding.ItemDiscHeaderBinding
import io.musicplayer.detail.DiscHeader
import io.musicplayer.music.Album
import io.musicplayer.music.Song
import io.musicplayer.ui.recycler.IndicatorAdapter
import io.musicplayer.ui.recycler.Item
import io.musicplayer.ui.recycler.MenuItemListener
import io.musicplayer.ui.recycler.SimpleItemCallback
import io.musicplayer.util.context
import io.musicplayer.util.formatDurationMs
import io.musicplayer.util.getPlural
import io.musicplayer.util.inflater

/**
 * An adapter for displaying [Album] information and it's children.
 * @author OxygenCobalt
 */
class AlbumDetailAdapter(private val listener: Listener) :
    DetailAdapter<AlbumDetailAdapter.Listener>(listener, DIFFER) {

    override fun getItemViewType(position: Int) =
        when (differ.currentList[position]) {
            is Album -> AlbumDetailViewHolder.VIEW_TYPE
            is DiscHeader -> DiscHeaderViewHolder.VIEW_TYPE
            is Song -> AlbumSongViewHolder.VIEW_TYPE
            else -> super.getItemViewType(position)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        when (viewType) {
            AlbumDetailViewHolder.VIEW_TYPE -> AlbumDetailViewHolder.new(parent)
            DiscHeaderViewHolder.VIEW_TYPE -> DiscHeaderViewHolder.new(parent)
            AlbumSongViewHolder.VIEW_TYPE -> AlbumSongViewHolder.new(parent)
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
                is Album -> (holder as AlbumDetailViewHolder).bind(item, listener)
                is DiscHeader -> (holder as DiscHeaderViewHolder).bind(item)
                is Song -> (holder as AlbumSongViewHolder).bind(item, listener)
            }
        }
    }

    override fun isItemFullWidth(position: Int): Boolean {
        val item = differ.currentList[position]
        return super.isItemFullWidth(position) || item is Album || item is DiscHeader
    }

    companion object {
        private val DIFFER =
            object : SimpleItemCallback<Item>() {
                override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
                    when {
                        oldItem is Album && newItem is Album ->
                            AlbumDetailViewHolder.DIFFER.areItemsTheSame(oldItem, newItem)
                        oldItem is DiscHeader && newItem is DiscHeader ->
                            DiscHeaderViewHolder.DIFFER.areItemsTheSame(oldItem, newItem)
                        oldItem is Song && newItem is Song ->
                            AlbumSongViewHolder.DIFFER.areItemsTheSame(oldItem, newItem)
                        else -> DetailAdapter.DIFFER.areItemsTheSame(oldItem, newItem)
                    }
            }
    }

    interface Listener : DetailAdapter.Listener {
        fun onNavigateToArtist()
    }
}

private class AlbumDetailViewHolder private constructor(private val binding: ItemDetailBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: Album, listener: AlbumDetailAdapter.Listener) {
        binding.detailCover.bind(item)
        binding.detailType.text = binding.context.getString(item.releaseType.stringRes)

        binding.detailName.text = item.resolveName(binding.context)

        binding.detailSubhead.apply {
            text = item.artist.resolveName(context)
            setOnClickListener { listener.onNavigateToArtist() }
        }

        binding.detailInfo.apply {
            val date =
                item.date?.let { context.getString(R.string.fmt_number, it.year) }
                    ?: context.getString(R.string.def_date)

            val songCount = context.getPlural(R.plurals.fmt_song_count, item.songs.size)

            val duration = item.durationMs.formatDurationMs(true)

            text = context.getString(R.string.fmt_three, date, songCount, duration)
        }

        binding.detailPlayButton.setOnClickListener { listener.onPlayParent() }
        binding.detailShuffleButton.setOnClickListener { listener.onShuffleParent() }
    }

    companion object {
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_ALBUM_DETAIL

        fun new(parent: View) =
            AlbumDetailViewHolder(ItemDetailBinding.inflate(parent.context.inflater))

        val DIFFER =
            object : SimpleItemCallback<Album>() {
                override fun areItemsTheSame(oldItem: Album, newItem: Album) =
                    oldItem.rawName == newItem.rawName &&
                        oldItem.artist.rawName == newItem.artist.rawName &&
                        oldItem.date == newItem.date &&
                        oldItem.songs.size == newItem.songs.size &&
                        oldItem.durationMs == newItem.durationMs &&
                        oldItem.releaseType == newItem.releaseType
            }
    }
}

class DiscHeaderViewHolder(private val binding: ItemDiscHeaderBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: DiscHeader) {
        binding.discNo.text = binding.context.getString(R.string.fmt_disc_no, item.disc)
    }

    companion object {
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_DISC_HEADER

        fun new(parent: View) =
            DiscHeaderViewHolder(ItemDiscHeaderBinding.inflate(parent.context.inflater))

        val DIFFER =
            object : SimpleItemCallback<DiscHeader>() {
                override fun areItemsTheSame(oldItem: DiscHeader, newItem: DiscHeader) =
                    oldItem.disc == newItem.disc
            }
    }
}

private class AlbumSongViewHolder private constructor(private val binding: ItemAlbumSongBinding) :
    IndicatorAdapter.ViewHolder(binding.root) {
    fun bind(item: Song, listener: MenuItemListener) {
        // Hide the track number view if the song does not have a track.
        if (item.track != null) {
            binding.songTrack.apply {
                text = context.getString(R.string.fmt_number, item.track)
                isInvisible = false
                contentDescription = context.getString(R.string.desc_track_number, item.track)
            }
        } else {
            binding.songTrack.apply {
                text = ""
                isInvisible = true
                contentDescription = context.getString(R.string.def_track)
            }
        }

        binding.songName.text = item.resolveName(binding.context)
        binding.songDuration.text = item.durationMs.formatDurationMs(false)

        // binding.songMenu.setOnClickListener { listener.onOpenMenu(item, it) }
        binding.root.setOnLongClickListener {
            listener.onOpenMenu(item, it)
            true
        }
        binding.root.setOnClickListener { listener.onItemClick(item) }
    }

    override fun updateIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.root.isActivated = isActive
        binding.songTrackBg.isPlaying = isPlaying
    }

    companion object {
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_ALBUM_SONG

        fun new(parent: View) =
            AlbumSongViewHolder(ItemAlbumSongBinding.inflate(parent.context.inflater))

        val DIFFER =
            object : SimpleItemCallback<Song>() {
                override fun areItemsTheSame(oldItem: Song, newItem: Song) =
                    oldItem.rawName == newItem.rawName && oldItem.durationMs == newItem.durationMs
            }
    }
}
