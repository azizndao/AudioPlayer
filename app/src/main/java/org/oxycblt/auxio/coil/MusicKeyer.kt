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
 
package org.oxycblt.auxio.coil

import coil.key.Keyer
import coil.request.Options
import org.oxycblt.auxio.music.Music
import org.oxycblt.auxio.music.Song

/** A basic keyer for music data. */
class MusicKeyer : Keyer<Music> {
    override fun key(data: Music, options: Options): String {
        return if (data is Song) {
            // Group up song covers with album covers for better caching
            key(data.album, options)
        } else {
            "${data::class.simpleName}: ${data.id}"
        }
    }
}
