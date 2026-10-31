cmake_minimum_required(VERSION 3.22.1)

project("pocketdesk_bridge" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_definitions(-DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles2-lib GLESv2)
find_library(jnigraphics-lib jnigraphics)

add_library(pocketdesk_bridge SHARED
    display_bridge.cpp
    runner_bridge.cpp
)

target_include_directories(pocketdesk_bridge PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(pocketdesk_bridge
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles2-lib}
    ${jnigraphics-lib}
)

# Also create liblindesk_display.so target for compatibility with LinDesk display spec
add_library(lindesk_display SHARED
    display_bridge.cpp
    runner_bridge.cpp
)

target_include_directories(lindesk_display PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(lindesk_display
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles2-lib}
    ${jnigraphics-lib}
)
