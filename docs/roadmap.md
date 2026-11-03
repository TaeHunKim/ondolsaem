# Roadmap

Open work for OndolSaem, roughly in priority order. Done items move to the release notes in the commit history.

## Planned

1. **More models per category.** Most categories offer one usable model today (`ModelCatalog`: OCR has only ML Kit, speech only SenseVoice, faces only SFace; MobileCLIP2-S0, Moonshine and PP-OCRv5 are listed as *Coming soon*). Add curated alternatives per category, each with a license check (`THIRD_PARTY_NOTICES.md`), a SHA-256, measured size and memory, and a runtime that fits (LiteRT-LM, ML Kit, or ONNX through sherpa-onnx's ONNX Runtime and the JNI bridge, see [architecture.md](architecture.md#faces)). Candidates: MobileCLIP2-S0 (embedding, small, English only), Moonshine tiny (speech), PP-OCRv5 mobile (OCR), YuNet as an alternative face detector, smaller or larger chat models.
2. **Real-device numbers.** Run `scripts/bench-device.sh` on real phones (GPU backend, two clients sharing one model) and record the results in [development.md](development.md#measuring-on-a-device). Only emulator CPU numbers exist so far.
3. **Two apps indexing at the same time, end to end.** Per-app turns of background requests (`PriorityGate`) are covered by unit tests only. An end-to-end check needs a second client with its own package name (for example a small test client app in this repository) indexing next to Nunsori, and the log showing their requests alternate.
