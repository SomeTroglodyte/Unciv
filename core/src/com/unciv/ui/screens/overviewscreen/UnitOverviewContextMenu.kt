package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UnitActionType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AnimatedMenuPopup
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.UnitDisbandPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions

// todo
//  - Kdoc
//  - Disbanding won't update the unit list - does the grid have a copy as source?

class UnitOverviewContextMenu(
    private val context: UnitOverviewTab,
    positionNextTo: Actor,
    private val unit: MapUnit,
    private val onButtonClicked: () -> Unit
) : AnimatedMenuPopup(context.overviewScreen.stage, getActorTopRight(positionNextTo)) {
    private val allEquivalentUnits = unit.civ.units.getCivUnits().filter { it.name == unit.name }
    private val editButton = EditButton(::changeUnitName) // must initialize before nameField
    private val nameField = getNameEditField()
    private var unitWasRenamed = false

    private enum class WakeType(val wakeText: String = "") {
        None,
        IsSleeping("Wake up") {
            override fun test(unit: MapUnit) = unit.isSleeping()
            override fun wakeAllText(unit: MapUnit) = "Wake up all sleeping [${unit.name}]"
        },
        IsFortified("Un-fortify") {
            override fun test(unit: MapUnit) = unit.isFortified()
            override fun wakeAllText(unit: MapUnit) = "Un-fortify all [${unit.name}]"
        },
        IsAutomated("Stop automation") {
            override fun test(unit: MapUnit) = unit.isAutomated()
            override fun wake(unit: MapUnit) { UnitActions.invokeUnitAction(unit, UnitActionType.StopAutomation) }
            override fun wakeAllText(unit: MapUnit) = "Stop automation for all [${unit.name}]"
        },
        ;
        open fun test(unit: MapUnit): Boolean = false
        open fun wake(unit: MapUnit) { unit.action = null }
        open fun wakeAllText(unit: MapUnit) = ""
    }
    private val wakeType = WakeType.values().firstOrNull { it.test(unit) } ?: WakeType.None

    init {
        closeListeners.add {
            if (anyButtonWasClicked || unitWasRenamed) onButtonClicked()
        }
    }

    override fun createContentTable(): Table {
        val table = super.createContentTable()!!
        table.add(ImageGetter.getUnitIcon(unit.name).surroundWithCircle(50f)).expand(false, false)
        table.add(nameField).padRight(0f).padLeft(0f)
        table.add(editButton).expand(false, false).row()

        if (unit.currentMovement > 0)
            table.add(getButton("Disband unit", KeyboardBinding.DisbandUnit, ::disbandUnit)).colspan(3).row()
        if (canDisbandAll())
            table.add(getButton("Disband all [${unit.name}]", KeyboardBinding.None, ::disbandAll)).colspan(3).row()
        if (wakeType != WakeType.None)
            table.add(getButton(wakeType.wakeText, KeyboardBinding.None, ::wakeUp)).colspan(3).row()
        if (canWakeAll())
            table.add(getButton(wakeType.wakeAllText(unit), KeyboardBinding.None, ::wakeAll)).colspan(3).row()
        if (unit.canAutomate())
            table.add(getButton("Automate", KeyboardBinding.None, ::automate)).colspan(3).row()
        if (canAutomateAll())
            table.add(getButton("Automate all [${unit.name}]", KeyboardBinding.None, ::automateAll)).colspan(3).row()

        context.stage.keyboardFocus = nameField
        return table
    }

    private fun disbandUnit() {
        UnitDisbandPopup(unit, context.overviewScreen) {
            onButtonClicked()
        }.open(true)
    }

    // we'll enumerate the same Sequence thrice - this is UI, not perf-critical
    private fun disbandAllCandidates() = allEquivalentUnits.mapNotNull {
        val disbandGold = it.baseUnit.getDisbandGold(it.civ)
        if (it.currentMovement > 0 && disbandGold > 0)
            it to disbandGold
        else null
    }
    private fun canDisbandAll() = disbandAllCandidates().any { it.first != unit }
    private fun disbandAll() {
        val count = disbandAllCandidates().count()
        val disbandGold = disbandAllCandidates().sumOf { it.second }
        val message = "{Disband [$count] [${unit.name}] for [$disbandGold] gold?}\n" +
            "{(Only affects units that yield gold for disbanding)}"
        ConfirmPopup(context.overviewScreen, message, Constants.yes) {
            val worldScreen = GUI.getWorldScreen()
            for ((toDisband, _) in disbandAllCandidates()) {
                toDisband.disband()
                if (unit in worldScreen.bottomUnitTable.selectedUnits)
                    worldScreen.bottomUnitTable.selectUnit(null)
            }
            unit.civ.updateStatsForNextTurn()
            worldScreen.shouldUpdate = true
            onButtonClicked()
        }.open(true)
    }

    private fun wakeUp() { wakeType.wake(unit) }
    private fun wakeCandidates() = allEquivalentUnits.filter { wakeType.test(it) }
    private fun canWakeAll() = wakeType != WakeType.None && wakeCandidates().any { it != unit }
    private fun wakeAll() = wakeCandidates().forEach { wakeType.wake(it) }



    private fun automate(unit: MapUnit) = UnitActions.invokeUnitAction(unit, UnitActionType.Automate)
    private fun automate() { automate(unit) }
    private fun automateCandidates() = allEquivalentUnits.filter { it.canAutomate() }
    private fun canAutomateAll() = unit.canAutomate() && automateCandidates().any { it != unit }
    private fun automateAll() = automateCandidates().forEach { automate(it) }

    private fun getNameEditField(): TextField {
        // hint is never displayed - *except* when on Android the UncivTextField reaction to "visible area changed due to soft keyboard" decides to show a popup
        val nameField = UncivTextField("Choose name for [${unit.baseUnit.name}]", getNameFieldText())
        nameField.textFieldFilter = TextField.TextFieldFilter { _, char -> char !in "[]{}\"\\<>"}
        nameField.maxLength = 32
        nameField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                nameField.updateStyle()
            }
        })
        val style = TextField.TextFieldStyle(BaseScreen.skin.get(TextField.TextFieldStyle::class.java)) // get our own clone
        nameField.style = style
        nameField.cursorPosition = Int.MAX_VALUE
        nameField.updateStyle(false)
        return nameField
    }
    private fun getNameFieldText() = unit.instanceName ?: unit.baseUnit.name.tr(hideIcons = true)
    private fun TextField.updateStyle(isChanged: Boolean = text != getNameFieldText()) {
        if (isChanged) {
            style.fontColor = Color.WHITE
            editButton.enable()
        } else {
            style.fontColor = if (unit.instanceName == null) Color.LIGHT_GRAY else Color.GOLDENROD
            editButton.disable()
        }
    }
    private fun changeUnitName() {
        val userInput = nameField.text
        val baseName = unit.baseUnit.name.tr(hideIcons = true)
        if (userInput == "" || userInput == baseName) {
            unit.instanceName = null
            nameField.text = baseName
        } else {
            unit.instanceName = userInput
        }
        nameField.updateStyle(false)
        unitWasRenamed = true
    }

    private companion object {
        //fun MapUnit.isWorkerEquivalent() = hasUnique(UniqueType.BuildImprovements)

        // addAutomateActions decides whether maybe some units can never automate. Same for the currentMovement test.
        fun MapUnit.canAutomate() = UnitActions.getUnitActions(this, UnitActionType.Automate).any()
    }

    private class EditButton(changeUnitName: () -> Unit) : IconCircleGroup (
        size = 50f,
        ImageGetter.getImage("OtherIcons/Pencil"),
        color = Color(0x000c31)
    ) {
        init {
            onClick(changeUnitName)
        }

        fun enable() {
            touchable = Touchable.enabled
            actor.color = Color.GREEN
        }
        fun disable() {
            touchable = Touchable.disabled
            actor.color = Color.GRAY
        }

    }
}
