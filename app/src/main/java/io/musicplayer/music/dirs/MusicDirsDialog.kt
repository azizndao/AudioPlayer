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
 
package io.musicplayer.music.dirs

import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import io.musicplayer.BuildConfig
import io.musicplayer.R
import io.musicplayer.databinding.DialogMusicDirsBinding
import io.musicplayer.music.Directory
import io.musicplayer.settings.Settings
import io.musicplayer.ui.fragment.ViewBindingDialogFragment
import io.musicplayer.util.context
import io.musicplayer.util.getSystemServiceCompat
import io.musicplayer.util.logD
import io.musicplayer.util.showToast

/**
 * Dialog that manages the music dirs setting.
 * @author OxygenCobalt
 */
class MusicDirsDialog :
    ViewBindingDialogFragment<DialogMusicDirsBinding>(), MusicDirAdapter.Listener {
    private val dirAdapter = MusicDirAdapter(this)
    private val settings: Settings by lifecycleObject { binding -> Settings(binding.context) }
    private val storageManager: StorageManager by lifecycleObject { binding ->
        binding.context.getSystemServiceCompat(StorageManager::class)
    }

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogMusicDirsBinding.inflate(inflater)

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        // Don't set the click listener here, we do some custom magic in onCreateView instead.
        builder
            .setTitle(R.string.set_dirs)
            .setNeutralButton(R.string.lbl_add, null)
            .setNegativeButton(R.string.lbl_cancel, null)
            .setPositiveButton(R.string.lbl_save) { _, _ ->
                val dirs = settings.getMusicDirs(storageManager)
                val newDirs =
                    MusicDirs(dirs = dirAdapter.dirs, shouldInclude = isInclude(requireBinding()))
                if (dirs != newDirs) {
                    logD("Committing changes")
                    settings.setMusicDirs(newDirs)
                }
            }
    }

    override fun onBindingCreated(binding: DialogMusicDirsBinding, savedInstanceState: Bundle?) {
        val launcher =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree(), ::addDocTreePath)

        // Now that the dialog exists, we get the view manually when the dialog is shown
        // and override its click listener so that the dialog does not auto-dismiss when we
        // click the "Add"/"Save" buttons. This prevents the dialog from disappearing in the former
        // and the app from crashing in the latter.
        requireDialog().setOnShowListener {
            val dialog = it as AlertDialog

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                logD("Opening launcher")
                launcher.launch(null)
            }
        }

        binding.dirsRecycler.apply {
            adapter = dirAdapter
            itemAnimator = null
        }

        var dirs = settings.getMusicDirs(storageManager)

        if (savedInstanceState != null) {
            val pendingDirs = savedInstanceState.getStringArrayList(KEY_PENDING_DIRS)

            if (pendingDirs != null) {
                dirs =
                    MusicDirs(
                        pendingDirs.mapNotNull { Directory.fromDocumentUri(storageManager, it) },
                        savedInstanceState.getBoolean(KEY_PENDING_MODE))
            }
        }

        dirAdapter.addAll(dirs.dirs)
        requireBinding().dirsEmpty.isVisible = dirs.dirs.isEmpty()

        binding.folderModeGroup.apply {
            check(
                if (dirs.shouldInclude) {
                    R.id.dirs_mode_include
                } else {
                    R.id.dirs_mode_exclude
                })

            updateMode()
            addOnButtonCheckedListener { _, _, _ -> updateMode() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(
            KEY_PENDING_DIRS, ArrayList(dirAdapter.dirs.map { it.toString() }))
        outState.putBoolean(KEY_PENDING_MODE, isInclude(requireBinding()))
    }

    override fun onDestroyBinding(binding: DialogMusicDirsBinding) {
        super.onDestroyBinding(binding)
        binding.dirsRecycler.adapter = null
    }

    override fun onRemoveDirectory(dir: Directory) {
        dirAdapter.remove(dir)
        requireBinding().dirsEmpty.isVisible = dirAdapter.dirs.isEmpty()
    }

    private fun addDocTreePath(uri: Uri?) {
        if (uri == null) {
            // A null URI means that the user left the file picker without picking a directory
            logD("No URI given (user closed the dialog)")
            return
        }

        val dir = parseExcludedUri(uri)
        if (dir != null) {
            dirAdapter.add(dir)
            requireBinding().dirsEmpty.isVisible = false
        } else {
            requireContext().showToast(R.string.err_bad_dir)
        }
    }

    private fun parseExcludedUri(uri: Uri): Directory? {
        // Turn the raw URI into a document tree URI
        val docUri =
            DocumentsContract.buildDocumentUriUsingTree(
                uri, DocumentsContract.getTreeDocumentId(uri))

        // Turn it into a semi-usable path
        val treeUri = DocumentsContract.getTreeDocumentId(docUri)

        // Parsing handles the rest
        return Directory.fromDocumentUri(storageManager, treeUri)
    }

    private fun updateMode() {
        val binding = requireBinding()
        if (isInclude(binding)) {
            binding.dirsModeDesc.setText(R.string.set_dirs_mode_include_desc)
        } else {
            binding.dirsModeDesc.setText(R.string.set_dirs_mode_exclude_desc)
        }
    }

    private fun isInclude(binding: DialogMusicDirsBinding) =
        binding.folderModeGroup.checkedButtonId == R.id.dirs_mode_include

    companion object {
        const val TAG = BuildConfig.APPLICATION_ID + ".tag.EXCLUDED"
        const val KEY_PENDING_DIRS = BuildConfig.APPLICATION_ID + ".key.PENDING_DIRS"
        const val KEY_PENDING_MODE = BuildConfig.APPLICATION_ID + ".key.SHOULD_INCLUDE"
    }
}
