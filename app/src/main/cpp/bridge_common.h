#pragma once

#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/bitmap.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <thread>
#include <atomic>

#define LOG_TAG "PocketDeskNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

struct DisplayContext {
    ANativeWindow* window = nullptr;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface surface = EGL_NO_SURFACE;
    EGLConfig config = nullptr;

    GLuint program = 0;
    GLuint textureId = 0;
    GLint posAttr = -1;
    GLint texAttr = -1;
    GLint samplerUniform = -1;

    int surfaceWidth = 1080;
    int surfaceHeight = 2400;
    int bufferWidth = 1080;
    int bufferHeight = 2400;
    int dpi = 160;

    std::vector<uint8_t> pixelBuffer;
    std::mutex renderMutex;
    std::atomic<bool> isRunning{false};
    int serverFd = -1;
    int clientFd = -1;
    std::thread serverThread;
};
