# Privacy

OndolSaem has no server, no account and no analytics. It runs AI models on the phone for other apps.

## What is stored

In the app's private storage (removed on uninstall or "Clear data"):

- Downloaded models under `files/models/<model id>/`.
- Settings (DataStore `host_settings`): the active model and memory policy of each category, the chat context size, the optional Hugging Face token, and the list of client apps (package names and the categories each used). Request counts and timings are kept in memory only.

Inputs that client apps send (texts, images, audio, chat messages) are processed in memory and never stored; speech input is copied to a temporary file in the cache for the duration of the call and deleted afterwards. Chat conversation state stays in memory while the chat model is loaded.

## Network

Only to download models the user asks for:

- `https://huggingface.co/litert-community/...` (EmbeddingGemma 2 and the chat models; the Hugging Face token, if set, is sent only here)
- `https://github.com/k2-fsa/sherpa-onnx/releases/...` (SenseVoice)

Before a download on a metered network (mobile data or metered Wi-Fi) the app asks first. Every downloaded file is checked against a SHA-256 recorded in the app, so a damaged or replaced file is not used.

## Who can use it

Only apps signed with the same key as OndolSaem, or with a key listed in `app/src/main/res/values/allowed_signers.xml`; every call is checked. Other apps get a `SecurityException`.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Model downloads; metered-network check |
| `POST_NOTIFICATIONS` | Download progress and the "models kept in memory" notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Downloads in the background |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Keeping models the user set to "keep loaded" in memory |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Asking the user to turn off battery optimization, offered only after Android refused to keep models loaded from the background; it lets the keep-alive service start in the background |
