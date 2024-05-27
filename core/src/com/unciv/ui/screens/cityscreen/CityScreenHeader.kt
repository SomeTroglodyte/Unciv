package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.nation.getRelativeLuminance
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.toImageButton
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.widgets.ScalingTableWrapper
import com.unciv.ui.components.widgets.UnitGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.pickerscreens.CityRenamePopup

/**
 *  City screen top row, contains City name, navigation, wltk decorations, annex/raze
 *
 *  Assumes WLTK cannot change while within the same CityScreen instance - name and state(puppet/resistance/razing/capital) can be updated.
 */
internal class CityScreenHeader(private val cityScreen: TabbedCityScreen) : ScalingTableWrapper() {
    private val city = cityScreen.city
    private val wltk = city.isWeLoveTheKingDayActive()
    private val innerColor = city.civ.nation.getInnerColor()

    private val annexAndRazeCell: Cell<Actor?>
    private val nameLabel: Label
    private val closeButtonCell: Cell<Group>
    private val iconWrapper = Table()

    private val razeIcon = ImageGetter.getImage("OtherIcons/Fire")
    private val puppetIcon = ImageGetter.getImage("OtherIcons/Puppet").apply { color = Color.LIGHT_GRAY }
    private val resistanceIcon = ImageGetter.getImage("StatIcons/Resistance")
    private val capitalIcon = ImageGetter.getImage("OtherIcons/Star").apply { color = Color.LIGHT_GRAY }

    companion object {
        /** Size of puppet/raze/resitance/capital indicators */
        const val stateIconSize = 20f
        const val nameFontSize = 30
        /** Image size for the navigation buttons */
        const val navigationButtonSize = 25f
        /** Clickable area of the navigation buttons is increased by this */
        const val navigationButtonPadding = 10f
        /** Size of the WLTK decoration icons */
        const val wltkIconSize = CityScreen.wltkIconSize
        const val garrisonIconSize = 30f
        const val closeButtonSize = 50f
    }

    init {
        val outerColor = city.civ.nation.getOuterColor()
        background = BaseScreen.skinStrings.getUiBackground("CityScreen/Header", tintColor = outerColor)

        annexAndRazeCell = add().padLeft(10f)  // Left

        add().growX()

        addNavigation(-1, KeyboardBinding.PreviousCity)

        addWltkIcon("OtherIcons/WLTK LR") { color = Color.GOLD }
        addWltkIcon("OtherIcons/WLTK 1") { color = Color.FIREBRICK }?.padRight(10f)

        add(iconWrapper)
        nameLabel = addName()
        city.getGarrison()?.also { add(UnitGroup(it, garrisonIconSize)).padLeft(5f) }

        addWltkIcon("OtherIcons/WLTK 2") { color = Color.FIREBRICK }?.padLeft(10f)
        addWltkIcon("OtherIcons/WLTK LR") {
            color = Color.GOLD
            scaleX = -scaleX
            originX = wltkIconSize * 0.5f
        }

        addNavigation(1, KeyboardBinding.NextCity)

        add().growX()

        val closeButton = "OtherIcons/Close"
            .toImageButton(closeButtonSize - 20f, closeButtonSize, BaseScreen.skinStrings.skinConfig.baseColor, Color.RED)
        closeButton.onActivation { cityScreen.exit() }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
        closeButtonCell = add(closeButton).right().padRight(10f)  // Right
    }

    internal fun update(annexOrRaze: Boolean = false, name: Boolean = false) {
        val maxWidth = cityScreen.stage.width
        resetScale()
        if (!name) updateAnnexOrRaze()
        if (!annexOrRaze) updateName()
        scaleTo(maxWidth)
    }

    private fun updateAnnexOrRaze() {
        val canAnnex = !city.civ.hasUnique(UniqueType.MayNotAnnexCities)
        val button = when {
            city.isPuppet && canAnnex -> getChangeStateButton("Annex city") { city.annexCity() }
            city.isBeingRazed -> getChangeStateButton("Stop razing city") { city.isBeingRazed = false }
            else -> getChangeStateButton("Raze city", city.canBeDestroyed() && canAnnex) { city.isBeingRazed = true }
        }
        annexAndRazeCell.setActor(button)
        closeButtonCell.minWidth(button.prefWidth) // so the inner cells center properly

        fun Table.addStateIcon(image: Image) = add(image).size(stateIconSize).padRight(5f)
        iconWrapper.apply {
            clear()
            if (city.isBeingRazed) addStateIcon(razeIcon)
            if (city.isPuppet) addStateIcon(puppetIcon)
            if (city.isInResistance()) addStateIcon(resistanceIcon)
            if (city.isCapital()) addStateIcon(capitalIcon)
        }
    }

    private fun updateName() {
        nameLabel.setText("{${city.name}} (${city.population.population})".tr(hideIcons = true))
    }

    private fun addName(): Label {
        val currentCityLabel = "".toLabel(innerColor, nameFontSize, Align.center)
        if (cityScreen.canChangeState) currentCityLabel.onActivation {
            CityRenamePopup(cityScreen, city) {
                cityScreen.updateName()
            }
        }
        add(currentCityLabel)
        return currentCityLabel
    }

    private fun addNavigation(delta: Int, binding: KeyboardBinding) {
        if (cityScreen.viewableCities.size <= 1) return

        val arrowName = if (delta > 0) "OtherIcons/ForwardArrow" else "OtherIcons/BackArrow"
        val overColor = if (getRelativeLuminance(innerColor) < 0.5f) Color.BLACK else Color.WHITE
        val style = ImageButton.ImageButtonStyle()
        val image = ImageGetter.getDrawable(arrowName)
        style.imageUp = image.tint(innerColor)
        style.imageOver = image.tint(overColor)
        val button = ImageButton(style)
        val wrapper = Table()
        wrapper.add(button).size(navigationButtonSize).pad(navigationButtonPadding)
        wrapper.onActivation { page(delta) }
        wrapper.keyShortcuts.add(binding)  // or as param to onActivation to get tooltips
        add(wrapper).pad(10f)
    }

    private fun addWltkIcon(name: String, apply: Image.()->Unit): Cell<Actor>? {
        if (!wltk) return null
        return add(ImageGetter.getImage(name).apply(apply)).size(wltkIconSize)
    }

    private fun getChangeStateButton(text: String, enable: Boolean = true, action: () -> Unit) =
        text.toTextButton().apply {
            labelCell.pad(10f)
            onActivation {
                action()
                cityScreen.updateAnnexOrRaze()
                cityScreen.updateStats()
            }
            if (!cityScreen.canChangeState || !enable) disable()
        }

    fun page(delta: Int) {
        val cities = cityScreen.viewableCities
        val numCities = cities.size
        if (numCities == 0) return
        val indexOfCity = cities.indexOf(city)
        val indexOfNextCity = (indexOfCity + delta + numCities) % numCities
        val newCityScreen = TabbedCityScreen(cities[indexOfNextCity], cityScreen.activePageID)
        cityScreen.game.replaceCurrentScreen(newCityScreen)
    }
}
