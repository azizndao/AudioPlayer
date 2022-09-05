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
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsets
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.NeoBottomSheetBehavior
import io.musicplayer.util.coordinatorLayoutBehavior
import io.musicplayer.util.replaceSystemBarInsetsCompat
import io.musicplayer.util.systemBarInsetsCompat
import kotlin.math.abs

/**
 * A behavior that automatically re-layouts and re-insets content to align with the parent layout's
 * bottom sheet.
 *
 * Ideally, we would one day want to switch to only re-insetting content, however this comes with
 * several issues::
 * 1. Scroll position. I need to find a good way to save padding in order to prevent desync, as
 * window insets tend to be applied after restoration.
 * 2. Over scrolling. Glow scrolls will not cut it, as the bottom glow will be caught under the bar,
 * and moving it above the insets will result in an incorrect glow position when the bar is not
 * shown. I have to emulate stretch scrolling below Android 12 instead. However, this is also
 * similarly distorted by the insets, and thus I must go further and modify the edge effect to be at
 * least somewhat clamped to the insets themselves.
 * 3. Touch events. Bottom sheets must always intercept touches in their bounds, or they will click
 * the now overlapping content view that is only inset by it and not unhidden by it.
 *
 * @author OxygenCobalt
 */
class BottomSheetContentBehavior<V : View>(context: Context, attributeSet: AttributeSet?) :
    CoordinatorLayout.Behavior<V>(context, attributeSet) {
    private var dep: View? = null
    private var lastInsets: WindowInsets? = null
    private var lastConsumed = -1
    private var setup = false

    override fun layoutDependsOn(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        if (dependency.coordinatorLayoutBehavior is NeoBottomSheetBehavior) {
            dep = dependency
            return true
        }

        return false
    }

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: V,
        dependency: View
    ): Boolean {
        val behavior = dependency.coordinatorLayoutBehavior as NeoBottomSheetBehavior
        val consumed = behavior.calculateConsumedByBar()
        if (consumed == Int.MIN_VALUE) {
            return false
        }

        if (consumed != lastConsumed) {
            lastConsumed = consumed

            val insets = lastInsets
            if (insets != null) {
                child.dispatchApplyWindowInsets(insets)
            }

            lastInsets?.let(child::dispatchApplyWindowInsets)
            measureContent(parent, child, consumed)
            layoutContent(child)
            return true
        }

        return false
    }

    override fun onMeasureChild(
        parent: CoordinatorLayout,
        child: V,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ): Boolean {
        val dep = dep ?: return false
        val behavior = dep.coordinatorLayoutBehavior as NeoBottomSheetBehavior
        val consumed = behavior.calculateConsumedByBar()
        if (consumed == Int.MIN_VALUE) {
            return false
        }

        measureContent(parent, child, consumed)

        return true
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        super.onLayoutChild(parent, child, layoutDirection)
        layoutContent(child)

        if (!setup) {
            child.setOnApplyWindowInsetsListener { _, insets ->
                lastInsets = insets
                val dep = dep ?: return@setOnApplyWindowInsetsListener insets
                val behavior = dep.coordinatorLayoutBehavior as NeoBottomSheetBehavior
                val consumed = behavior.calculateConsumedByBar()
                if (consumed == Int.MIN_VALUE) {
                    return@setOnApplyWindowInsetsListener insets
                }

                val bars = insets.systemBarInsetsCompat

                insets.replaceSystemBarInsetsCompat(
                    bars.left, bars.top, bars.right, (bars.bottom - consumed).coerceAtLeast(0))
            }

            setup = true
        }

        return true
    }

    private fun measureContent(parent: View, child: View, consumed: Int) {
        val contentWidthSpec =
            View.MeasureSpec.makeMeasureSpec(parent.measuredWidth, View.MeasureSpec.EXACTLY)
        val contentHeightSpec =
            View.MeasureSpec.makeMeasureSpec(
                parent.measuredHeight - consumed, View.MeasureSpec.EXACTLY)

        child.measure(contentWidthSpec, contentHeightSpec)
    }

    private fun layoutContent(child: View) {
        child.layout(0, 0, child.measuredWidth, child.measuredHeight)
    }

    private fun NeoBottomSheetBehavior<*>.calculateConsumedByBar(): Int {
        val offset = calculateSlideOffset()
        if (offset == Float.MIN_VALUE || peekHeight < 0) {
            return Int.MIN_VALUE
        }

        return if (offset >= 0) {
            peekHeight
        } else {
            (peekHeight * (1 - abs(offset))).toInt()
        }
    }
}
