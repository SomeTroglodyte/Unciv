package com.unciv.models.stats

/** Similar to [StatMap], this is a Collection of Stats by a hierarchy of Strings,
 *  with all set/add methods implementing copy-on-write such that passed [Stats]
 *  instances are never modified (even if it's actually a [MutableStats]) */
class StatTreeNode {
    val children = LinkedHashMap<String, StatTreeNode>()
    private var innerStats: Stats? = null

    fun setInnerStat(stat: Stat, value: Float) {
        when (innerStats) {
            null -> innerStats = MutableStats.from(stat, value)
            is MutableStats -> (innerStats as MutableStats).add(stat, value)
            else -> innerStats = MutableStats.from(innerStats!!).add(stat, value)
        }
    }

    private fun addInnerStats(stats: Stats) {
        when (innerStats) {
            null -> innerStats = stats
            is MutableStats -> (innerStats as MutableStats).add(stats)
            else -> innerStats = MutableStats.from(innerStats!!).add(stats)
        }
        // What happens if we add 2 stats to the same leaf? A: accumulates
    }

    fun addStats(newStats: Stats, vararg hierarchyList: String) {
        if (hierarchyList.isEmpty()) {
            addInnerStats(newStats)
            return
        }
        val childName = hierarchyList.first()
        if (!children.containsKey(childName))
            children[childName] = StatTreeNode()
        children[childName]!!.addStats(newStats, *hierarchyList.drop(1).toTypedArray())
    }

    fun add(otherTree: StatTreeNode) {
        if (otherTree.innerStats != null) addInnerStats(otherTree.innerStats!!)
        for ((key, value) in otherTree.children) {
            if (!children.containsKey(key)) children[key] = value
            else children[key]!!.add(value)
        }
    }

    fun clone() : StatTreeNode {
        val new = StatTreeNode()
        new.innerStats = this.innerStats?.clone()
        new.children.putAll(this.children)
        return new
    }

    val totalStats: Stats
        get() {
            val toReturn = MutableStats()
            if (innerStats != null) toReturn.add(innerStats!!)
            for (child in children.values) toReturn.add(child.totalStats)
            return toReturn
        }
}
