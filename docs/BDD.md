# BDD

Behaviour is specified in Gherkin and executed on a device/emulator with
**Cucumber-Android** (`io.cucumber:cucumber-android:7.14.0`,
`CucumberAndroidJUnitRunner`).

```
app/src/androidTest/assets/features/*.feature   Gherkin
app/src/androidTest/java/com/lucknow/bdd/       runner + steps
```

## Feature map

| Feature | Scenarios | What is actually exercised |
| --- | --- | --- |
| `dynamic_interception.feature` | 1 | Real `MainActivity` (ActivityScenario) + real hidden `WebViewInterceptionDriver` against a local MockWebServer page that fetches paginated JSON on scroll. Asserts the page loaded, JSON was captured, and the captured documents reach the parsing module (24 integers from 2 pages). |
| `llm_normalization.feature` | 3 | `QwenSequenceExtractor` with a scripted engine: rigid prompt content, strict integer array, draw-volume equality, self-healing retry with the validation error injected, and deterministic fallback when no runtime/model is available. |
| `prng_detection.feature` | 2 | Real Chaquopy + `scipy`/`statsmodels`: a period-6 sequence must produce a significant ACF at lag 6 and the UI headline `PRNG Flaw Detected`; a SHA-256-mixed sequence must not be flagged. |
| `mechanical_bias.feature` | 1 | Real chi-square on a skewed 200-draw fixture; significant p-value and `Mechanical Bias Detected` headline. |
| `offline_processing.feature` | 1 | Analysis with no server: report must come from `lucknow-python` and expose local scipy/statsmodels versions. |
| `audit_trail.feature` | 1 | Real `SqliteScanStore` write/read of a completed run and its dataset. |

Scenarios are tagged (`@interception`, `@normalization`, `@analysis`, `@offline`, `@audit`) so CI can
run subsets, e.g. `-Pcucumber.filter.tags="@analysis"` style filtering through the runner.

## Assertions on user-visible wording

Verdict copy lives in `ui/AnalysisPresentation`, which the Compose results screen renders and the
steps assert. That keeps "the UI highlights *PRNG Flaw Detected*" honest without a brittle
pixel/Compose-tree coupling, and JVM tests (`AnalysisPresentationTest`) cover the same mapping
without an emulator.

## Fixtures are verified host-side

The "well mixed" and "periodic" fixtures are deterministic and were validated against the exact
statistical engine by `python_tests/test_analysis.py`, so a failing scenario indicates a regression
rather than a flaky random sample. Notably, a false positive in *any* of 20 lags is not rare
(~64% at α = 0.05), which is why the clean fixture is a cryptographically mixed sequence verified to
have no significant lag.

## Running

```bash
# device or emulator connected
./gradlew :app:connectedDebugAndroidTest -PabiFilters=x86_64

# reports
app/build/reports/androidTests/connected/index.html
```

In CI, `reactivecircus/android-emulator-runner` boots API 34 (x86_64) and runs the same command; the
APK is rebuilt for `x86_64` because the production build is `arm64-v8a` only.

The emulator job disables llama.cpp (`-Plucknow.withLlama=false`): the LLM path is validated with a
scripted engine, and the native runtime is verified separately by the release build's symbol/ABI
checks. This keeps the emulator job fast and avoids needing an NDK in the BDD job.

## Adding a scenario

1. Add the scenario to an existing feature (or a new file) under `assets/features`.
2. Add/extend a step class in `com.lucknow.bdd.steps` (glue package).
3. Share scenario state through `BddWorld`; reset happens in `Hooks.@Before`.
4. Prefer real components over mocks; only the LLM text generator and the remote site are faked.
