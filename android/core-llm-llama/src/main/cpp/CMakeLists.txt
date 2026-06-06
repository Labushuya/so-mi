# android/core-llm-llama/src/main/cpp/CMakeLists.txt
#
# Bridges Android Gradle's CMake invocation into the upstream llama.cpp
# tree at android/core-llm-llama/upstream/ (git submodule). We do NOT
# copy any ggml/llama/common sources — add_subdirectory makes them
# buildable in place, so `git submodule update` is all it takes to bump.
#
# NOTE: the upstream `examples/llama.android/lib/src/main/cpp/ai_chat.cpp`
# JNI shim must match the Kotlin package `com.arm.aichat.internal` for
# its JNI symbols (`Java_com_arm_aichat_internal_InferenceEngineImpl_*`)
# to resolve. We DO NOT rename the package — we wrap it behind our own
# `io.somi.llm.llama.LlamaCppContext` Kotlin class instead.

cmake_minimum_required(VERSION 3.22.1)
project("ai-chat" LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Path: this file lives at  <module>/src/main/cpp/CMakeLists.txt
# Submodule lives at         <module>/upstream/
# So three levels up gets us back to <module>, then descend into upstream.
set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../upstream)

# Mirror the upstream llama.android/lib CMakeLists.txt's per-ABI logic.
if(DEFINED ANDROID_ABI)
    message(STATUS "core-llm-llama: building for Android ABI ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI ON)
        set(GGML_OPENMP ON)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

# Restate the build flags here defensively; Gradle's externalNativeBuild
# arguments override these via the command line, but a developer running
# cmake by hand (e.g. for clangd index generation) gets the same config.
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE          OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL      OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE       OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL          OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN          OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_SRC} build-llama)

# JNI shim — taken verbatim from upstream.
# Vendored via srcDirs in build.gradle.kts; we reference it here via path.
add_library(${CMAKE_PROJECT_NAME} SHARED
    ${LLAMA_SRC}/examples/llama.android/lib/src/main/cpp/ai_chat.cpp
)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
    GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
    GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
    GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${LLAMA_SRC}
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include
    ${LLAMA_SRC}/ggml/src
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    llama-common
    android
    log
)

# Strip in release builds; size-optimize.
set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES
    LINK_FLAGS_RELEASE "-Wl,--gc-sections -Wl,--strip-all"
)
