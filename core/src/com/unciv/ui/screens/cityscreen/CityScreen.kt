package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.unciv.GUI
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.closeAllPopups
import com.unciv.ui.screens.basescreen.BaseScreen

abstract class CityScreen : BaseScreen() {

    // Fields needed in common
    abstract val city: City
    abstract val selectedConstruction: IConstruction?

    private val selectedCiv: Civilization = GUI.getWorldScreen().selectedCiv
    private val isSpying = selectedCiv.gameInfo.isEspionageEnabled() && selectedCiv != city.civ
    /** Toggles or adds/removes all state changing buttons */
    internal val canChangeState = GUI.isAllowedChangeState() && !isSpying

    /**
     * This is the regular civ city list if we are not spying, if we are spying then it is every foreign city that our spies are in
     */
    internal val viewableCities get() =
        if (isSpying) selectedCiv.espionageManager.getCitiesWithOurSpies()
            .filter { it.civ !=  GUI.getWorldScreen().selectedCiv }
        else city.civ.cities

    // Methods
    abstract fun update()
    abstract fun updateWithoutConstructionAndMap()

    /** Ask whether user wants to buy [selectedTile] for gold.
     *
     * Used from onClick and keyboard dispatch, thus only minimal parameters are passed,
     * and it needs to do all checks and the sound as appropriate.
     */
    abstract fun askToBuyTile(selectedTile: Tile)

    fun canCityBeChanged(): Boolean = canChangeState && !city.isPuppet

    abstract fun clearSelection()
    fun selectConstruction(name: String) = selectConstruction(city.cityConstructions.getConstruction(name))
    abstract fun selectConstruction(newConstruction: IConstruction)
    abstract fun selectTile(newTile: Tile?)

    // UniqueType.CreatesOneImprovement support
    abstract fun startPickTileForCreatesOneImprovement(construction: Building, stat: Stat, isBuying: Boolean)
    abstract fun stopPickTileForCreatesOneImprovement()

    /** Convenience shortcut to [CivConstructions.hasFreeBuilding][com.unciv.logic.civilization.CivConstructions.hasFreeBuilding], nothing more */
    fun hasFreeBuilding(building: Building) =
        city.civ.civConstructions.hasFreeBuilding(city, building)

    companion object {
        // This is a fake constructor to allow the established object creation syntax
        operator fun invoke (city: City, initSelectedConstruction: IConstruction? = null, initSelectedTile: Tile? = null): CityScreen =
            if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT))
                TabbedCityScreen(city, selectConstruction = initSelectedConstruction, selectTile = initSelectedTile)
            else ClassicCityScreen(city, initSelectedConstruction, initSelectedTile)

        /** Distance from stage edges to floating widgets */
        const val posFromEdge = 5f

        /** Size of the decoration icons shown besides the raze button */
        const val wltkIconSize = 40f

        // Only reason this is in Companion: separation, so the main section can be more lean and interface-like
        fun askToBuyTile(cityScreen: CityScreen, selectedTile: Tile, selectNext: ()->Unit) = cityScreen.run {
            // These checks are redundant for the onClick action, but not for the keyboard binding
            if (!canChangeState || !city.expansion.canBuyTile(selectedTile)) return
            val goldCostOfTile = city.expansion.getGoldCostOfTile(selectedTile)
            if (!city.civ.hasStatToBuy(Stat.Gold, goldCostOfTile)) return

            closeAllPopups()

            val purchasePrompt = "Currently you have [${city.civ.gold}] [Gold].".tr() + "\n\n" +
                "Would you like to purchase [Tile] for [$goldCostOfTile] [${Stat.Gold.character}]?".tr()
            ConfirmPopup(
                this,
                purchasePrompt,
                "Purchase",
                true,
                restoreDefault = { update() }
            ) {
                SoundPlayer.play(UncivSound.Coin)
                city.expansion.buyTile(selectedTile)
                selectNext()
            }.open()
        }
    }
}
