package com.unciv.ui.popups

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

class UnitRenamePopup(screen: BaseScreen, unit: MapUnit, actionOnClose: ()->Unit) : AskTextPopup(
    screen,
    label = "Choose name for [${unit.baseUnit.name}]",
    icon = ImageGetter.getUnitIcon(unit.name).surroundWithCircle(80f),
    defaultText = unit.instanceName ?: unit.baseUnit.name.tr(hideIcons = true),
    actionOnOk = { userInput ->
        //If the user inputs an empty string, clear the unit instanceName so the base name is used
        unit.instanceName = userInput.takeUnless { userInput.isBlank() || userInput == unit.baseUnit.name.tr(hideIcons = true) }
        actionOnClose()
    }
)
