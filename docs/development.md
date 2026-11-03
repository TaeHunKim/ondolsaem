# Development

Design: [architecture.md](architecture.md).

## Setup

- Android Studio (its bundled JBR is the JDK), SDK at `~/Library/Android/sdk` with API 37 platform. `source scripts/env.sh` sets `JAVA_HOME`, `ANDROID_HOME` and `PATH`.
- `local.properties` holds `sdk.dir` (git-ignored).

## Layout

| Module | Contents |
|---|---|
| `:sdk` | Client contract (AIDL in `src/main/aidl`, constants in `OndolSaem`) and client library. Its manifest adds a `<queries>` entry for the host package to every client app. |
| `:app` | The host app (code map in [architecture.md](architecture.md)). Before the first build run `scripts/fetch-sherpa-onnx.sh` (sherpa-onnx AAR into `app/libs`, Silero VAD into `app/src/main/assets`; neither is committed). |

## Build and test

| Level | Command |
|---|---|
| Unit | `./gradlew assembleDebug testDebugUnitTest` |
| Instrumented | `./gradlew connectedDebugAndroidTest` (needs a device; uninstalls the app at the end, which deletes downloaded models). `HostServiceTest` drives the real bound service through `OndolSaemClient` with fake engines (`ModelRuntime`'s factory and `readyOverride` hooks), so it needs no model. |

Scripts (`scripts/`): `fetch-sherpa-onnx.sh`, `emu-start.sh [--window]`, `emu-stop.sh`, `run.sh` (installDebug + launch), `logcat.sh [N]`, `screenshot.sh`.

To let a client built on another machine (another debug key) connect, add that key's SHA-256 to `app/src/main/res/values/allowed_signers.xml` (`apksigner verify --print-certs app-debug.apk`). The emulator AVD `lsa_api36` is shared with Nunsori.

## Working with a client app

Clients depend on `com.github.taehunkim.ondolsaem:sdk:<version>`. To change the SDK and a client together, check this repository out next to the client and point the client at it. Nunsori, for example, reads `ondolsaem.dir=../ondolsaem` from its `local.properties` and adds this build as a Gradle composite build. The SDK's `group` (`com.github.taehunkim.ondolsaem`) and project name (`sdk`) must stay equal to the published coordinates for that substitution to work.

## Measuring on a device

`scripts/bench-device.sh [-s SERIAL] [-n REQUESTS]` runs `SharedModelBench` (androidTest, skipped unless the argument `bench=1` is given) on a connected phone or emulator with the active embedding model already downloaded. It installs the app and the test APK and starts the benchmark with `am instrument`, so the models stay (`connectedDebugAndroidTest` would uninstall the app). It writes `build/bench/<device>-<model>-<date>.txt`:

| Line | Meaning |
|---|---|
| `load_ms`, `backend`, `model_kb`, `pss_kb_before/after` | First load of the model, GPU or CPU, memory growth while loading, process PSS |
| `one client, query` / `image` | Latency of one client, one request at a time |
| `two clients, client A/B`, requests per second | Two clients sending background requests at once: each waits for the other's request, throughput stays the same |
| `interactive next to images` | Queries from one client while another sends images: a query waits at most for the one request already running (no preemption) |

The two clients run in the test process, so they share one app identity; the per-app turns of `PriorityGate` are covered by its unit tests. Emulator reference (CPU, eg2-440m, n=12): load 0.3 s, query p50 35 ms, image p50 0.73 s, two clients 28 requests/s, interactive next to images p50 0.76 s. Real-device GPU numbers still need to be recorded here (roadmap).

## Publishing the SDK

JitPack builds the SDK from a git tag (`jitpack.yml` runs `:sdk:publishReleasePublicationToMavenLocal`):

1. Bump `version` in `sdk/build.gradle.kts`.
2. Tag and push: `git tag -a sdk-vX.Y.Z -m "OndolSaem SDK X.Y.Z"`, `git push origin sdk-vX.Y.Z`. Clients use the tag name as the version.
3. Local check without JitPack: `./gradlew :sdk:publishReleasePublicationToMavenLocal` (writes to `~/.m2`).

## Release build

Same scheme as Nunsori, with its own property names: `ONDOLSAEM_STORE_FILE`, `ONDOLSAEM_STORE_PASSWORD`, `ONDOLSAEM_KEY_ALIAS`, `ONDOLSAEM_KEY_PASSWORD` in `~/.gradle/gradle.properties` (or environment variables). Use the **same keystore and key as Nunsori**, so the service's signature check accepts release builds of both apps. Without these properties `./gradlew assembleRelease` builds an unsigned APK. Bump `appVersionName` in `app/build.gradle.kts` first.
