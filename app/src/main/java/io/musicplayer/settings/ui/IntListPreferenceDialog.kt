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
 
package io.musicplayer.settings.ui

import android.os.Bundle
import androidx.preference.PreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.musicplayer.BuildConfig
import io.musicplayer.R

class IntListPreferenceDialog : PreferenceDialogFragmentCompat() {
    private val listPreference: IntListPreference
        get() = (preference as IntListPreference)
    private var pendingValueIndex = -1

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        // PreferenceDialogFragmentCompat does not allow us to customize the actual creation
        // of the alert dialog, so we have to manually override onCreateDialog and customize it
        // ourselves.
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(listPreference.title)
            .setPositiveButton(null, null)
            .setNegativeButton(R.string.lbl_cancel, null)
            .setSingleChoiceItems(listPreference.entries, listPreference.getValueIndex()) { _, index
                ->
                pendingValueIndex = index
                dismiss()
            }
            .create()

    override fun onDialogClosed(positiveResult: Boolean) {
        if (pendingValueIndex > -1) {
            listPreference.setValueIndex(pendingValueIndex)
        }
    }

    companion object {
        const val TAG = BuildConfig.APPLICATION_ID + ".tag.INT_PREF"

        fun from(pref: IntListPreference): IntListPreferenceDialog {
            return IntListPreferenceDialog().apply {
                arguments = Bundle().apply { putString(ARG_KEY, pref.key) }
            }
        }
    }
}
