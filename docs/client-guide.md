# Using OndolSaem from an app

1. Depend on the SDK: `implementation("com.github.taehunkim.ondolsaem:sdk:<version>")` with `maven("https://jitpack.io")` in the repositories (or a composite build of a local checkout, see [development.md](development.md#working-with-a-client-app)). The SDK's manifest adds the `<queries>` entry for the host and the model-change receiver.
2. Sign the app with the same key as OndolSaem, or add its certificate digest to the host's `allowed_signers.xml`.
3. Create one `OndolSaemClient(context)` per process. It binds on first use and unbinds when idle.

```kotlin
val client = OndolSaemClient(context)
if (!client.isInstalled()) { /* show an install hint */ }
val info = client.activeModel(OndolSaem.Categories.EMBEDDING)   // id, version, ready, audio, vision
val v = client.embedQuery("a cat on a sofa")                     // EmbeddingResult(vector, modelId, modelVersion)
client.acquire(OndolSaem.Categories.EMBEDDING).use { /* model stays loaded for a batch */ }
client.chat("conv-1", system, history, "question", tools = listOf(ToolSpec(...))) { name, args -> runTool(name, args) }
    .collect { event -> /* Token / ToolRan */ }
```

- Store `modelId` (and `modelVersion`) next to anything derived from a model. When it differs from `activeModel()` the data is stale.
- React to model changes: collect `client.modelChanges` while running, and set `ModelChangedReceiver.handler` in `Application.onCreate` for changes while the app is not running (treat it as a hint and re-check `activeModel()`).
- Errors: `HostUnavailableException` (`NOT_INSTALLED`, `NOT_ALLOWED`, `NO_ANSWER`, `TOO_OLD`) and `HostCallException` with `OndolSaem.Errors` codes (`MODEL_NOT_READY` means: ask the user to download or pick a model in OndolSaem).
- Use `PRIORITY_BACKGROUND` for bulk work so interactive requests from any app go first.
- Versions: the client exchanges versions with the host on every connection (`hostInfo()` returns the host's level, minimum client level and app version). Pass `minHostApi` to `OndolSaemClient` for the lowest host level your app works with; calls into newer levels are checked by the SDK. Handle `HOST_TOO_OLD` ("update OndolSaem") and `CLIENT_TOO_OLD` ("update this app"); use `supports(OndolSaem.Api.Vx)` to switch optional features off on older hosts.
