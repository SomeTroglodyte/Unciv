 package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.civilization.CityStateType
import com.unciv.models.stats.INamed
import com.unciv.models.translations.Translations
import com.unciv.models.translations.squareBraceRegex
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaText
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.colorFromRGB

enum class VictoryType {
    Neutral,
    Cultural,
    Domination,
    Scientific
}

class Nation : INamed, CivilopediaText() {
    override lateinit var name: String

    var leaderName = ""
    fun getLeaderDisplayName() = if (isCityState()) name
    else "[$leaderName] of [$name]"

    val style = ""
    var cityStateType: CityStateType? = null
    var preferredVictoryType: VictoryType = VictoryType.Neutral
    var declaringWar = ""
    var attacked = ""
    var defeated = ""
    var introduction = ""
    var tradeRequest = ""

    var neutralHello = ""
    var hateHello = ""

    lateinit var outerColor: List<Int>
    var uniqueName = ""
    var uniques = HashSet<String>()
    val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }
    var uniqueText = ""
    var innerColor: List<Int>? = null
    var startBias = ArrayList<String>()

    @Transient
    private lateinit var outerColorObject: Color
    fun getOuterColor(): Color = outerColorObject

    @Transient
    private lateinit var innerColorObject: Color

    fun getInnerColor(): Color = innerColorObject

    fun isCityState() = cityStateType != null
    fun isMajorCiv() = !isBarbarian() && !isCityState() && !isSpectator()
    fun isBarbarian() = name == Constants.barbarians
    fun isSpectator() = name == Constants.spectator

    // This is its own transient because we'll need to check this for every tile-to-tile movement which is harsh
    @Transient
    var forestsAndJunglesAreRoads = false

    // Same for Inca unique
    @Transient
    var ignoreHillMovementCost = false

    @Transient
    var embarkDisembarkCosts1 = false

    fun setTransients() {
        outerColorObject = colorFromRGB(outerColor)

        if (innerColor == null) innerColorObject = Color.BLACK
        else innerColorObject = colorFromRGB(innerColor!!)

        forestsAndJunglesAreRoads =
            uniques.contains("All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel.")
        ignoreHillMovementCost =
            uniques.contains("Units ignore terrain costs when moving into any tile with Hills")
        embarkDisembarkCosts1 =
            uniques.contains("Units pay only 1 movement point to embark and disembark")
    }

    lateinit var cities: ArrayList<String>


    fun getUniqueString(ruleset: Ruleset, forPickerScreen: Boolean = true): String {
        val textList = ArrayList<String>()

        if (leaderName.isNotEmpty() && !forPickerScreen) {
            textList += getLeaderDisplayName().tr()
            textList += ""
        }

        if (uniqueName != "") textList += uniqueName.tr() + ":"
        if (uniqueText != "") {
            textList += " " + uniqueText.tr()
        } else {
            textList += "  " + uniques.joinToString(", ") { it.tr() }
            textList += ""
        }

        if (startBias.isNotEmpty()) {
            textList += "Start bias:".tr() + startBias.joinToString(", ", " ") { it.tr() }
            textList += ""
        }
        addUniqueBuildingsText(textList, ruleset)
        addUniqueUnitsText(textList, ruleset)
        addUniqueImprovementsText(textList, ruleset)

        return textList.joinToString("\n")
    }

    private fun addUniqueBuildingsText(textList: ArrayList<String>, ruleset: Ruleset, forCivilopediaText: Boolean = false) {
        for (building in ruleset.buildings.values) {
            //.filter { it.uniqueTo == name && "Will not be displayed in Civilopedia" !in it.uniques }) {
            if (building.uniqueTo != name || "Will not be displayed in Civilopedia" in building.uniques) continue
            val category = if (building.isWonder || building.isNationalWonder) "Wonder" else "Building"
            val nameAndLink = (if (forCivilopediaText) "[$category/${building.name}] " else "") + building.name.tr()
            if (building.replaces != null && ruleset.buildings.containsKey(building.replaces!!)) {
                val originalBuilding = ruleset.buildings[building.replaces!!]!!

                if (forCivilopediaText) {
                    textList += nameAndLink
                    textList += "[Building/${originalBuilding.name}]  - " + "Replaces [${originalBuilding.name}]".tr()
                } else {
                    textList += nameAndLink + " - " + "Replaces [${originalBuilding.name}]".tr()
                }
                val originalBuildingStatMap = originalBuilding.toHashMap()
                for (stat in building.toHashMap())
                    if (stat.value != originalBuildingStatMap[stat.key])
                        textList += "  " + stat.key.toString().tr() + " " +
                            "[${stat.value.toInt()}] vs [${originalBuildingStatMap[stat.key]!!.toInt()}]".tr()

                for (unique in building.uniques.filter { it !in originalBuilding.uniques })
                    textList += "  " + unique.tr()
                if (building.maintenance != originalBuilding.maintenance)
                    textList += "  " + "{Maintenance} ".tr() + "[${building.maintenance}] vs [${originalBuilding.maintenance}]".tr()
                if (building.cost != originalBuilding.cost)
                    textList += "  " + "{Cost} ".tr() + "[${building.cost}] vs [${originalBuilding.cost}]".tr()
                if (building.cityStrength != originalBuilding.cityStrength)
                    textList += "  " + "{City strength} ".tr() + "[${building.cityStrength}] vs [${originalBuilding.cityStrength}]".tr()
                if (building.cityHealth != originalBuilding.cityHealth)
                    textList += "  " + "{City health} ".tr() + "[${building.cityHealth}] vs [${originalBuilding.cityHealth}]".tr()
                textList += ""
            } else if (building.replaces != null) {
                textList += nameAndLink + " - " + "Replaces [${building.replaces}], which is not found in the ruleset!".tr()
            } else {
                textList += nameAndLink
                textList += "  " + building.getShortDescription(ruleset)
            }
        }
    }

    private fun addUniqueUnitsText(textList: ArrayList<String>, ruleset: Ruleset, forCivilopediaText: Boolean = false) {
        for (unit in ruleset.units.values) {
            if (unit.uniqueTo != name || "Will not be displayed in Civilopedia" in unit.uniques) continue
            val nameAndLink = (if (forCivilopediaText) "[Unit/${unit.name}] " else "") + unit.name.tr()
            if (unit.replaces != null && ruleset.units.containsKey(unit.replaces!!)) {
                val originalUnit = ruleset.units[unit.replaces!!]!!
                if (forCivilopediaText) {
                    textList += nameAndLink
                    textList += "[Unit/${originalUnit.name}]  - " + "Replaces [${originalUnit.name}]".tr()
                } else {
                    textList += nameAndLink + " - " + "Replaces [${originalUnit.name}]".tr()
                }
                if (unit.cost != originalUnit.cost)
                    textList += "  " + "{Cost} ".tr() + "[${unit.cost}] vs [${originalUnit.cost}]".tr()
                if (unit.strength != originalUnit.strength)
                    textList += "  " + "${Fonts.strength} " + "[${unit.strength}] vs [${originalUnit.strength}]".tr()
                if (unit.rangedStrength != originalUnit.rangedStrength)
                    textList += "  " + "${Fonts.rangedStrength} " + "[${unit.rangedStrength}] vs [${originalUnit.rangedStrength}]".tr()
                if (unit.range != originalUnit.range)
                    textList += "  " + "${Fonts.range} " + "[${unit.range}] vs [${originalUnit.range}]".tr()
                if (unit.movement != originalUnit.movement)
                    textList += "  " + "${Fonts.movement} " + "[${unit.movement}] vs [${originalUnit.movement}]".tr()
                for (resource in originalUnit.getResourceRequirements().keys)
                    if (!unit.getResourceRequirements().containsKey(resource)) {
                        textList += (if (forCivilopediaText) "[Resource/$resource]  " else "  ") +
                                "[$resource] not required".tr()
                    }
                for (unique in unit.uniques.filterNot { it in originalUnit.uniques })
                    textList += "  " +  Translations.translateBonusOrPenalty(unique)
                for (unique in originalUnit.uniques.filterNot { it in unit.uniques })
                    textList += "  " +  "Lost ability".tr() + "(" +
                            "vs [${originalUnit.name}]".tr() + "): " +
                            Translations.translateBonusOrPenalty(unique)
                for (promotion in unit.promotions.filter { it !in originalUnit.promotions })
                    textList += (if (forCivilopediaText) "[Promotion/$promotion]  " else "  ") +
                        promotion.tr() + " (" +
                        Translations.translateBonusOrPenalty(ruleset.unitPromotions[promotion]!!.effect) + ")"
            } else if (unit.replaces != null) {
                textList += nameAndLink + " - " + "Replaces [${unit.replaces}], which is not found in the ruleset!".tr()
            } else {
                textList += nameAndLink
                textList += "  " + unit.getDescription(true).split("\n").joinToString("\n  ")
            }

            textList += ""
        }
    }

    private fun addUniqueImprovementsText(textList: ArrayList<String>, ruleset: Ruleset, forCivilopediaText: Boolean = false) {
        for (improvement in ruleset.tileImprovements.values) {
            if (improvement.uniqueTo != name ) continue

            textList += improvement.name.tr()
            textList += "  " + improvement.clone().toString()   // = (improvement as Stats).toString minus import plus copy overhead
            if (forCivilopediaText && improvement.terrainsCanBeBuiltOn.isNotEmpty()) {
                var first = true
                improvement.terrainsCanBeBuiltOn.forEach {
                    textList += "[Terrain/$it]  " + (if (first) "{Can be built on }" else " or ") + "{$it}"
                    first = false
                }
            }
            for (unique in improvement.uniques)
                textList += "  " + unique.tr()
        }
    }

    override fun getCivilopediaTextHeader(): String {
        return "(Nation/$name)" + super.getCivilopediaTextHeader()
    }

    override fun replacesCivilopediaDescription() = true
    override fun hasCivilopediaTextLines() = true
    override fun getCivilopediaTextLines(ruleset: Ruleset): List<String> {
        val textList = ArrayList<String>()


        if (leaderName.isNotEmpty()) {
            textList += "^ " + getLeaderDisplayName()
            textList += "(LeaderIcons/$leaderName)@300"
            textList += ""
        }

        if (uniqueName != "") textList += "{$uniqueName}:"
        if (uniqueText != "") {
            textList += "  {$uniqueText}"
        } else {
            uniques.forEach {
                textList += "  ${it.tr()}"
            }
            textList += ""
        }

        if (startBias.isNotEmpty()) {
            var first = true
            startBias.forEach {
                // can be "Avoid []"
                val link = if ('[' in it) {
                    squareBraceRegex.find(it)!!.groups[1]!!.value
                } else it
                textList += "[Terrain/$link] " + (if (first) "{Start bias:} " else " ") + it.tr()
            }
            textList += ""
        }
        addUniqueBuildingsText(textList, ruleset, true)
        addUniqueUnitsText(textList, ruleset, true)
        addUniqueImprovementsText(textList, ruleset, true)

        return textList
    }
}
