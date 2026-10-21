cmake_minimum_required(VERSION 3.16)
project(LinktoMac VERSION 1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -flto")

# Try pkg-config first for FFmpeg (works on Linux, macOS with pkg-config)
# Fall back to Homebrew path on macOS
find_package(PkgConfig QUIET)
if(PkgConfig_FOUND)
    pkg_check_modules(FFMPEG REQUIRED libavcodec libavformat libavutil libswscale)
else()
    # Fallback for macOS without pkg-config: try Homebrew
    if(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
        execute_process(
            COMMAND brew --prefix
            OUTPUT_VARIABLE HOMEBREW_PREFIX
            OUTPUT_STRIP_TRAILING_WHITESPACE
        )
        
        # Homebrew symlinks are in ${HOMEBREW_PREFIX}/opt/ffmpeg
        set(FFMPEG_PREFIX "${HOMEBREW_PREFIX}/opt/ffmpeg")
        # Also check Cellar directly in case opt symlink is missing
        set(FFMPEG_CELLAR_PREFIX "${HOMEBREW_PREFIX}/Cellar/ffmpeg")
        
        find_path(AVCODEC_INCLUDE_DIR libavcodec/avcodec.h 
            PATHS ${FFMPEG_PREFIX}/include ${FFMPEG_CELLAR_PREFIX}/*/include
            NO_DEFAULT_PATH)
        find_library(AVCODEC_LIBRARY avcodec 
            PATHS ${FFMPEG_PREFIX}/lib ${FFMPEG_CELLAR_PREFIX}/*/lib
            NO_DEFAULT_PATH)
        find_library(AVFORMAT_LIBRARY avformat 
            PATHS ${FFMPEG_PREFIX}/lib ${FFMPEG_CELLAR_PREFIX}/*/lib
            NO_DEFAULT_PATH)
        find_library(AVUTIL_LIBRARY avutil 
            PATHS ${FFMPEG_PREFIX}/lib ${FFMPEG_CELLAR_PREFIX}/*/lib
            NO_DEFAULT_PATH)
        find_library(SWSCALE_LIBRARY swscale 
            PATHS ${FFMPEG_PREFIX}/lib ${FFMPEG_CELLAR_PREFIX}/*/lib
            NO_DEFAULT_PATH)
        
        if(AVCODEC_INCLUDE_DIR)
            set(FFMPEG_INCLUDE_DIRS ${AVCODEC_INCLUDE_DIR})
            # Use full library paths to ensure linker finds them
            set(FFMPEG_LIBRARIES ${AVCODEC_LIBRARY} ${AVFORMAT_LIBRARY} ${AVUTIL_LIBRARY} ${SWSCALE_LIBRARY})
            # Also add the library directory for the linker
            get_filename_component(FFMPEG_LIB_DIR ${AVCODEC_LIBRARY} DIRECTORY)
            set(FFMPEG_LIBRARY_DIRS ${FFMPEG_LIB_DIR})
        else()
            message(FATAL_ERROR "FFmpeg not found. Install via: brew install ffmpeg (or ensure pkg-config is available)")
        endif()
    else()
        message(FATAL_ERROR "FFmpeg not found. Install via package manager or ensure pkg-config is available")
    endif()
endif()

# SDL2
find_package(SDL2 REQUIRED)

# OpenSSL for TLS 1.3
find_package(OpenSSL REQUIRED)

# Apple frameworks for VideoToolbox and Cocoa
find_library(VIDEOTOOLBOX_FRAMEWORK VideoToolbox)
find_library(COREMEDIA_FRAMEWORK CoreMedia)
find_library(COREVIDEO_FRAMEWORK CoreVideo)
find_library(COREFOUNDATION_FRAMEWORK CoreFoundation)
find_library(APPKIT_FRAMEWORK AppKit)

# Native library
add_library(mirror_native STATIC
    src/decoder.cpp
    src/renderer.mm
    src/input_handler.cpp
    src/network.cpp
    src/mirror_client.cpp
)

target_include_directories(mirror_native PUBLIC
    ${FFMPEG_INCLUDE_DIRS}
    ${SDL2_INCLUDE_DIRS}
    src/
)

# Add FFmpeg library directory for linker
if(FFMPEG_LIBRARY_DIRS)
    target_link_directories(mirror_native PUBLIC ${FFMPEG_LIBRARY_DIRS})
endif()

target_link_libraries(mirror_native PUBLIC
    ${FFMPEG_LIBRARIES}
    ${SDL2_LIBRARIES}
    OpenSSL::SSL
    OpenSSL::Crypto
    ${VIDEOTOOLBOX_FRAMEWORK}
    ${COREMEDIA_FRAMEWORK}
    ${COREVIDEO_FRAMEWORK}
    ${COREFOUNDATION_FRAMEWORK}
    ${APPKIT_FRAMEWORK}
)

# Standalone executable (for testing)
add_executable(mirror_client src/main.cpp)
target_link_libraries(mirror_client mirror_native)

# Python bindings (optional)
find_package(pybind11 QUIET)
if(pybind11_FOUND)
    pybind11_add_module(mirror_core src/python_bindings.cpp)
    target_link_libraries(mirror_core PRIVATE mirror_native)
endif()
