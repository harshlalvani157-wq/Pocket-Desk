#pragma once

#include <string>
#include <vector>
#include <mutex>
#include <map>
#include <sys/types.h>

struct WindowEntry {
    uint32_t windowId;
    pid_t pid;
    std::string title;
};

class ProcessRunner {
public:
    static ProcessRunner& getInstance();

    pid_t launchProcess(
        const std::string& command,
        const std::string& rootfsPath,
        const std::vector<std::string>& extraEnv,
        int scale = 2,
        int xftDpi = 192
    );

    int terminateProcess(pid_t pid);
    bool isAlive(pid_t pid);
    int reapZombies();

    void registerWindow(uint32_t windowId, pid_t pid, const std::string& title);
    void unregisterWindow(uint32_t windowId);
    std::vector<WindowEntry> getActiveWindows();

private:
    ProcessRunner() = default;
    ~ProcessRunner() = default;

    std::mutex m_mutex;
    std::map<uint32_t, WindowEntry> m_windows;
};
