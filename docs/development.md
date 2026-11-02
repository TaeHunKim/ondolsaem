# Development

Design: [architecture.md](architecture.md).

## Setup

- Android Studio (its bundled JBR is the JDK), SDK at `~/Library/Android/sdk` with API 37 platform. `source scripts/env.sh` sets `JAVA_HOME`, `ANDROID_HOME` and `PATH`.
- `local.properties` holds `sdk.dir` (git-ignored).

## Layout

| Module | Contents |
|---|---|
| `:sdk` | Client contract (AIDL in `src/main/aidl`, constants in `OndolSaem`) and client library. Its manifest adds a `<queries>` entry for the host package to every client app. |
| `:app` | The host app. |

## Build and test

| Level | Command |
|---|---|
| Unit | `./gradlew assembleDebug testDebugUnitTest` |
| Instrumented | `./gradlew connectedDebugAndroidTest` (needs a device; uninstalls the app at the end, which deletes downloaded models) |

Scripts (`scripts/`): `emu-start.sh [--window]`, `emu-stop.sh`, `run.sh` (installDebug + launch), `logcat.sh [N]`, `screenshot.sh`. The emulator AVD `lsa_api36` is shared with Nunsori.

## Working with a client app

Clients depend on `com.github.taehunkim.ondolsaem:sdk:<version>`. To change the SDK and a client together, check this repository out next to the client and point the client at it. Nunsori, for example, reads `ondolsaem.dir=../ondolsaem` from its `local.properties` and adds this build as a Gradle composite build. The SDK's `group` (`com.github.taehunkim.ondolsaem`) and project name (`sdk`) must stay equal to the published coordinates for that substitution to work.

## Publishing the SDK

JitPack builds the SDK from a git tag (`jitpack.yml` runs `:sdk:publishReleasePublicationToMavenLocal`):

1. Bump `version` in `sdk/build.gradle.kts`.
2. Tag and push: `git tag -a sdk-vX.Y.Z -m "OndolSaem SDK X.Y.Z"`, `git push origin sdk-vX.Y.Z`. Clients use the tag name as the version.
3. Local check without JitPack: `./gradlew :sdk:publishReleasePublicationToMavenLocal` (writes to `~/.m2`).

## Release build

Same scheme as Nunsori, with its own property names: `ONDOLSAEM_STORE_FILE`, `ONDOLSAEM_STORE_PASSWORD`, `ONDOLSAEM_KEY_ALIAS`, `ONDOLSAEM_KEY_PASSWORD` in `~/.gradle/gradle.properties` (or environment variables). Use the **same keystore and key as Nunsori**, so the service's signature check accepts release builds of both apps. Without these properties `./gradlew assembleRelease` builds an unsigned APK. Bump `appVersionName` in `app/build.gradle.kts` first.
