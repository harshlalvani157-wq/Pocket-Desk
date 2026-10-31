# PocketDesk — Native Android Mobile Shell with Embedded Debian 13 (Mobian-Style) Linux Runtime

**PocketDesk** is an open-source, production-ready Android mobile shell and rootless Linux container runtime inspired by LinDesk. It brings native Mobian / GNOME Mobile touch applications to modern Android devices without requiring root access.

---

## 🏛️ Core Architectural Principles: Dual-Layer Hybrid Architecture

PocketDesk avoids heavy, crashing desktop sessions (GNOME Shell / Phosh) inside PRoot caused by missing systemd and DRM-KMS drivers. Instead, it utilizes a dual-layer hybrid architecture:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        HOST LAYER (Android 14+)                        │
│   • Jetpack Compose & Material 3 (Libadwaita Dark Theme)               │
│   • Home Screen Launcher Grid (Real-time app search & .desktop parser) │
│   • Multi-Tasking Carousel (HorizontalPager, live window snapshots)    │
│   • Floating Keyboard Ribbon (Esc, Tab, /, -, Ctrl, Alt, Paste, D-Pad) │
│   • Quick Controls Modal Sheet (Auto-DPI, Orientation, Keyboard Switch)│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                       JNI & Unix Domain Sockets
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                    BRIDGE LAYER (C++ NDK / EGL / GLES2)                │
│   • Display Bridge (libpocketdesk_bridge.so & liblindesk_display.so)   │
│   • Rootless Display Server: listens on abstract socket                │
│   • Hardware Accelerated Rendering: GLES 2.0 texture mapping at 60 FPS │
│   • Process Runner & SIGTERM Dispatcher: rootless PRoot fork/exec      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                         Rootless PRoot Isolation
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                     GUEST LAYER (Debian 13 ARM64)                      │
│   • Official Mobian / GNOME Mobile touch-first applications            │
│   • Portfolio (Dev.tchx84.Portfolio), Console (kgx), Web (Epiphany)    │
│   • Papers, Text Editor, File Roller, VS Code (ARM64)                  │
│   • Custom Touch GTK3/4 CSS (Enlarged hitboxes & kinetic scrolling)    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 📦 Pre-Installed Mobian / GNOME Mobile Applications

1. **Portfolio** (`dev.tchx84.Portfolio`): Modern touch-first mobile file manager.
2. **Console** (`gnome-console` / `kgx`): Adaptive GTK4 terminal emulator.
3. **Web** (`epiphany-browser`): Touch-scrolling WebKitGTK browser.
4. **Papers** (`org.gnome.Papers`): Modern Libadwaita PDF and document viewer.
5. **Text Editor** (`gnome-text-editor`): GTK4 adaptive text and code editor.
6. **File Roller** (`file-roller`): Archive and compression manager.
7. **VS Code** (`code` / `code-oss` arm64): Full desktop IDE with mobile scaling.

---

## 📱 Android Host UI Specification

### 1. Bottom Navigation Bar (Persistent Overlay)
Always visible at the bottom of the screen with 4 items:
* `[< Back]`: Injects `Esc` or window-back events into the active Linux app.
* `[Desktop]`: Hides the active Linux window and displays the Android App Launcher Grid.
* `[Tasks]`: Opens the Recent Apps Multi-Tasking Carousel.
* `[Tools]`: Opens a modal bottom sheet with quick toggles (DPI, Orientation, Keyboard switch).

### 2. App Launcher Grid ("Desktop" Tab)
* Parses `/usr/share/applications/*.desktop` inside the rootfs:
  * Extracts `Name`, `Exec`, and `Icon` (resolves PNG/SVG from `/usr/share/icons/hicolor/`).
  * If the rootfs is not yet uncompressed, displays pre-configured fallback tiles for the 7 default apps.
* Top search input bar: `🔍 Search apps...`.
  * Tapping the search bar automatically requests focus and opens the keyboard.
  * Real-time filtering of the app grid as the user types.
* Tapping an app:
  * Shows a clean Android splash screen (app icon + circular progress spinner).
  * Executes the background command: `exec <binary> &`.
  * Switches screen visibility from the App Grid to the active Linux `SurfaceView`.

### 3. Recent Apps Switcher ("Tasks" Tab)
* Horizontal card deck (`HorizontalPager`):
  * Header label: `Swipe to switch · Tap to open · Swipe up to close`.
  * Card indicator: `N / Total · App Name`.
  * Live or cached thumbnail snapshots of open Linux application windows.
  * App icon centered at the bottom of each card.
* **Interactions**:
  * **Tap a card**: Focuses that window ID, hides the Tasks screen, and maps that window to full screen.
  * **Swipe up on a card**: Triggers card-exit animation and immediately sends a termination signal to the Linux process: `kill(pid, SIGTERM)`.

### 4. Keyboard & Input Handling
* Floating accessory toolbar above the keyboard with keys: `Esc`, `/`, `-`, `Tab`, `Ctrl`, `Alt`, `Paste`, `Combos`, arrows (`←`, `↑`, `↓`, `→`).
* **Settings Toggle**:
  * Option 1: `Android Host Keyboard` (uses native Android Gboard/touch IME).
  * Option 2: `Linux Virtual Keyboard` (spawns Linux `squeekboard` or `onboard`).

### 5. Auto-DPI & Responsive Screen Scaling
* Detects Android screen density via `LocalConfiguration.current` / `DisplayMetrics`.
* Calculates optimal scaling automatically so UI elements are finger-friendly:
  ```kotlin
  val scaleFactor = (displayMetrics.densityDpi / 160f).coerceIn(1.5f, 3.0f)
  val xftDpi = (96 * scaleFactor).toInt()
  ```
* Exports environment variables to all Linux processes:
  * `GDK_SCALE=2` (or calculated integer)
  * `GDK_DPI_SCALE=1`
  * `XFT_DPI=<calculated_xft_dpi>`
  * `GTK_THEME=Adwaita:dark`
  * `QT_SCALE_FACTOR=2`

---

## 🛠️ Project Structure

```
pocketdesk/
├── build.gradle.kts                # Project-level Gradle build file
├── settings.gradle.kts             # Gradle settings with repositories
├── gradle.properties               # Memory & compiler optimization flags
├── setup_rootfs.sh                 # Debian 13 Mobian bootstrap script
├── gradle/
│   └── libs.versions.toml          # Centralized version catalog
└── app/
    ├── build.gradle.kts            # App-level build file (Compose, NDK, M3)
    └── src/main/
        ├── AndroidManifest.xml     # App permissions, Activities, and FGS
        ├── cpp/
        │   ├── CMakeLists.txt      # NDK CMake build configuration
        │   ├── bridge_common.h     # Native state structs and macros
        │   ├── display_bridge.cpp  # EGL/GLES2 surface presenter & socket server
        │   └── runner_bridge.cpp   # PRoot fork/exec & SIGTERM dispatcher
        ├── java/com/pocketdesk/
        │   ├── MainActivity.kt     # Onboarding and launch entrypoint
        │   ├── DesktopActivity.kt  # Main shell activity & state machine
        │   ├── PocketDeskApp.kt    # Application singleton & notification channel
        │   ├── model/
        │   │   ├── AppItem.kt      # Desktop application data model
        │   │   └── RunningTask.kt  # Multi-tasking task data model
        │   ├── parser/
        │   │   └── DesktopEntryParser.kt # XDG .desktop & icon parser
        │   ├── service/
        │   │   └── LinuxBridgeService.kt # Foreground service & JNI bridge
        │   ├── theme/
        │   │   ├── Color.kt        # Mobian Libadwaita dark palette
        │   │   ├── Theme.kt        # Material3 theme definition
        │   │   └── Type.kt         # Typography configuration
        │   └── ui/
        │       ├── AppLauncherScreen.kt  # App grid, search bar, and splash
        │       ├── BottomNavigationBar.kt# Persistent 4-item navigation bar
        │       ├── KeyboardRibbon.kt     # Floating keyboard accessory keys
        │       ├── LinuxSurfaceView.kt   # GLES2 SurfaceView container
        │       ├── TaskSwitcherScreen.kt # Recent apps card switcher
        │       └── ToolsBottomSheet.kt   # Quick controls modal sheet
        └── res/
            ├── values/
            │   ├── colors.xml
            │   ├── strings.xml
            │   └── themes.xml
            └── xml/
                ├── backup_rules.xml
                └── data_extraction_rules.xml
```

---

## 🚀 Building & Running

### Prerequisites
- Android SDK 34
- Android NDK 26+ (e.g. `27.1.12297006`)
- Java 17+
- CMake 3.22.1+

### Build APK
```bash
cd ~/projects/pocketdesk
./gradlew assembleDebug
```

### Bootstrap Rootfs
Run the bootstrap script on device or host:
```bash
chmod +x setup_rootfs.sh
./setup_rootfs.sh /data/data/com.pocketdesk/files/debian-rootfs
```
