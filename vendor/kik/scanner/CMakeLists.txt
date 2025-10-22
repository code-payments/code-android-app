# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.4.0 or lower.

cmake_minimum_required(VERSION 3.4.4)

project(codeScanner)

# --- DYNAMIC PROJECT ROOT DETECTION ---
# This block dynamically finds the project's root directory by searching upwards
# from the current CMake file for a directory named "vendor". This avoids
# hardcoding relative paths like "../../../".

set(CURRENT_DIR ${CMAKE_CURRENT_SOURCE_DIR})
set(PROJECT_ROOT_DIR "")

# Loop upwards until the root of the filesystem is reached or the marker is found
while(NOT IS_DIRECTORY "${CURRENT_DIR}/vendor")
    get_filename_component(PARENT_DIR ${CURRENT_DIR} DIRECTORY)
    # Check if we have reached the top of the filesystem
    if(PARENT_DIR STREQUAL CURRENT_DIR)
        message(FATAL_ERROR "Could not find project root directory containing 'vendor' folder.")
        break()
    endif()
    set(CURRENT_DIR ${PARENT_DIR})
endwhile()

set(PROJECT_ROOT_DIR ${CURRENT_DIR})
message(STATUS "Project root found at: ${PROJECT_ROOT_DIR}")
set(OpenCV_DIR ${PROJECT_ROOT_DIR}/vendor/opencv/sdk/native/jni CACHE PATH "Path to OpenCV for Android SDK")
set(OpenCV_INSTALL_PATH ${PROJECT_ROOT_DIR}/vendor/opencv)
message(STATUS "OpenCV install path found at: ${OpenCV_INSTALL_PATH}")
# --- END DYNAMIC DETECTION ---

find_package(OpenCV REQUIRED)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds it for you.
# Gradle automatically packages shared libraries with your APK.

file(GLOB_RECURSE zxing_sources "src/main/cpp/zxing/*/*.cpp")
file(GLOB encoding_sources "src/main/cpp/encoding/*.cpp")
file(GLOB scan_sources "src/main/cpp/scan/*.cpp")

# Creates the 'codeScanner' library from all the dynamically found source files.
add_library(
        codeScanner
        SHARED
        src/main/cpp/zxing/Exception.cpp
        ${zxing_sources}
        ${encoding_sources}
        ${scan_sources}
)

# Creates the 'kikCodes' library.
add_library(
        kikCodes
        SHARED
        src/main/cpp/Code.cpp
)

target_include_directories(
        codeScanner
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because system libraries are included in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library(log-lib log)

target_link_libraries(
        kikCodes
        codeScanner
        ${log-lib}
)

target_link_libraries(codeScanner ${OpenCV_LIBS})