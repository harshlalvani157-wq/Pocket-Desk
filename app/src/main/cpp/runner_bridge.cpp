#include "bridge_common.h"

#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <cstring>
#include <cstdlib>
#include <vector>
#include <string>

extern "C" {

JNIEXPORT jint JNICALL
Java_com_pocketdesk_service_LinuxBridgeService_nativeForkExec(
    JNIEnv* env,
    jobject thiz,
    jstring jProotBin,
    jstring jRootfsPath,
    jstring jCommand,
    jobjectArray jEnvVars,
    jstring jWorkDir
) {
    const char* prootBin = jProotBin ? env->GetStringUTFChars(jProotBin, nullptr) : nullptr;
    const char* rootfsPath = jRootfsPath ? env->GetStringUTFChars(jRootfsPath, nullptr) : nullptr;
    const char* command = jCommand ? env->GetStringUTFChars(jCommand, nullptr) : nullptr;
    const char* workDir = jWorkDir ? env->GetStringUTFChars(jWorkDir, nullptr) : "/root";

    std::vector<std::string> envStrings;
    if (jEnvVars) {
        jsize envCount = env->GetArrayLength(jEnvVars);
        for (jsize i = 0; i < envCount; ++i) {
            auto jStr = static_cast<jstring>(env->GetObjectArrayElement(jEnvVars, i));
            if (jStr) {
                const char* cStr = env->GetStringUTFChars(jStr, nullptr);
                if (cStr) {
                    envStrings.emplace_back(cStr);
                    env->ReleaseStringUTFChars(jStr, cStr);
                }
                env->DeleteLocalRef(jStr);
            }
        }
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork() failed: %s", strerror(errno));
        if (prootBin) env->ReleaseStringUTFChars(jProotBin, prootBin);
        if (rootfsPath) env->ReleaseStringUTFChars(jRootfsPath, rootfsPath);
        if (command) env->ReleaseStringUTFChars(jCommand, command);
        if (workDir) env->ReleaseStringUTFChars(jWorkDir, workDir);
        return -1;
    }

    if (pid == 0) {
        // Child process
        // Redirect stdout/stderr to logcat or null
        int devNull = open("/dev/null", O_RDWR);
        if (devNull >= 0) {
            dup2(devNull, STDIN_FILENO);
            close(devNull);
        }

        // Apply environment variables
        for (const auto& ev : envStrings) {
            putenv(strdup(ev.c_str()));
        }

        // Standard Linux environment fallback
        setenv("TERM", "xterm-256color", 0);
        setenv("HOME", workDir ? workDir : "/root", 1);
        setenv("USER", "root", 0);
        setenv("LOGNAME", "root", 0);
        setenv("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin", 0);

        // PRoot execution arguments
        std::vector<const char*> argv;
        if (prootBin && access(prootBin, X_OK) == 0 && rootfsPath) {
            argv.push_back(prootBin);
            argv.push_back("-r");
            argv.push_back(rootfsPath);
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
            argv.push_back(workDir ? workDir : "/root");
            argv.push_back("/bin/sh");
            argv.push_back("-c");
            argv.push_back(command ? command : "/bin/sh");
            argv.push_back(nullptr);

            execv(prootBin, const_cast<char* const*>(argv.data()));
        } else {
            // Direct /bin/sh fallback if proot is bundled elsewhere or testing
            argv.push_back("/system/bin/sh");
            argv.push_back("-c");
            argv.push_back(command ? command : "/system/bin/sh");
            argv.push_back(nullptr);

            execv("/system/bin/sh", const_cast<char* const*>(argv.data()));
        }

        // If execv returns, an error occurred
        LOGE("execv failed: %s", strerror(errno));
        _exit(127);
    }

    // Parent process
    LOGI("Successfully launched guest process with PID: %d (Command: %s)", pid, command ? command : "");

    if (prootBin) env->ReleaseStringUTFChars(jProotBin, prootBin);
    if (rootfsPath) env->ReleaseStringUTFChars(jRootfsPath, rootfsPath);
    if (command) env->ReleaseStringUTFChars(jCommand, command);
    if (workDir) env->ReleaseStringUTFChars(jWorkDir, workDir);

    return static_cast<jint>(pid);
}

JNIEXPORT jint JNICALL
Java_com_pocketdesk_service_LinuxBridgeService_nativeSendSignal(
    JNIEnv* env,
    jobject thiz,
    jint pid,
    jint signal
) {
    if (pid <= 0) return -1;
    int res = kill(static_cast<pid_t>(pid), signal);
    if (res != 0) {
        LOGW("kill(%d, %d) returned error: %s", pid, signal, strerror(errno));
    } else {
        LOGI("Dispatched signal %d to PID %d", signal, pid);
    }
    return res;
}

JNIEXPORT jboolean JNICALL
Java_com_pocketdesk_service_LinuxBridgeService_nativeIsProcessAlive(
    JNIEnv* env,
    jobject thiz,
    jint pid
) {
    if (pid <= 0) return JNI_FALSE;
    // Sending signal 0 checks for process existence without sending an actual signal
    int res = kill(static_cast<pid_t>(pid), 0);
    return (res == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_pocketdesk_service_LinuxBridgeService_nativeReapChildProcesses(
    JNIEnv* env,
    jobject thiz
) {
    int status = 0;
    pid_t reaped = 0;
    int count = 0;
    while ((reaped = waitpid(-1, &status, WNOHANG)) > 0) {
        LOGD("Reaped zombie child process PID: %d (status: %d)", reaped, status);
        count++;
    }
    return count;
}

} // extern "C"
