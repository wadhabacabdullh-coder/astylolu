#!/usr/bin/env bash
# Builds the Stockfish UCI engine (vendored in ./src) for Android with the NDK.
#
# Usage: build-android.sh <ndk-dir> <output-jniLibs-dir>
#
# Output layout (the binaries are named lib*.so so that Android installs them
# into the app's native library directory, the only app location that may be
# executed on Android 10+):
#   <out>/arm64-v8a/libstockfish.so          ARMv8 + NEON
#   <out>/arm64-v8a/libstockfish_dotprod.so  ARMv8.2 + NEON dot product
#   <out>/armeabi-v7a/libstockfish.so        ARMv7 + NEON
#   <out>/x86_64/libstockfish.so             x86-64 SSE4.1 + POPCNT
#
# The NNUE network is NOT embedded (-DNNUE_EMBEDDING_OFF); the app ships it as
# an asset and passes its path through the UCI "EvalFile" option.
set -euo pipefail

NDK="${1:?ndk dir}"
OUT="${2:?output dir}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [ ! -d "$TOOLCHAIN" ]; then
  TOOLCHAIN="$(ls -d "$NDK"/toolchains/llvm/prebuilt/*/bin | head -1)"
fi
export PATH="$TOOLCHAIN:$PATH"
JOBS="${JOBS:-$(nproc 2>/dev/null || echo 4)}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

build() { # <sf-arch> <abi> <libname>
  local arch="$1" abi="$2" name="$3"
  local dir="$WORK/$arch"
  cp -r "$HERE/src" "$dir"
  (
    cd "$dir"
    make -s -j"$JOBS" all ARCH="$arch" COMP=ndk \
      EXTRACXXFLAGS="-DNNUE_EMBEDDING_OFF" \
      EXTRALDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384" >/dev/null
    llvm-strip stockfish
  )
  mkdir -p "$OUT/$abi"
  cp "$dir/stockfish" "$OUT/$abi/$name"
  echo "built $abi/$name ($arch)"
}

build armv8           arm64-v8a   libstockfish.so
build armv8-dotprod   arm64-v8a   libstockfish_dotprod.so
build armv7-neon      armeabi-v7a libstockfish.so
build x86-64-sse41-popcnt x86_64  libstockfish.so
