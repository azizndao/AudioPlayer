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
 
package org.oxycblt.auxio.playback.system

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.oxycblt.auxio.util.logD

/**
 * Some apps like to party like it's 2011 and just blindly query for the ACTION_MEDIA_BUTTON intent
 * to determine the media apps on a system. *Auxio does not expose this.* Auxio exposes a
 * MediaSession that an app should control instead through the much better MediaController API. But
 * who cares about that, we need to make sure the 3% of barely functioning TouchWiz devices running
 * KitKat don't break! To prevent Auxio from not showing up at all in these apps, we declare a
 * BroadcastReceiver that deliberately handles this event. This also means that Auxio will start
 * without warning if you use the media buttons while the app exists, because I guess we just have
 * to deal with this.
 * @author OxygenCobalt
 */
class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
            logD("Received external media button intent")
            intent.component = ComponentName(context, PlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
