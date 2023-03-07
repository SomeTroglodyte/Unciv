package com.unciv.models.stats

import com.unciv.Constants
import com.unciv.models.translations.tr
import java.time.temporal.TemporalAmount
import kotlin.reflect.KProperty0

/**
 * A container for the seven basic ["currencies"][Stat] in Unciv,
 * **Immutable**, protecting from logic mistakes.
 *
 * Supports e.g. `for ((key,value) in <Stats>)` - the [iterator] will skip zero values automatically.
 *
 * Also possible: `<Stats>`.[values].sum() and similar aggregates over a Sequence<Float>.
 */
open class Stats(
    open val production: Float = 0f,
    open val food: Float = 0f,
    open val gold: Float = 0f,
    open val science: Float = 0f,
    open val culture: Float = 0f,
    open val happiness: Float = 0f,
    open val faith: Float = 0f
): Iterable<Stats.StatValuePair> {

    // This is what facilitates indexed access by [Stat] or add(Stat,Float)
    // without additional memory allocation or expensive conditionals
    private fun statToProperty(stat: Stat): KProperty0<Float>{
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
    open operator fun get(stat: Stat) = statToProperty(stat).get()

    /** Compares two instances. Not callable via `==`. */
    // This is an overload, not an override conforming to the kotlin conventions of `equals(Any?)`,
    // so do not rely on it to be called for the `==` operator! A tad more efficient, though.
    @Suppress("CovariantEquals")    // historical reasons to keep this function signature
    fun equals(otherStats: Stats): Boolean {
        return production == otherStats.production
                && food == otherStats.food
                && gold == otherStats.gold
                && science == otherStats.science
                && culture == otherStats.culture
                && happiness == otherStats.happiness
                && faith == otherStats.faith
    }

    /** Compares two instances. Callable via `==`. */
    override fun equals(other: Any?): Boolean = if (other is Stats) equals(other) else false

    /** @return a new instance containing the same values as `this` */
    fun clone() = Stats(production, food, gold, science, culture, happiness, faith)

    /** @return `true` if all values are zero */
    fun isEmpty() = (
            production == 0f
            && food == 0f
            && gold == 0f
            && science == 0f
            && culture == 0f
            && happiness == 0f
            && faith == 0f )

    /** @return a new [Stats] instance containing the sum of its operands value by value */
    operator fun plus(stats: Stats) = Stats(
        production + stats.production,
        food + stats.food,
        gold + stats.gold,
        science + stats.science,
        culture + stats.culture,
        happiness + stats.happiness,
        faith + stats.faith
    )

    /** @return The result of multiplying each value of this instance by [number] as a new instance */
    operator fun times(number: Int) = times(number.toFloat())
    /** @return The result of multiplying each value of this instance by [number] as a new instance */
    operator fun times(number: Float) = Stats(
        production * number,
        food * number,
        gold * number,
        science * number,
        culture * number,
        happiness * number,
        faith * number
    )

    operator fun div(number: Float) = times(1/number)

    /** ***Not*** only a debug helper. It returns a string representing the content, already _translated_.
     *
     * Example output: `+1 Production, -1 Food`.
     */
    override fun toString() = joinToString()

    /** Since notifications are translated on the fly, when saving stats there we need to do so in English */
    fun toStringForNotifications() = joinToString { it.toStringForNotifications() }

    // For display in diplomacy window
    fun toStringWithDecimals() = joinToString { it.toStringWithDecimals() }

    // function that removes the icon from the Stats object since the circular icons all appear the same
    // delete this and replace above instances with toString() once the text-coloring-affecting-font-icons bug is fixed (e.g., in notification text)
    fun toStringWithoutIcons() = joinToString { it.toStringWithoutIcons() }

    /** Represents one [key][Stat]/[value][Float] pair returned by the [iterator] */
    data class StatValuePair (val key: Stat, val value: Float) {
        private fun toStringNumericPart() = (if (value > 0) "+" else "") + value.toInt() + " "
        override fun toString() = toStringNumericPart() + key.toString().tr()
        fun toStringForNotifications() = toStringNumericPart() + key.toString().tr(Constants.english)
        fun toStringWithDecimals() = (if (value > 0) "+" else "") + value.toString().removeSuffix(".0") + " " + key.toString().tr()
        fun toStringWithoutIcons() = value.toInt().toString() + " " + key.name.tr().substring(startIndex = 1)
    }

    /** Enables iteration over the non-zero [Stat]/value [pairs][StatValuePair].
     * Explicit use unnecessary - [Stats] is [iterable][Iterable] directly.
     * @see iterator */
    fun asSequence() = sequence {
        if (production != 0f) yield(StatValuePair(Stat.Production, production))
        if (food != 0f) yield(StatValuePair(Stat.Food, food))
        if (gold != 0f) yield(StatValuePair(Stat.Gold, gold))
        if (science != 0f) yield(StatValuePair(Stat.Science, science))
        if (culture != 0f) yield(StatValuePair(Stat.Culture, culture))
        if (happiness != 0f) yield(StatValuePair(Stat.Happiness, happiness))
        if (faith != 0f) yield(StatValuePair(Stat.Faith, faith))
    }

    /** Enables aggregates over the values, never empty */
    // Property syntax to emulate Map.values pattern
    // Doesn't skip zero values as it's meant for sum() or max() where the overhead would be higher than any gain
    val values
        get() = sequence {
            yield(production)
            yield(food)
            yield(gold)
            yield(science)
            yield(culture)
            yield(happiness)
            yield(faith)
        }

    /** Returns an iterator over the elements of this object, wrapped as [StatValuePair]s */
    override fun iterator(): Iterator<StatValuePair> = asSequence().iterator()

    /** Returns a mutable clone or _`this`_ if the instance is already a [MutableStats] */
    open fun toMutable() = MutableStats.from(this)
    /** Returns an immutable clone or _`this`_ if the instance is already an immutable [Stats] */
    open fun toImmutable() = this

    companion object {
        private val allStatNames = Stat.values().joinToString("|") { it.name }
        private val statRegexPattern = "([+-])(\\d+) ($allStatNames)"
        private val statRegex = Regex(statRegexPattern)
        private val entireStringRegexPattern = Regex("$statRegexPattern(, $statRegexPattern)*")

        /** Tests a given string whether it is a valid representation of [Stats],
         * close to what [toString] would produce.
         * - Values _must_ carry a sign - "1 Gold" tests `false`, "+1 Gold" is OK.
         * - Separator is ", " - comma space - the space is _not_ optional.
         * - Stat names must be untranslated and match case.
         * - Order is not important.
         * @see [parse]
         */
        fun isStats(string: String): Boolean {
            if (string.isEmpty() || string[0] !in "+-") return false // very quick negative check before the heavy Regex
            return entireStringRegexPattern.matches(string)
        }

        /** Parses a string to a [Stats] instance
         * - Values _must_ carry a sign - "1 Gold" will not parse, "+1 Gold" is OK.
         * - Separator is ", " - comma space - the space is _not_ optional.
         * - Stat names must be untranslated and match case.
         * - Order is not important.
         * @see [isStats]
         */
        fun parse(string: String): Stats {
            val toReturn = MutableStats()
            val statsWithBonuses = string.split(", ")
            for (statWithBonuses in statsWithBonuses) {
                val match = statRegex.matchEntire(statWithBonuses)!!
                val statName = match.groupValues[3]
                val statAmount = match.groupValues[2].toFloat() * (if (match.groupValues[1] == "-") -1 else 1)
                toReturn.add(Stat.valueOf(statName), statAmount)
            }
            return toReturn
        }

        val ZERO = Stats()

        fun from(stat: Stat, amount: Float): Stats = MutableStats.from(stat, amount)
    }
}
