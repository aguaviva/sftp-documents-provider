cmake_minimum_required(VERSION 3.9.4)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/libssh2/arm64-v8a/include)

add_library(ssh2_bindings SHARED libssh2_bindings.cpp)

target_link_libraries(ssh2_bindings PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/libssh2/arm64-v8a/lib/libssh2.a
        ${CMAKE_CURRENT_SOURCE_DIR}/libssh2/arm64-v8a/lib/libgcrypt.a
        ${CMAKE_CURRENT_SOURCE_DIR}/libssh2/arm64-v8a/lib/libgpg-error.a
        log)


