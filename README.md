# Luck Now

Production-grade on-device Android APK for lottery draw sequence analysis.

## What It Does

Luck Now intercepts JSON responses from lottery websites via a hidden WebView, parses them with Qwen2.5-0.5B via llama.cpp, and performs statistical analysis using scipy/statsmodels (chi-square, ACF, Ljung-Box).

## Features

- **Hidden WebView + shouldInterceptRequest**: Captures JSON payloads offline-capable
- **Qwen2.5-0.5B/Q4_K_M GGUF via llama.cpp JNI**: On-device LLM for parsing
- **Deterministic fallback**: When native runtime is unavailable
- **Chaquopy CPython 3.10 + scipy/statsmodels**: Statistical analysis engine
- **Chi-square test**: Detects mechanical bias in draw sequences
- **ACF (Autocorrelation)**: Detects PRNG flaws
- **Ljung-Box test**: Portmanteau test for serial correlation
- **Cucumber-Android BDD**: 6 features / 9 scenarios
- **GitHub Actions CI/CD**: Builds and signs arm64-v8a + x86_64 APKs

## Product Name

This product is **Luck Now** — not Sequntia.

## Requirements

- Android API 34+
- Min SDK: 34, Target SDK: 35
- NDK: 27.2.12479018
- CMake: 3.22.1
- Python: 3.10 (for Chaquopy)

## Building

### Prerequisites

```bash
# Install NDK and CMake
sdkmanager "ndk;27.2.12479018" "cmake;3.22.1"

# Vendor llama.cpp
bash scripts/fetch-llama-cpp.sh

# Build
./gradlew :app:assembleDebug :app:assembleRelease \
  -PabiFilters=arm64-v8a -Plucknow.withLlama=true
```

### Artifacts

The build produces:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/mapping/release/mapping.txt`

## Testing

### Unit Tests

```bash
./gradlew :app:testDebugUnitTest
```

### BDD Integration Tests

```bash
./gradlew :app:connectedDebugAndroidTest -PabiFilters=x86_64
```

### Python Tests (local verification of analysis module)

```bash
pip install -r python_tests/requirements.txt
python python_tests/test_analysis.py
```

## CI/CD Pipeline

### Workflows

1. **build-apk.yml**: Builds and tests the APK on every push to `main`
   - Unit tests (JVM + Lint)
   - Python tests (statistical engine verification)
   - BDD tests (Cucumber on API 34 emulator)
   - Build + sign APK for arm64-v8a + x86_64

2. **deploy.yml**: Deploys release artifacts to a `releases` branch

### Required Secrets

| Secret | Description |
|--------|-------------|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore for signing |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias (default: "lucknow") |
| `ANDROID_KEY_PASSWORD` | Key password |
| `DEPLOY_KEY` | Deploy key for pushing releases |

### Setup Instructions

1. Generate an Android keystore:
   ```bash
   keytool -genkeypair -alias lucknow -keyalg RSA -keysize 2048 \
     -keystore release.jks -storepass <password> -keypass <password>
   ```

2. Base64 encode the keystore:
   ```bash
   base64 release.jks | tr -d '\n'  # copy output
   ```

3. In GitHub repo settings → Secrets → Actions:
   - Add `ANDROID_KEYSTORE_BASE64` with the base64 value
   - Add `ANDROID_KEYSTORE_PASSWORD` with your password
   - Add `ANDROID_KEY_ALIAS` = `lucknow`
   - Add `ANDROID_KEY_PASSWORD` with your password

4. Generate a deploy key:
   ```bash
   ssh-keygen -t ed25519 -C "luck-now-deploy-key" -f lucknow_deploy -N ""
   ```

5. Add the public key to repo Settings → Deploy Keys (enable write access)

6. Store the private key as a GitHub Secret `DEPLOY_KEY` (base64 encoded)

## Deployment

The CI/CD pipeline automatically:

1. Runs all tests on every push
2. Builds signed APKs when tests pass
3. Pushes release artifacts to the `releases` branch
4. Creates GitHub Releases for tagged versions (v*)

To trigger a release, tag a commit:
```bash
git tag v1.0.0
git push origin v1.0.0
```

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for detailed architecture.

## BDD

See [docs/BDD.md](docs/BDD.md) for behavior-driven development details.

## Statistics

See [docs/STATISTICS.md](docs/STATISTICS.md) for statistical methodology.

## CI/CD

See [docs/CI_CD.md](docs/CI_CD.md) for pipeline details.

## License

MIT License
