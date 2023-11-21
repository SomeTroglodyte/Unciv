package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen

internal class CityScreenUnassignedPanel(private val mapPage: CityScreenMapPage) : Table() {
    private val numberLabel = "".toLabel(Color.FIREBRICK)

    init {
        touchable = Touchable.disabled
        isVisible = false
        isTransform = false

        background = BaseScreen.skinStrings.getUiBackground("CityScreen/UnassignedPanel/Background", BaseScreen.skinStrings.roundedEdgeRectangleSmallShape, Color(0x00000080))

        pad(5f)
        add("{Unassigned population}:".toLabel(Color.FIREBRICK, alignment = Align.right))
        add(numberLabel)
    }

    fun update() {
        val number = mapPage.city.population.getFreePopulation()
        if (!isVisible && number == 0) return

        numberLabel.setText(number)
        pack()
        setPosition(mapPage.width - 5f, mapPage.height - 5f, Align.topRight)
        if (isVisible && number > 0) return

        if (isVisible) {
            addAction(Actions.sequence(
                Actions.fadeOut(0.3f),
                Actions.visible(false)
            ))
        } else {
            color.a = 0f
            isVisible = true
            if (parent == null)
                mapPage.addActor(this)
            addAction(Actions.fadeIn(0.3f))
        }
    }
}
