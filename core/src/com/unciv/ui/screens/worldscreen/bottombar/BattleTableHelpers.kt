package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Counter
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.HealthBar
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.UnitGroup
import com.unciv.ui.components.WrappableLabel
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.WorldScreen
import kotlin.math.roundToInt

object BattleTableHelpers {

    class FlashRedAction(start:Float, end:Float, private val actorsToOriginalColors:Map<Actor, Color>) : FloatAction(start, end, 0.2f, Interpolation.sine){
        private fun updateRedPercent(percent: Float) {
            for ((actor, color) in actorsToOriginalColors)
                actor.color = color.cpy().lerp(Color.RED, start+percent*(end-start))
        }

        override fun update(percent: Float) = updateRedPercent(percent)
    }


    class MoveActorsAction(private val actorsToMove:List<Actor>, private val movementVector: Vector2) : RelativeTemporalAction(){
        init {
            duration = 0.3f
            interpolation = Interpolation.sine
        }
        override fun updateRelative(percentDelta: Float) {
            for (actor in actorsToMove){
                actor.moveBy(movementVector.x * percentDelta, movementVector.y * percentDelta)
            }
        }
    }


    class AttackAnimationAction(
        val attacker: ICombatant,
        val defenderActors: List<Actor>,
        val currentTileSetStrings: TileSetStrings
    ): SequenceAction(){
        init {
            if (defenderActors.any()) {
                val attackAnimationLocation = getAttackAnimationLocation()
                if (attackAnimationLocation != null){
                    var i = 1
                    while (ImageGetter.imageExists(attackAnimationLocation+i)){
                        val image = ImageGetter.getImage(attackAnimationLocation+i)
                        addAction(Actions.run {
                            defenderActors.first().parent.addActor(image)
                        })
                        addAction(Actions.delay(0.1f))
                        addAction(Actions.removeActor(image))
                        i++
                    }
                }
            }
        }

        private fun getAttackAnimationLocation(): String?{
            if (attacker is MapUnitCombatant) {
                val unitSpecificAttackAnimationLocation =
                        currentTileSetStrings.getString(
                            currentTileSetStrings.unitsLocation,
                            attacker.getUnitType().name,
                            "-attack-"
                        )
                if (ImageGetter.imageExists(unitSpecificAttackAnimationLocation+"1")) return unitSpecificAttackAnimationLocation
            }

            val unitTypeAttackAnimationLocation =
                    currentTileSetStrings.getString(currentTileSetStrings.unitsLocation, attacker.getUnitType().name, "-attack-")

            if (ImageGetter.imageExists(unitTypeAttackAnimationLocation+"1")) return unitTypeAttackAnimationLocation
            return null
        }
    }

    fun WorldScreen.battleAnimation(
        attacker: ICombatant, damageToAttacker: Boolean,
        defender: ICombatant, damageToDefender: Boolean
    ) {
        fun getMapActorsForCombatant(combatant: ICombatant):Sequence<Actor> =
                sequence {
                    val tileGroup = mapHolder.tileGroups[combatant.getTile()]!!
                    if (combatant.isCity()) {
                        val icon = tileGroup.layerMisc.improvementIcon
                        if (icon != null) yield (icon)
                    }
                    else {
                        val slot = if (combatant.isCivilian()) 0 else 1
                        yieldAll((tileGroup.layerUnitArt.getChild(slot) as Group).children)
                    }
                }

        val actorsToFlashRed =
                sequence {
                    if (damageToDefender) yieldAll(getMapActorsForCombatant(defender))
                    if (damageToAttacker) yieldAll(getMapActorsForCombatant(attacker))
                }.mapTo(arrayListOf()) { it to it.color.cpy() }.toMap()

        val actorsToMove = getMapActorsForCombatant(attacker).toList()

        val attackVectorHexCoords = defender.getTile().position.cpy().sub(attacker.getTile().position)
        val attackVectorWorldCoords = HexMath.hex2WorldCoords(attackVectorHexCoords)
            .nor()  // normalize vector to length of "1"
            .scl(10f) // we want 10 pixel movement

        stage.addAction(
            Actions.sequence(
                MoveActorsAction(actorsToMove, attackVectorWorldCoords),
                Actions.parallel( // While the unit is moving back to its normal position, we flash the damages on both units
                    MoveActorsAction(actorsToMove, attackVectorWorldCoords.cpy().scl(-1f)),
                    AttackAnimationAction(attacker,
                        if (damageToDefender) getMapActorsForCombatant(defender).toList() else listOf(),
                        mapHolder.currentTileSetStrings
                    ),
                    AttackAnimationAction(
                        defender,
                        if (damageToAttacker) getMapActorsForCombatant(attacker).toList() else listOf(),
                        mapHolder.currentTileSetStrings
                    ),
                    Actions.sequence(
                        FlashRedAction(0f,1f, actorsToFlashRed),
                        FlashRedAction(1f,0f, actorsToFlashRed)
                    )
                )
        ))


    }

    internal fun getHealthBar(
        maxHealth: Int, currentHealth: Int, maxRemainingHealth: Int, minRemainingHealth: Int,
        isVertical: Boolean = true
    ): HealthBar {
        val healthBar = HealthBar(0, maxHealth, segmentCount = 4, isVertical)
        healthBar.style.apply {
            colors = arrayOf(Color.GREEN, Color.ORANGE, Color.FIREBRICK, Color.BLACK)
            if (isVertical) setBarSize(10f, 100f)
            else setBarSize(10f, 100f)
            animateDuration = 0.5f
            flashingSegment = 2
        }
        healthBar.allowAnimations = UncivGame.Current.settings.continuousRendering
        healthBar.setValues(minRemainingHealth, maxRemainingHealth, currentHealth)
        val tipText = if (currentHealth == minRemainingHealth) "Health: [$currentHealth]"
            else "Health: [$currentHealth]\nMax left: [$maxRemainingHealth]\nMin left: [$minRemainingHealth]"
        if (isVertical)
            healthBar.addTooltip(tipText, 14f, tipAlign = Align.topLeft)
        else
            healthBar.addTooltip(tipText, 14f, targetAlign = Align.top)
        return healthBar
    }

    internal fun getCombatantIcon(combatant: ICombatant, size: Float = 25f) =
            if (combatant is MapUnitCombatant)
                // UnitGroup will break its "size" promise for embarked units, fixing in that apply
                UnitGroup(combatant.unit, size).apply { height = size }
            else
                // PortraitNation does not obey "size" properly either, hence /1.1
                ImageGetter.getNationPortrait(combatant.getCivInfo().nation, size / 1.1f)

    private fun Table.addHeadingLabel(text: String, alignment: Int = Align.center): Cell<Label> {
        return add(text.toLabel(fontSize = Constants.headingFontSize, alignment = alignment, hideIcons = true))
    }

    private fun Table.addModifierName(name: String, alignment: Int, expectedWidth: Float): Cell<WrappableLabel> {
        val description = if (name.startsWith("vs "))
            ("vs [" + name.drop(3) + "]")
        else name
        val label = WrappableLabel(description, expectedWidth, fontSize = 14)
        label.setAlignment(alignment)
        label.wrap = true
        return add(label).maxWidth(label.optimizePrefWidth() + 10f).colspan(2).padTop(4f)
    }

    private fun String.toModifierLabel(color: Color = Color.WHITE, alignment: Int): Label =
            toLabel(color, 14, alignment, true)

    private fun Table.addModifierValue(value: Int, alignment: Int): Cell<Label> {
        val percentage = (if (value > 0) "+" else "") + value + "%"
        val color = when {
            value < 0f -> Color.RED
            value > 0f -> Color.GREEN
            else -> Color.GRAY
        }
        return add(percentage.toModifierLabel(color, alignment)).padTop(4f)
    }

    private fun getCombatantHeader(combatant: ICombatant, strength: Int, iconOnLeft: Boolean = true) =
        Table().apply {
            if (iconOnLeft)
                add(getCombatantIcon(combatant, 36f))
            else if (strength > 0)
                addHeadingLabel(strength.toString(), Align.left).left()

            addHeadingLabel(combatant.getName())
                .pad(0f, 8f, 0f, 8f).center().growX()

            if (!iconOnLeft)
                add(getCombatantIcon(combatant, 36f))
            else if (strength > 0)
                addHeadingLabel(strength.toString(), Align.right).right()

            addSeparator().pad(3f, 0f, 8f, 0f)
        }
    internal fun getCombatantHeader(combatant: ICombatant, strength: Float, iconOnLeft: Boolean = true) =
            getCombatantHeader(combatant, strength.roundToInt(), iconOnLeft)

    internal fun Table.addAttackerModifiers(attacker: ICombatant, expectedWidth: Float, modifiers: Counter<String>) {
        add("Base strength".toLabel(fontSize = 14, alignment = Align.right)).colspan(2).right()
        val baseStrength = attacker.getAttackingStrength().toString() +
            (if (attacker.isRanged()) Fonts.rangedStrength else Fonts.strength)
        add(baseStrength.toModifierLabel(alignment = Align.right)).right().padLeft(5f).row()
        for ((name, value) in modifiers) {
            addModifierName(name, Align.right, expectedWidth).right()
            addModifierValue(value, Align.right).right().padLeft(5f).row()
        }
    }

    /** Adds the Attacker/Defender strength evaluation and battle prediction with HealthBars to
     *  its receiver Table, without the Attack button itself.
     *  @return Flags whether any damage was done to attacker/defender for passing on to [battleAnimation]
     */
    internal fun Table.simulateBattleUI(
        attacker: ICombatant,
        defender: ICombatant,
        tileToAttackFrom: Tile,
        minCombatantTableWidth: Float
    ): Pair<Boolean, Boolean> {
        clear()

        val defenceIcon =
                if (attacker.isRanged() && defender.isRanged() && !defender.isCity() && !(defender is MapUnitCombatant && defender.unit.isEmbarked()))
                    Fonts.rangedStrength
                else Fonts.strength // use strength icon if attacker is melee, defender is melee, defender is a city, or defender is embarked

        val attackerHealth = attacker.getHealth()
        val maxDamageToAttacker = BattleDamage.calculateDamageToAttacker(attacker, defender, tileToAttackFrom, 1f)
        val minDamageToAttacker = BattleDamage.calculateDamageToAttacker(attacker, defender, tileToAttackFrom, 0f)
        val minRemainingLifeAttacker = (attackerHealth - maxDamageToAttacker).coerceAtLeast(0)
        val maxRemainingLifeAttacker = (attackerHealth - minDamageToAttacker).coerceAtLeast(0)

        val defenderHealth = defender.getHealth()
        val maxDamageToDefender = BattleDamage.calculateDamageToDefender(attacker, defender, tileToAttackFrom, 1f)
        val minDamageToDefender = BattleDamage.calculateDamageToDefender(attacker, defender, tileToAttackFrom, 0f)
        val minRemainingLifeDefender = (defenderHealth - maxDamageToDefender).coerceAtLeast(0)
        val maxRemainingLifeDefender = (defenderHealth - minDamageToDefender).coerceAtLeast(0)

        val attackerStrength = BattleDamage.getAttackingStrength(attacker, defender, tileToAttackFrom)
        val attackerWrapper = getCombatantHeader(attacker, attackerStrength)

        val defenderStrength = BattleDamage.getDefendingStrength(attacker, defender, tileToAttackFrom)
        val defenderWrapper = getCombatantHeader(defender, defenderStrength, false)

        val expectedWidth = minCombatantTableWidth
            .coerceAtLeast(attackerWrapper.prefWidth)
            .coerceAtLeast(defenderWrapper.prefWidth) - 55f  // 55 is roughly "+33%" width

        attackerWrapper.addAttackerModifiers(attacker, expectedWidth,
            BattleDamage.getAttackModifiers(attacker, defender, tileToAttackFrom))

        defenderWrapper.apply {
            val baseStrength = defenceIcon + defender.getDefendingStrength(attacker.isRanged()).toString()
            add(baseStrength.toModifierLabel(alignment = Align.left)).left().padRight(5f)
            add("Base strength".toLabel(fontSize = 14, alignment = Align.left)).colspan(2).left().row()
            if (defender is MapUnitCombatant)
                for ((name, value) in BattleDamage.getDefenceModifiers(attacker, defender, tileToAttackFrom)) {
                    addModifierValue(value, Align.left).left().padRight(5f)
                    addModifierName(name, Align.left, expectedWidth).left().row()
                }
            val defeatedText = when {
                !attacker.isMelee() -> ""
                defender is CityCombatant && maxRemainingLifeDefender <= 1 -> "Occupied!"
                defender !is MapUnitCombatant -> ""
                !defender.isCivilian() -> ""
                defender.unit.hasUnique(UniqueType.Uncapturable) -> ""
                else -> "Captured!"
            }
            if (defeatedText.isNotEmpty()) {
                add(defeatedText.toLabel(Color.RED, alignment = Align.center)).colspan(3).fillX().padTop(15f)
            }
        }

        val attackerBar = getHealthBar(attacker.getMaxHealth(), attackerHealth, maxRemainingLifeAttacker, minRemainingLifeAttacker)
        val scaleTable = Table().apply {
            fun Table.legendEntry(value: Int) {
                if (rows > 0) add().expandY().row()
                add(value.toString().toLabel(Color.LIGHT_GRAY, 10, Align.center, true)).center().row()
            }
            for (i in 100 downTo 0 step 25) legendEntry(i)
        }
        val defenderBar = getHealthBar(defender.getMaxHealth(), defenderHealth, maxRemainingLifeDefender, minRemainingLifeDefender)

        val attackerCell = add(attackerWrapper).top()
        add(attackerBar).growY()
        add(scaleTable).growY().padLeft(0f).padRight(0f)
        add(defenderBar).growY()
        val defenderCell = add(defenderWrapper).top()

        val combatantCellWidth = attackerCell.prefWidth.coerceAtLeast(defenderCell.prefWidth)
        attackerCell.minWidth(combatantCellWidth)
        defenderCell.minWidth(combatantCellWidth)
        addSeparator(Color.GRAY, height = 1f).pad(0f)

        // from Battle.addXp(), check for can't gain more XP from Barbarians
        val maxXPFromBarbarians = attacker.getCivInfo().gameInfo.ruleset.modOptions.constants.maxXPfromBarbarians
        if (attacker is MapUnitCombatant && attacker.unit.promotions.totalXpProduced() >= maxXPFromBarbarians
                && defender.getCivInfo().isBarbarian()) {
            add("Cannot gain more XP from Barbarians"
                .toLabel(Color.SCARLET, fontSize = 16, alignment = Align.center)
                .apply { wrap = true }
            ).colspan(columns).width(2 * expectedWidth).row()
        }

        return (minDamageToAttacker > 0) to (minDamageToDefender > 0)
    }
}
