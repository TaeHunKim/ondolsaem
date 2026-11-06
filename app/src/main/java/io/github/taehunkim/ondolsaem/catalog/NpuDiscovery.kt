package io.github.taehunkim.ondolsaem.catalog

import io.github.taehunkim.ondolsaem.download.DownloadException
import io.github.taehunkim.ondolsaem.engine.NpuDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** An NPU build of a model: a file of the model's Hugging Face repository compiled ahead of time for one chip. */
data class NpuFile(val fileName: String, val sha256: String, val sizeBytes: Long)

/** A file listed by the Hugging Face API (`/api/models/<repo>/tree/main`); [sha256] is its LFS id. */
data class RepoFile(val path: String, val size: Long, val sha256: String?)

/**
 * Finds the NPU builds of the catalog's LiteRT-LM models for this phone's chip by listing their Hugging Face
 * repositories, so builds published after this app version are found too.
 *
 * Publishers name NPU builds `<main file name>_<Vendor>_<Chip>.litertlm` (e.g.
 * `embeddinggemma-2-740m_Qualcomm_SM8750.litertlm`, `gemma-4-E2B-it_Google_Tensor_G5.litertlm`): a file matches
 * when its suffix, in letters and digits, ends with the chip ([NpuDevice.key]). The LFS id is the file's SHA-256,
 * checked after the download like every model file.
 */
class NpuDiscovery(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build(),
    private val apiBase: String = "https://huggingface.co",
) {
    /** The result of one check: chip, time, and the NPU build per model id (models without one are absent). */
    data class Result(val soc: String, val checkedAt: Long, val builds: Map<String, NpuFile>, val checked: Set<String>)

    /** Lists the repositories of [models] (LiteRT-LM files only) and matches their files against [device]. */
    suspend fun check(models: List<ModelSpec>, device: NpuDevice, token: String?, now: Long): Result = withContext(Dispatchers.IO) {
        val bySource = models.mapNotNull { m -> (m.source as? ModelSource.HuggingFace)?.takeIf { it.fileName.endsWith(LITERTLM) }?.let { m to it } }
        val listings = bySource.map { it.second.repo }.distinct().associateWith { list(it, token) }
        val builds = bySource.mapNotNull { (m, src) -> match(src.fileName, listings.getValue(src.repo), device)?.let { m.id to it } }.toMap()
        Result(device.soc, now, builds, bySource.map { it.first.id }.toSet())
    }

    private fun list(repo: String, token: String?): List<RepoFile> {
        val req = Request.Builder().url("$apiBase/api/models/$repo/tree/main").apply {
            if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
        }.build()
        val body = try {
            client.newCall(req).execute().use { r ->
                when (r.code) {
                    200 -> r.body.string()
                    401, 403 -> throw DownloadException(DownloadException.Kind.AUTH, "HTTP ${r.code} for $repo")
                    else -> throw DownloadException(DownloadException.Kind.HTTP, "HTTP ${r.code} for $repo")
                }
            }
        } catch (e: IOException) {
            if (e is DownloadException) throw e
            throw DownloadException(DownloadException.Kind.NETWORK, e.message)
        }
        return parseTree(body)
    }

    companion object {
        private const val LITERTLM = ".litertlm"

        fun parseTree(json: String): List<RepoFile> {
            val a = JSONArray(json)
            return (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                if (o.optString("type") != "file") return@mapNotNull null
                RepoFile(o.optString("path"), o.optLong("size"), o.optJSONObject("lfs")?.optString("oid")?.takeIf { it.length == 64 })
            }
        }

        /** The NPU build of the model whose main file is [mainFile] for [device] among [files], if any. */
        fun match(mainFile: String, files: List<RepoFile>, device: NpuDevice): NpuFile? {
            val base = mainFile.removeSuffix(LITERTLM) + "_"
            return files.asSequence()
                .filter { it.path.startsWith(base) && it.path.endsWith(LITERTLM) && !it.path.contains('/') && it.sha256 != null && it.size > 0 }
                .filter { it.path.removePrefix(base).removeSuffix(LITERTLM).uppercase().filter(Char::isLetterOrDigit).endsWith(device.key) }
                .map { NpuFile(it.path, it.sha256!!, it.size) }
                .firstOrNull()
        }

        fun toJson(r: Result): String = JSONObject().apply {
            put("soc", r.soc); put("checkedAt", r.checkedAt)
            put("checked", JSONArray(r.checked.sorted()))
            put("builds", JSONObject().apply {
                r.builds.forEach { (id, f) -> put(id, JSONObject().put("file", f.fileName).put("sha256", f.sha256).put("size", f.sizeBytes)) }
            })
        }.toString()

        fun fromJson(json: String): Result? = runCatching {
            val o = JSONObject(json)
            val b = o.getJSONObject("builds")
            val c = o.getJSONArray("checked")
            Result(
                o.getString("soc"), o.getLong("checkedAt"),
                b.keys().asSequence().associateWith { id -> b.getJSONObject(id).let { NpuFile(it.getString("file"), it.getString("sha256"), it.getLong("size")) } },
                (0 until c.length()).map { c.getString(it) }.toSet(),
            )
        }.getOrNull()

        fun load(file: File): Result? = file.takeIf { it.exists() }?.let { fromJson(it.readText()) }
    }
}
