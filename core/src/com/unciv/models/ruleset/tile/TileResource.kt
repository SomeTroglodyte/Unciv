package com.unciv.models.ruleset.tile

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText
import java.util.*

class TileResource : NamedStats(), ICivilopediaText {

    var resourceType: ResourceType = ResourceType.Bonus
    var terrainsCanBeFoundOn: List<String> = listOf()
    var improvement: String? = null
    var improvementStats: Stats? = null

    override var civilopediaText = listOf<FormattedLine>()

    /**
     * The building that improves this resource, if any. E.G.: Granary for wheat, Stable for cattle.
     *
     */
    @Deprecated("Since 3.13.3 - replaced with '[stats] from [resource] tiles in this city' unique in the building")
    var building: String? = null
    var revealedBy: String? = null
    var unique: String? = null


    fun getDescription(ruleset: Ruleset): String {
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine(resourceType.name.tr())
        stringBuilder.appendLine(this.clone().toString())
        val terrainsCanBeBuiltOnString: ArrayList<String> = arrayListOf()
        terrainsCanBeBuiltOnString.addAll(terrainsCanBeFoundOn.map { it.tr() })
        stringBuilder.appendLine("Can be found on ".tr() + terrainsCanBeBuiltOnString.joinToString(", "))
        stringBuilder.appendLine()
        stringBuilder.appendLine("Improved by [$improvement]".tr())
        stringBuilder.appendLine("{Bonus stats for improvement}: ".tr() + "$improvementStats".tr())

        val buildingsThatConsumeThis = ruleset.buildings.values.filter { it.getResourceRequirements().containsKey(name) }
        if (buildingsThatConsumeThis.isNotEmpty())
            stringBuilder.appendLine("{Buildings that consume this resource}: ".tr()
                    + buildingsThatConsumeThis.joinToString { it.name.tr() })

        val unitsThatConsumeThis = ruleset.units.values.filter { it.getResourceRequirements().containsKey(name) }
        if (unitsThatConsumeThis.isNotEmpty())
            stringBuilder.appendLine("{Units that consume this resource}: ".tr()
                    + unitsThatConsumeThis.joinToString { it.name.tr() })

        if (unique != null) stringBuilder.appendLine(unique!!.tr())
        return stringBuilder.toString()
    }

    override fun getCivilopediaTextHeader() = FormattedLine(name, icon="Resource/$name", header=2)
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        textList += FormattedLine("{${resourceType.name}} {resource}", header=4, color=resourceType.color)

        textList += FormattedLine(this.clone().toString())

        if (terrainsCanBeFoundOn.isNotEmpty()) {
            textList += FormattedLine()
            if (terrainsCanBeFoundOn.size == 1) {
                with (terrainsCanBeFoundOn[0]) {
                    textList += FormattedLine("{Can be found on} {$this}", link="Terrain/$this")
                }
            } else {
                textList += FormattedLine("{Can be found on}:")
                terrainsCanBeFoundOn.forEach {
                    textList += FormattedLine(it, link="Terrain/$it", indent=1)
                }
            }
        }

        textList += FormattedLine()
        textList += FormattedLine("Improved by [$improvement]", link="Improvement/$improvement")
        if (improvementStats != null && !improvementStats!!.isEmpty())
            textList += FormattedLine("{Bonus stats for improvement}: " + improvementStats.toString())

        val buildingsThatConsumeThis = ruleset.buildings.values.filter { it.getResourceRequirements().containsKey(name) }
        if (buildingsThatConsumeThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Buildings that consume this resource}:")
            buildingsThatConsumeThis.forEach {
                textList += FormattedLine(it.name, link="Building/${it.name}", indent=1)
            }
        }

        val unitsThatConsumeThis = ruleset.units.values.filter { it.getResourceRequirements().containsKey(name) }
        if (unitsThatConsumeThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Units that consume this resource}: ")
            unitsThatConsumeThis.forEach {
                textList += FormattedLine(it.name, link="Unit/${it.name}", indent=1)
            }
        }

        if (unique != null) {
            textList += FormattedLine()
            textList += FormattedLine(unique!!)
        }
        return textList
    }
}


data class ResourceSupply(val resource:TileResource,var amount:Int, val origin:String)

class ResourceSupplyList:ArrayList<ResourceSupply>() {
    fun add(resource: TileResource, amount: Int, origin: String) {
        val existingResourceSupply = firstOrNull { it.resource == resource && it.origin == origin }
        if (existingResourceSupply != null) {
            existingResourceSupply.amount += amount
            if (existingResourceSupply.amount == 0) remove(existingResourceSupply)
        } else add(ResourceSupply(resource, amount, origin))
    }

    fun add(resourceSupplyList: ResourceSupplyList) {
        for (resourceSupply in resourceSupplyList)
            add(resourceSupply.resource, resourceSupply.amount, resourceSupply.origin)
    }
}
