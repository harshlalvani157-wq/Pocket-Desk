#include "display_server.h"

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <poll.h>
#include <cstring>

namespace {

const char* VERTEX_SHADER = R"(
    attribute vec4 a_Position;
    attribute vec2 a_TexCoord;
    varying vec2 v_TexCoord;
    void main() {
        gl_Position = a_Position;
        v_TexCoord = a_TexCoord;
    }
)";

// Fragment Shader (Performs BGR to RGB color swizzle in hardware as specified in blueprint)
const char* FRAGMENT_SHADER = R"(
    precision mediump float;
    uniform sampler2D u_Texture;
    varying vec2 v_TexCoord;
    void main() {
        vec4 c = texture2D(u_Texture, v_TexCoord);
        gl_FragColor = vec4(c.b, c.g, c.r, 1.0);
    }
)";

GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint compiled = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        glDeleteShader(s);
        return 0;
    }
    return s;
}

GLuint buildProgram() {
    GLuint vs = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
    if (!vs || !fs) return 0;

    GLuint p = glCreateProgram();
    glAttachShader(p, vs);
    glAttachShader(p, fs);
    glLinkProgram(p);

    glDeleteShader(vs);
    glDeleteShader(fs);
    return p;
}

} // namespace

DisplayServer& DisplayServer::getInstance() {
    static DisplayServer s_instance;
    return s_instance;
}

DisplayServer::~DisplayServer() {
    stop();
}

bool DisplayServer::init(const std::string& socketPath, int width, int height, int dpi) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_width = width;
    m_height = height;
    m_dpi = dpi;

    if (!m_running.load()) {
        m_running.store(true);
        m_thread = std::thread(&DisplayServer::socketServerLoop, this, socketPath);
    }
    return true;
}

bool DisplayServer::setSurface(JNIEnv* env, jobject jSurface) {
    std::lock_guard<std::mutex> lock(m_mutex);
    destroyEGL();

    if (jSurface != nullptr) {
        m_window = ANativeWindow_fromSurface(env, jSurface);
        if (!m_window) {
            LOGE("ANativeWindow_fromSurface failed");
            return false;
        }
        m_width = ANativeWindow_getWidth(m_window);
        m_height = ANativeWindow_getHeight(m_window);
        return initEGL();
    }
    return true;
}

void DisplayServer::resize(int width, int height, int dpi) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_width = width;
    m_height = height;
    m_dpi = dpi;

    if (m_texture > 0) {
        glBindTexture(GL_TEXTURE_2D, m_texture);
        std::vector<uint32_t> blank(width * height, 0xFF1E1E1E);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, blank.data());
        render();
    }
}

bool DisplayServer::initEGL() {
    if (!m_window) return false;

    m_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (m_display == EGL_NO_DISPLAY) return false;

    EGLint maj, min;
    if (!eglInitialize(m_display, &maj, &min)) return false;

    const EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_BLUE_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_RED_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };

    EGLint numConfigs;
    if (!eglChooseConfig(m_display, attribs, &m_config, 1, &numConfigs) || numConfigs <= 0) return false;

    const EGLint ctxAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    m_context = eglCreateContext(m_display, m_config, EGL_NO_CONTEXT, ctxAttribs);
    if (m_context == EGL_NO_CONTEXT) return false;

    m_surface = eglCreateWindowSurface(m_display, m_config, m_window, nullptr);
    if (m_surface == EGL_NO_SURFACE) return false;

    if (!eglMakeCurrent(m_display, m_surface, m_surface, m_context)) return false;

    m_program = buildProgram();
    m_posAttr = glGetAttribLocation(m_program, "a_Position");
    m_texAttr = glGetAttribLocation(m_program, "a_TexCoord");
    m_samplerUniform = glGetUniformLocation(m_program, "u_Texture");

    glGenTextures(1, &m_texture);
    glBindTexture(GL_TEXTURE_2D, m_texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    std::vector<uint32_t> initial(m_width * m_height, 0xFF1E1E1E);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, m_width, m_height, 0, GL_RGBA, GL_UNSIGNED_BYTE, initial.data());

    render();
    LOGI("Hardware EGL display pipeline initialized (%dx%d)", m_width, m_height);
    return true;
}

void DisplayServer::render() {
    if (m_display == EGL_NO_DISPLAY || m_surface == EGL_NO_SURFACE) return;

    glViewport(0, 0, m_width, m_height);
    glClearColor(0.12f, 0.12f, 0.12f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(m_program);

    static const GLfloat pos[] = {
        -1.0f,  1.0f, 0.0f,
        -1.0f, -1.0f, 0.0f,
         1.0f,  1.0f, 0.0f,
         1.0f, -1.0f, 0.0f
    };

    static const GLfloat uv[] = {
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 0.0f,
        1.0f, 1.0f
    };

    glEnableVertexAttribArray(m_posAttr);
    glVertexAttribPointer(m_posAttr, 3, GL_FLOAT, GL_FALSE, 0, pos);

    glEnableVertexAttribArray(m_texAttr);
    glVertexAttribPointer(m_texAttr, 2, GL_FLOAT, GL_FALSE, 0, uv);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, m_texture);
    glUniform1i(m_samplerUniform, 0);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(m_posAttr);
    glDisableVertexAttribArray(m_texAttr);

    eglSwapBuffers(m_display, m_surface);
}

void DisplayServer::destroyEGL() {
    if (m_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(m_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (m_texture) { glDeleteTextures(1, &m_texture); m_texture = 0; }
        if (m_program) { glDeleteProgram(m_program); m_program = 0; }
        if (m_surface != EGL_NO_SURFACE) { eglDestroySurface(m_display, m_surface); m_surface = EGL_NO_SURFACE; }
        if (m_context != EGL_NO_CONTEXT) { eglDestroyContext(m_display, m_context); m_context = EGL_NO_CONTEXT; }
        eglTerminate(m_display);
        m_display = EGL_NO_DISPLAY;
    }
    if (m_window) {
        ANativeWindow_release(m_window);
        m_window = nullptr;
    }
}

void DisplayServer::sendPointer(int action, float x, float y, int button) {
    if (m_clientFd < 0) return;
    uint32_t packet[5];
    packet[0] = 1; // POINTER_EVENT
    packet[1] = static_cast<uint32_t>(action);
    memcpy(&packet[2], &x, sizeof(float));
    memcpy(&packet[3], &y, sizeof(float));
    packet[4] = static_cast<uint32_t>(button);
    write(m_clientFd, packet, sizeof(packet));
}

void DisplayServer::sendKey(int keyCode, bool isDown, int modifiers) {
    if (m_clientFd < 0) return;
    uint32_t packet[4];
    packet[0] = 2; // KEY_EVENT
    packet[1] = static_cast<uint32_t>(keyCode);
    packet[2] = isDown ? 1 : 0;
    packet[3] = static_cast<uint32_t>(modifiers);
    write(m_clientFd, packet, sizeof(packet));
}

jobject DisplayServer::captureSnapshotBitmap(JNIEnv* env) {
    std::lock_guard<std::mutex> lock(m_mutex);
    int w = m_width > 0 ? m_width : 720;
    int h = m_height > 0 ? m_height : 1560;

    int thumbW = 320;
    int thumbH = 480;

    jclass bmpClass = env->FindClass("android/graphics/Bitmap");
    jclass cfgClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argbFid = env->GetStaticFieldID(cfgClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb = env->GetStaticObjectField(cfgClass, argbFid);
    jmethodID createBmp = env->GetStaticMethodID(bmpClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jobject bitmap = env->CallStaticObjectMethod(bmpClass, createBmp, thumbW, thumbH, argb);
    if (!bitmap) return nullptr;

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 && pixels) {
        if (m_display != EGL_NO_DISPLAY && m_surface != EGL_NO_SURFACE) {
            std::vector<uint8_t> readBuf(w * h * 4);
            glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, readBuf.data());

            auto* dst = static_cast<uint32_t*>(pixels);
            for (int dy = 0; dy < thumbH; ++dy) {
                int sy = h - 1 - (dy * h / thumbH);
                for (int dx = 0; dx < thumbW; ++dx) {
                    int sx = dx * w / thumbW;
                    size_t idx = (sy * w + sx) * 4;
                    uint8_t r = readBuf[idx + 0];
                    uint8_t g = readBuf[idx + 1];
                    uint8_t b = readBuf[idx + 2];
                    uint8_t a = readBuf[idx + 3];
                    dst[dy * thumbW + dx] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
    }
    return bitmap;
}

void DisplayServer::socketServerLoop(std::string socketPath) {
    unlink(socketPath.c_str());
    m_serverFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (m_serverFd < 0) return;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, socketPath.c_str(), sizeof(addr.sun_path) - 1);

    if (bind(m_serverFd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        close(m_serverFd);
        m_serverFd = -1;
        return;
    }
    listen(m_serverFd, 4);

    while (m_running.load()) {
        struct pollfd pfd;
        pfd.fd = m_serverFd;
        pfd.events = POLLIN;
        if (poll(&pfd, 1, 500) <= 0) continue;

        int client = accept(m_serverFd, nullptr, nullptr);
        if (client < 0) continue;
        m_clientFd = client;

        while (m_running.load()) {
            uint32_t header[6];
            if (read(client, header, sizeof(header)) != sizeof(header)) break;

            int32_t x = static_cast<int32_t>(header[1]);
            int32_t y = static_cast<int32_t>(header[2]);
            int32_t w = static_cast<int32_t>(header[3]);
            int32_t h = static_cast<int32_t>(header[4]);
            uint32_t size = header[5];

            if (size > 0 && w > 0 && h > 0) {
                std::vector<uint8_t> buf(size);
                size_t rdTotal = 0;
                while (rdTotal < size) {
                    ssize_t n = read(client, buf.data() + rdTotal, size - rdTotal);
                    if (n <= 0) break;
                    rdTotal += n;
                }
                if (rdTotal == size) {
                    std::lock_guard<std::mutex> lock(m_mutex);
                    if (m_texture > 0) {
                        glBindTexture(GL_TEXTURE_2D, m_texture);
                        glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buf.data());
                        render();
                    }
                }
            }
        }
        close(client);
        m_clientFd = -1;
    }

    if (m_serverFd >= 0) {
        close(m_serverFd);
        m_serverFd = -1;
    }
    unlink(socketPath.c_str());
}

void DisplayServer::stop() {
    m_running.store(false);
    if (m_serverFd >= 0) {
        shutdown(m_serverFd, SHUT_RDWR);
        close(m_serverFd);
        m_serverFd = -1;
    }
    if (m_thread.joinable()) {
        m_thread.join();
    }
    std::lock_guard<std::mutex> lock(m_mutex);
    destroyEGL();
}
