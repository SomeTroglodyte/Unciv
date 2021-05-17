package com.unciv.models.ruleset.unit

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.INamed
import com.unciv.models.translations.Translations
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaText

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

    override fun getCivilopediaTextHeader(): String =
        "(Promotion/$name)" + super.getCivilopediaTextHeader()
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<String> {
        val textList = ArrayList<String>()

        for (unique in listOf(effect).filter { it.isNotEmpty() } + uniques.sorted()) {
            textList += " " + Translations.translateBonusOrPenalty(unique)
        }

        val promotionsForUnitType = ruleset.unitPromotions.values
        val filteredPrerequisites = prerequisites.filter { promotionsForUnitType.any { promotion -> promotion.name == it } }
        if (filteredPrerequisites.isNotEmpty()) {
            textList += ""
            if (filteredPrerequisites.size == 1) {
                with (filteredPrerequisites[0]) {
                    textList += "[Promotion/$this]  {Requires}: {$this}"
                }
            } else {
                textList += " {Requires}:"
                filteredPrerequisites.withIndex().forEach {
                    textList += "[Promotion/${it.value}]${if (it.index == 0) "  " else " { OR }"}{${it.value}}"
                }
            }
        }

        if (unitTypes.isNotEmpty()) {
            textList += ""
            val types = unitTypes.partition { it in ruleset.units }
            if (unitTypes.size == 1) {
                if (types.first.isNotEmpty())
                    with (types.first.first()) {
                        textList += "[Unit/$this]  Available for [${this.tr()}]"
                    }
                else
                    textList += " Available for [${types.second.first().tr()}]"
            } else {
                textList += " {Available for}:"
                types.second.forEach {
                    textList += "  $it"
                }
                types.first.forEach {
                    textList += "[Unit/$it]  $it"
                }
            }
        }

        val freeForUnits = ruleset.units.filter { it.value.promotions.contains(name) }.map { it.key }
        if (freeForUnits.isNotEmpty()) {
            textList += ""
            if (freeForUnits.size == 1) {
                with (freeForUnits[0]) {
                    textList += "[Unit/$this] {Free for} {$this}"
                }
            } else {
                textList += " {Free for}:"
                freeForUnits.forEach {
                    textList += "[Unit/$it] $it"
                }
            }
        }

        return textList
    }
}
