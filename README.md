# OndolSaem / 온돌샘

**English** | [한국어](README.ko.md)

OndolSaem is an Android app that hosts on-device AI models for other apps. It downloads curated models per category (text/image/audio embedding, chat LLM, OCR, speech recognition, faces), keeps them in memory according to a per-category policy, and serves them to client apps over a bound AIDL service. A model is loaded once for all apps instead of once per app.

The name joins *ondol* (온돌, the floor heating that warms a whole house from underneath) and *saem* (샘, a spring everyone draws water from). As acronyms:
- **ONDOL**: On-device Neural Deployment & Orchestration Layer
- **SAEM**: Shared AI Engine Manager

Status: early development (0.2.0). The first client is [Nunsori / 눈소리](https://github.com/taehunkim/nunsori), an on-device semantic search app.

## Features

| Feature | Description |
|---|---|
| Model categories | Each category has a short description and curated models; one downloaded model is the category default |
| Downloads | Warning before downloading on a metered network (mobile data or metered Wi-Fi), with a "wait for Wi-Fi" option |
| Model changes | Changing an embedding model warns which apps must re-embed their data; client apps are notified right away or on their next connection |
| Residency | Per category: keep loaded, unload right after use, or unload after N minutes. Unloaded models are loaded again on the next request. If Android refuses to keep the host running in the background, a notification and a dashboard card offer to turn off battery optimization |
| First run | A short guide: what OndolSaem is, connected apps, recommended models to download (740M embedding with audio by default), notifications |
| Dashboard | Device memory, the host's memory use, loaded models and which apps hold them |
| Apps | Apps that use the host, their categories and request statistics; own apps (same signing key or an allowed key) connect directly; other apps ask first and are allowed, denied or revoked here |
| Client SDK | `com.github.TaeHunKim:ondolsaem:<tag>` on JitPack, see [docs/architecture.md](docs/architecture.md) |

## Documentation

- [docs/architecture.md](docs/architecture.md): service contract, categories, residency, model-change notifications, access control
- [docs/development.md](docs/development.md): build, scripts, SDK publishing, working with a client app
- [docs/client-guide.md](docs/client-guide.md): using OndolSaem from an app
- [docs/privacy.md](docs/privacy.md): what is stored, network, who can connect
- [docs/roadmap.md](docs/roadmap.md): planned work

## License

Apache License 2.0, see [LICENSE](LICENSE). Third-party components: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
