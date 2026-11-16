package de.mm20.launcher2.search

/**
 * Items that share a tag. [tag] is null for the items without any tag.
 */
data class TagGroup<T>(val tag: String?, val items: List<T>)

/**
 * Splits [items] by tag: one group per tag sorted by name ignoring case, then the untagged items.
 * An item with more tags is in each of its groups; items keep their order inside a group.
 */
fun <T> groupByTag(
    items: List<T>,
    tagsByKey: Map<String, List<String>>,
    key: (T) -> String,
): List<TagGroup<T>> {
    val byTag = sortedMapOf<String, MutableList<T>>(String.CASE_INSENSITIVE_ORDER.thenComparing(naturalOrder()))
    val untagged = mutableListOf<T>()
    for (item in items) {
        val tags = tagsByKey[key(item)]
        if (tags.isNullOrEmpty()) untagged += item
        else for (tag in tags) byTag.getOrPut(tag) { mutableListOf() } += item
    }
    val groups = byTag.map { TagGroup(it.key, it.value) }
    return if (untagged.isEmpty()) groups else groups + TagGroup(null, untagged)
}
