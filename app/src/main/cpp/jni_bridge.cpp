#include "display_server.h"
#include "process_runner.h"

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_pocketdesk_app_runtime_NativeBridge_initDisplayServer(
    JNIEnv* env,
    jclass clazz,
    jstring jSocketPath,
    jint width,
    jint height,
    jint dpi
) {
    const char* path = env->GetStringUTFChars(jSocketPath, nullptr);
    std::string socketPath = path ? path : "/tmp/.X11-unix/X0";
    if (path) env->ReleaseStringUTFChars(jSocketPath, path);

    return DisplayServer::getInstance().init(socketPath, width, height, dpi) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pocketdesk_app_runtime_NativeBridge_setSurface(
    JNIEnv* env,
    jclass clazz,
    jobject jSurface
) {
    return DisplayServer::getInstance().setSurface(env, jSurface) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_pocketdesk_app_runtime_NativeBridge_resizeDisplay(
    JNIEnv* env,
    jclass clazz,
    jint width,
    jint height,
    jint dpi
) {
    DisplayServer::getInstance().resize(width, height, dpi);
}

JNIEXPORT void JNICALL
Java_com_pocketdesk_app_runtime_NativeBridge_sendPointerEvent(
    JNIEnv* env,
    jclass clazz,
    jint action,
    jfloat x,
    jfloat y,
    jint button
) {
    DisplayServer::getInstance().sendPointer(action, x, y, button);
}

JNIEXPORT void JNICALL
Java_com_pocketdesk_app_runtime_NativeBridge_sendKeyEvent(
    JNIEnv* env,
    jclass clazz,
    jint keyCode,
    jboolean isDown,
    jint modifiers
) {
    DisplayServer::getInstance().sendKey(keyCode, isDown, modifiers);
}

JNIEXPORT jobject JNICALL
Java_com_pocketdesk_app_runtime_NativeBridge_captureSnapshot(
    JNIEnv* env,
    jclass clazz
) {
    return DisplayServer::getInstance().captureSnapshotBitmap(env);
}

JNIEXPORT void JNICALL
Java_com_pocketdesk_app_runtime_NativeBridge_stopDisplayServer(
    JNIEnv* env,
    jclass clazz
) {
    DisplayServer::getInstance().stop();
}

JNIEXPORT jint JNICALL
Java_com_pocketdesk_app_runtime_NativeBridge_launchProcess(
    JNIEnv* env,
    jclass clazz,
    jstring jCommand,
    jstring jRootfsPath,
    jobjectArray jEnvVars,
    jint scale,
    jint xftDpi
) {
    const char* cmd = jCommand ? env->GetStringUTFChars(jCommand, nullptr) : "";
    const char* rootfs = jRootfsPath ? env->GetStringUTFChars(jRootfsPath, nullptr) : "";

    std::vector<std::string> extraEnv;
    if (jEnvVars) {
        jsize count = env->GetArrayLength(jEnvVars);
        for (jsize i = 0; i < count; ++i) {
            auto jStr = static_cast<jstring>(env->GetObjectArrayElement(jEnvVars, i));
            if (jStr) {
                const char* s = env->GetStringUTFChars(jStr, nullptr);
                if (s) {
                    extraEnv.emplace_back(s);
                    env->ReleaseStringUTFChars(jStr, s);
                }
                env->DeleteLocalRef(jStr);
            }
        }
    }

    pid_t pid = ProcessRunner::getInstance().launchProcess(cmd, rootfs, extraEnv, scale, xftDpi);

    if (cmd && jCommand) env->ReleaseStringUTFChars(jCommand, cmd);
    if (rootfs && jRootfsPath) env->ReleaseStringUTFChars(jRootfsPath, rootfs);

    return static_cast<jint>(pid);
}

JNIEXPORT jint JNICALL
Java_com_pocketdesk_app_runtime_NativeBridge_terminateProcess(
    JNIEnv* env,
    jclass clazz,
    jint pid
) {
    return ProcessRunner::getInstance().terminateProcess(static_cast<pid_t>(pid));
}

JNIEXPORT jboolean JNICALL
Java_com_pocketdesk_app_runtime_NativeBridge_isProcessAlive(
    JNIEnv* env,
    jclass clazz,
    jint pid
) {
    return ProcessRunner::getInstance().isAlive(static_cast<pid_t>(pid)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_pocketdesk_app_runtime_NativeBridge_reapChildProcesses(
    JNIEnv* env,
    jclass clazz
) {
    return ProcessRunner::getInstance().reapZombies();
}

} // extern "C"
