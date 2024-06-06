package com.unciv.ui.popups

import com.unciv.GUI
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

/**
 *  Show a confirmation Popup for disbanding a unit, open it, and if user confirms perform the disband.
 *
 *  Will only open if the WorldScreen has no open Popups.
 *  Will update the WorldScreen after disband and clear the selection if the unit was part of the selection, but not auto-select the next one.
 *
 *  @param unit The victim
 *  @param screen Optional parent, defaults to the current WorldScreen.
 *  @param actionOnClose Called if the unit was disbanded. Receives the current WorldScreen.
 *  @throws NullPointerException if there is no WorldScreen
 */
class UnitDisbandPopup(
    private val unit: MapUnit,
    private val screen: BaseScreen = GUI.getWorldScreen(),
    private val actionOnClose: (WorldScreen)->Unit
) : ConfirmPopup(
    screen,
    getMessage(unit),
    "Disband unit",
    action = { onConfirm(unit, actionOnClose) }
) {
    private companion object {
        fun getMessage(unit: MapUnit) = if (unit.currentTile.getOwner() == unit.civ)
            "Disband this unit for [${unit.baseUnit.getDisbandGold(unit.civ)}] gold?"
            else "Do you really want to disband this unit?"
        fun onConfirm(unit: MapUnit, actionOnClose: (WorldScreen) -> Unit) {
            unit.disband()
            unit.civ.updateStatsForNextTurn() // less upkeep!
            val worldScreen = GUI.getWorldScreen()
            worldScreen.shouldUpdate = true
            if (unit in worldScreen.bottomUnitTable.selectedUnits)
                worldScreen.bottomUnitTable.selectUnit(null)
            actionOnClose(worldScreen)
        }
    }
}
