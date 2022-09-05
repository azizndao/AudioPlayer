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
 
package io.musicplayer.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.NeoBottomSheetBehavior
import io.musicplayer.R
import io.musicplayer.util.getDimen
import io.musicplayer.util.systemGestureInsetsCompat

/**
 * Implements a reasonable enough skeleton around BottomSheetBehavior (Excluding auxio extensions in
 * the vendored code because I course I have to) for normal use without absurd bugs.
 * @author OxygenCobalt
 */
abstract class AuxioSheetBehavior<V : View>(context: Context, attributeSet: AttributeSet?) :
    NeoBottomSheetBehavior<V>(context, attributeSet) {
    private var setup = false

    init {
        // We need to disable isFitToContents for us to have our bottom sheet expand to the
        // whole of the screen and not just whatever portion it takes up.
        isFitToContents = false
    }

    /** Called when the sheet background is being created */
    abstract fun createBackground(context: Context): Drawable

    /** Called when the child the bottom sheet applies to receives window insets. */
    open fun applyWindowInsets(child: View, insets: WindowInsets): WindowInsets {
        // All sheet behaviors derive their peek height from the size of the "bar" (i.e the
        // first child) plus the gesture insets.
        val gestures = insets.systemGestureInsetsCompat
        peekHeight = (child as ViewGroup).getChildAt(0).height + gestures.bottom
        return insets
    }

    // Enable experimental settings to allow us to skip the half expanded state without
    // dumb hacks.
    override fun shouldSkipHalfExpandedStateWhenDragging() = true
    override fun shouldExpandOnUpwardDrag(dragDurationMillis: Long, yPositionPercentage: Float) =
        true

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        val layout = super.onLayoutChild(parent, child, layoutDirection)

        if (!setup) {
            child.apply {
                translationZ = context.getDimen(R.dimen.elevation_normal)
                background = createBackground(context)
                setOnApplyWindowInsetsListener(::applyWindowInsets)
            }

            setup = true
        }

        // Sometimes CoordinatorLayout tries to be "hElpfUl" and just does not dispatch window
        // insets sometimes. Ensure that we get them.
        child.requestApplyInsets()

        return layout
    }
}
