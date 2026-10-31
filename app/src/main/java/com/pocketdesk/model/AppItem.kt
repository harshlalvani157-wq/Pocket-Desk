package com.pocketdesk.model

data class AppItem(
    val id: String,
    val name: String,
    val exec: String,
    val iconName: String,
    val iconPath: String? = null,
    val comment: String = "",
    val categories: List<String> = emptyList(),
    val isDefaultFallback: Boolean = false
) {
    companion object {
        // 7 Pre-installed Official Mobian / GNOME Mobile applications
        val DEFAULT_MOBIAN_APPS = listOf(
            AppItem(
                id = "dev.tchx84.Portfolio",
                name = "Portfolio",
                exec = "portfolio %U",
                iconName = "dev.tchx84.Portfolio",
                comment = "Modern touch-first mobile file manager",
                categories = listOf("Utility", "FileManager"),
                isDefaultFallback = true
            ),
            AppItem(
                id = "org.gnome.Console",
                name = "Console",
                exec = "kgx",
                iconName = "org.gnome.Console",
                comment = "Adaptive GTK4 terminal emulator",
                categories = listOf("System", "TerminalEmulator"),
                isDefaultFallback = true
            ),
            AppItem(
                id = "org.gnome.Epiphany",
                name = "Web",
                exec = "epiphany",
                iconName = "org.gnome.Epiphany",
                comment = "Touch-scrolling WebKitGTK browser",
                categories = listOf("Network", "WebBrowser"),
                isDefaultFallback = true
            ),
            AppItem(
                id = "org.gnome.Papers",
                name = "Papers",
                exec = "papers %U",
                iconName = "org.gnome.Papers",
                comment = "Modern Libadwaita PDF and document viewer",
                categories = listOf("Office", "Viewer"),
                isDefaultFallback = true
            ),
            AppItem(
                id = "org.gnome.TextEditor",
                name = "Text Editor",
                exec = "gnome-text-editor %U",
                iconName = "org.gnome.TextEditor",
                comment = "GTK4 adaptive text and code editor",
                categories = listOf("Utility", "TextEditor"),
                isDefaultFallback = true
            ),
            AppItem(
                id = "org.gnome.FileRoller",
                name = "File Roller",
                exec = "file-roller %U",
                iconName = "org.gnome.FileRoller",
                comment = "Archive and compression manager",
                categories = listOf("Utility", "Archiving"),
                isDefaultFallback = true
            ),
            AppItem(
                id = "code",
                name = "VS Code",
                exec = "code --no-sandbox",
                iconName = "com.visualstudio.code",
                comment = "Full desktop IDE with mobile scaling",
                categories = listOf("Development", "IDE"),
                isDefaultFallback = true
            )
        )
    }
}
