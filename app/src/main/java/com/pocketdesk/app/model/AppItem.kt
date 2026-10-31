package com.pocketdesk.app.model

data class AppItem(
    val id: String,
    val name: String,
    val exec: String,
    val iconName: String,
    val iconPath: String? = null,
    val comment: String = "",
    val categories: List<String> = emptyList()
) {
    companion object {
        // Official Mobian Touch-First Pre-installed Packages
        val MOBIAN_DEFAULT_APPS = listOf(
            AppItem(
                id = "dev.tchx84.Portfolio",
                name = "Portfolio",
                exec = "portfolio",
                iconName = "dev.tchx84.Portfolio",
                comment = "Modern touch-first mobile file manager"
            ),
            AppItem(
                id = "org.gnome.Console",
                name = "Console",
                exec = "kgx",
                iconName = "org.gnome.Console",
                comment = "Adaptive GTK4 mobile terminal emulator"
            ),
            AppItem(
                id = "org.gnome.Epiphany",
                name = "Web",
                exec = "epiphany",
                iconName = "org.gnome.Epiphany",
                comment = "Touch-scrolling WebKitGTK browser"
            ),
            AppItem(
                id = "org.gnome.Papers",
                name = "Papers",
                exec = "papers",
                iconName = "org.gnome.Papers",
                comment = "Libadwaita PDF and document viewer"
            ),
            AppItem(
                id = "org.gnome.TextEditor",
                name = "Text Editor",
                exec = "gnome-text-editor",
                iconName = "org.gnome.TextEditor",
                comment = "GTK4 adaptive text and code editor"
            ),
            AppItem(
                id = "org.gnome.FileRoller",
                name = "File Roller",
                exec = "file-roller",
                iconName = "org.gnome.FileRoller",
                comment = "Archive and compression manager"
            ),
            AppItem(
                id = "code",
                name = "Visual Studio Code",
                exec = "code --no-sandbox",
                iconName = "com.visualstudio.code",
                comment = "Full desktop IDE with mobile scaling"
            )
        )
    }
}
