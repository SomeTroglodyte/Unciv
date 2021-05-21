package com.unciv.models.ruleset.tile

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.NamedStats
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText
import java.util.*
import kotlin.math.roundToInt

class TileImprovement : NamedStats(), ICivilopediaText {

    var terrainsCanBeBuiltOn: Collection<String> = ArrayList()

    // Used only for Camp - but avoid hardcoded comparison and *allow modding*
    // Terrain Features that need not be cleared if the improvement enables a resource
    var resourceTerrainAllow: Collection<String> = ArrayList()

    var techRequired: String? = null

    var uniqueTo:String? = null
    var uniques = ArrayList<String>()
    val uniqueObjects:List<Unique> by lazy { uniques.map { Unique(it) } }
    val shortcutKey: Char? = null

    val turnsToBuild: Int = 0 // This is the base cost.

    override var civilopediaText = listOf<FormattedLine>()


    fun getTurnsToBuild(civInfo: CivilizationInfo): Int {
        var realTurnsToBuild = turnsToBuild.toFloat() * civInfo.gameInfo.gameParameters.gameSpeed.modifier
        // todo UNIFY THESE
        if (civInfo.hasUnique("Worker construction increased 25%"))
            realTurnsToBuild *= 0.75f
        if (civInfo.hasUnique("Tile improvement speed +25%"))
            realTurnsToBuild *= 0.75f
        return realTurnsToBuild.roundToInt()
    }

    fun getDescription(ruleset: Ruleset, forPickerScreen: Boolean = true): String {
        val stringBuilder = StringBuilder()
        val statsDesc = this.clone().toString()
        if (statsDesc.isNotEmpty()) stringBuilder.appendLine(statsDesc)
        if (uniqueTo!=null && !forPickerScreen) stringBuilder.appendLine("Unique to [$uniqueTo]".tr())
        if (!terrainsCanBeBuiltOn.isEmpty()) {
            val terrainsCanBeBuiltOnString: ArrayList<String> = arrayListOf()
            for (i in terrainsCanBeBuiltOn) {
                terrainsCanBeBuiltOnString.add(i.tr())
            }
            stringBuilder.appendLine("Can be built on ".tr() + terrainsCanBeBuiltOnString.joinToString(", "))//language can be changed when setting changes.
        }
        val statsToResourceNames = HashMap<String, ArrayList<String>>()
        for (tr: TileResource in ruleset.tileResources.values.filter { it.improvement == name }) {
            val statsString = tr.improvementStats.toString()
            if (!statsToResourceNames.containsKey(statsString))
                statsToResourceNames[statsString] = ArrayList()
            statsToResourceNames[statsString]!!.add(tr.name.tr())
        }
        statsToResourceNames.forEach {
            stringBuilder.appendLine(it.key + " for ".tr() + it.value.joinToString(", "))
        }

        if (techRequired != null) stringBuilder.appendLine("Required tech: [$techRequired]".tr())

        for(unique in uniques)
            stringBuilder.appendLine(unique.tr())

        return stringBuilder.toString()
    }

    fun hasUnique(unique: String) = uniques.contains(unique)
    fun isGreatImprovement() = hasUnique("Great Improvement")

    override fun getCivilopediaTextHeader() = FormattedLine(name, icon="Improvement/$name", header=2)
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        val statsDesc = this.clone().toString()
        if (statsDesc.isNotEmpty()) textList += FormattedLine(statsDesc)

        if (uniqueTo!=null) {
            textList += FormattedLine()
            textList += FormattedLine("Unique to [$uniqueTo]", link="Nation/$uniqueTo")
        }

        if (terrainsCanBeBuiltOn.isNotEmpty()) {
            textList += FormattedLine()
            if (terrainsCanBeBuiltOn.size == 1) {
                with (terrainsCanBeBuiltOn.first()) {
                    textList += FormattedLine("{Can be built on} {$this}", link="Terrain/$this")
                }
            } else {
                textList += FormattedLine("{Can be built on}:")
                terrainsCanBeBuiltOn.forEach {
                    textList += FormattedLine(it, link="Terrain/$it", indent=1)
                }
            }
        }

        val statsToResourceNames = HashMap<String, ArrayList<String>>()
        for (resource in ruleset.tileResources.values.filter { it.improvement == name }) {
            val statsString = resource.improvementStats.toString()
            if (statsString !in statsToResourceNames)
                statsToResourceNames[statsString] = ArrayList()
            statsToResourceNames[statsString]!!.add(resource.name)
        }
        if (statsToResourceNames.isNotEmpty()) {
            statsToResourceNames.forEach {
                textList += FormattedLine()
                if (it.value.size == 1) {
                    with(it.value[0]) {
                        textList += FormattedLine("${it.key}{ for }{$this}", link="Resource/$this")
                    }
                } else {
                    textList += FormattedLine("${it.key}{ for }:")
                    it.value.forEach { resource ->
                        textList += FormattedLine(resource, link="Resource/$resource", indent=1)
                    }
                }
            }
        }

        if (techRequired != null) {
            textList += FormattedLine()
            textList += FormattedLine("Required tech: [$techRequired]", link="Technology/$techRequired")
        }

        if (uniques.isNotEmpty()) {
            textList += FormattedLine()
            for(unique in uniques.sorted())
                textList += FormattedLine(unique)
        }

        val unitEntry = ruleset.units.asSequence().firstOrNull { "Can construct [$name]" in it.value.uniques }
        if (unitEntry != null) {
            textList += FormattedLine()
            textList += FormattedLine("{Can be constructed by} {${unitEntry.key}}", link="Unit/${unitEntry.key}")
        }

        return textList
    }
}

