#pragma once

#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/bitmap.h>

#include <EGL/egl.h>
#include <GLES2/gl2.h>

#include <string>
#include <vector>
#include <mutex>
#include <thread>
#include <atomic>

#define TAG "PocketDeskDisplay"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

class DisplayServer {
public:
    static DisplayServer& getInstance();

    bool init(const std::string& socketPath, int width, int height, int dpi);
    bool setSurface(JNIEnv* env, jobject jSurface);
    void resize(int width, int height, int dpi);
    void sendPointer(int action, float x, float y, int button);
    void sendKey(int keyCode, bool isDown, int modifiers);
    jobject captureSnapshotBitmap(JNIEnv* env);
    void stop();

private:
    DisplayServer() = default;
    ~DisplayServer();

    bool initEGL();
    void destroyEGL();
    void render();
    void socketServerLoop(std::string socketPath);

    ANativeWindow* m_window = nullptr;
    EGLDisplay m_display = EGL_NO_DISPLAY;
    EGLContext m_context = EGL_NO_CONTEXT;
    EGLSurface m_surface = EGL_NO_SURFACE;
    EGLConfig m_config = nullptr;

    GLuint m_program = 0;
    GLuint m_texture = 0;
    GLint m_posAttr = -1;
    GLint m_texAttr = -1;
    GLint m_samplerUniform = -1;

    int m_width = 1080;
    int m_height = 2400;
    int m_dpi = 160;

    std::mutex m_mutex;
    std::atomic<bool> m_running{false};
    int m_serverFd = -1;
    int m_clientFd = -1;
    std::thread m_thread;
};
