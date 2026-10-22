#!/bin/bash
# Builds the debug-only JVMTI agent into the app's debug jniLibs. jvmti.h comes from the JDK
# (the NDK does not ship it) and is not vendored.
set -euo pipefail
cd "$(dirname "$0")/../.."
NDK=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/ndk/* | sort -V | tail -1)
JH=${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}
BIN=$(echo "$NDK"/toolchains/llvm/prebuilt/*/bin)
OUT=apps/flipcash/app/src/debug/jniLibs
for pair in arm64-v8a:aarch64 x86_64:x86_64; do
  abi=${pair%%:*}; tgt=${pair##*:}
  mkdir -p "$OUT/$abi"
  "$BIN/${tgt}-linux-android29-clang" -shared -fPIC -O2 -I"$JH/include" -I"$JH/include/darwin" \
    -Wl,-z,max-page-size=16384 -llog \
    -o "$OUT/$abi/libhotswap_agent.so" apps/flipcash/app/src/debug/cpp/hotswap_agent.c
done
ls -l $OUT/*/libhotswap_agent.so
