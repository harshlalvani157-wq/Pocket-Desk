#!/usr/bin/env bash
# ==============================================================================
# POCKETDESK ROOTFS BOOTSTRAP SCRIPT
# Architecture: ARM64 (aarch64) | Base: Debian 13 (Trixie) Mobian-style
# Target: Rootless PRoot deployment for Android
# ==============================================================================

set -euo pipefail

TARGET_DIR="${1:-$HOME/debian-rootfs}"
CACHE_DIR="${TARGET_DIR}_cache"
DEBIAN_VERSION="trixie"
ROOTFS_URL="https://images.linuxcontainers.org/images/debian/trixie/arm64/default"

echo "=========================================================="
echo "  POCKETDESK: Debian 13 (Trixie) ARM64 Rootfs Setup"
echo "  Target Destination: ${TARGET_DIR}"
echo "=========================================================="

mkdir -p "${TARGET_DIR}"
mkdir -p "${CACHE_DIR}"

# Step 1: Download Debian 13 Minimal Rootfs if not already present
TARBALL="${CACHE_DIR}/debian-13-arm64-rootfs.tar.xz"

if [ ! -f "${TARBALL}" ]; then
    echo "[1/5] Fetching Debian 13 ARM64 rootfs archive..."
    # Attempt download from official LXC mirror or Debian rootfs archive
    LATEST_BUILD=$(curl -sSL "${ROOTFS_URL}/" | grep -oE '[0-9]{8}_[0-9]{2}:[0-9]{2}' | tail -n 1 || true)
    
    if [ -n "${LATEST_BUILD}" ]; then
        DOWNLOAD_URL="${ROOTFS_URL}/${LATEST_BUILD}/rootfs.tar.xz"
        echo "Found latest build: ${DOWNLOAD_URL}"
        curl -L --progress-bar -o "${TARBALL}" "${DOWNLOAD_URL}" || rm -f "${TARBALL}"
    fi

    # Fallback mirror if needed
    if [ ! -f "${TARBALL}" ]; then
        echo "Trying fallback CDN mirror..."
        FALLBACK_URL="https://github.com/termux/proot-distro/releases/download/v4.20.0/debian-trixie-aarch64-pd-v4.20.0.tar.xz"
        curl -L --progress-bar -o "${TARBALL}" "${FALLBACK_URL}"
    fi
else
    echo "[1/5] Found cached rootfs tarball at ${TARBALL}"
fi

# Step 2: Extract rootfs safely
echo "[2/5] Extracting rootfs into ${TARGET_DIR}..."
tar -xJf "${TARBALL}" -C "${TARGET_DIR}" --exclude='dev' || true

# Step 3: Configure network DNS & APT repositories
echo "[3/5] Configuring APT repositories for Debian 13 (Trixie)..."
mkdir -p "${TARGET_DIR}/etc/apt"
cat << 'EOF' > "${TARGET_DIR}/etc/resolv.conf"
nameserver 1.1.1.1
nameserver 8.8.8.8
EOF

cat << 'EOF' > "${TARGET_DIR}/etc/apt/sources.list"
deb http://deb.debian.org/debian trixie main contrib non-free non-free-firmware
deb http://deb.debian.org/debian-security trixie-security main contrib non-free non-free-firmware
deb http://deb.debian.org/debian trixie-updates main contrib non-free non-free-firmware
EOF

# Prevent daemons from failing during install inside PRoot
cat << 'EOF' > "${TARGET_DIR}/usr/sbin/policy-rc.d"
#!/bin/sh
exit 101
EOF
chmod +x "${TARGET_DIR}/usr/sbin/policy-rc.d"

# Step 4: Install Mobian / GNOME Mobile applications and dependencies
echo "[4/5] Setting up guest installation script..."

cat << 'EOF' > "${TARGET_DIR}/tmp/install_mobian_packages.sh"
#!/bin/sh
set -e
export DEBIAN_FRONTEND=noninteractive
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

apt-get update

# Core touch desktop libraries, fonts, and icons
apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    gnupg \
    locales \
    fonts-cantarell \
    fonts-noto \
    fonts-dejavu \
    adwaita-icon-theme \
    libadwaita-1-0 \
    gtk-4-examples \
    dbus-x11 \
    pulseaudio-utils

# Generate UTF-8 locale
echo "en_US.UTF-8 UTF-8" > /etc/locale.gen
locale-gen

# 1. Console (gnome-console / kgx)
echo "Installing GNOME Console..."
apt-get install -y --no-install-recommends gnome-console || apt-get install -y --no-install-recommends kgx

# 2. Web (epiphany-browser)
echo "Installing Epiphany Web Browser..."
apt-get install -y --no-install-recommends epiphany-browser

# 3. Text Editor (gnome-text-editor)
echo "Installing GNOME Text Editor..."
apt-get install -y --no-install-recommends gnome-text-editor

# 4. File Roller (Archive manager)
echo "Installing File Roller..."
apt-get install -y --no-install-recommends file-roller

# 5. Papers (Document / PDF viewer)
echo "Installing Papers / Evince Document Viewer..."
apt-get install -y --no-install-recommends papers || apt-get install -y --no-install-recommends evince

# 6. Squeekboard (Mobian touch-native virtual keyboard)
echo "Installing Squeekboard..."
apt-get install -y --no-install-recommends squeekboard || true

# 7. Portfolio (dev.tchx84.Portfolio touch file manager)
echo "Configuring Portfolio Touch File Manager..."
apt-get install -y --no-install-recommends python3 python3-gi gir1.2-adw-1 gir1.2-gtk-4.0 || true

# 8. VS Code ARM64 repository
echo "Configuring VS Code repository..."
curl -sSL https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor -o /etc/apt/trusted.gpg.d/packages.microsoft.gpg || true
echo "deb [arch=arm64] https://packages.microsoft.com/repos/code stable main" > /etc/apt/sources.list.d/vscode.list
apt-get update && apt-get install -y --no-install-recommends code || true

apt-get clean
rm -rf /var/lib/apt/lists/*
EOF

chmod +x "${TARGET_DIR}/tmp/install_mobian_packages.sh"

echo "Guest setup script created at /tmp/install_mobian_packages.sh"

# Step 5: Configure GTK3/4 touch CSS and dark Adwaita theme
echo "[5/5] Configuring GTK3/4 touch CSS & Adwaita Dark styling..."

USER_HOME="${TARGET_DIR}/root"
mkdir -p "${USER_HOME}/.config/gtk-3.0"
mkdir -p "${USER_HOME}/.config/gtk-4.0"

# Touch-friendly GTK CSS: enlarged touch targets, wide scrollbars, finger-friendly controls
cat << 'EOF' > "${USER_HOME}/.config/gtk-4.0/gtk.css"
/* PocketDesk GTK4 Touch Optimizations */
scrollbar {
    min-width: 16px;
    min-height: 16px;
}
scrollbar slider {
    min-width: 12px;
    min-height: 12px;
    border-radius: 8px;
    background-color: #3584E4;
}
button {
    min-height: 44px;
    min-width: 44px;
    padding: 8px 14px;
    border-radius: 12px;
}
entry {
    min-height: 42px;
    padding: 6px 12px;
    border-radius: 10px;
}
headerbar {
    min-height: 52px;
    padding: 0 8px;
}
headerbar button {
    min-height: 38px;
    min-width: 38px;
}
EOF

cp "${USER_HOME}/.config/gtk-4.0/gtk.css" "${USER_HOME}/.config/gtk-3.0/gtk.css"

# GTK Settings: Adwaita Dark
cat << 'EOF' > "${USER_HOME}/.config/gtk-3.0/settings.ini"
[Settings]
gtk-theme-name = Adwaita-dark
gtk-application-prefer-dark-theme = true
gtk-icon-theme-name = Adwaita
gtk-font-name = Cantarell 11
gtk-cursor-theme-name = Adwaita
gtk-cursor-theme-size = 24
EOF

cat << 'EOF' > "${USER_HOME}/.config/gtk-4.0/settings.ini"
[Settings]
gtk-theme-name = Adwaita-dark
gtk-application-prefer-dark-theme = true
gtk-icon-theme-name = Adwaita
gtk-font-name = Cantarell 11
gtk-cursor-theme-name = Adwaita
gtk-cursor-theme-size = 24
EOF

# Mobile Shell Environment Profile
mkdir -p "${TARGET_DIR}/etc/profile.d"
cat << 'EOF' > "${TARGET_DIR}/etc/profile.d/pocketdesk.sh"
# PocketDesk Mobian Mobile Environment
export DISPLAY=:0
export WAYLAND_DISPLAY=wayland-0
export XDG_RUNTIME_DIR=/tmp
export GDK_SCALE=2
export GDK_DPI_SCALE=1
export XFT_DPI=192
export GTK_THEME=Adwaita:dark
export QT_SCALE_FACTOR=2
export PULSE_SERVER=127.0.0.1:4713
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
EOF

chmod +x "${TARGET_DIR}/etc/profile.d/pocketdesk.sh"

echo "=========================================================="
echo "  POCKETDESK ROOTFS BOOTSTRAP READY!"
echo "  Rootfs Path: ${TARGET_DIR}"
echo "  Run inside PRoot: /tmp/install_mobian_packages.sh"
echo "=========================================================="
