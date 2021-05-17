package com.unciv.ui.civilopedia

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.Constants
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.surroundWithCircle
import java.io.File

object CivilopediaImageGetters {
    private const val policyIconFolder = "PolicyIcons"

    // Todo: potential synergy with map editor
    fun terrainImage(terrain: Terrain, ruleset: Ruleset, imageSize: Float): Actor {
        val tileInfo = TileInfo()
        tileInfo.ruleset = ruleset
        when (terrain.type) {
            TerrainType.NaturalWonder -> {
                tileInfo.naturalWonder = terrain.name
                tileInfo.baseTerrain = terrain.turnsInto ?: Constants.grassland
            }
            TerrainType.TerrainFeature -> {
                tileInfo.terrainFeatures.add(terrain.name)
                tileInfo.baseTerrain = terrain.occursOn.lastOrNull() ?: Constants.grassland
            }
            else ->
                tileInfo.baseTerrain = terrain.name
        }
        tileInfo.setTerrainTransients()
        val group = TileGroup(tileInfo, TileSetStrings(), imageSize * 40f/54f)  // TileGroup normally spills out of its bounding box
        group.showEntireMap = true
        group.forMapEditorIcon = true
        group.update()
        return group
    }

    val construction = { name: String, size: Float ->
        ImageGetter.getConstructionImage(name)
            .surroundWithCircle(size, color = ICivilopediaText.FormattingConstants.defaultColor)
    }
    val improvement = { name: String, size: Float ->
        ImageGetter.getImprovementIcon(name, size)
    }
    val nation = { name: String, size: Float ->
        val nation = ImageGetter.ruleset.nations[name]
        if (nation == null) null
        else ImageGetter.getNationIndicator(nation, size)
    }
    val policy = { name: String, size: Float ->
        ImageGetter.getImage(policyIconFolder + File.separator + name)
            .apply { setSize(size,size) }
    }
    val resource = { name: String, size: Float ->
        ImageGetter.getResourceImage(name, size)
    }
    val technology = { name: String, size: Float ->
        ImageGetter.getTechIcon(name).apply{
            color = ICivilopediaText.FormattingConstants.defaultColor
            setSize(size,size)
        }
    }
    val promotion = { name: String, size: Float ->
        ImageGetter.getPromotionIcon(name, size)
    }
    val terrain = { name: String, size: Float ->
        val terrain = ImageGetter.ruleset.terrains[name]
        if (terrain == null) null
        else terrainImage(terrain, ImageGetter.ruleset, size)
    }
}

/** Enum used as keys for Civilopedia "pages" (categories).
 *
 *  Note names are singular on purpose - a "link" allows both key and label
 *
 * @param label Translatable caption for the Civilopedia button
 */
enum class CivilopediaCategories (
        val label: String,
        val getImage: ((name: String, size: Float) -> Actor?)?
    ) {
    Building ("Buildings", CivilopediaImageGetters.construction ),
    Wonder ("Wonders", CivilopediaImageGetters.construction ),
    Resource ("Resources", CivilopediaImageGetters.resource ),
    Terrain ("Terrains", CivilopediaImageGetters.terrain ),
    Improvement ("Tile Improvements", CivilopediaImageGetters.improvement ),
    Unit ("Units", CivilopediaImageGetters.construction ),
    Nation ("Nations", CivilopediaImageGetters.nation ),
    Technology ("Technologies", CivilopediaImageGetters.technology ),
    Promotion ("Promotions", CivilopediaImageGetters.promotion ),
    Tutorial ("Tutorials", null ),
    Difficulty ("Difficulty levels", null ),
    Policy ("Policies", CivilopediaImageGetters.policy )    // Omitted on CivilopediaScreen
    ;

    companion object {
        fun fromLink(name: String): CivilopediaCategories? =
            values().firstOrNull { it.name == name }
            ?: values().firstOrNull { it.label == name }

    }
}
