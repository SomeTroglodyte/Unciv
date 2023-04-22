package com.unciv.logic.automation.unit

import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.map.tile.Tile

class AttackableTile(
    val tileToAttackFrom: Tile,
    val tileToAttack: Tile,
    val movementLeftAfterMovingToAttackTile: Float,
    @Suppress("unused") /** This is only for debug purposes */
    val combatant: ICombatant? = Battle.getMapCombatantOfTile(tileToAttack)
) {
    constructor(attacker: ICombatant, defender: ICombatant)
        : this(attacker.getTile(), defender.getTile(), 0f, defender)
}
