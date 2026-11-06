package io.github.taehunkim.ondolsaem.engine

import io.github.taehunkim.ondolsaem.catalog.NpuDiscovery
import io.github.taehunkim.ondolsaem.catalog.NpuFile
import io.github.taehunkim.ondolsaem.catalog.RepoFile
import io.github.taehunkim.ondolsaem.download.NpuRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuTest {
    @Test fun qualcommChipsMapToTheirHexagonVersion() {
        assertEquals(NpuDevice("SM8750", NpuVendor.QUALCOMM, "V79"), Npu.parse("SM8750"))
        assertEquals(NpuDevice("SM8750", NpuVendor.QUALCOMM, "V79"), Npu.parse("SM8750-3-AB")) // "for Galaxy" bins
        assertEquals("V73", Npu.parse("sm8550")?.hexagon)
        assertEquals("V69", Npu.parse("SM8450")?.hexagon)
    }

    @Test fun anUnknownSnapdragonIsRecognizedButNotUsable() {
        val d = Npu.parse("SM7675")!!
        assertEquals(NpuVendor.QUALCOMM, d.vendor)
        assertFalse(d.usable)
        assertTrue(Npu.parse("SM8750")!!.usable)
    }

    @Test fun mediatekAndTensorChips() {
        assertEquals(NpuDevice("MT6991", NpuVendor.MEDIATEK), Npu.parse("MT6991"))
        assertEquals(NpuDevice("MT6897", NpuVendor.MEDIATEK), Npu.parse("MT6897"))
        assertEquals(NpuDevice("TENSOR_G5", NpuVendor.GOOGLE_TENSOR), Npu.parse("Tensor G5"))
        assertEquals(NpuDevice("TENSOR_G4", NpuVendor.GOOGLE_TENSOR), Npu.parse("Tensor G4"))
        assertTrue(Npu.parse("Tensor G5")!!.usable)
    }

    @Test fun chipsWithoutALiteRtNpu() {
        assertNull(Npu.parse("Exynos 2500"))
        assertNull(Npu.parse("ranchu"))
        assertNull(Npu.parse(""))
        assertNull(Npu.parse(null))
    }

    private val files = listOf(
        RepoFile("embeddinggemma-2-740m.litertlm", 484622336, "e".repeat(64)),
        RepoFile("embeddinggemma-2-740m_Google_Tensor_G5.litertlm", 657656500, "a".repeat(64)),
        RepoFile("embeddinggemma-2-740m_MediaTek_MT6991.litertlm", 895008768, "b".repeat(64)),
        RepoFile("embeddinggemma-2-740m_NPU_Uncompiled.litertlm", 569641652, "c".repeat(64)),
        RepoFile("embeddinggemma-2-740m_Qualcomm_SM8750.litertlm", 543522816, "d".repeat(64)),
        RepoFile("embeddinggemma-2-740m_Qualcomm_QCS8275.litertlm", 562888704, "f".repeat(64)),
        RepoFile("README.md", 100, null),
    )

    @Test fun findsTheBuildForThisChipByItsFileName() {
        val main = "embeddinggemma-2-740m.litertlm"
        assertEquals(NpuFile("embeddinggemma-2-740m_Qualcomm_SM8750.litertlm", "d".repeat(64), 543522816), NpuDiscovery.match(main, files, Npu.parse("SM8750")!!))
        assertEquals("embeddinggemma-2-740m_Google_Tensor_G5.litertlm", NpuDiscovery.match(main, files, Npu.parse("Tensor G5")!!)?.fileName)
        assertEquals("embeddinggemma-2-740m_MediaTek_MT6991.litertlm", NpuDiscovery.match(main, files, Npu.parse("MT6991")!!)?.fileName)
        assertEquals("embeddinggemma-2-740m_Qualcomm_QCS8275.litertlm", NpuDiscovery.match(main, files, Npu.parse("QCS8275")!!)?.fileName)
    }

    @Test fun noBuildForOtherChipsOrOtherModels() {
        assertNull(NpuDiscovery.match("embeddinggemma-2-740m.litertlm", files, Npu.parse("SM8650")!!))
        assertNull(NpuDiscovery.match("embeddinggemma-2-740m.litertlm", files, Npu.parse("Tensor G6")!!))
        assertNull(NpuDiscovery.match("gemma-4-E4B-it.litertlm", files, Npu.parse("SM8750")!!))
    }

    @Test fun parsesTheHuggingFaceTreeAndKeepsResultsAsJson() {
        val tree = """[{"type":"file","path":"a_Qualcomm_SM8750.litertlm","size":5,"lfs":{"oid":"${"1".repeat(64)}","size":5}},
            {"type":"file","path":"README.md","size":3},{"type":"directory","path":"x"}]"""
        val parsed = NpuDiscovery.parseTree(tree)
        assertEquals(listOf(RepoFile("a_Qualcomm_SM8750.litertlm", 5, "1".repeat(64)), RepoFile("README.md", 3, null)), parsed)
        val r = NpuDiscovery.Result("SM8750", 42, mapOf("eg2-740m" to NpuFile("a.litertlm", "1".repeat(64), 5)), setOf("eg2-740m", "eg2-440m"))
        assertEquals(r, NpuDiscovery.fromJson(NpuDiscovery.toJson(r)))
    }

    @Test fun qnnLibrariesOfOneHexagonVersion() {
        assertEquals(
            listOf("libQnnHtp.so", "libQnnSystem.so", "libQnnHtpV79Stub.so", "libQnnHtpV79Skel.so"),
            NpuRuntime.qnnLibraries("V79"),
        )
    }
}
