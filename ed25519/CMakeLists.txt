# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.4.0 or lower.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds it for you.
# Gradle automatically packages shared libraries with your APK.

add_subdirectory(libs/base64)
add_subdirectory(libs/ed25519)
add_subdirectory(libs/codeScanner)

link_directories(libs/ed25519/src)
link_directories(libs/codeScanner/src)

include_directories(
    libs/ed25519/src
    libs/base64
    libs/codeScanner/src
)

add_library(
        native-lib
        SHARED
        src/main/cpp/native-lib.cpp
        )

add_library(
        kikCodes
        SHARED
        src/main/cpp/kik-codes.cpp
        )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because system libraries are included in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in the
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       native-lib
                       ed25519
                       codeScanner
                       base64
                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )

target_link_libraries( # Specifies the target library.
                       kikCodes
                       codeScanner
                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )
