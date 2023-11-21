package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.automation.Automation
import com.unciv.logic.city.City
import com.unciv.logic.map.tile.Tile
import com.unciv.models.TutorialTrigger
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.components.tilegroups.CityTileGroup
import com.unciv.ui.components.tilegroups.CityTileState
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.components.widgets.LoadingImage
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.popups.ToastPopup
import com.unciv.utils.Concurrency


internal class CityScreenMapPage(
    private val cityScreen: TabbedCityScreen,
    selectTile: Tile? = null
) : Table(), TabbedPager.IPageExtensions {
    internal val city = cityScreen.city

    /** Holds City tiles group*/
    private var tileGroups = ArrayList<CityTileGroup>()

    /** Mute update() while map is (re-)built */
    private var rebuildInProgress = false

    /** The ScrollPane for the background map view of the city surroundings */
    private val mapScrollPane = CityMapHolder()

    /** which tile is selected? **/
    internal var selectedTile: Tile? = selectTile

    /** Cached city.expansion.chooseNewTileToOwn() */
    private var nextTileToOwn: Tile? = null

    /** floating panel indicating unassigned population */
    private val unassignedPanel = CityScreenUnassignedPanel(this)

    /** floating Tile info panel */
    private val tileInfo = CityScreenTileTable(cityScreen)

    /** Support for [UniqueType.CreatesOneImprovement] - need user to pick a tile */
    class PickTileForImprovementData(
        val building: Building,
        val improvement: TileImprovement,
        val isBuying: Boolean,
        val buyStat: Stat
    )

    /** If set, we are waiting for the user to pick a tile for [UniqueType.CreatesOneImprovement] */
    private var pickTileData: PickTileForImprovementData? = null

    /** A [Building] with [UniqueType.CreatesOneImprovement] has been selected _in the queue_: show the tile it will place the improvement on */
    private var selectedQueueEntryTargetTile: Tile? = null

    private fun canCityBeChanged() = cityScreen.canCityBeChanged()

    init {
        // This is an empty Table until its tab is activated
        updateNextTileToOwn()
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(true)
        initializeTiles()
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(false)
    }

    internal fun updateNextTileToOwn() {
        nextTileToOwn = city.expansion.chooseNewTileToOwn()
    }

    private fun initializeTiles() {
        if (tileGroups.isNotEmpty()) return
        val loading = LoadingImage(80f)
        add(loading).center()
        Concurrency.run("City map") {
            rebuildInProgress
            val tileGroupMap = getTileGroupMap()
            Concurrency.runOnGLThread {
                mapScrollPane.actor = tileGroupMap
                loading.hide(::addMapScrollPane)
            }
        }
    }

    private fun addMapScrollPane() {
        clear()
        add(mapScrollPane).grow()
        validate()
        mapScrollPane.layout() // center scrolling
        mapScrollPane.scrollPercentX = 0.5f
        mapScrollPane.scrollPercentY = 0.5f
        mapScrollPane.updateVisualScroll()
        rebuildInProgress = false
        update()
    }

    private fun getTileGroupMap(): TileGroupMap<CityTileGroup> {
        val tileSetStrings = TileSetStrings()
        val cityTileGroups = city.getCenterTile().getTilesInDistance(5)
            .filter { city.civ.hasExplored(it) }
            .map { CityTileGroup(city, it, tileSetStrings) }

        for (tileGroup in cityTileGroups) {
            tileGroup.onClick { tileGroupOnClick(tileGroup, city) }
            tileGroup.layerMisc.onClick { tileWorkedIconOnClick(tileGroup, city) }
            tileGroup.layerMisc.onDoubleClick { tileWorkedIconDoubleClick(tileGroup, city) }
            tileGroups.add(tileGroup)
        }

        val tilesToUnwrap = mutableSetOf<CityTileGroup>()
        for (tileGroup in tileGroups) {
            val xDifference = city.getCenterTile().position.x - tileGroup.tile.position.x
            val yDifference = city.getCenterTile().position.y - tileGroup.tile.position.y
            //if difference is bigger than 5 the tileGroup we are looking for is on the other side of the map
            if (xDifference > 5 || xDifference < -5 || yDifference > 5 || yDifference < -5) {
                //so we want to unwrap its position
                tilesToUnwrap.add(tileGroup)
            }
        }

        return TileGroupMap(mapScrollPane, tileGroups, tileGroupsToUnwrap = tilesToUnwrap)
    }

    internal fun update() {
        if (rebuildInProgress) return

        unassignedPanel.update()

        if (selectedTile != null && tileInfo.parent == null) addActor(tileInfo)
        tileInfo.update(selectedTile)
        tileInfo.setPosition(width - 5f, 5f, Align.bottomRight)

        val cityUniqueCache = LocalUniqueCache()
        fun isExistingImprovementValuable(tile: Tile): Boolean {
            if (tile.improvement == null) return false
            val civInfo = city.civ

            val statDiffForNewImprovement = tile.stats.getStatDiffForImprovement(
                tile.getTileImprovement()!!,
                civInfo,
                city,
                cityUniqueCache
            )

            // If stat diff for new improvement is negative/zero utility, current improvement is valuable
            return Automation.rankStatsValue(statDiffForNewImprovement, civInfo) <= 0
        }

        fun Color.applyAlpha(alpha: Float) = Color().set(r, g, b, alpha)

        fun getPickImprovementColor(tile: Tile): Color {
            val improvementToPlace = pickTileData!!.improvement
            return when {
                tile.isMarkedForCreatesOneImprovement() -> Color.BROWN.applyAlpha(0.7f)
                !tile.improvementFunctions.canBuildImprovement(improvementToPlace, city.civ) -> Color.RED.applyAlpha(0.4f)
                isExistingImprovementValuable(tile) -> Color.ORANGE.applyAlpha(0.5f)
                tile.improvement != null -> Color.YELLOW.applyAlpha(0.6f)
                tile.turnsToImprovement > 0 -> Color.YELLOW.applyAlpha(0.6f)
                else -> Color.GREEN.applyAlpha(0.5f)
            }
        }

        if (rebuildInProgress) return
        for (tileGroup in tileGroups.toList()) {
            tileGroup.update()

            tileGroup.layerMisc.removeHexOutline()
            when {
                tileGroup.tile == nextTileToOwn ->
                    tileGroup.layerMisc.addHexOutline(colorFromRGB(200, 20, 220))
                /** Support for [UniqueType.CreatesOneImprovement] */
                tileGroup.tile == selectedQueueEntryTargetTile ->
                    tileGroup.layerMisc.addHexOutline(Color.BROWN)
                pickTileData != null && city.tiles.contains(tileGroup.tile.position) ->
                    tileGroup.layerMisc.addHexOutline(getPickImprovementColor(tileGroup.tile))
            }

            if (tileGroup.tile == selectedTile)
                tileGroup.layerOverlay.showHighlight(Color.BLUE)
            else
                tileGroup.layerOverlay.hideHighlight()

            if (tileGroup.tileState == CityTileState.BLOCKADED)
                cityScreen.displayTutorial(TutorialTrigger.CityTileBlockade)

            if (rebuildInProgress) return
        }
    }

    private fun tileWorkedIconOnClick(tileGroup: CityTileGroup, city: City) {
        if (!canCityBeChanged()) return
        val tile = tileGroup.tile

        // Cycling as: Not-worked -> Worked  -> Not-worked
        if (tileGroup.tileState == CityTileState.WORKABLE) {
            toggleTileWorked(tile)
            city.cityStats.update()
            cityScreen.updateStats()
            update()
        } else if (tileGroup.tileState == CityTileState.PURCHASABLE) {
            cityScreen.askToBuyTile(tile)
        }
    }

    private fun toggleTileWorked(tile: Tile) {
        if (!tile.providesYield() && city.population.getFreePopulation() > 0) {
            city.workedTiles.add(tile.position)
            cityScreen.game.settings.addCompletedTutorialTask("Reassign worked tiles")
        } else {
            city.workedTiles.remove(tile.position)
            city.lockedTiles.remove(tile.position)
        }
    }

    private fun tileWorkedIconDoubleClick(tileGroup: CityTileGroup, city: City) {
        if (!canCityBeChanged() || tileGroup.tileState != CityTileState.WORKABLE) return
        val tile = tileGroup.tile

        // Double-click should lead to locked tiles - both for unworked AND worked tiles

        val tileWasFree = !tile.isWorked()
        if (tileWasFree) // If not worked, try to work it first
            toggleTileWorked(tile)

        if (tile.isWorked())
            city.lockedTiles.add(tile.position)

        if (tileWasFree) {
            city.cityStats.update()
            cityScreen.updateStats()
        }
        update()
    }

    private fun tileGroupOnClick(tileGroup: CityTileGroup, city: City) {
        //todo: if (city.isPuppet) return - only prevent tile buying
        val tile = tileGroup.tile

        if (pickTileData == null)
            selectTile(tile)
        else
            pickCreatesOneImprovementTile(tile)

        update()
    }

    /** [UniqueType.CreatesOneImprovement] support - select tile for improvement */
    private fun pickCreatesOneImprovementTile(tileInfo: Tile) {
        val pickTileData = this.pickTileData!!
        this.pickTileData = null
        val improvement = pickTileData.improvement
        if (tileInfo.improvementFunctions.canBuildImprovement(improvement, city.civ)) {
            if (pickTileData.isBuying) {
                cityScreen.askToBuyConstruction(pickTileData.building, pickTileData.buyStat, tileInfo)
            } else {
                // This way to store where the improvement a CreatesOneImprovement Building will create goes
                // might get a bit fragile if several buildings constructing the same improvement type
                // were to be allowed in the queue - or a little nontransparent to the user why they
                // won't reorder - maybe one day redesign to have the target tiles attached to queue entries.
                tileInfo.improvementFunctions.markForCreatesOneImprovement(improvement.name)
                cityScreen.addToQueue(pickTileData.building.name)
            }
        }
    }

    internal fun selectConstruction(newConstruction: IConstruction) {
        if (newConstruction is Building && newConstruction.hasCreateOneImprovementUnique()) {
            val improvement = newConstruction.getImprovementToCreate(city.getRuleset())
            selectedQueueEntryTargetTile = if (improvement == null) null
                else city.cityConstructions.getTileForImprovement(improvement.name)
        } else {
            selectedQueueEntryTargetTile = null
            pickTileData = null
        }
        selectedTile = null
    }

    internal fun selectTile(newTile: Tile?) {
        selectedQueueEntryTargetTile = null
        pickTileData = null
        selectedTile = newTile
    }

    internal fun clearSelection() = selectTile(null)

    internal fun startPickTileForCreatesOneImprovement(construction: Building, stat: Stat, isBuying: Boolean) {
        val improvement = construction.getImprovementToCreate(city.getRuleset()) ?: return
        pickTileData = PickTileForImprovementData(construction, improvement, isBuying, stat)
        update()
        ToastPopup("Please select a tile for this building's [${improvement.name}]", cityScreen)
    }
    internal fun stopPickTileForCreatesOneImprovement() {
        if (pickTileData == null) return
        pickTileData = null
        update()
    }
}
