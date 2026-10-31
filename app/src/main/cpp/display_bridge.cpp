#include "bridge_common.h"

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <cstring>
#include <cstdlib>

namespace {

const char* VERTEX_SHADER_SRC = R"(
    attribute vec4 a_position;
    attribute vec2 a_texCoord;
    varying vec2 uv;
    void main() {
        gl_Position = a_position;
        uv = a_texCoord;
    }
)";

const char* FRAGMENT_SHADER_SRC = R"(
    precision mediump float;
    varying vec2 uv;
    uniform sampler2D pixels;
    void main() {
        vec4 c = texture2D(pixels, uv);
        gl_FragColor = vec4(c.b, c.g, c.r, 1.0);
    }
)";

GLuint compileShader(GLenum type, const char* src) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);
    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetShaderInfoLog(shader, infoLen, nullptr, infoLog.data());
            LOGE("Shader compilation failed: %s", infoLog.data());
        }
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GLuint createProgram(const char* vertexSrc, const char* fragmentSrc) {
    GLuint vertexShader = compileShader(GL_VERTEX_SHADER, vertexSrc);
    if (!vertexShader) return 0;

    GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSrc);
    if (!fragmentShader) {
        glDeleteShader(vertexShader);
        return 0;
    }

    GLuint program = glCreateProgram();
    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);
    glLinkProgram(program);

    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint infoLen = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetProgramInfoLog(program, infoLen, nullptr, infoLog.data());
            LOGE("Program link failed: %s", infoLog.data());
        }
        glDeleteProgram(program);
        program = 0;
    }

    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);
    return program;
}

bool initEGL(DisplayContext* ctx) {
    if (!ctx || !ctx->window) return false;

    ctx->display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (ctx->display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed: 0x%x", eglGetError());
        return false;
    }

    EGLint major, minor;
    if (!eglInitialize(ctx->display, &major, &minor)) {
        LOGE("eglInitialize failed: 0x%x", eglGetError());
        return false;
    }

    const EGLint configAttribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_BLUE_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_RED_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 0,
        EGL_STENCIL_SIZE, 0,
        EGL_NONE
    };

    EGLint numConfigs;
    if (!eglChooseConfig(ctx->display, configAttribs, &ctx->config, 1, &numConfigs) || numConfigs <= 0) {
        LOGE("eglChooseConfig failed");
        return false;
    }

    const EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };

    ctx->context = eglCreateContext(ctx->display, ctx->config, EGL_NO_CONTEXT, contextAttribs);
    if (ctx->context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed: 0x%x", eglGetError());
        return false;
    }

    ctx->surface = eglCreateWindowSurface(ctx->display, ctx->config, ctx->window, nullptr);
    if (ctx->surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed: 0x%x", eglGetError());
        return false;
    }

    if (!eglMakeCurrent(ctx->display, ctx->surface, ctx->surface, ctx->context)) {
        LOGE("eglMakeCurrent failed: 0x%x", eglGetError());
        return false;
    }

    ctx->program = createProgram(VERTEX_SHADER_SRC, FRAGMENT_SHADER_SRC);
    if (!ctx->program) {
        LOGE("Failed to create GLES2 program");
        return false;
    }

    ctx->posAttr = glGetAttribLocation(ctx->program, "a_position");
    ctx->texAttr = glGetAttribLocation(ctx->program, "a_texCoord");
    ctx->samplerUniform = glGetUniformLocation(ctx->program, "pixels");

    glGenTextures(1, &ctx->textureId);
    glBindTexture(GL_TEXTURE_2D, ctx->textureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    // Initial texture upload with black background
    std::vector<uint32_t> initialPixels(ctx->bufferWidth * ctx->bufferHeight, 0xFF1E1E1E);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, ctx->bufferWidth, ctx->bufferHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, initialPixels.data());

    LOGI("EGL and GLES2 pipeline successfully initialized (%dx%d)", ctx->bufferWidth, ctx->bufferHeight);
    return true;
}

void renderFrame(DisplayContext* ctx) {
    if (!ctx || ctx->display == EGL_NO_DISPLAY || ctx->surface == EGL_NO_SURFACE) return;

    glViewport(0, 0, ctx->surfaceWidth, ctx->surfaceHeight);
    glClearColor(0.12f, 0.12f, 0.12f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(ctx->program);

    static const GLfloat vertices[] = {
        -1.0f,  1.0f, 0.0f, // top left
        -1.0f, -1.0f, 0.0f, // bottom left
         1.0f,  1.0f, 0.0f, // top right
         1.0f, -1.0f, 0.0f  // bottom right
    };

    static const GLfloat texCoords[] = {
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 0.0f,
        1.0f, 1.0f
    };

    glEnableVertexAttribArray(ctx->posAttr);
    glVertexAttribPointer(ctx->posAttr, 3, GL_FLOAT, GL_FALSE, 0, vertices);

    glEnableVertexAttribArray(ctx->texAttr);
    glVertexAttribPointer(ctx->texAttr, 2, GL_FLOAT, GL_FALSE, 0, texCoords);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, ctx->textureId);
    glUniform1i(ctx->samplerUniform, 0);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(ctx->posAttr);
    glDisableVertexAttribArray(ctx->texAttr);

    eglSwapBuffers(ctx->display, ctx->surface);
}

void releaseEGL(DisplayContext* ctx) {
    if (!ctx) return;
    if (ctx->display != EGL_NO_DISPLAY) {
        eglMakeCurrent(ctx->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (ctx->textureId) {
            glDeleteTextures(1, &ctx->textureId);
            ctx->textureId = 0;
        }
        if (ctx->program) {
            glDeleteProgram(ctx->program);
            ctx->program = 0;
        }
        if (ctx->surface != EGL_NO_SURFACE) {
            eglDestroySurface(ctx->display, ctx->surface);
            ctx->surface = EGL_NO_SURFACE;
        }
        if (ctx->context != EGL_NO_CONTEXT) {
            eglDestroyContext(ctx->display, ctx->context);
            ctx->context = EGL_NO_CONTEXT;
        }
        eglTerminate(ctx->display);
        ctx->display = EGL_NO_DISPLAY;
    }
    if (ctx->window) {
        ANativeWindow_release(ctx->window);
        ctx->window = nullptr;
    }
}

// Background display server thread that listens on Unix domain socket
void runDisplayServer(DisplayContext* ctx, std::string socketPath) {
    unlink(socketPath.c_str());

    ctx->serverFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (ctx->serverFd < 0) {
        LOGE("Failed to create display server socket: %s", strerror(errno));
        return;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    bool isAbstract = (!socketPath.empty() && socketPath[0] == '@');
    if (isAbstract) {
        addr.sun_path[0] = '\0';
        strncpy(addr.sun_path + 1, socketPath.c_str() + 1, sizeof(addr.sun_path) - 2);
    } else {
        strncpy(addr.sun_path, socketPath.c_str(), sizeof(addr.sun_path) - 1);
    }

    socklen_t addrLen = isAbstract
        ? (offsetof(struct sockaddr_un, sun_path) + 1 + strlen(socketPath.c_str() + 1))
        : sizeof(addr);

    if (bind(ctx->serverFd, (struct sockaddr*)&addr, addrLen) < 0) {
        LOGE("Failed to bind display socket %s: %s", socketPath.c_str(), strerror(errno));
        close(ctx->serverFd);
        ctx->serverFd = -1;
        return;
    }

    if (listen(ctx->serverFd, 8) < 0) {
        LOGE("Failed to listen on display socket: %s", strerror(errno));
        close(ctx->serverFd);
        ctx->serverFd = -1;
        return;
    }

    LOGI("Display server listening on %s", socketPath.c_str());

    while (ctx->isRunning) {
        struct pollfd pfd;
        pfd.fd = ctx->serverFd;
        pfd.events = POLLIN;
        int pollRes = poll(&pfd, 1, 500);
        if (pollRes <= 0) continue;

        int client = accept(ctx->serverFd, nullptr, nullptr);
        if (client < 0) continue;

        ctx->clientFd = client;
        LOGI("Guest Linux window client connected (fd=%d)", client);

        // Protocol loop: read damage rects or full frame updates
        // Header: [cmd:uint32][x:int32][y:int32][w:int32][h:int32][dataSize:uint32]
        while (ctx->isRunning) {
            uint32_t header[6];
            ssize_t n = read(client, header, sizeof(header));
            if (n != sizeof(header)) break;

            uint32_t cmd = header[0];
            int32_t x = static_cast<int32_t>(header[1]);
            int32_t y = static_cast<int32_t>(header[2]);
            int32_t w = static_cast<int32_t>(header[3]);
            int32_t h = static_cast<int32_t>(header[4]);
            uint32_t dataSize = header[5];

            if (dataSize > 0 && w > 0 && h > 0) {
                std::vector<uint8_t> buffer(dataSize);
                size_t totalRead = 0;
                while (totalRead < dataSize) {
                    ssize_t rd = read(client, buffer.data() + totalRead, dataSize - totalRead);
                    if (rd <= 0) break;
                    totalRead += rd;
                }

                if (totalRead == dataSize) {
                    std::lock_guard<std::mutex> lock(ctx->renderMutex);
                    if (ctx->textureId > 0) {
                        glBindTexture(GL_TEXTURE_2D, ctx->textureId);
                        glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buffer.data());
                        renderFrame(ctx);
                    }
                }
            }
        }

        close(client);
        ctx->clientFd = -1;
        LOGI("Guest client disconnected");
    }

    if (ctx->serverFd >= 0) {
        close(ctx->serverFd);
        ctx->serverFd = -1;
    }
    if (!isAbstract) {
        unlink(socketPath.c_str());
    }
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_pocketdesk_service_LinuxBridgeService_nativeCreateDisplayContext(
    JNIEnv* env,
    jobject thiz,
    jstring jSocketPath,
    jint width,
    jint height,
    jint dpi
) {
    const char* pathStr = env->GetStringUTFChars(jSocketPath, nullptr);
    std::string socketPath = pathStr ? pathStr : "/tmp/.X11-unix/X0";
    if (pathStr) env->ReleaseStringUTFChars(jSocketPath, pathStr);

    auto* ctx = new DisplayContext();
    ctx->bufferWidth = width;
    ctx->bufferHeight = height;
    ctx->surfaceWidth = width;
    ctx->surfaceHeight = height;
    ctx->dpi = dpi;
    ctx->pixelBuffer.resize(width * height * 4, 0);
    ctx->isRunning = true;

    ctx->serverThread = std::thread(runDisplayServer, ctx, socketPath);

    LOGI("Display context created: %p (size: %dx%d, dpi: %d)", ctx, width, height, dpi);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jboolean JNICALL
Java_com_pocketdesk_service_LinuxBridgeService_nativeSetSurface(
    JNIEnv* env,
    jobject thiz,
    jlong nativePtr,
    jobject jSurface
) {
    auto* ctx = reinterpret_cast<DisplayContext*>(nativePtr);
    if (!ctx) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(ctx->renderMutex);

    releaseEGL(ctx);

    if (jSurface != nullptr) {
        ctx->window = ANativeWindow_fromSurface(env, jSurface);
        if (!ctx->window) {
            LOGE("ANativeWindow_fromSurface failed");
            return JNI_FALSE;
        }
        ctx->surfaceWidth = ANativeWindow_getWidth(ctx->window);
        ctx->surfaceHeight = ANativeWindow_getHeight(ctx->window);

        if (!initEGL(ctx)) {
            LOGE("initEGL failed on new surface");
            return JNI_FALSE;
        }

        renderFrame(ctx);
        return JNI_TRUE;
    }

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_pocketdesk_service_LinuxBridgeService_nativeResizeDisplay(
    JNIEnv* env,
    jobject thiz,
    jlong nativePtr,
    jint width,
    jint height,
    jint dpi
) {
    auto* ctx = reinterpret_cast<DisplayContext*>(nativePtr);
    if (!ctx) return;

    std::lock_guard<std::mutex> lock(ctx->renderMutex);
    ctx->surfaceWidth = width;
    ctx->surfaceHeight = height;
    ctx->dpi = dpi;

    if (ctx->textureId > 0) {
        glBindTexture(GL_TEXTURE_2D, ctx->textureId);
        std::vector<uint32_t> empty(width * height, 0xFF1E1E1E);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, empty.data());
        renderFrame(ctx);
    }
}

JNIEXPORT void JNICALL
Java_com_pocketdesk_service_LinuxBridgeService_nativeSendPointerEvent(
    JNIEnv* env,
    jobject thiz,
    jlong nativePtr,
    jint action,
    jfloat x,
    jfloat y,
    jint button
) {
    auto* ctx = reinterpret_cast<DisplayContext*>(nativePtr);
    if (!ctx || ctx->clientFd < 0) return;

    // Send input packet to guest display client
    // Protocol: [pktType=1:uint32][action:int32][x:float][y:float][button:int32]
    uint32_t packet[5];
    packet[0] = 1; // POINTER_EVENT
    packet[1] = static_cast<uint32_t>(action);
    float fx = x;
    float fy = y;
    memcpy(&packet[2], &fx, sizeof(float));
    memcpy(&packet[3], &fy, sizeof(float));
    packet[4] = static_cast<uint32_t>(button);

    write(ctx->clientFd, packet, sizeof(packet));
}

JNIEXPORT void JNICALL
Java_com_pocketdesk_service_LinuxBridgeService_nativeSendKeyEvent(
    JNIEnv* env,
    jobject thiz,
    jlong nativePtr,
    jint keycode,
    jboolean isDown,
    jint modifiers
) {
    auto* ctx = reinterpret_cast<DisplayContext*>(nativePtr);
    if (!ctx || ctx->clientFd < 0) return;

    // Protocol: [pktType=2:uint32][keycode:int32][isDown:int32][modifiers:int32]
    uint32_t packet[4];
    packet[0] = 2; // KEY_EVENT
    packet[1] = static_cast<uint32_t>(keycode);
    packet[2] = isDown ? 1 : 0;
    packet[3] = static_cast<uint32_t>(modifiers);

    write(ctx->clientFd, packet, sizeof(packet));
}

JNIEXPORT jobject JNICALL
Java_com_pocketdesk_service_LinuxBridgeService_nativeCaptureSnapshot(
    JNIEnv* env,
    jobject thiz,
    jlong nativePtr
) {
    auto* ctx = reinterpret_cast<DisplayContext*>(nativePtr);
    if (!ctx) return nullptr;

    std::lock_guard<std::mutex> lock(ctx->renderMutex);
    int w = ctx->surfaceWidth > 0 ? ctx->surfaceWidth : 1080;
    int h = ctx->surfaceHeight > 0 ? ctx->surfaceHeight : 2400;

    // Create an Android ARGB_8888 Bitmap
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888Field = env->GetStaticFieldID(configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888 = env->GetStaticObjectField(configClass, argb8888Field);

    jmethodID createBitmapMethod = env->GetStaticMethodID(
        bitmapClass, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;"
    );

    // Scale down for thumbnail (e.g. 1/3 scale for silky smooth task switcher)
    int thumbW = w / 3;
    int thumbH = h / 3;
    if (thumbW < 120) thumbW = 120;
    if (thumbH < 240) thumbH = 240;

    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethod, thumbW, thumbH, argb8888);
    if (!bitmap) return nullptr;

    void* pixelPtr = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixelPtr) >= 0 && pixelPtr) {
        if (ctx->display != EGL_NO_DISPLAY && ctx->surface != EGL_NO_SURFACE) {
            std::vector<uint8_t> readBuf(w * h * 4);
            glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, readBuf.data());

            // Nearest-neighbor / linear subsample and flip Y
            auto* dst = static_cast<uint32_t*>(pixelPtr);
            for (int dy = 0; dy < thumbH; ++dy) {
                int sy = h - 1 - (dy * h / thumbH);
                for (int dx = 0; dx < thumbW; ++dx) {
                    int sx = dx * w / thumbW;
                    size_t srcIdx = (sy * w + sx) * 4;
                    uint8_t r = readBuf[srcIdx + 0];
                    uint8_t g = readBuf[srcIdx + 1];
                    uint8_t b = readBuf[srcIdx + 2];
                    uint8_t a = readBuf[srcIdx + 3];
                    dst[dy * thumbW + dx] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
    }

    return bitmap;
}

JNIEXPORT void JNICALL
Java_com_pocketdesk_service_LinuxBridgeService_nativeDestroyDisplayContext(
    JNIEnv* env,
    jobject thiz,
    jlong nativePtr
) {
    auto* ctx = reinterpret_cast<DisplayContext*>(nativePtr);
    if (!ctx) return;

    ctx->isRunning = false;
    if (ctx->serverFd >= 0) {
        shutdown(ctx->serverFd, SHUT_RDWR);
        close(ctx->serverFd);
        ctx->serverFd = -1;
    }
    if (ctx->serverThread.joinable()) {
        ctx->serverThread.join();
    }

    std::lock_guard<std::mutex> lock(ctx->renderMutex);
    releaseEGL(ctx);
    delete ctx;

    LOGI("Display context destroyed: %p", ctx);
}

} // extern "C"
