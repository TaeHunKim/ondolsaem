package io.github.taehunkim.ondolsaem.catalog

import androidx.annotation.StringRes
import io.github.taehunkim.ondolsaem.R
import io.github.taehunkim.ondolsaem.sdk.OndolSaem.Categories

sealed interface ModelSource {
    /** Downloaded by the user from Hugging Face into app storage. */
    data class HuggingFace(
        val repo: String,
        val fileName: String,
        val sha256: String? = null,
        /** More files of the same repository (path in the repo → SHA-256), stored next to the main file by name. */
        val extras: Map<String, String?> = emptyMap(),
        /**
         * Converted on the phone after the download (the downloaded main file is then deleted), e.g.
         * [Conversions.NLI_W8]; the model file the runtime opens is then [ModelManager.modelFile].
         */
        val convert: String? = null,
    ) : ModelSource {
        val url: String get() = url(fileName)
        fun url(path: String): String = "https://huggingface.co/$repo/resolve/main/$path"
    }

    /**
     * One archive (tar.bz2) downloaded and unpacked; [files] maps an entry name suffix to the local file name,
     * [sha256] the local file name to the expected hash of the unpacked file.
     */
    data class Archive(val url: String, val files: Map<String, String>, val sha256: Map<String, String> = emptyMap()) : ModelSource

    object Conversions {
        /** fp32 mDeBERTa → weight-only int8 (engine/NliW8Converter, assets/nli-w8). */
        const val NLI_W8 = "nli-w8"
    }

    /** Shipped inside the APK (small). */
    data object Bundled : ModelSource

    /** Listed in the catalog but not implemented yet. */
    data object Planned : ModelSource
}

data class ModelSpec(
    val id: String,
    /** A [ModelCategory] id. */
    val category: String,
    val displayName: String,
    @StringRes val descriptionRes: Int,
    val sizeBytes: Long?,
    val license: String,
    val source: ModelSource,
    /** Embedding models only: can embed audio clips (needs the audio encoder). */
    val audio: Boolean = false,
    /** Chat models only: accepts images as input. */
    val vision: Boolean = false,
    /** Bump when the same id starts producing different outputs (clients then redo their derived data). */
    val version: Int = 1,
)

/**
 * Curated models. SHA-256 values are the Hugging Face LFS ids (`/api/models/<repo>/tree/main`); for the SenseVoice
 * archive they are the hashes of the unpacked files of the official release. Re-check sizes and hashes when a model
 * file changes upstream.
 */
object ModelCatalog {
    val all: List<ModelSpec> = listOf(
        ModelSpec(
            id = "eg2-440m", category = Categories.EMBEDDING,
            displayName = "EmbeddingGemma 2 · text+vision 440M",
            descriptionRes = R.string.model_eg2_440m_desc,
            sizeBytes = 387_710_976L, license = "Apache-2.0",
            source = ModelSource.HuggingFace(
                "litert-community/embeddinggemma-2-text-vision-440m-litert-lm",
                "embeddinggemma-2-text-vision-440m.litertlm",
                "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0",
            ),
        ),
        ModelSpec(
            id = "eg2-740m", category = Categories.EMBEDDING,
            displayName = "EmbeddingGemma 2 · 740M (+audio)",
            descriptionRes = R.string.model_eg2_740m_desc,
            sizeBytes = 484_622_336L, license = "Apache-2.0",
            source = ModelSource.HuggingFace(
                "litert-community/embeddinggemma-2-740m-litert-lm",
                "embeddinggemma-2-740m.litertlm",
                "e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c",
            ),
            audio = true,
        ),
        ModelSpec(
            id = "mobileclip2-s0", category = Categories.EMBEDDING,
            displayName = "MobileCLIP2-S0",
            descriptionRes = R.string.model_mobileclip2_desc,
            sizeBytes = null, license = "Apple ASCL",
            source = ModelSource.Planned,
        ),
        ModelSpec(
            id = "chat-gemma4-e2b", category = Categories.LLM,
            displayName = "Gemma 4 E2B (chat, images)",
            descriptionRes = R.string.model_chat_gemma4_e2b_desc,
            sizeBytes = 2_588_147_712L, license = "Apache-2.0",
            source = ModelSource.HuggingFace(
                "litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm",
                "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            ),
            vision = true,
        ),
        ModelSpec(
            id = "chat-gemma4-e4b", category = Categories.LLM,
            displayName = "Gemma 4 E4B (chat, images)",
            descriptionRes = R.string.model_chat_gemma4_e4b_desc,
            sizeBytes = 3_659_530_240L, license = "Apache-2.0",
            source = ModelSource.HuggingFace(
                "litert-community/gemma-4-E4B-it-litert-lm", "gemma-4-E4B-it.litertlm",
                "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
            ),
            vision = true,
        ),
        ModelSpec(
            id = "chat-qwen25-1.5b", category = Categories.LLM,
            displayName = "Qwen2.5 1.5B (chat, text only)",
            descriptionRes = R.string.model_chat_qwen_desc,
            sizeBytes = 1_597_922_856L, license = "Apache-2.0",
            source = ModelSource.HuggingFace(
                "litert-community/Qwen2.5-1.5B-Instruct", "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                "c4a9b7289365ab80fe599b844b77ad253eb3c53fb0e5d963d699146a6f01255e",
            ),
        ),
        ModelSpec(
            id = "mlkit-ko", category = Categories.OCR,
            displayName = "ML Kit Text Recognition v2 (Korean + Latin)",
            descriptionRes = R.string.model_mlkit_desc,
            sizeBytes = null, license = "ML Kit Terms",
            source = ModelSource.Bundled,
        ),
        ModelSpec(
            id = "asr-sensevoice", category = Categories.ASR,
            displayName = "SenseVoice (Korean, English, Chinese, Japanese)",
            descriptionRes = R.string.model_sensevoice_desc,
            sizeBytes = 163_002_883L, license = "FunASR Model License",
            source = ModelSource.Archive(
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2",
                mapOf("model.int8.onnx" to "model.int8.onnx", "tokens.txt" to "tokens.txt"),
                mapOf(
                    "model.int8.onnx" to "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51",
                    "tokens.txt" to "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc",
                ),
            ),
        ),
        ModelSpec(
            id = "face-sface", category = Categories.FACE,
            displayName = "SFace (face recognition) + ML Kit face detection",
            descriptionRes = R.string.model_sface_desc,
            sizeBytes = 38_696_353L, license = "Apache-2.0 (SFace), ML Kit Terms (detection)",
            source = ModelSource.HuggingFace(
                "opencv/face_recognition_sface", "face_recognition_sface_2021dec.onnx",
                "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79",
            ),
        ),
        ModelSpec(
            // the published int8 builds give wrong answers on sherpa-onnx's ONNX Runtime (see NliW8Converter):
            // the fp32 file is downloaded and turned into weight-only int8 (341 MB) on the phone
            id = "nli-mdeberta-v3-w8", category = Categories.DECISION,
            displayName = "mDeBERTa-v3 NLI · int8 (multilingual, Korean)",
            descriptionRes = R.string.model_nli_w8_desc,
            sizeBytes = 1_116_156_872L + 16_316_225L, license = "MIT",
            source = ModelSource.HuggingFace(
                NLI_REPO, "onnx/model.onnx", "af43c04cb326eec0629bb8de8ccd4d8a73db6030272047c5f03f66277d059437",
                extras = mapOf("tokenizer.json" to NLI_TOKENIZER_SHA), convert = ModelSource.Conversions.NLI_W8,
            ),
        ),
        ModelSpec(
            id = "nli-mdeberta-v3-q4", category = Categories.DECISION,
            displayName = "mDeBERTa-v3 NLI · q4 (multilingual, Korean)",
            descriptionRes = R.string.model_nli_q4_desc,
            sizeBytes = 886_137_147L + 16_316_225L, license = "MIT",
            source = ModelSource.HuggingFace(
                NLI_REPO, "onnx/model_q4.onnx", "09fcf761615f574942f0a95e08b3221da7e13cb335141609630eaffd223beb17",
                extras = mapOf("tokenizer.json" to NLI_TOKENIZER_SHA),
            ),
        ),
        ModelSpec(
            id = "d1-omni-600m", category = Categories.DECISION,
            displayName = "LiquidAI d1-omni 600M (text, images, audio)",
            descriptionRes = R.string.model_d1_desc,
            sizeBytes = null, license = "LFM Open License 1.0",
            source = ModelSource.Planned,
        ),
        ModelSpec(
            id = "asr-moonshine", category = Categories.ASR,
            displayName = "Moonshine tiny (Korean / English)",
            descriptionRes = R.string.model_moonshine_desc,
            sizeBytes = null, license = "MIT",
            source = ModelSource.Planned,
        ),
        ModelSpec(
            id = "ppocrv5-ko", category = Categories.OCR,
            displayName = "PP-OCRv5 mobile (Korean)",
            descriptionRes = R.string.model_ppocr_desc,
            sizeBytes = null, license = "Apache-2.0",
            source = ModelSource.Planned,
        ),
    )

    private const val NLI_REPO = "onnx-community/mDeBERTa-v3-base-xnli-multilingual-nli-2mil7-ONNX"
    private const val NLI_TOKENIZER_SHA = "ac9bd80d04d21b4df95917bdf7c750cbea28acc30a4462c76ca2b9c86d371863"

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }
    fun ofCategory(category: String): List<ModelSpec> = all.filter { it.category == category }
}
