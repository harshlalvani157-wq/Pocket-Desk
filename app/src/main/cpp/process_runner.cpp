#include "process_runner.h"

#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/wait.h>
#include <cstdlib>
#include <cstring>

#define TAG "PocketDeskRunner"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

ProcessRunner& ProcessRunner::getInstance() {
    static ProcessRunner s_instance;
    return s_instance;
}

pid_t ProcessRunner::launchProcess(
    const std::string& command,
    const std::string& rootfsPath,
    const std::vector<std::string>& extraEnv,
    int scale,
    int xftDpi
) {
    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork() failed: %s", strerror(errno));
        return -1;
    }

    if (pid == 0) {
        // Child Process
        int devNull = open("/dev/null", O_RDWR);
        if (devNull >= 0) {
            dup2(devNull, STDIN_FILENO);
            close(devNull);
        }

        // Set touch-optimized Mobian environment variables
        setenv("DISPLAY", ":0", 1);
        setenv("GDK_BACKEND", "x11", 1);
        setenv("WAYLAND_DISPLAY", "wayland-0", 1);
        setenv("XDG_RUNTIME_DIR", "/tmp", 1);
        setenv("GDK_SCALE", std::to_string(scale).c_str(), 1);
        setenv("GDK_DPI_SCALE", "1", 1);
        setenv("XFT_DPI", std::to_string(xftDpi).c_str(), 1);
        setenv("GTK_THEME", "Adwaita:dark", 1);
        setenv("QT_SCALE_FACTOR", std::to_string(scale).c_str(), 1);
        setenv("PULSE_SERVER", "127.0.0.1:4713", 1);
        setenv("LANG", "C.UTF-8", 1);
        setenv("LC_ALL", "C.UTF-8", 1);

        for (const auto& ev : extraEnv) {
            putenv(strdup(ev.c_str()));
        }

        // Check if PRoot binary exists in rootfs or lib dir
        std::string prootBin = rootfsPath + "/usr/bin/proot";
        if (access(prootBin.c_str(), X_OK) != 0) {
            prootBin = "/data/data/com.pocketdesk.app/lib/libproot.so";
        }

        std::vector<const char*> argv;
        if (access(prootBin.c_str(), X_OK) == 0 && access(rootfsPath.c_str(), F_OK) == 0) {
            argv.push_back(prootBin.c_str());
            argv.push_back("-r");
            argv.push_back(rootfsPath.c_str());
            argv.push_back("-0");
            argv.push_back("-b");
            argv.push_back("/dev");
            argv.push_back("-b");
            argv.push_back("/proc");
            argv.push_back("-b");
            argv.push_back("/sys");
            argv.push_back("-b");
            argv.push_back("/sdcard");
            argv.push_back("-w");
            argv.push_back("/root");
            argv.push_back("/bin/sh");
            argv.push_back("-c");
            argv.push_back(command.c_str());
            argv.push_back(nullptr);

            execv(prootBin.c_str(), const_cast<char* const*>(argv.data()));
        } else {
            // Direct /system/bin/sh execution fallback
            argv.push_back("/system/bin/sh");
            argv.push_back("-c");
            argv.push_back(command.c_str());
            argv.push_back(nullptr);

            execv("/system/bin/sh", const_cast<char* const*>(argv.data()));
        }

        LOGE("execv failed: %s", strerror(errno));
        _exit(127);
    }

    LOGI("Process launched with PID %d: %s", pid, command.c_str());
    registerWindow(pid, pid, command);
    return pid;
}

int ProcessRunner::terminateProcess(pid_t pid) {
    if (pid <= 0) return -1;
    LOGI("Dispatching SIGTERM to process PID %d", pid);
    int res = kill(pid, SIGTERM);
    unregisterWindow(pid);
    return res;
}

bool ProcessRunner::isAlive(pid_t pid) {
    if (pid <= 0) return false;
    return (kill(pid, 0) == 0);
}

int ProcessRunner::reapZombies() {
    int status = 0;
    pid_t reaped = 0;
    int count = 0;
    while ((reaped = waitpid(-1, &status, WNOHANG)) > 0) {
        unregisterWindow(reaped);
        count++;
    }
    return count;
}

void ProcessRunner::registerWindow(uint32_t windowId, pid_t pid, const std::string& title) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_windows[windowId] = WindowEntry{windowId, pid, title};
}

void ProcessRunner::unregisterWindow(uint32_t windowId) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_windows.erase(windowId);
}

std::vector<WindowEntry> ProcessRunner::getActiveWindows() {
    std::lock_guard<std::mutex> lock(m_mutex);
    std::vector<WindowEntry> list;
    for (const auto& pair : m_windows) {
        list.push_back(pair.second);
    }
    return list;
}
