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

/** An NPU build of a model: a file of the same Hugging Face repository compiled ahead of time for one chip. */
data class NpuFile(val fileName: String, val sha256: String, val sizeBytes: Long)

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
    /**
     * NPU builds by chip ([io.github.taehunkim.ondolsaem.engine.NpuDevice.soc]), downloaded on demand next to the
     * main file; the main file stays for the GPU/CPU fallback.
     */
    val npu: Map<String, NpuFile> = emptyMap(),
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
            npu = mapOf(
                "TENSOR_G5" to NpuFile("embeddinggemma-2-text-vision-440m_Google_Tensor_G5.litertlm", "96cdbae7b3dd6fa0f829fef2d0dd5bafc0179890d9bafbfe972db852e818d758", 559_418_036L),
                "TENSOR_G6" to NpuFile("embeddinggemma-2-text-vision-440m_Google_Tensor_G6.litertlm", "40e98aeed9ef08abfd91d5d309f64f4fa06195c513931e917bcf5ea41a910668", 513_166_004L),
                "MT6991" to NpuFile("embeddinggemma-2-text-vision-440m_MediaTek_MT6991.litertlm", "53cb4b8b3d8ca3e022ceec1dd809ddd26c281721c4652e1fadc82d2433cbc5de", 798_097_408L),
                "MT6993" to NpuFile("embeddinggemma-2-text-vision-440m_MediaTek_MT6993.litertlm", "1cfc52d73266fe0e099e853b816150c22deb32fea421cabf9647dc8d8112ede9", 767_770_624L),
                "SM8550" to NpuFile("embeddinggemma-2-text-vision-440m_Qualcomm_SM8550.litertlm", "4427eecca4289555e72b297308f4c9cf97110a657de25dd2fe7946ac824f6214", 465_993_728L),
                "SM8650" to NpuFile("embeddinggemma-2-text-vision-440m_Qualcomm_SM8650.litertlm", "ec7d2ee513830337343567409f32fea4a3e4f8f7b94facd0a20f3d3523cd7480", 465_747_968L),
                "SM8750" to NpuFile("embeddinggemma-2-text-vision-440m_Qualcomm_SM8750.litertlm", "c5c05cb59833541a2d0d9374c7b50d2c3a406a4a84c85762775e5f9c6d96cd0c", 446_611_456L),
                "SM8850" to NpuFile("embeddinggemma-2-text-vision-440m_Qualcomm_SM8850.litertlm", "661369235aa9fc23cf04a213d4e445c4634073832279f5a261ed22650a8ebc5b", 462_536_704L),
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
            npu = mapOf(
                "TENSOR_G5" to NpuFile("embeddinggemma-2-740m_Google_Tensor_G5.litertlm", "5b53d76c2c7c66d2dc6230ff158ea0033ac5939ac7cfcf827c3a22cdfc742a5e", 657_656_500L),
                "TENSOR_G6" to NpuFile("embeddinggemma-2-740m_Google_Tensor_G6.litertlm", "ed0e4f413e705946eda544078d8bf5acf10fe5aee2946451c81919a1f59665e7", 615_713_460L),
                "MT6991" to NpuFile("embeddinggemma-2-740m_MediaTek_MT6991.litertlm", "99e6a35b824b4f7ad4f7c7f20c59ba380aa90e0b4442aadfee3674230c6c7cca", 895_008_768L),
                "MT6993" to NpuFile("embeddinggemma-2-740m_MediaTek_MT6993.litertlm", "ed2a2554bfb6d7abcafd0e9e36ee2f8d7de82888e7ed0fc52dd144e65b171742", 864_681_984L),
                "SM8550" to NpuFile("embeddinggemma-2-740m_Qualcomm_SM8550.litertlm", "802686a865fd7350f2ce4b6fa17ce02a411f56b5b508ec2bd8cb7310b2db82e6", 562_905_088L),
                "SM8650" to NpuFile("embeddinggemma-2-740m_Qualcomm_SM8650.litertlm", "3b8a18c6d043b8132c4eda45611296e1ae455ada82cda8b591ec0ca141e041c5", 562_659_328L),
                "SM8750" to NpuFile("embeddinggemma-2-740m_Qualcomm_SM8750.litertlm", "ac1973c0195dc6e28a621651f2f5e04d35fdc7a7b11cdbbe94f29f5dccb57996", 543_522_816L),
                "SM8850" to NpuFile("embeddinggemma-2-740m_Qualcomm_SM8850.litertlm", "19f6e8db6aa74b116d1c558c862ee131f60ce093576d3c8dd1898f656ca8080b", 559_448_064L),
            ),
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
            npu = mapOf(
                "TENSOR_G5" to NpuFile("gemma-4-E2B-it_Google_Tensor_G5.litertlm", "72d056066f34362adcecf6b76995de67ad899f6f33dfe9079593c1acd2984191", 3_206_299_658L),
                "TENSOR_G6" to NpuFile("gemma-4-E2B-it_Google_Tensor_G6.litertlm", "d6cbe8d625f4965a8c32565f65ae774d19598d0d967a4c2101a7e04988daaf58", 3_453_960_202L),
            ),
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
