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
 
package io.musicplayer.home.tabs

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.musicplayer.BuildConfig
import io.musicplayer.R
import io.musicplayer.databinding.DialogTabsBinding
import io.musicplayer.settings.Settings
import io.musicplayer.ui.DisplayMode
import io.musicplayer.ui.fragment.ViewBindingDialogFragment
import io.musicplayer.util.context
import io.musicplayer.util.logD

/**
 * The dialog for customizing library tabs.
 * @author OxygenCobalt
 */
class TabCustomizeDialog : ViewBindingDialogFragment<DialogTabsBinding>(), TabAdapter.Listener {
    private val tabAdapter = TabAdapter(this)
    private val settings: Settings by lifecycleObject { binding -> Settings(binding.context) }
    private val touchHelper: ItemTouchHelper by lifecycleObject {
        ItemTouchHelper(TabDragCallback(tabAdapter))
    }

    override fun onCreateBinding(inflater: LayoutInflater) = DialogTabsBinding.inflate(inflater)

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        builder
            .setTitle(R.string.set_lib_tabs)
            .setPositiveButton(R.string.lbl_ok) { _, _ ->
                logD("Committing tab changes")
                settings.libTabs = tabAdapter.tabs
            }
            .setNegativeButton(R.string.lbl_cancel, null)
    }

    override fun onBindingCreated(binding: DialogTabsBinding, savedInstanceState: Bundle?) {
        val savedTabs = findSavedTabState(savedInstanceState)
        if (savedTabs != null) {
            logD("Found saved tab state")
            tabAdapter.submitTabs(savedTabs)
        } else {
            tabAdapter.submitTabs(settings.libTabs)
        }

        binding.tabRecycler.apply {
            adapter = tabAdapter
            touchHelper.attachToRecyclerView(this)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TABS, Tab.toSequence(tabAdapter.tabs))
    }

    override fun onDestroyBinding(binding: DialogTabsBinding) {
        super.onDestroyBinding(binding)
        binding.tabRecycler.adapter = null
    }

    override fun onVisibilityToggled(displayMode: DisplayMode) {
        // Tab viewholders bind with the initial tab state, which will drift from the actual
        // state of the tabs over editing. So, this callback simply provides the displayMode
        // for us to locate within the data and then update.
        val index = tabAdapter.tabs.indexOfFirst { it.mode == displayMode }
        if (index > -1) {
            val tab = tabAdapter.tabs[index]
            tabAdapter.setTab(
                index,
                when (tab) {
                    is Tab.Visible -> Tab.Invisible(tab.mode)
                    is Tab.Invisible -> Tab.Visible(tab.mode)
                })
        }

        (requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
            tabAdapter.tabs.filterIsInstance<Tab.Visible>().isNotEmpty()
    }

    override fun onPickUpTab(viewHolder: RecyclerView.ViewHolder) {
        touchHelper.startDrag(viewHolder)
    }

    private fun findSavedTabState(savedInstanceState: Bundle?): Array<Tab>? {
        if (savedInstanceState != null) {
            // Restore any pending tab configurations
            return Tab.fromSequence(savedInstanceState.getInt(KEY_TABS))
        }

        return null
    }

    companion object {
        const val TAG = BuildConfig.APPLICATION_ID + ".tag.TAB_CUSTOMIZE"
        const val KEY_TABS = BuildConfig.APPLICATION_ID + ".key.PENDING_TABS"
    }
}
