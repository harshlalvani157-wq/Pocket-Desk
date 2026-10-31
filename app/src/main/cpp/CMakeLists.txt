cmake_minimum_required(VERSION 3.22.1)

project("pocketdesk_bridge" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles2-lib GLESv2)
find_library(jnigraphics-lib jnigraphics)

add_library(pocketdesk_bridge SHARED
    display_server.cpp
    process_runner.cpp
    jni_bridge.cpp
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

add_library(pocketdesk_display SHARED
    display_server.cpp
    process_runner.cpp
    jni_bridge.cpp
)

target_include_directories(pocketdesk_display PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(pocketdesk_display
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles2-lib}
    ${jnigraphics-lib}
)
