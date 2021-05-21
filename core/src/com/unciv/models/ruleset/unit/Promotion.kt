package com.unciv.models.ruleset.unit

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.INamed
import com.unciv.models.translations.Translations
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaText
import com.unciv.ui.civilopedia.FormattedLine

class Promotion : INamed, CivilopediaText() {
    override lateinit var name: String
    var prerequisites = listOf<String>()
    var effect = ""
    var unitTypes = listOf<String>() // The json parser wouldn't agree to deserialize this as a list of UnitTypes. =(

    var uniques = listOf<String>()
    val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } + Unique(effect)  }

    fun getDescription(promotionsForUnitType: Collection<Promotion>, forCivilopedia:Boolean=false, ruleSet:Ruleset? = null):String {
        // we translate it before it goes in to get uniques like "vs units in rough terrain" and after to get "vs city
        val stringBuilder = StringBuilder()

        for (unique in uniques + effect) {
            stringBuilder.appendLine(Translations.translateBonusOrPenalty(unique))
        }

        if(prerequisites.isNotEmpty()) {
            val prerequisitesString: ArrayList<String> = arrayListOf()
            for (i in prerequisites.filter { promotionsForUnitType.any { promotion -> promotion.name == it } }) {
                prerequisitesString.add(i.tr())
            }
            stringBuilder.appendLine("{Requires}: ".tr() + prerequisitesString.joinToString(" OR ".tr()))
        }
        if(forCivilopedia){
            if (unitTypes.isNotEmpty()) {
                val unitTypesString = unitTypes.joinToString(", ") { it.tr() }
                stringBuilder.appendLine("Available for [$unitTypesString]".tr())
            }

            if (ruleSet!=null) {
                val freeforUnits = ruleSet.units.filter { it.value.promotions.contains(name) }
                if (freeforUnits.isNotEmpty()) {
                    val freeforString = freeforUnits.map { it.value.name }.joinToString(", ") { it.tr() }
                    stringBuilder.appendLine("Free for [$freeforString]".tr())
                }
            }
        }
        return stringBuilder.toString()
    }

    override fun getCivilopediaTextHeader() = FormattedLine(name, icon="Promotion/$name", header=2)
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        for (unique in listOf(effect).filter { it.isNotEmpty() } + uniques.sorted()) {
            textList += FormattedLine(Translations.translateBonusOrPenalty(unique))
        }

        val promotionsForUnitType = ruleset.unitPromotions.values
        val filteredPrerequisites = prerequisites.filter { promotionsForUnitType.any { promotion -> promotion.name == it } }
        if (filteredPrerequisites.isNotEmpty()) {
            textList += FormattedLine()
            if (filteredPrerequisites.size == 1) {
                with (filteredPrerequisites[0]) {
                    textList += FormattedLine("{Requires}: {$this}", link="Promotion/$this")
                }
            } else {
                textList += FormattedLine("{Requires}:")
                filteredPrerequisites.withIndex().forEach {
                    textList += FormattedLine((if (it.index == 0) "" else "{OR} ") + it.value, link="Promotion/${it.value}")
                }
            }
        }

        if (unitTypes.isNotEmpty()) {
            textList += FormattedLine()
            val types = unitTypes.partition { it in ruleset.units }
            if (unitTypes.size == 1) {
                if (types.first.isNotEmpty())
                    with (types.first.first()) {
                        textList += FormattedLine("Available for [${this.tr()}]", link="Unit/$this")
                    }
                else
                    textList += FormattedLine("Available for [${types.second.first().tr()}]")
            } else {
                textList += FormattedLine("{Available for}:")
                types.second.forEach {
                    textList += FormattedLine(it, indent=1)
                }
                types.first.forEach {
                    textList += FormattedLine(it, indent=1, link="Unit/$it")
                }
            }
        }

        val freeForUnits = ruleset.units.filter { it.value.promotions.contains(name) }.map { it.key }
        if (freeForUnits.isNotEmpty()) {
            textList += FormattedLine()
            if (freeForUnits.size == 1) {
                with (freeForUnits[0]) {
                    textList += FormattedLine("{Free for} {$this}", link="Unit/$this")
                }
            } else {
                textList += FormattedLine("{Free for}:")
                freeForUnits.forEach {
                    textList += FormattedLine(it, link="Unit/$it")
                }
            }
        }

        return textList
    }
}
