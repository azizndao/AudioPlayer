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
 
package io.musicplayer.settings

import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.MaterialFadeThrough
import io.musicplayer.databinding.FragmentSettingsBinding
import io.musicplayer.ui.fragment.ViewBindingFragment

/**
 * A container [Fragment] for the settings menu.
 * @author OxygenCobalt
 */
class SettingsFragment : ViewBindingFragment<FragmentSettingsBinding>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentSettingsBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentSettingsBinding, savedInstanceState: Bundle?) {
        binding.settingsAppbar.liftOnScrollTargetViewId = androidx.preference.R.id.recycler_view
        binding.settingsToolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }
}
