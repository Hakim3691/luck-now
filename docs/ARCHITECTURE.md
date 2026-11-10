# Architecture

## Runtime shape

```
┌──────────────────────────── Android app process ────────────────────────────┐
│                                                                             │
│  Compose UI (MainActivity → LuckNowApp → Home/Scanning/Review/Results)       │
│        │  StateFlow<ScanUiState>                                            │
│  ScanViewModel ─────────────────────────────────────────────────────────┐    │
│        │                                                               │    │
│        ├─ PageInterceptionDriver (WebViewInterceptionDriver)           │    │
│        │     • hidden WebView attached to the decor view, alpha 0      │    │
│        │     • WebViewClient.shouldInterceptRequest → OkHttp re-fetch   │    │
│        │     • document-start JS hooks for fetch/XHR                    │    │
│        │     • scroll/pagination loop, SHA-256 de-duplication           │    │
│        │     • site adapters for pages that publish HTML only           │    │
│        │                                                               │    │
│        ├─ ExtractionSelector → QwenSequenceExtractor ──► LlamaNative    │    │
│        │        │                                   (liblucknow_llm.so, │    │
│        │        │                                    llama.cpp static)  │    │
│        │        └─► HeuristicSequenceExtractor (fallback)               │    │
│        │                                                               │    │
│        ├─ PythonStatisticalAnalyzer ──► Chaquopy CPython 3.10           │    │
│        │        └─ lucknow.analysis (numpy/scipy/pandas/statsmodels)    │    │
│        │                                                               │    │
│        └─ SqliteScanStore (audit trail, payload cache)                  │    │
└─────────────────────────────────────────────────────────────────────────────┘
```

No network service is required beyond the target site. There is no Redis, Celery worker, WebSocket
server or Docker on the device; those server-side concepts map to a coroutine pipeline with
observable state.

## Pipeline

1. **Validate URL** (`ScanViewModel.normalizeUrl`) — HTTPS/HTTP only, host required.
2. **Create run** in SQLite (`INTERCEPTING`).
3. **Intercept** (`WebViewInterceptionDriver.intercept`, main thread):
   * WebView created programmatically; JS, DOM storage, no file access, no mixed content.
   * `shouldInterceptRequest` runs off the main thread; candidate responses are re-fetched through
     `HttpTransport` (OkHttp), inspected and handed to the sink, then returned to the WebView intact
     so the page still works. Failures return `null`, letting the WebView do its own fetch.
   * Document-start script hooks `window.fetch` and `XMLHttpRequest` and posts JSON to a bounded
     `@JavascriptInterface` bridge, covering APIs that omit an `Accept` header.
   * After `onPageFinished` the driver scrolls and clicks obvious "next" controls until two
     consecutive steps produce no new payload (or limits are reached).
   * Direct JSON endpoints are probed once if nothing was captured.
   * If the wire produced nothing at all, the rendered document is read through a site adapter
     (`SiteDrawAdapters` → `DlbResultAdapter`, `NlbResultAdapter`). Adapters are per-site on
     purpose: dlb.lk and nlb.lk publish no JSON, and their pages differ in structure. The script
     runs in the page's own origin and posts `{"adapter","label","draws"}` over the bridge, so an
     endpoint that needs cookies or the page's own pagination still works. `DomDrawPayload` then
     validates and re-serializes it (at least 10 numbers, 0..999) before it reaches the normalizer,
     and the wait is a bounded poll rather than an unbounded renderer call.
4. **Normalize** (`ExtractionSelector`, `QwenSequenceExtractor`):
   * Engine choice is explicit: native runtime present **and** model installed → Qwen; otherwise the
     deterministic parser, flagged `degraded` in the UI.
   * Qwen gets a rigid ChatML prompt, greedy decoding, at most 512 new tokens.
   * Output must parse as integers only, match the estimated draw volume, stay in 0..999. A rejected
     answer injects its validation error into the next prompt (max 3 attempts).
5. **Review** — the user sees the integer sequence, engine, attempts and expected volume.
6. **Analyse** (`PythonStatisticalAnalyzer` → `lucknow.analysis.analyze`):
   * Runs on `Dispatchers.Default`; the interpreter starts lazily.
   * Returns a single JSON document; failures are still JSON (`{"ok": false, ...}`) and surface as a
     snackbar/banner rather than a crash.
7. **Persist** — `AWAITING_REVIEW` then `COMPLETED` with raw report + verdict, or `FAILED` with the
   error message.

## Threading

| Work | Dispatcher |
| --- | --- |
| WebView lifecycle, JS evaluation, scroll loop | `Dispatchers.Main` |
| Interception re-fetch (`shouldInterceptRequest`) | WebView's background thread |
| Extraction (pure Kotlin) | caller (view-model coroutine) / `Dispatchers.Default` for Python |
| Python analysis | `Dispatchers.Default` |
| SQLite | `Dispatchers.IO` |

`ScanViewModel` mutates a single `MutableStateFlow`; payload callbacks from background threads use
atomic `update`, so no UI-thread affinity is violated.

## Persistence

```sql
scans(id TEXT PK, url TEXT, created_at INTEGER, status TEXT, payload_count INTEGER,
      value_count INTEGER, engine TEXT, verdict TEXT, values_json TEXT,
      report_json TEXT, error TEXT)

payloads(id INTEGER PK AUTOINCREMENT, scan_id TEXT, url TEXT, mime_type TEXT,
         body TEXT, captured_at INTEGER)
```

* Statuses: `INTERCEPTING → AWAITING_REVIEW → COMPLETED | FAILED`.
* Raw payloads are capped (8 rows per scan, 128 KB each) and used for offline re-inspection.
* `allowBackup=false` plus `data_extraction_rules.xml` keep scraped data off cloud backup/transfer.

## Failure handling

| Failure | Behaviour |
| --- | --- |
| Invalid URL | Inline field error, no run created |
| Navigation error | Logged; interception continues with whatever arrived |
| No JSON and no matching adapter | Run `FAILED`, naming the host and the two supported sites |
| Site adapter finds nothing usable | Run `FAILED` with the adapter's reason in the scan log |
| LLM absent / no model | Deterministic parser, `degraded = true`, reason shown |
| LLM returns invalid output | Up to 3 error-injected retries, then a clear failure |
| Python interpreter unavailable | `AnalysisException` surfaced in the UI |
| Analysis engine error document | Message from the engine is shown |

## Native module

`app/src/main/cpp/CMakeLists.txt` builds llama.cpp (`LLAMA_BUILD_SERVER/TESTS/TOOLS/EXAMPLES` off,
OpenMP/llamafile off) and links it statically into `liblucknow_llm.so`. The bridge exposes three JNI
methods (`loadModel`, `generate`, `release`) on `com.lucknow.llm.LlamaNative`.

* Model load is CPU-only (`n_gpu_layers = 0`), context 4096, 4 threads by default.
* Each generation clears the KV cache, so requests are independent.
* Greedy sampling is used for deterministic extraction; temperature > 0.01 selects temp + dist.
* A `std::mutex` serialises generation because a `llama_context` is not thread-safe.
* Sources are pinned (`scripts/fetch-llama-cpp.sh`) and git-ignored; the Kotlin side degrades
  gracefully when the library is absent (`LlamaNative.isLoaded`).

## Size budget

The APK is dominated by the embedded interpreter and scientific stack:

| Component | Rough size |
| --- | --- |
| CPython 3.10 + Chaquopy runtime | ~10 MB |
| numpy + scipy + pandas + statsmodels wheels | ~95 MB |
| llama.cpp bridge (`liblucknow_llm.so`) | ~5.7 MB |
| Compose/AndroidX/resources | ~10 MB |
| **Total (arm64-v8a debug)** | **~128 MB** |

Model weights (380 MB) are downloaded at runtime, not packaged.
