package com.unciv.models.stats


/** Collection of Stats by Strings - with copy-on-write modification functions */
class StatMap : LinkedHashMap<String, Stats>() {
    override fun clone() = StatMap().also {
        for ((key, entry) in this) {
            // Note: entry.clone() would produce immutable entries needing another clone should they be modified later
            // Valid solution, but our uses _will_ need to modify, better store mutable copies right away
            it[key] = MutableStats.from(entry)
        }
    }

    fun add(source: String, stats: Stats) {
        when (val existing = this[source]) {
            null -> this[source] = stats
            is MutableStats -> existing.add(stats)
            else -> this[source] = MutableStats.from(existing).add(stats)
        }
    }

    fun applyPercentBonus(stats: Stats, vararg statFilter: Stat) {
        if (statFilter.none { stats[it] != 0f }) return // optimize - all percentages zero
        for ((source, entry) in this) {
            if (statFilter.none { entry[it] != 0f }) continue // optimize - all values zero
            if (entry is MutableStats) entry.applyPercentBonus(stats, *statFilter)
            else this[source] = MutableStats.from(entry).applyPercentBonus(stats, *statFilter)
        }
    }

    /** This will add an entry for [source] when necessary, and then _replace_ the specified [stat]'s value */
    fun replaceSpecificStat(source: String, stat: Stat, value: Float) {
        when (val existing = this[source]) {
            null -> this[source] = Stats.from(stat, value)
            is MutableStats -> existing[stat] = value
            else -> this[source] = MutableStats.from(existing).apply { set(stat, value) }
        }
    }

    val totalStats: Stats
        get() = MutableStats().apply {
            for (entry in this@StatMap.values) add(entry)
        }
}
