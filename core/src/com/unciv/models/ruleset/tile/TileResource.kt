package com.unciv.models.ruleset.tile

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.ICivilopediaText
import java.util.*

class TileResource : NamedStats(), ICivilopediaText {

    var resourceType: ResourceType = ResourceType.Bonus
    var terrainsCanBeFoundOn: List<String> = listOf()
    var improvement: String? = null
    var improvementStats: Stats? = null

    override var civilopediaText = listOf<String>()

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
        stringBuilder.appendln()
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

    override fun getCivilopediaTextHeader(): String =
        "(Resource/$name)" + super.getCivilopediaTextHeader()
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<String> {
        val textList = ArrayList<String>()

        textList += when (resourceType) {
            ResourceType.Bonus -> "+81c784####"
            ResourceType.Luxury -> "+ffeb7f####"
            ResourceType.Strategic -> "+c5a189####"
        } + "{${resourceType.name}} {resource}"

        textList += " " + this.clone().toString()

        if (terrainsCanBeFoundOn.isNotEmpty()) {
            textList += ""
            if (terrainsCanBeFoundOn.size == 1) {
                with (terrainsCanBeFoundOn[0]) {
                    textList += "[Terrain/$this] {Can be found on} {$this}"
                }
            } else {
                textList += " {Can be found on}:"
                terrainsCanBeFoundOn.forEach {
                    textList += "[Terrain/$it]  $it"
                }
            }
        }

        textList += ""
        textList += "[Improvement/$improvement] Improved by [$improvement]"
        if (improvementStats != null && !improvementStats!!.isEmpty())
            textList += " {Bonus stats for improvement}: " + improvementStats.toString()

        val buildingsThatConsumeThis = ruleset.buildings.values.filter { it.getResourceRequirements().containsKey(name) }
        if (buildingsThatConsumeThis.isNotEmpty()) {
            textList += ""
            textList += " {Buildings that consume this resource}:"
            buildingsThatConsumeThis.forEach {
                textList += "[Building/${it.name}]  ${it.name}"
            }
        }

        val unitsThatConsumeThis = ruleset.units.values.filter { it.getResourceRequirements().containsKey(name) }
        if (unitsThatConsumeThis.isNotEmpty()) {
            textList += ""
            textList += "{Units that consume this resource}: "
            unitsThatConsumeThis.forEach {
                textList += "[Unit/${it.name}]  ${it.name}"
            }
        }

        if (unique != null) {
            textList += ""
            textList += " $unique"
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
