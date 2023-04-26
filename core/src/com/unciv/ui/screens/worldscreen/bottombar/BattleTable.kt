package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.automation.unit.AttackableTile
import com.unciv.logic.automation.unit.BattleHelper
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.ColorMarkupLabel
import com.unciv.ui.components.extensions.addBorderAllowOpacity
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.addAttackerModifiers
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.battleAnimation
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.getCombatantHeader
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.getCombatantIcon
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.getHealthBar
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.simulateBattleUI
import com.unciv.utils.DebugUtils

class BattleTable(val worldScreen: WorldScreen): Table() {
    /** This is used as max modifier column width for label wrapping */
    private val defaultColumnWidth = worldScreen.stage.width / 5

    init {
        isVisible = false
        skin = BaseScreen.skin
        background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/BattleTable",
            tintColor = BaseScreen.skinStrings.skinConfig.baseColor.apply { a = 0.8f }
        )

        defaults().pad(5f)
        pad(5f)
        touchable = Touchable.enabled
    }

    private fun hide() {
        isVisible = false
        clear()
        pack()
    }

    fun update() {
        if (!worldScreen.canChangeState) return hide()

        val attacker = tryGetAttacker() ?: return hide()

        if (attacker is MapUnitCombatant && attacker.unit.baseUnit.isNuclearWeapon()) {
            val selectedTile = worldScreen.mapHolder.selectedTile
                ?: return hide() // no selected tile
            simulateNuke(attacker, selectedTile)
        } else if (attacker is MapUnitCombatant && attacker.unit.isPreparingAirSweep()) {
            val selectedTile = worldScreen.mapHolder.selectedTile
                ?: return hide() // no selected tile
            simulateAirsweep(attacker, selectedTile)
        } else {
            val defender = tryGetDefender() ?: return hide()
            if (attacker is CityCombatant && defender is CityCombatant) return hide()
            val tileToAttackFrom = if (attacker is MapUnitCombatant)
                BattleHelper.getAttackableEnemies(
                    attacker.unit,
                    attacker.unit.movement.getDistanceToTiles()
                )
                    .firstOrNull { it.tileToAttack == defender.getTile() }?.tileToAttackFrom ?: attacker.getTile()
            else attacker.getTile()
            simulateBattle(attacker, defender, tileToAttackFrom)
        }

        isVisible = true
        pack()
        addBorderAllowOpacity(1f, Color.WHITE)
    }

    private fun tryGetAttacker(): ICombatant? {
        val unitTable = worldScreen.bottomUnitTable
        return if (unitTable.selectedUnit != null
                && !unitTable.selectedUnit!!.isCivilian()
                && !unitTable.selectedUnit!!.hasUnique(UniqueType.CannotAttack))  // purely cosmetic - hide battle table
                    MapUnitCombatant(unitTable.selectedUnit!!)
        else if (unitTable.selectedCity != null)
            CityCombatant(unitTable.selectedCity!!)
        else null // no attacker
    }

    private fun tryGetDefender(): ICombatant? {
        val selectedTile = worldScreen.mapHolder.selectedTile ?: return null // no selected tile
        return tryGetDefenderAtTile(selectedTile, false)
    }

    private fun tryGetDefenderAtTile(selectedTile: Tile, includeFriendly: Boolean): ICombatant? {
        val attackerCiv = worldScreen.viewingCiv
        val defender: ICombatant? = Battle.getMapCombatantOfTile(selectedTile)

        if (defender == null || (!includeFriendly && defender.getCivInfo() == attackerCiv))
            return null  // no enemy combatant in tile

        val canSeeDefender = when {
            DebugUtils.VISIBLE_MAP -> true
            defender.isInvisible(attackerCiv) -> attackerCiv.viewableInvisibleUnitsTiles.contains(selectedTile)
            defender.isCity() -> attackerCiv.hasExplored(selectedTile)
            else -> attackerCiv.viewableTiles.contains(selectedTile)
        }

        if (!canSeeDefender) return null

        return defender
    }

    private fun simulateBattle(attacker: ICombatant, defender: ICombatant, tileToAttackFrom: Tile) {
        val (damageToAttacker, damageToDefender) =
            this.simulateBattleUI(attacker, defender, tileToAttackFrom, defaultColumnWidth)

        row().pad(5f)
        val attackText: String = when (attacker) {
            is CityCombatant -> "Bombard"
            else -> "Attack"
        }
        val attackButton = attackText.toTextButton().apply { color = Color.RED }

        var attackableTile: AttackableTile? = null

        if (attacker.canAttack()) {
            if (attacker is MapUnitCombatant) {
                attackableTile = BattleHelper
                        .getAttackableEnemies(attacker.unit, attacker.unit.movement.getDistanceToTiles())
                        .firstOrNull{ it.tileToAttack == defender.getTile()}
            } else if (attacker is CityCombatant) {
                val canBombard = UnitAutomation.getBombardableTiles(attacker.city).contains(defender.getTile())
                if (canBombard) {
                    attackableTile = AttackableTile(attacker, defender)
                }
            }
        }

        if (!worldScreen.isPlayersTurn || attackableTile == null) {
            attackButton.disable()
            attackButton.label.color = Color.GRAY
        } else {
           attackButton.onClick(UncivSound.Silent) {  // onAttackButtonClicked will do the sound
                onAttackButtonClicked(attacker, defender, attackableTile, damageToAttacker, damageToDefender)
            }
        }

        add(attackButton).colspan(columns)

        pack()

        setPosition(worldScreen.stage.width / 2 - width / 2, 5f)
    }

    private fun onAttackButtonClicked(
        attacker: ICombatant,
        defender: ICombatant,
        attackableTile: AttackableTile,
        damageToAttacker: Boolean,
        damageToDefender: Boolean
    ) {
        val canStillAttack = Battle.movePreparingAttack(attacker, attackableTile)
        worldScreen.mapHolder.removeUnitActionOverlay() // the overlay was one of attacking
        // There was a direct worldScreen.update() call here, removing its 'private' but not the comment justifying the modifier.
        // My tests (desktop only) show the red-flash animations look just fine without.
        worldScreen.shouldUpdate = true
        worldScreen.preActionGameInfo = worldScreen.gameInfo // Reset - can no longer undo
        //Gdx.graphics.requestRendering()  // Use this if immediate rendering is required

        if (!canStillAttack) return
        SoundPlayer.play(attacker.getAttackSound())
        Battle.attackOrNuke(attacker, attackableTile)

        worldScreen.battleAnimation(attacker, damageToAttacker, defender, damageToDefender)
    }

    private fun getRelation(attacker: MapUnitCombatant, victim: ICombatant): RelationshipLevel {
        val attackerCiv = attacker.getCivInfo()
        val victimCiv = victim.getCivInfo()
        return when {
            attackerCiv == victimCiv -> RelationshipLevel.Ally
            attackerCiv.isAtWarWith(victimCiv) -> RelationshipLevel.Enemy
            victimCiv.isCityState() && victimCiv.getAllyCiv() == attackerCiv.civName ->
                RelationshipLevel.Ally
            else -> RelationshipLevel.Neutral
        }
    }

    private fun simulateNuke(attacker: MapUnitCombatant, targetTile: Tile) {
        clear()

        val blastRadius = attacker.unit.getMatchingUniques(UniqueType.BlastRadius)
            .firstOrNull()?.run { params[0].toInt() }
            ?: 2

        data class VictimInfo(val combatant: ICombatant, val relation: RelationshipLevel)
        val victims =
            targetTile.getTilesInDistance(blastRadius)
            .mapNotNull { tryGetDefenderAtTile(it, true) }
            .map { VictimInfo(it, getRelation(attacker, it)) }
            .sortedWith(
                compareBy<VictimInfo> { it.combatant is MapUnitCombatant }
                .thenBy { it.relation }
                .thenBy { it.combatant.getName() }
            ).toList()

        val victimsByRelation = victims.groupBy { it.relation }
        val allyVictims = victimsByRelation[RelationshipLevel.Ally]?.size ?: 0
        val neutralVictims = victimsByRelation[RelationshipLevel.Neutral]?.size ?: 0
        val enemyVictims = victimsByRelation[RelationshipLevel.Enemy]?.size ?: 0
        val victimCount = allyVictims + neutralVictims + enemyVictims

        val onRightSet: Set<RelationshipLevel> = when {
            victimCount < 4 -> setOf()
            victimsByRelation.size <= 1 -> setOf()
            allyVictims >= enemyVictims + neutralVictims -> setOf(RelationshipLevel.Neutral, RelationshipLevel.Enemy)
            neutralVictims >= allyVictims + enemyVictims -> setOf(RelationshipLevel.Neutral)
            else -> setOf(RelationshipLevel.Enemy)
        }
        val forceBreak = if (victimCount < 8) Int.MAX_VALUE else (victimCount + 1) / 2

        val leftWrapper = Table()
        val rightWrapper = Table()
        fun getVictimLabel(victim: ICombatant, relation: RelationshipLevel): Label {
            val color = when(relation) {
                RelationshipLevel.Ally -> Color.YELLOW
                RelationshipLevel.Neutral -> Color.ORANGE
                else -> Color.WHITE
            }
            return victim.getName().toLabel(color, alignment = Align.center, hideIcons = true)
        }
        for ((victim, relation) in victims) {
            val table = if (relation in onRightSet || leftWrapper.rows >= forceBreak)
                rightWrapper else leftWrapper
            table.add(getCombatantIcon(victim)).padRight(5f).padBottom(3f)
            table.add(getVictimLabel(victim, relation)).fillX().row()
        }

        val minWidth = leftWrapper.prefWidth.coerceAtLeast(rightWrapper.prefWidth)
        val colSpan = if (rightWrapper.rows == 0) 1 else 2
        val attackerWrapper = getCombatantHeader(attacker, 0f)

        add(attackerWrapper).colspan(colSpan).fillX().center().row()
        add(leftWrapper).top().minWidth(minWidth)
        if (colSpan == 2) add(rightWrapper).top().minWidth(minWidth)
        row()

        val summary = "You will hit [$victimCount] victims: [$enemyVictims] enemies, «ORANGE»[$neutralVictims] innocent bystanders«» and «YELLOW»[$allyVictims] allies«»."
        val summaryLabel = ColorMarkupLabel(summary, 14)
        summaryLabel.wrap = true
        summaryLabel.setAlignment(Align.center)
        val summaryWidth = 200f
            .coerceAtLeast(attackerWrapper.prefWidth)
            .coerceAtLeast(colSpan * minWidth)
        add(summaryLabel).maxWidth(summaryWidth).colspan(colSpan).center().padTop(8f)

        val canNuke = Battle.mayUseNuke(attacker, targetTile)
        addAttackButtonAndPosition("NUKE", attacker, targetTile, canNuke) {
            Battle.NUKE(attacker, targetTile)
        }
    }

    private fun simulateAirsweep(attacker: MapUnitCombatant, targetTile: Tile) {
        clear()
        val modifiers = BattleDamage.getAirSweepAttackModifiers(attacker)
        val attackerStrength = attacker.getAttackingStrength() *
            (1f + modifiers.sumValues() / 100f)
        val attackerWrapper = getCombatantHeader(attacker, attackerStrength)
        val expectedWidth = defaultColumnWidth
            .coerceAtLeast(attackerWrapper.prefWidth) - 55f  // 55 is roughly "+33%" width
        val health = attacker.getHealth()
        attackerWrapper.add(getHealthBar(attacker.getMaxHealth(), health, health, health, false))
            .colspan(3).height(10f).padBottom(10f).growX().row()
        attackerWrapper.addAttackerModifiers(attacker, expectedWidth, modifiers)
        add(attackerWrapper).row()

        addAttackButtonAndPosition("Air Sweep", attacker, targetTile, true) {
            Battle.airSweep(attacker, targetTile)
        }
    }

    private fun addAttackButtonAndPosition(
        text: String,
        attacker: MapUnitCombatant,
        targetTile: Tile,
        canNuke: Boolean,
        action: () -> Unit
    ) {
        addSeparator(Color.GRAY, height = 1f).pad(8f,0f,8f,0f)

        val attackButton = text.toTextButton().apply { color = Color.RED }

        val canReach = attacker.getTile().aerialDistanceTo(targetTile) <= attacker.unit.getRange()
        if (worldScreen.isPlayersTurn && attacker.canAttack() && canReach && canNuke) {
            attackButton.onClick(attacker.getAttackSound()) {
                action()
                worldScreen.mapHolder.removeUnitActionOverlay() // the overlay was one of attacking
                worldScreen.shouldUpdate = true
            }
        } else {
            attackButton.disable()
            attackButton.label.color = Color.GRAY
        }

        add(attackButton).colspan(columns)
        pack()
        setPosition(worldScreen.stage.width / 2 - width / 2, 5f)
    }
}
