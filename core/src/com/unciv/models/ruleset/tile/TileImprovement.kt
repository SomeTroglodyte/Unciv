package com.unciv.models.ruleset.tile

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.NamedStats
import com.unciv.models.translations.tr
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

    override var civilopediaText = listOf<String>()


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

    override fun getCivilopediaTextHeader(): String =
        "(Improvement/$name)" + super.getCivilopediaTextHeader()
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<String> {
        val textList = ArrayList<String>()

        val statsDesc = this.clone().toString()
        if (statsDesc.isNotEmpty()) textList += " $statsDesc"

        if (uniqueTo!=null) {
            textList += ""
            textList += "[Nation/$uniqueTo] Unique to [$uniqueTo]"
        }

        if (terrainsCanBeBuiltOn.isNotEmpty()) {
            textList += ""
            if (terrainsCanBeBuiltOn.size == 1) {
                with (terrainsCanBeBuiltOn.first()) {
                    textList += "[Terrain/$this] {Can be built on} {$this}"
                }
            } else {
                textList += " {Can be built on}:"
                terrainsCanBeBuiltOn.forEach {
                    textList += "[Terrain/$it]  $it"
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
                textList += ""
                if (it.value.size == 1) {
                    with(it.value[0]) {
                        textList += "[Resource/$this] ${it.key}{ for }{$this}"
                    }
                } else {
                    textList += " ${it.key}{ for }:"
                    it.value.forEach { resource ->
                        textList += "[Resource/$resource]  $resource"
                    }
                }
            }
        }

        if (techRequired != null) {
            textList += ""
            textList += "[Technology/$techRequired] Required tech: [$techRequired]"
        }

        if (uniques.isNotEmpty()) {
            textList += ""
            for(unique in uniques.sorted())
                textList += " $unique"
        }

        val unitEntry = ruleset.units.asSequence().firstOrNull { "Can construct [$name]" in it.value.uniques }
        if (unitEntry != null) {
            textList += ""
            textList += "[Unit/${unitEntry.key}] {Can be constructed by} {${unitEntry.key}}"
        }

        return textList
    }
}

