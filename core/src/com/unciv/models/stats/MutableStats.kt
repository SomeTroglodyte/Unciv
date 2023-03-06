package com.unciv.models.stats

import com.unciv.ui.components.extensions.toPercent
import kotlin.reflect.KMutableProperty0

/**
 * A container for the seven basic ["currencies"][Stat] in Unciv,
 * **Mutable** subclass of [Stats], allowing for easy merging of sources and applying bonuses.
 *
 * Supports e.g. `for ((key,value) in <Stats>)` - the [iterator] will skip zero values automatically.
 *
 * Also possible: `<Stats>`.[values].sum() and similar aggregates over a Sequence<Float>.
 */
class MutableStats(
    // These overrides regrettably do not actually replace the Stat val's, their storage is separate
    override var production: Float = 0f,
    override var food: Float = 0f,
    override var gold: Float = 0f,
    override var science: Float = 0f,
    override var culture: Float = 0f,
    override var happiness: Float = 0f,
    override var faith: Float = 0f
) : Stats() {
    /** Conversion constructor: immutable to mutable */
    // private for better readability, use `from` factory instead
    private constructor(s: Stats) : this(s.production, s.food, s.gold, s.science, s.culture, s.happiness, s.faith)

    // This is what facilitates indexed access by [Stat] or add(Stat,Float)
    // without additional memory allocation or expensive conditionals
    private fun statToProperty(stat: Stat): KMutableProperty0<Float> {
        return when(stat) {
            Stat.Production -> ::production
            Stat.Food -> ::food
            Stat.Gold -> ::gold
            Stat.Science -> ::science
            Stat.Culture -> ::culture
            Stat.Happiness -> ::happiness
            Stat.Faith -> ::faith
        }
    }

    /** Indexed read of a value for a given [Stat], e.g. `this.gold == this[Stat.Gold]` */
    override fun get(stat: Stat) = statToProperty(stat).get()
    /** Indexed write of a value for a given [Stat], e.g. `this.gold += 1f` is equivalent to `this[Stat.Gold] += 1f` */
    operator fun set(stat: Stat, value: Float) = statToProperty(stat).set(value)

    /** Reset all values to zero (in place) */
    fun clear() {
        production = 0f
        food = 0f
        gold = 0f
        science = 0f
        culture = 0f
        happiness = 0f
        faith = 0f
    }

    /** Adds each value of another [Stats] instance to this one in place */
    fun add(other: Stats): Stats {
        production += other.production
        food += other.food
        gold += other.gold
        science += other.science
        culture += other.culture
        happiness += other.happiness
        faith += other.faith
        return this
    }

    /** Adds the [value] parameter to the instance value specified by [stat] in place
     * @return `this` to allow chaining */
    fun add(stat: Stat, value: Float): Stats {
        set(stat, value + get(stat))
        return this
    }

    /** Multiplies each value of this instance by [number] in place */
    fun timesInPlace(number: Float) {
        production *= number
        food *= number
        gold *= number
        science *= number
        culture *= number
        happiness *= number
        faith *= number
    }

    /** Apply weighting for Production Ranking */
    fun applyRankingWeights(){
        food *= 14
        production *= 12
        gold *= 8 // 3 gold worth about 2 production
        science *= 7
        culture *= 6
        happiness *= 10 // base
        faith *= 5
    }

    /** Apply boni expressed as percentages, filtered by [statFilter], chainable */
    fun applyPercentBonus(stats: Stats, vararg statFilter: Stat): MutableStats {
        for (stat in statFilter) {
            this[stat] *= stats[stat].toPercent()
        }
        return this
    }

    override fun toMutable() = this

    companion object {
        /** Factory creates a [MutableStats] instance with one [stat] preset to [amount] */
        fun from(stat: Stat, amount: Float) = MutableStats().also { it[stat] = amount }
        /** Factory creates a [MutableStats] instance cloned from immutable [stats] */
        fun from(stats: Stats) = MutableStats(stats)
        /** Factory creates a [MutableStats] instance cloned from immutable [stats] if non-null, or an empty one instead */
        @JvmName("fromNullableStats")
        fun from(stats: Stats?) = if (stats == null) MutableStats() else MutableStats(stats)
    }
}
