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
 
package io.musicplayer.playback.queue

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsets
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.shape.MaterialShapeDrawable
import io.musicplayer.R
import io.musicplayer.ui.AuxioSheetBehavior
import io.musicplayer.util.*

/**
 * The bottom sheet behavior designed for the queue in particular.
 * @author OxygenCobalt
 */
class QueueSheetBehavior<V : View>(context: Context, attributeSet: AttributeSet?) :
    AuxioSheetBehavior<V>(context, attributeSet) {
    private var barHeight = 0
    private var barSpacing = context.getDimenSize(R.dimen.spacing_small)

    init {
        isHideable = false
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: V, dependency: View) =
        dependency.id == R.id.playback_bar_fragment

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: V,
        dependency: View
    ): Boolean {
        barHeight = dependency.height
        return false // No change, just grabbed the height
    }

    override fun createBackground(context: Context) =
        MaterialShapeDrawable.createWithElevationOverlay(context).apply {
            fillColor = context.getAttrColorCompat(R.attr.colorSurface)
            elevation = context.getDimen(R.dimen.elevation_normal)
        }

    override fun applyWindowInsets(child: View, insets: WindowInsets): WindowInsets {
        super.applyWindowInsets(child, insets)

        // Offset our expanded panel by the size of the playback bar, as that is shown when
        // we slide up the panel.
        val bars = insets.systemBarInsetsCompat
        expandedOffset = bars.top + barHeight + barSpacing
        return insets.replaceSystemBarInsetsCompat(
            bars.left, bars.top, bars.right, expandedOffset + bars.bottom)
    }
}
