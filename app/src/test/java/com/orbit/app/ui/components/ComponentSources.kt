package com.orbit.app.ui.components

import java.io.File

/** Finds the main source tree from a unit test, whether Gradle runs it from the module or the repo root. */
internal fun mainSourceRoot(): File =
    listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }

internal fun mainKotlinSources(): List<File> =
    mainSourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
