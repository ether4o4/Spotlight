package com.neversoft.spotlight.desktop.win

import com.neversoft.spotlight.desktop.model.LaunchAction
import java.awt.Desktop
import java.io.File

/**
 * Opens a clicked result with the OS: files/folders/shortcuts go through the
 * shell's default handler; note apps that can't deep-link to a single note are
 * simply brought up (Sticky Notes shows all notes, Notepad restores its tabs).
 */
object ResultLauncher {

    fun launch(action: LaunchAction) {
        runCatching {
            when (action) {
                is LaunchAction.OpenPath -> Desktop.getDesktop().open(File(action.path))
                LaunchAction.OpenStickyNotes -> ProcessBuilder(
                    "explorer.exe",
                    "shell:AppsFolder\\Microsoft.MicrosoftStickyNotes_8wekyb3d8bbwe!App",
                ).start()
                LaunchAction.OpenNotepad -> ProcessBuilder("notepad.exe").start()
            }
        }
    }
}
