package com.unciv.models.ruleset.tech

import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.translations.tr
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.civilopedia.CivilopediaText
import com.unciv.ui.civilopedia.FormattedLine
import java.util.*

class Technology: CivilopediaText() {

    lateinit var name: String

    var cost: Int = 0
    var prerequisites = HashSet<String>()
    var uniques = ArrayList<String>()
    val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }

    var column: TechColumn? = null // The column that this tech is in the tech tree
    var row: Int = 0
    var quote = ""

    fun getDescription(ruleset: Ruleset): String {
        val lineList = ArrayList<String>() // more readable than StringBuilder, with same performance for our use-case
        for (unique in uniques) lineList += unique.tr()

        for (improvement in ruleset.tileImprovements.values)
            for (unique in improvement.uniqueObjects) {
                if (unique.placeholderText == "[] once [] is discovered" && unique.params.last() == name)
                    lineList += "[${unique.params[0]}] from every [${improvement.name}]"
                else if (unique.placeholderText == "[] on [] tiles once [] is discovered" && unique.params.last() == name)
                    lineList += "[${unique.params[0]}] from every [${improvement.name}] on [${unique.params[1]}] tiles"
            }

        val viewingCiv = UncivGame.Current.worldScreen.viewingCiv
        val enabledUnits = getEnabledUnits(viewingCiv).filter { "Will not be displayed in Civilopedia" !in it.uniques }
        if (enabledUnits.isNotEmpty()) {
            lineList += "{Units enabled}: "
            for (unit in enabledUnits)
                lineList += " * " + unit.name.tr() + " (" + unit.getShortDescription() + ")"
        }

        val enabledBuildings = getEnabledBuildings(viewingCiv)

        val regularBuildings = enabledBuildings.filter {
            !it.isWonder && !it.isNationalWonder
                    && "Will not be displayed in Civilopedia" !in it.uniques
        }
        if (regularBuildings.isNotEmpty()) {
            lineList += "{Buildings enabled}: "
            for (building in regularBuildings)
                lineList += "* " + building.name.tr() + " (" + building.getShortDescription(ruleset) + ")"
        }

        val wonders = enabledBuildings.filter {
            (it.isWonder || it.isNationalWonder)
                    && "Will not be displayed in Civilopedia" !in it.uniques
        }
        if (wonders.isNotEmpty()) {
            lineList += "{Wonders enabled}: "
            for (wonder in wonders)
                lineList += " * " + wonder.name.tr() + " (" + wonder.getShortDescription(ruleset) + ")"
        }

        for (building in getObsoletedBuildings(viewingCiv)
                .filter { "Will not be displayed in Civilopedia" !in it.uniques })
            lineList += "[${building.name}] obsoleted"


        for (resource in ruleset.tileResources.values.asSequence().filter { it.revealedBy == name }
                .map { it.name })
            lineList += "Reveals [$resource] on the map"

        val tileImprovements = ruleset.tileImprovements.values.filter { it.techRequired == name }
        if (tileImprovements.isNotEmpty())
            lineList += "{Tile improvements enabled}: " + tileImprovements.joinToString { it.name.tr() }

        return lineList.joinToString("\n") { it.tr() }
    }

    fun getEnabledBuildings(civInfo: CivilizationInfo): List<Building> {
        var enabledBuildings = civInfo.gameInfo.ruleSet.buildings.values.filter {
            it.requiredTech == name &&
                    (it.uniqueTo == null || it.uniqueTo == civInfo.civName)
        }
        val replacedBuildings = enabledBuildings.mapNotNull { it.replaces }
        enabledBuildings = enabledBuildings.filter { it.name !in replacedBuildings }

        if (!civInfo.gameInfo.gameParameters.nuclearWeaponsEnabled)
            enabledBuildings = enabledBuildings.filterNot { it.name == "Manhattan Project" }

        return enabledBuildings
    }

    fun getObsoletedBuildings(civInfo: CivilizationInfo): Sequence<Building> {
        val obsoletedBuildings = civInfo.gameInfo.ruleSet.buildings.values.asSequence()
                .filter { it.uniqueObjects.any { it.placeholderText=="Obsolete with []" && it.params[0]==name } }
        return obsoletedBuildings.filter { civInfo.getEquivalentBuilding(it.name)==it }
    }

    fun getEnabledUnits(civInfo: CivilizationInfo): List<BaseUnit> {
        var enabledUnits = civInfo.gameInfo.ruleSet.units.values.filter {
            it.requiredTech == name &&
                    (it.uniqueTo == null || it.uniqueTo == civInfo.civName)
        }
        val replacedUnits = civInfo.gameInfo.ruleSet.units.values.filter { it.uniqueTo == civInfo.civName }
                .mapNotNull { it.replaces }
        enabledUnits = enabledUnits.filter { it.name !in replacedUnits }

        if (!civInfo.gameInfo.gameParameters.nuclearWeaponsEnabled)
            enabledUnits = enabledUnits.filterNot { it.uniques.contains("Requires Manhattan Project") }

        return enabledUnits
    }

    override fun toString() = name

    fun era(): String = column!!.era

    fun isContinuallyResearchable() = uniques.contains("Can be continually researched")

    override fun getCivilopediaTextHeader() = FormattedLine(name, icon="Technology/$name", header=2)
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val lineList = ArrayList<FormattedLine>()

        if (quote.isNotEmpty()) {
            lineList += FormattedLine(quote, size=21, color="#DCB")
            lineList += FormattedLine()
        }
        if (prerequisites.isNotEmpty()) {
            prerequisites.withIndex().forEach { 
                lineList += FormattedLine(
                    if (it.index==0) "Requires [${it.value}]"
                    else "{and} {${it.value}}",
                    link="Technology/${it.value}",
                    indent=if (it.index==0) 0 else 1
                )
            }
        }
        val leadsTo = ruleset.technologies.values.filter { name in it.prerequisites }
        if (leadsTo.isNotEmpty()) {
            leadsTo.withIndex().forEach {
                lineList += FormattedLine(
                    if (it.index==0) "Leads to [${it.value.name}]"
                    else "{and} {${it.value.name}}",
                    link="Technology/${it.value.name}",
                    indent=if (it.index==0) 0 else 1
                )
            }
        }

        if (prerequisites.isNotEmpty() || leadsTo.isNotEmpty())
            lineList += FormattedLine()
        for (unique in uniques) lineList += FormattedLine(unique)
        var wantEmpty = uniques.isNotEmpty()

        for (improvement in ruleset.tileImprovements.values)
            for (unique in improvement.uniqueObjects) {
                if (unique.placeholderText == "[] once [] is discovered" && unique.params.last() == name) {
                    if (wantEmpty) { lineList += FormattedLine(); wantEmpty = false }
                    lineList += FormattedLine("[${unique.params[0]}] from every [${improvement.name}]",
                        link="Improvement/${improvement.name}")
                } else if (unique.placeholderText == "[] on [] tiles once [] is discovered" && unique.params.last() == name) {
                    if (wantEmpty) { lineList += FormattedLine(); wantEmpty = false }
                    lineList += FormattedLine("[${unique.params[0]}] from every [${improvement.name}] on [${unique.params[1]}] tiles",
                        link="Improvement/${improvement.name}")
                }
            }

        val viewingCiv = UncivGame.Current.worldScreen.viewingCiv
        val enabledUnits = getEnabledUnits(viewingCiv).filter { "Will not be displayed in Civilopedia" !in it.uniques }
        if (enabledUnits.isNotEmpty()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{Units enabled}:")
            for (unit in enabledUnits)
                lineList += FormattedLine(unit.name.tr() + " (" + unit.getShortDescription() + ")",
                    link="Unit/${unit.name}")
        }

        val enabledBuildings = getEnabledBuildings(viewingCiv)
            .filter { "Will not be displayed in Civilopedia" !in it.uniques }
            .partition { it.isWonder || it.isNationalWonder }
        if (enabledBuildings.first.isNotEmpty()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{Wonders enabled}:")
            for (wonder in enabledBuildings.first)
                lineList += FormattedLine(wonder.name.tr() + " (" + wonder.getShortDescription(ruleset) + ")",
                    link="Wonder/${wonder.name}")
        }
        if (enabledBuildings.second.isNotEmpty()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{Buildings enabled}:")
            for (building in enabledBuildings.second)
                lineList += FormattedLine(building.name.tr() + " (" + building.getShortDescription(ruleset) + ")",
                    link="Building/${building.name}")
        }

        wantEmpty = true
        for (building in getObsoletedBuildings(viewingCiv)
                .filter { "Will not be displayed in Civilopedia" !in it.uniques }) {
            if (wantEmpty) { lineList += FormattedLine(); wantEmpty = false }
            lineList += FormattedLine("[${building.name}] obsoleted", link="Building/${building.name}")
        }

        wantEmpty = true
        for (resource in ruleset.tileResources.values.asSequence()
                .filter { it.revealedBy == name }.map { it.name }) {
            if (wantEmpty) { lineList += FormattedLine(); wantEmpty = false }
            lineList += FormattedLine("Reveals [$resource] on the map", link="Resource/$resource")
        }

        val tileImprovements = ruleset.tileImprovements.values.filter { it.techRequired == name }
        if (tileImprovements.isNotEmpty()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{Tile improvements enabled}:")
            tileImprovements.forEach {
                lineList += FormattedLine("$it", link="Improvement/$it")
            }
        }

        return lineList
    }
}
