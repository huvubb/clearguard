#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
revision=dde879bc4f954b809892fd0d660515f42536394a
archive_sha=83f099b6bbb531ca72d22cdd76d86641438469a214fd49a38f026c6a868598ed
runtime_build="${RUNNER_TEMP:-/tmp}/clearguard-llama-${revision}"
mkdir -p "$runtime_build" "$repo_root/app/src/main/jniLibs/arm64-v8a"
if [[ ! -f "$runtime_build/source.tar.gz" ]]; then
    curl --fail --location --retry 3 "https://codeload.github.com/ggml-org/llama.cpp/tar.gz/$revision" -o "$runtime_build/source.tar.gz"
fi
printf '%s  %s\n' "$archive_sha" "$runtime_build/source.tar.gz" | sha256sum --check
if [[ ! -d "$runtime_build/source" ]]; then
    mkdir "$runtime_build/source"
    tar -xzf "$runtime_build/source.tar.gz" --strip-components=1 -C "$runtime_build/source"
fi
cmake -S "$runtime_build/source" -B "$runtime_build/build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="${ANDROID_HOME:?}/ndk/29.0.13113456/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF \
    -DGGML_OPENMP=OFF -DGGML_CPU_KLEIDIAI=OFF -DGGML_LLAMAFILE=OFF \
    -DLLAMA_CURL=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF \
    -DLLAMA_BUILD_SERVER=OFF -DLLAMA_BUILD_TOOLS=ON -DGGML_CPU_ARM_ARCH=armv8-a \
    -DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384
cmake --build "$runtime_build/build" --target llama-completion -j 2
# Android allows executable code from the installed native-library directory.
cp "$runtime_build/build/bin/llama-completion" "$repo_root/app/src/main/jniLibs/arm64-v8a/libclearguard_llama.so"
