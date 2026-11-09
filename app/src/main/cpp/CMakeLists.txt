cmake_minimum_required(VERSION 3.22.1)

project(lucknow_llm C CXX)

# Vendored llama.cpp (see scripts/fetch-llama-cpp.sh). Path:
# app/src/main/cpp -> app/src/main -> app/src -> app -> <repo root>
set(LLAMA_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp")

if(NOT EXISTS "${LLAMA_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp sources not found at ${LLAMA_SOURCE_DIR}. "
        "Run scripts/fetch-llama-cpp.sh, or build with -Plucknow.withLlama=false.")
endif()

# Build only the inference library: no server, tools, tests, examples or curl.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_IS_DEV OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(LLAMA_ALL_WARNINGS_3RD_PARTY OFF CACHE BOOL "" FORCE)
set(LLAMA_USE_PREBUILT_UI OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_SOURCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp" EXCLUDE_FROM_ALL)

add_library(lucknow_llm SHARED llama_bridge.cpp)

target_compile_features(lucknow_llm PRIVATE cxx_std_17)
target_compile_options(lucknow_llm PRIVATE -O3 -fvisibility=hidden -fexceptions -frtti)

find_library(ANDROID_LOG_LIB log)
target_link_libraries(lucknow_llm PRIVATE llama ${ANDROID_LOG_LIB})
