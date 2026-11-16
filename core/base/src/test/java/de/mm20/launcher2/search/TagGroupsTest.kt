package de.mm20.launcher2.search

import org.junit.Assert
import org.junit.Test

class TagGroupsTest {

    @Test
    fun groupsByTagThenUntagged() {
        val groups = groupByTag(
            items = listOf("chrome", "maps", "chess", "notes", "firefox"),
            tagsByKey = mapOf(
                "chrome" to listOf("internet"),
                "firefox" to listOf("Internet"),
                "chess" to listOf("Games", "internet"),
                "maps" to emptyList(),
            ),
            key = { it },
        )
        Assert.assertEquals(
            listOf(
                TagGroup("Games", listOf("chess")),
                // different tags, sorted ignoring case
                TagGroup("Internet", listOf("firefox")),
                TagGroup("internet", listOf("chrome", "chess")),
                TagGroup(null, listOf("maps", "notes")),
            ),
            groups,
        )
    }

    @Test
    fun noUntaggedGroupWhenEverythingIsTagged() {
        val groups = groupByTag(listOf("a"), mapOf("a" to listOf("x")), key = { it })
        Assert.assertEquals(listOf(TagGroup("x", listOf("a"))), groups)
    }
}
