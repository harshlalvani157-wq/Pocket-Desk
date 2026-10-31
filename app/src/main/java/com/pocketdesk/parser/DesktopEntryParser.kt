package com.pocketdesk.parser

import com.pocketdesk.model.AppItem
import java.io.File

object DesktopEntryParser {

    fun parseInstalledApps(rootfsPath: String): List<AppItem> {
        val rootfsDir = File(rootfsPath)
        if (!rootfsDir.exists() || !rootfsDir.isDirectory) {
            return AppItem.DEFAULT_MOBIAN_APPS
        }

        val appDirs = listOf(
            File(rootfsDir, "usr/share/applications"),
            File(rootfsDir, "usr/local/share/applications"),
            File(rootfsDir, "var/lib/flatpak/exports/share/applications")
        )

        val apps = mutableListOf<AppItem>()
        val seenExecs = mutableSetOf<String>()

        for (dir in appDirs) {
            if (!dir.exists() || !dir.isDirectory) continue

            val desktopFiles = dir.listFiles { file -> file.extension == "desktop" } ?: continue

            for (file in desktopFiles) {
                try {
                    val app = parseSingleDesktopFile(file, rootfsDir)
                    if (app != null && seenExecs.add(app.exec.split(" ").firstOrNull() ?: app.exec)) {
                        apps.add(app)
                    }
                } catch (e: Exception) {
                    // Ignore malformed desktop entries
                }
            }
        }

        if (apps.isEmpty()) {
            return AppItem.DEFAULT_MOBIAN_APPS
        }

        return apps.sortedBy { it.name.lowercase() }
    }

    private fun parseSingleDesktopFile(file: File, rootfsDir: File): AppItem? {
        var inDesktopEntry = false
        var name: String? = null
        var exec: String? = null
        var icon: String? = null
        var comment = ""
        val categories = mutableListOf<String>()
        var noDisplay = false
        var hidden = false

        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inDesktopEntry = trimmed == "[Desktop Entry]"
                return@forEachLine
            }

            if (!inDesktopEntry || trimmed.startsWith("#") || !trimmed.contains("=")) {
                return@forEachLine
            }

            val key = trimmed.substringBefore("=").trim()
            val value = trimmed.substringAfter("=").trim()

            when (key) {
                "Name" -> if (name == null) name = value
                "Exec" -> if (exec == null) exec = value
                "Icon" -> if (icon == null) icon = value
                "Comment" -> if (comment.isEmpty()) comment = value
                "NoDisplay" -> noDisplay = value.equals("true", ignoreCase = true)
                "Hidden" -> hidden = value.equals("true", ignoreCase = true)
                "Categories" -> {
                    categories.addAll(value.split(";").map { it.trim() }.filter { it.isNotEmpty() })
                }
            }
        }

        if (noDisplay || hidden || name.isNullOrBlank() || exec.isNullOrBlank()) {
            return null
        }

        val cleanExec = exec!!.replace(Regex("%[fFuUdDnNickvm]"), "").trim()
        val iconPath = icon?.let { resolveIconPath(it, rootfsDir) }

        return AppItem(
            id = file.nameWithoutExtension,
            name = name!!,
            exec = cleanExec,
            iconName = icon ?: "application-x-executable",
            iconPath = iconPath,
            comment = comment,
            categories = categories,
            isDefaultFallback = false
        )
    }

    private fun resolveIconPath(iconName: String, rootfsDir: File): String? {
        if (iconName.startsWith("/")) {
            val directFile = File(rootfsDir, iconName.removePrefix("/"))
            if (directFile.exists()) return directFile.absolutePath
        }

        val searchSubdirs = listOf(
            "usr/share/icons/hicolor/scalable/apps/$iconName.svg",
            "usr/share/icons/hicolor/symbolic/apps/$iconName-symbolic.svg",
            "usr/share/icons/hicolor/512x512/apps/$iconName.png",
            "usr/share/icons/hicolor/256x256/apps/$iconName.png",
            "usr/share/icons/hicolor/128x128/apps/$iconName.png",
            "usr/share/icons/hicolor/48x48/apps/$iconName.png",
            "usr/share/pixmaps/$iconName.png",
            "usr/share/pixmaps/$iconName.svg"
        )

        for (rel in searchSubdirs) {
            val candidate = File(rootfsDir, rel)
            if (candidate.exists()) {
                return candidate.absolutePath
            }
        }

        return null
    }
}
