
cmake_minimum_required(VERSION 3.4.1)

file(GLOB files src/*.*)

add_library(
  codeScanner
  SHARED
  ${files}
)

target_link_libraries(
    codeScanner
    log
    android
)
