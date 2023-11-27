package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.ruleset.Building
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.images.ImageGetter

class CityScreenBuildingsTable(
    private val cityScreen: CityScreen,
    private val onSelect: (Building) -> Unit
) : Table(), TabbedPager.IPageExtensions {
    private val city = cityScreen.city
    private val specialistGroup = BuildingsGrouping("Specialist Buildings") { !it.newSpecialists().isEmpty() }
    private val wonderGroup = BuildingsGrouping("Wonders") { it.isAnyWonder() }
    private val regularGroup = BuildingsGrouping("Other") { true }
    private val collator = cityScreen.game.settings.getCollatorFromLocale()
    private var isDirty = true

    internal fun update() {
        clear()
        specialistGroup.reset()
        wonderGroup.reset()
        regularGroup.reset()
        val sortedBuildings = city.cityConstructions.getBuiltBuildings()
            .sortedWith(compareBy(collator) { it.name })
        for (building in sortedBuildings) {
            // If you want Wonders that grant Specialist slots in the Wonders group instead, just reverse call order
            if (!specialistGroup(building) && !wonderGroup(building)) regularGroup(building)
        }
        isDirty = false
    }

    private fun markDirty() {
        isDirty = true
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        if (isDirty) update()
    }

    /** A manager, not a Table, whose [invoke] will add a BuildingEntry(building) if appropriate
     *  to `this@CityScreenBuildingsTable`, ensuring a section header is output when needed. */
    private inner class BuildingsGrouping(private val caption: String, private val filter: (Building)->Boolean) {
        private var headerDone = false
        fun reset() {
            headerDone = false
        }
        operator fun invoke(building: Building): Boolean {
            if (!filter(building)) return false
            if (!headerDone) {
                headerDone = true
                addSeparator(color = Color.LIGHT_GRAY)
                add(caption.toLabel(alignment = Align.center)).growX()
                addSeparator(color = Color.LIGHT_GRAY)
            }
            add(BuildingEntry(building)).growX().right().row()
            return true
        }
    }

    private inner class BuildingEntry(building: Building) : Table() {
        init {
            val info = Table()
            val statsAndSpecialists = Table()

            val icon = ImageGetter.getConstructionPortrait(building.name, 50f)
            val isFree = cityScreen.hasFreeBuilding(building)
            val displayName = if (isFree) "{${building.name}} ({Free})" else building.name

            info.add(displayName.toLabel(fontSize = Constants.defaultFontSize, hideIcons = true)).padBottom(5f).right().row()

            val stats = building.getStats(city).joinToString(separator = " ") {
                "" + it.value.toInt() + it.key.character
            }
            statsAndSpecialists.add(stats.toLabel(fontSize = Constants.defaultFontSize)).right()

            val assignedSpec = city.population.getNewSpecialists()
            val specialistIcons = Table()
            for ((specialistName, maxCount) in building.newSpecialists()) {
                val specialist = city.getRuleset().specialists[specialistName]
                    ?: continue // probably a mod that doesn't have the specialist defined yet
                val assignedCount = assignedSpec[specialistName].coerceAtMost(maxCount)
                val unassignedCount = maxCount - assignedCount
                repeat(assignedCount) {
                    specialistIcons.add(ImageGetter.getSpecialistIcon(specialist.colorObject)).size(20f)
                }
                repeat(unassignedCount) {
                    specialistIcons.add(ImageGetter.getSpecialistIcon(Color.GRAY)).size(20f)
                }
            }
            statsAndSpecialists.add(specialistIcons).right()

            info.add(statsAndSpecialists).right()

            add(info).right().top().padRight(10f).padTop(5f)
            add(icon).right()

            onClick { onSelect(building) }
        }
    }
}
