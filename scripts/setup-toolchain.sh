#!/usr/bin/env bash
# Installs a self-contained JDK 17 + Android SDK + Gradle into $1 (default: $HOME/lucknow-toolchain).
# No root required. Used for local and ephemeral cloud builds; CI images already ship this toolchain.
set -euo pipefail

ROOT="${1:-$HOME/lucknow-toolchain}"
mkdir -p "$ROOT"
cd "$ROOT"

if [ ! -d "$ROOT/jdk" ]; then
  echo "==> Downloading Temurin JDK 17"
  curl -fsSL -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p jdk && tar xzf jdk.tar.gz -C jdk --strip-components=1
  rm -f jdk.tar.gz
fi
export JAVA_HOME="$ROOT/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -d "$ROOT/android-sdk/cmdline-tools/latest" ]; then
  echo "==> Downloading Android command line tools"
  curl -fsSL -o cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  rm -rf cmdline-tools-src && mkdir -p cmdline-tools-src
  unzip -q cmdline-tools.zip -d cmdline-tools-src
  mkdir -p "$ROOT/android-sdk/cmdline-tools"
  mv cmdline-tools-src/cmdline-tools "$ROOT/android-sdk/cmdline-tools/latest"
  rm -rf cmdline-tools-src cmdline-tools.zip
fi
export ANDROID_HOME="$ROOT/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

if [ ! -d "$ROOT/gradle" ]; then
  echo "==> Downloading Gradle 8.11.1"
  curl -fsSL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip"
  unzip -q gradle.zip -d .
  mv gradle-8.11.1 gradle
  rm -f gradle.zip
fi
export PATH="$ROOT/gradle/bin:$PATH"

echo "==> Accepting licenses and installing SDK packages"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null

echo "==> Toolchain ready at $ROOT"
java -version
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --version
gradle --version | head -5
