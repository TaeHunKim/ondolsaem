package io.github.taehunkim.ondolsaem.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test fun everyModelBelongsToAKnownCategory() {
        for (m in ModelCatalog.all) assertNotNull("${m.id} -> ${m.category}", ModelCategories.byId(m.category))
    }

    @Test fun categoryDefaultsExistInTheirCategory() {
        for (c in ModelCategories.all) {
            val spec = ModelCatalog.byId(c.defaultModelId)
            assertNotNull(c.id, spec)
            assertEquals(c.id, spec!!.category)
            assertTrue("default of ${c.id} must be usable", spec.source !is ModelSource.Planned)
        }
    }

    @Test fun idsAreUnique() {
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.id }.toSet().size)
    }

    @Test fun embeddingChangeRequiresReindex() {
        assertEquals(ChangeImpact.REINDEX_REQUIRED, ModelCategories.byId("embedding")!!.changeImpact)
    }
}
