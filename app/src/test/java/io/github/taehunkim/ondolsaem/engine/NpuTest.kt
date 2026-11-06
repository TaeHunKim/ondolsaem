package io.github.taehunkim.ondolsaem.engine

import io.github.taehunkim.ondolsaem.catalog.ModelCatalog
import io.github.taehunkim.ondolsaem.download.NpuRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuTest {
    @Test fun qualcommChipsMapToTheirHexagonVersion() {
        assertEquals(NpuDevice("SM8750", NpuVendor.QUALCOMM, "V79"), Npu.parse("SM8750"))
        assertEquals(NpuDevice("SM8750", NpuVendor.QUALCOMM, "V79"), Npu.parse("SM8750-3-AB")) // "for Galaxy" bins
        assertEquals("V73", Npu.parse("sm8550")?.hexagon)
        assertEquals("V81", Npu.parse("SM8850")?.hexagon)
    }

    @Test fun mediatekAndTensorChips() {
        assertEquals(NpuDevice("MT6991", NpuVendor.MEDIATEK), Npu.parse("MT6991"))
        assertEquals(NpuDevice("TENSOR_G5", NpuVendor.GOOGLE_TENSOR), Npu.parse("Tensor G5"))
        assertEquals(NpuDevice("TENSOR_G6", NpuVendor.GOOGLE_TENSOR), Npu.parse("TENSOR_G6"))
    }

    @Test fun otherChipsHaveNoNpuBuilds() {
        assertNull(Npu.parse("SM8450"))
        assertNull(Npu.parse("Tensor G4"))
        assertNull(Npu.parse("Exynos 2500"))
        assertNull(Npu.parse(""))
        assertNull(Npu.parse(null))
    }

    @Test fun everyNpuBuildIsForAKnownChipAndComesFromTheModelsRepository() {
        for (m in ModelCatalog.all) for ((soc, f) in m.npu) {
            val d = Npu.parse(soc.replace('_', ' '))
            assertEquals("${m.id} $soc", soc, d?.soc)
            assertEquals("${m.id} $soc", 64, f.sha256.length)
            assertTrue("${m.id} ${f.fileName}", f.fileName.endsWith(".litertlm") && f.sizeBytes > 0)
            val base = (m.source as io.github.taehunkim.ondolsaem.catalog.ModelSource.HuggingFace).fileName.removeSuffix(".litertlm")
            assertTrue("${m.id} ${f.fileName}", f.fileName.startsWith(base + "_"))
        }
    }

    @Test fun embeddingModelsHaveSnapdragon8EliteBuilds() {
        for (id in listOf("eg2-440m", "eg2-740m")) assertTrue(id, "SM8750" in ModelCatalog.byId(id)!!.npu)
    }

    @Test fun qnnLibrariesOfOneHexagonVersion() {
        assertEquals(
            listOf("libQnnHtp.so", "libQnnSystem.so", "libQnnHtpV79Stub.so", "libQnnHtpV79Skel.so"),
            NpuRuntime.qnnLibraries("V79"),
        )
    }
}
