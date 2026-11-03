# Architecture

OndolSaem is a model host: it owns model files, loads models, keeps them in memory by policy and runs inference for client apps over a bound service. Clients never load models themselves. Code map (`app/src/main/java/io/github/taehunkim/ondolsaem/`): `catalog/` (categories and curated models), `download/` (resumable downloads in WorkManager), `engine/` (LiteRT-LM embedding and chat, ML Kit OCR, sherpa-onnx speech), `runtime/` (`ModelRuntime`, `LeasedResource`, `PriorityGate`, `KeepAliveService`), `service/` (`HostService`, `CallerCheck`, `ClientRegistry`, `ModelChangeNotifier`), `settings/`, `ui/`. The contract and client live in `sdk/`.

## Service contract (`:sdk`)

One AIDL interface per model category, reached from a small entry point, so a new category adds an interface without touching existing ones. Methods are only ever appended; clients check `apiVersion()` before calling newer methods.

| Interface | Methods |
|---|---|
| `IOndolSaem` (entry) | `apiVersion()`, `categories()`, `activeModel(category)` → `ModelInfo(category, id, displayName, version, ready, audio, vision)`, `getCapability(category)` → `IBinder`, `registerClient` / `unregisterClient(IClientCallback)`, `acquire(category, clientToken)` → `ILease` (keeps a model loaded, e.g. for one chat turn; dropped when the client process dies) |
| `IEmbeddingService` | `embedQuery`, `embedDocument`, `embedImage(pfd)`, `embedImages(pfd[])`, `embedAudio(pfd)`; every result carries `modelId` + `modelVersion` |
| `IChatService` | `reply(request, IChatCallback, IToolExecutor, priority)` returns at once and streams tokens; tool calls are executed in the client through `IToolExecutor.execute(name, argsJson)`; `cancel(requestId)`, `reset()`. The conversation key is prefixed with the client package. |
| `IOcrService` | `recognize(pfd)` |
| `IAsrService` | `canTranscribe()`, `speechSpans(pfd)` (VAD), `transcribe(pfd, fromMs, ICueCallback)`; input is raw 16 kHz mono 16-bit PCM; the callback returns false to stop |

- Large inputs (images, WAV, PCM) go as `ParcelFileDescriptor` because of Binder's ~1 MB transaction limit; the host then needs no storage permission.
- Every request carries a priority: `INTERACTIVE` (chat, search queries) runs before `BACKGROUND` (indexing).
- Errors: `ServiceSpecificException` is not public API, so the host throws `IllegalStateException("ondolsaem:<code>:<message>")` (`HostErrors`), which the client turns into `HostCallException(code)` with the codes in `OndolSaem.Errors` (`MODEL_NOT_READY`, `MODEL_FAILED`, `BAD_INPUT`, `CANCELLED`). A refused caller gets a `SecurityException` (client: `HostUnavailableException(NOT_ALLOWED)`).
- `OndolSaemClient` in the SDK binds on first use (`BIND_AUTO_CREATE | BIND_IMPORTANT`), retries a call once when the host process died (not chat), unbinds after an idle minute, detects a missing host (`isInstalled()`, `launchIntent()`), and exposes suspend functions and a chat `Flow`.

## Categories

A category is a registry entry (string id, title, description, change impact, default residency), not an enum, so new ones (face recognition, fast decision models, …) are added without changing existing code.

| Category | Change impact |
|---|---|
| Embedding | `REINDEX_REQUIRED`: strong warning listing the apps that must re-embed |
| OCR, speech recognition | `DERIVED_DATA`: notice that derived text may change |
| Chat LLM | `NONE` |

Each category has curated models with descriptions; one model is the category default (stored in DataStore, `HostSettings`). Clients always use the default of a category. A category whose default is not downloaded answers `MODEL_NOT_READY`; speech still offers voice activity detection (Silero VAD is bundled).

## Downloads

Downloads run in WorkManager with resume (`.part` file + HTTP Range) and a progress notification. On a metered network (mobile data or metered Wi-Fi, `ConnectivityManager.isActiveNetworkMetered`) the app warns before starting and offers "download now" or "wait for Wi-Fi" (`NetworkType.UNMETERED`, shown as "Waiting for Wi-Fi"). There is no checksum check yet beyond the byte count.

## Residency

Per category: keep loaded, unload right after use, or unload after 1, 2, 5 or 15 minutes (default 2). A request for an unloaded model loads it again; clients do not manage this. Each category holds one model in a `LeasedResource`: every request takes a lease, and the idle timer starts when the last lease is returned. Requests of one category run one at a time through a `PriorityGate`, where waiting interactive requests go before waiting background ones.

While a "keep loaded" model is in memory, `KeepAliveService` (foreground service, type `specialUse`, notification "N models in memory") keeps the host process alive; otherwise the process lives as long as clients are bound. Android may refuse to start the service while the host is in the background (Android 12+); it then starts the next time the host app is opened, and the model stays loaded only as long as the process does. On memory pressure (`onTrimMemory` at `RUNNING_LOW` or worse) idle models that are not "keep loaded" are unloaded.

## Model-change notifications

When a category default changes (the user picks another model with a warning for `REINDEX_REQUIRED` / `DERIVED_DATA` categories that lists the apps using it), the active model finishes downloading, or the active model is deleted:
1. Connected clients get `IClientCallback.onModelChanged(category, ModelInfo)`.
2. Every known client package gets an explicit broadcast `io.github.taehunkim.ondolsaem.action.MODEL_CHANGED` (sent with `setPackage`), which reaches apps that are not running. The SDK's manifest declares `ModelChangedReceiver` for it in every client; the app sets `ModelChangedReceiver.handler` in `Application.onCreate`. Anyone could send this action, so it is only a hint to re-check the host. The host's `<queries>` entry for the action makes client apps visible to it.
3. Clients also compare `activeModel()` with their stored value on connect, and each embedding result carries the model id.

The client decides when to redo its work (right away or when idle).

## Access control

Only own apps may connect. The service is exported **without a permission**; `CallerCheck` verifies the caller on every call instead: a caller is allowed when one of its packages is signed with the host's own key (`checkSignatures`) or with a certificate listed in `res/values/allowed_signers.xml` (SHA-256 hex, checked with `hasSigningCertificate`). The list holds the shared release key and the maintainer's debug key, so a debug client can use a release host and the other way round. Results are cached per uid.

A custom `signature` permission was dropped on purpose: Android grants a permission only if its defining app is installed first, so a client installed before the host would never get it. Opening the host to third-party apps later means adding a user-approval path to the allowlist.

## Dashboard

Memory first, refreshed every 2 seconds: device memory (`ActivityManager.MemoryInfo`), the host's process memory (`Debug.MemoryInfo`: total PSS, native, graphics, Java), and per category the loaded model with backend (GPU/CPU), its memory (PSS growth measured while loading), the apps holding it, the time left before it unloads, the policy, and "load now" / "unload now". The Apps tab lists the client apps (stored in DataStore) with the categories they use and per-category request counts and average latency since the host started (`ClientRegistry`).
