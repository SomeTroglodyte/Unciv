package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.logic.civilization.CivRankingHistory
import com.unciv.ui.images.ImageGetter
import yairm210.purity.annotations.Pure

/** Used in [CivRankingHistory] for [VictoryScreenCivRankings] and [VictoryScreenCharts], and various automation tasks (to get measurements from a civ).
 *
 *  - The order of UI presentation is enum order!
 *  - [idForSerialization] must be unique, checked by a unit test (RankingTypeTests)
 *  - [idForSerialization] cannot be a blank, minus or any digit due to the way [CivRankingHistory] is serialized
 */
enum class RankingType(
    val idForSerialization: Char,
    label: String? = null,
    val getImage: () -> Image? = { null }
) {
    // production, gold, happiness, culture and faith already have icons added when the line is `tr()`anslated
    Score('S', { ImageGetter.getImage("OtherIcons/Score", Color.FIREBRICK) }),
    Cities('t', { ImageGetter.getImage("OtherIcons/Cities") }),
    Population('N', { ImageGetter.getStatIcon("Population") }),
    Growth('C', "Growth", { ImageGetter.getStatIcon("Food") }),
    Production('P'),
    GoldIncome('g', "[Gold] income"),
    CultureIncome('c', "[Culture] income"),
    FaithIncome('f', "[Faith] income"),
    ScienceIncome('s', "[Science] income"),
    Gold('G'),
    Culture('A'),
    Faith('I'),
    Happiness('H'),
    Territory('T', { ImageGetter.getImage("OtherIcons/Hexagon") }),
    Military('M', { ImageGetter.getImage("UnitIcons/Cannon") }),
    Civilians('V', { ImageGetter.getImage("UnitIcons/Worker") }),
    Force('F', { ImageGetter.getImage("OtherIcons/Shield") }),
    Technologies('W', { ImageGetter.getStatIcon("Science") }),
    QuestsWon('Q', "Quests fulfilled", { ImageGetter.getImage("OtherIcons/Quest_White") }),
    WondersBuilt('w', "Wonders built", { ImageGetter.getImage("OtherIcons/Wonders") }),
    ReligionCities('R', "Believing cities", { ImageGetter.getImage("ReligionIcons/Religion") } ),
    ReligionFollowers('r', "Believers", { ImageGetter.getImage("ReligionIcons/Follower") } ),
    ;
    val label = label ?: name
    constructor(idForSerialization: Char, getImage: () -> Image?) : this(idForSerialization, null, getImage)

    companion object {
        @Pure fun fromIdForSerialization(char: Char): RankingType? =
                entries.firstOrNull { it.idForSerialization == char }
    }
}
