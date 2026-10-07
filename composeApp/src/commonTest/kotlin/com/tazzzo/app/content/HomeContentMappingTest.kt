package com.tazzzo.app.content

import com.tazzzo.app.data.content.ContentLink
import com.tazzzo.app.data.content.HomeBlock
import com.tazzzo.app.data.content.HomeBlockDto
import com.tazzzo.app.data.content.HomeContentDto
import com.tazzzo.app.data.content.toDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The published Home is rendered as published — and nothing the backend did not publish is invented. */
class HomeContentMappingTest {

    private fun banner(id: String = "B1", image: String? = "https://cdn.example.test/b.jpg", link: String? = "product:TZP-1") =
        HomeBlockDto(blockId = id, type = "BANNER", title = "Festive", imageUrl = image, link = link, ids = null)

    @Test fun theLinkGrammarIsClosed() {
        assertEquals(ContentLink.Product("TZP-1"), ContentLink.parse("product:TZP-1"))
        assertEquals(ContentLink.Category("TZV-000037"), ContentLink.parse(" Category:TZV-000037 "))
        assertEquals(ContentLink.Search("atta 5 kg"), ContentLink.parse("search:atta 5 kg"))
        for (bad in listOf(null, "", "product:", "product:ABC", "product:TZP-1;drop", "category:TZP-1", "category:TZV-1",
            "search:", "search:" + "x".repeat(101), "search:a\u0000b", "https://evil.example.test", "deeplink:x", ":product")) {
            assertNull(ContentLink.parse(bad), "must refuse <$bad>")
        }
    }

    @Test fun blocksKeepTheServerOrderAndUnknownTypesAreSkipped() {
        val c = HomeContentDto(listOf(
            HomeBlockDto("R1", "PRODUCT_RAIL", "Staples", ids = listOf("TZP-1", "TZP-2")),
            HomeBlockDto("X1", "VIDEO", "Nope"),
            banner("B1"),
            HomeBlockDto("G1", "CATEGORY_GRID", "Shop by aisle", ids = listOf("TZS-000001", "TZV-000037"))
        )).toDomain()
        assertEquals(listOf("R1", "B1", "G1"), c.blocks.map { it.blockId })
        assertIs<HomeBlock.ProductRail>(c.blocks[0]); assertIs<HomeBlock.Banner>(c.blocks[1]); assertIs<HomeBlock.CategoryGrid>(c.blocks[2])
    }

    @Test fun aBannerNeedsAnHttpsImageAndKeepsAnUnparseableLinkAsUntappable() {
        assertTrue(HomeContentDto(listOf(banner(image = "http://cdn.example.test/b.jpg"))).toDomain().blocks.isEmpty())
        assertTrue(HomeContentDto(listOf(banner(image = null))).toDomain().blocks.isEmpty())
        val b = HomeContentDto(listOf(banner(link = "javascript:alert(1)"))).toDomain().blocks.single() as HomeBlock.Banner
        assertEquals("https://cdn.example.test/b.jpg", b.imageUrl); assertNull(b.link); assertEquals("Festive", b.title)
    }

    @Test fun idsAreValidatedDedupedAndCappedAndAnEmptyRailIsDropped() {
        val rail = HomeContentDto(listOf(HomeBlockDto("R1", "PRODUCT_RAIL", "x",
            ids = (1..20).map { "TZP-$it" } + listOf("TZP-1", "bogus", "TZS-000001")))).toDomain().blocks.single() as HomeBlock.ProductRail
        assertEquals((1..HomeBlock.MAX_RAIL_IDS).map { "TZP-$it" }, rail.productIds)
        assertTrue(HomeContentDto(listOf(HomeBlockDto("R2", "PRODUCT_RAIL", "x", ids = listOf("bogus")))).toDomain().blocks.isEmpty())
        val grid = HomeContentDto(listOf(HomeBlockDto("G1", "CATEGORY_GRID", "x", ids = listOf("TZV-000037", "TZV-000037", "TZP-1")))).toDomain().blocks.single() as HomeBlock.CategoryGrid
        assertEquals(listOf("TZV-000037"), grid.nodeIds)
    }

    @Test fun duplicateBlockIdsBlankIdsAndMoreThanTheCapAreIgnored() {
        val dup = HomeContentDto(listOf(banner(id = "B1"), banner(id = "B2"), banner(id = "B1"), banner(id = " "), banner(id = " B2 "))).toDomain()
        assertEquals(listOf("B1", "B2"), dup.blocks.map { it.blockId }, "a repeated id (even padded) keeps its first block; a blank id is skipped")
        val many = HomeContentDto((1..30).map { banner(id = "B$it") }).toDomain()
        assertEquals(20, many.blocks.size)
        assertEquals((1..20).map { "B$it" }, many.blocks.map { it.blockId })
    }

    @Test fun theGridCapAndTheTitleBoundsApply() {
        val grid = HomeContentDto(listOf(HomeBlockDto("G1", "CATEGORY_GRID", "  Aisles  ", ids = (1..15).map { "TZV-%06d".format(it) }))).toDomain().blocks.single() as HomeBlock.CategoryGrid
        assertEquals(12, grid.nodeIds.size); assertEquals("Aisles", grid.title)
        val long = HomeContentDto(listOf(banner().copy(title = "x".repeat(500)))).toDomain().blocks.single()
        assertEquals(80, long.title.length)
    }

    @Test fun explicitNullsFromTheWireAreTreatedAsAbsent() {
        val c = HomeContentDto(listOf(
            HomeBlockDto(blockId = "R1", type = "PRODUCT_RAIL", title = null, ids = listOf("TZP-1")),
            HomeBlockDto(blockId = "R2", type = "PRODUCT_RAIL", title = "x", ids = null),
            HomeBlockDto(blockId = null, type = "BANNER", title = "x", imageUrl = "https://cdn.example.test/b.jpg"),
            HomeBlockDto(blockId = "B3", type = null, title = "x", imageUrl = "https://cdn.example.test/b.jpg")
        )).toDomain()
        assertEquals(listOf("R1"), c.blocks.map { it.blockId })
        assertEquals("", c.blocks[0].title)
    }
}
