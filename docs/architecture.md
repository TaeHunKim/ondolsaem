# Architecture

OndolSaem is a model host: it owns model files, loads models, keeps them in memory by policy and runs inference for client apps over a bound service. Clients never load models themselves. **Status:** the 0.1.0 scaffold has the modules, the `IOndolSaem` entry point (`apiVersion()` only) and the SDK constants; everything else below is the design being implemented.

## Service contract (`:sdk`)

One AIDL interface per model category, reached from a small entry point, so a new category adds an interface without touching existing ones. Methods are only ever appended; clients check `apiVersion()` before calling newer methods.

| Interface | Methods |
|---|---|
| `IOndolSaem` (entry) | `apiVersion()`, `categories()`, `activeModel(category)` → `ModelInfo(id, version, category, dims, capabilities)`, `getCapability(category)` → `IBinder`, `registerClient(IClientCallback)` |
| `IEmbeddingService` | `embedQuery`, `embedDocument`, `embedImage(pfd)`, `embedImages(pfd[])`, `embedAudio(pfd)`; every result carries `modelId` + `modelVersion` |
| `IChatService` | `reply(request, IChatCallback, IToolExecutor)` with streamed tokens; tool calls are executed in the client through `IToolExecutor.execute(name, argsJson)`; `cancel`, `reset(conversationKey)` |
| `IOcrService` | `recognize(pfd)` |
| `IAsrService` | `speechSpans(pfd)` (VAD), `transcribe(pfd, fromMs, ICueCallback)` |

- Large inputs (images, WAV, PCM) go as `ParcelFileDescriptor` because of Binder's ~1 MB transaction limit; the host then needs no storage permission.
- Every request carries a priority: `INTERACTIVE` (chat, search queries) runs before `BACKGROUND` (indexing).
- `OndolSaemClient` in the SDK wraps binding, reconnect and retry when the host process dies, host-missing detection with an install intent, and coroutine APIs.

## Categories

A category is a registry entry (string id, title, description, change impact, default residency), not an enum, so new ones (face recognition, fast decision models, …) are added without changing existing code.

| Category | Change impact |
|---|---|
| Embedding | `REINDEX_REQUIRED`: strong warning listing the apps that must re-embed |
| OCR, speech recognition | `DERIVED_DATA`: notice that derived text may change |
| Chat LLM | `NONE` |

Each category has curated models with descriptions; one downloaded model is the category default. Clients always use the default of a category.

## Downloads

Downloads run in WorkManager with resume. On a metered network (mobile data or metered Wi-Fi) the app warns once before starting and offers "download now" or "wait for Wi-Fi" (`NetworkType.UNMETERED`).

## Residency

Per category: keep loaded, unload right after use, or unload after N minutes. A request for an unloaded model loads it again; clients do not manage this. While a "keep loaded" model is in memory, a foreground service (with a notification showing the loaded models and memory) keeps the host process alive; otherwise the process lives as long as clients are bound. On memory pressure (`onTrimMemory`) models that are not "keep loaded" go first.

## Model-change notifications

When a category default changes:
1. Connected clients get `IClientCallback.onModelChanged(category, ModelInfo)`.
2. Every known client package gets an explicit broadcast `io.github.taehunkim.ondolsaem.action.MODEL_CHANGED` (sent with `setPackage`, permission-protected), which reaches apps that are not running.
3. Clients also compare `activeModel()` with their stored value on connect, and each embedding result carries the model id.

The client decides when to redo its work (right away or when idle).

## Access control

Only own apps may connect:
- The bind permission uses `protectionLevel="signature|knownSigner"` with the SHA-256 digests of both the debug and the release signing certificates (Android 12+).
- The service additionally checks the caller on every call with `PackageManager.hasSigningCertificate(uid, cert, CERT_INPUT_SHA256)` against the same allowlist (works on all supported API levels).

So a debug client can use a release host and the other way round. Opening the host to third-party apps later means adding a user-approval path to that allowlist.

## Dashboard

Memory first: device memory (`ActivityManager.MemoryInfo`), the host's process memory (`Debug.MemoryInfo`: native, graphics, Java), and per loaded model the backend (GPU/CPU), estimated memory, residency policy, remaining idle time and the client apps holding it; usage per client app (last use, request count, latency).
