package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.city.CityFocus
import com.unciv.models.stats.Stat
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.roundToInt

internal class CityScreenMiniStats(
    private val cityScreen: CityScreen,
    private val onUpdate: ()->Unit
) : Table() {
    companion object {
        const val iconSize = 27f
    }

    init {
        padTop(5f).padBottom(5f)
        background = BaseScreen.skinStrings.getUiBackground("CityScreen/MiniStats", tintColor = BaseScreen.clearColor)
        update()
    }

    fun update() {
        val city = cityScreen.city
        val selectedColor: Color = BaseScreen.skin.getColor("selection")

        clear()
        for ((stat, amount) in city.cityStats.currentCityStats) {
            if (stat == Stat.Faith && !city.civ.gameInfo.isReligionEnabled()) continue
            val icon = Table()
            val image = ImageGetter.getStatIcon(stat.name)
            val focus = CityFocus.safeValueOf(stat)
            val toggledFocus = if (focus.name == city.cityAIFocus) {
                icon.add(image.surroundWithCircle(iconSize, false, color = selectedColor))
                CityFocus.NoFocus
            } else {
                icon.add(image.surroundWithCircle(iconSize, false, color = Color.CLEAR))
                focus
            }
            if (cityScreen.canCityBeChanged()) {
                icon.onActivation(binding = toggledFocus.binding) {
                    city.setCityFocus(toggledFocus)
                    city.reassignPopulation()
                    onUpdate()
                }
            }
            val firstPad = if (cells.isEmpty) 5f else 0f
            add(icon).size(iconSize).padRight(3f).padLeft(firstPad)
            val valueToDisplay = if (stat == Stat.Happiness) city.cityStats.happinessList.values.sum() else amount
            add(valueToDisplay.roundToInt().toLabel()).padRight(5f)
        }
    }
}
