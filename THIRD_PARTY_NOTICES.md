# Third-party notices

OndolSaem is licensed under the [Apache License 2.0](LICENSE), Copyright 2026 Taehun Kim.
It uses the components below. Versions come from [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Libraries (linked into the app)

| Component | Version | License |
|---|---|---|
| AndroidX (Compose BoM, Material 3, Activity, Lifecycle, WorkManager, DataStore, AppCompat) | see catalog (Compose BoM 2026.09.00, WorkManager 2.12.0) | Apache-2.0 |
| Kotlin, kotlinx.coroutines | 2.4.21 / 1.11.0 | Apache-2.0 |
| OkHttp | 5.5.1 | Apache-2.0 |
| LiteRT-LM (`litertlm-android`) | 0.19.0 | Apache-2.0 |
| sherpa-onnx (AAR, downloaded by `scripts/fetch-sherpa-onnx.sh`, not committed) | 2.0.1 | Apache-2.0 |
| Apache Commons Compress | 1.28.0 | Apache-2.0 |
| ML Kit Text Recognition (Korean) | 16.0.1 | [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms) (proprietary; fetched through Gradle, not redistributed in this repository) |

The client SDK (`:sdk`) uses only kotlinx.coroutines and the Android framework.

## Models

No model is stored in this repository. The app downloads them at the user's request (Models tab), except Silero VAD,
which is fetched at build time and bundled in the APK. Each model keeps its own license; check it before
redistributing.

| Model | Size | Source | License as stated by the source |
|---|---|---|---|
| EmbeddingGemma 2 text+vision 440M | 388 MB | Hugging Face `litert-community/embeddinggemma-2-text-vision-440m-litert-lm` | Apache-2.0 (model card) |
| EmbeddingGemma 2 740M (+audio) | ~485 MB | Hugging Face `litert-community/embeddinggemma-2-740m-litert-lm` | Apache-2.0 (model card) |
| Gemma 4 E2B / E4B (chat) | 2.6 GB / 3.7 GB | Hugging Face `litert-community/gemma-4-E2B-it-litert-lm`, `gemma-4-E4B-it-litert-lm` | Apache-2.0 (model card) |
| Qwen2.5 1.5B Instruct (chat) | 1.6 GB | Hugging Face `litert-community/Qwen2.5-1.5B-Instruct` | Apache-2.0 (model card) |
| SenseVoice small, int8 (via sherpa-onnx) | 163 MB archive | GitHub `k2-fsa/sherpa-onnx` release `asr-models` | FunASR Model License |
| Silero VAD | 0.6 MB | GitHub `k2-fsa/sherpa-onnx` release `asr-models` | MIT |
| ML Kit Korean text recognition | n/a | Google (Gradle dependency) | ML Kit Terms |

The EmbeddingGemma model cards say Apache-2.0 but also link to the Gemma terms of use; the catalog in
[`ModelCatalog.kt`](app/src/main/java/io/github/taehunkim/ondolsaem/catalog/ModelCatalog.kt) follows the card.
