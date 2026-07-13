package com.neversoft.spotlight.desktop.win

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolves the well-known Windows locations the providers read from. The env
 * lookup is injectable so tests can point every location at temp fixtures and
 * run on any OS.
 */
class WindowsPaths(private val env: (String) -> String? = System::getenv) {

    val userHome: Path? =
        (env("USERPROFILE") ?: System.getProperty("user.home"))?.let(Paths::get)

    private val localAppData: Path? =
        env("LOCALAPPDATA")?.let(Paths::get) ?: userHome?.resolve("AppData/Local")

    private val roamingAppData: Path? =
        env("APPDATA")?.let(Paths::get) ?: userHome?.resolve("AppData/Roaming")

    private val programData: Path? = env("ProgramData")?.let(Paths::get)

    /** Both Start Menu roots: all-users (ProgramData) and the current user's. */
    fun startMenuRoots(): List<Path> = listOfNotNull(
        programData?.resolve("Microsoft/Windows/Start Menu/Programs"),
        roamingAppData?.resolve("Microsoft/Windows/Start Menu/Programs"),
    ).filter { Files.isDirectory(it) }

    /** The Sticky Notes store — a plain SQLite database in the app's package dir. */
    fun stickyNotesDb(): Path? = localAppData
        ?.resolve("Packages/Microsoft.MicrosoftStickyNotes_8wekyb3d8bbwe/LocalState/plum.sqlite")
        ?.takeIf { Files.isRegularFile(it) }

    /** Windows 11 Notepad's tab-state dir — holds every open tab, saved or not. */
    fun notepadTabStateDir(): Path? = localAppData
        ?.resolve("Packages/Microsoft.WindowsNotepad_8wekyb3d8bbwe/LocalState/TabState")
        ?.takeIf { Files.isDirectory(it) }

    /** Roots for the general file index: the whole user profile. */
    fun indexRoots(): List<Path> = listOfNotNull(userHome).filter { Files.isDirectory(it) }

    /** Where "Downloads" lives, for the Downloads sub-filter. */
    fun downloadsDir(): Path? = userHome?.resolve("Downloads")

    /**
     * Roots scanned for text/markdown notes (deep content search). Kept to the
     * places people actually keep notes rather than the whole profile so every
     * file's content can be held in memory.
     */
    fun noteRoots(): List<Path> = listOfNotNull(
        userHome?.resolve("Documents"),
        userHome?.resolve("Desktop"),
        userHome?.resolve("Notes"),
    ).filter { Files.isDirectory(it) }
}
