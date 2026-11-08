# CI/CD Pipeline

## Overview

The CI/CD pipeline automates building, testing, and releasing the Luck Now Android APK.

## Workflows

### 1. Build APK (build-apk.yml)

- **Trigger**: Push to `main` branch
- **Jobs**:
  - **Unit Tests**: Runs JVM unit tests and Android Lint
  - **Python Tests**: Verifies the on-device statistical analysis module
  - **BDD Tests**: Runs Cucumber scenarios on an API 34 emulator
  - **Build APK**: Compiles and signs the APK for arm64-v8a and x86_64

### 2. Deploy Releases (deploy.yml)

- **Trigger**: Push to `main` when build artifacts change
- **Action**: Pushes APK artifacts to a `releases` branch

## Required Secrets

### For Build APK

| Secret | Description |
|--------|-------------|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded Android keystore for signing APKs |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias (default: "lucknow") |
| `ANDROID_KEY_PASSWORD` | Key password (default: same as keystore password) |

### For Deploy Releases

| Secret | Description |
|--------|-------------|
| `DEPLOY_KEY` | Deploy key for pushing to the `releases` branch |

## Local Build

```bash
./gradlew :app:assembleDebug :app:assembleRelease \
  -PabiFilters=arm64-v8a -Plucknow.withLlama=true
```

## Testing

```bash
# Unit tests
./gradlew :app:testDebugUnitTest

# BDD tests (emulator)
./gradlew :app:connectedDebugAndroidTest -PabiFilters=x86_64

# Python tests
pip install -r python_tests/requirements.txt
python python_tests/test_analysis.py
```

## Artifacts

The pipeline produces:

- `app-arm64-v8a-debug.apk` - Debug build for arm64-v8a
- `app-arm64-v8a-release.apk` - Release build for arm64-v8a
- `app-x86_64-debug.apk` - Debug build for x86_64 (for emulator)
- `app-x86_64-release.apk` - Release build for x86_64
- `mapping-arm64-v8a.txt` - ProGuard/R8 mapping for arm64-v8a
- `mapping-x86_64.txt` - ProGuard/R8 mapping for x86_64
