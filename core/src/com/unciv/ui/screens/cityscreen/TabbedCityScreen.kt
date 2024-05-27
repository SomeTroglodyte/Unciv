package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.stats.Stat
import com.unciv.ui.audio.CityAmbiencePlayer
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.input.KeyShortcutDispatcherVeto
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.screens.worldscreen.WorldScreen

//Todo Icons: Copy to moddable names and/or redo
//Todo floating "Select tile for building" instead of Toast on map
//Todo Header can underflow -> why is there empty space left and right
//Todo Pager build when complete, else, nullable
//Todo Keyboard bindings

//Todo CityStatsTable does not cooperate in Layout
//Todo CityScreenConstructionsPage does not cooperate in Layout
//Todo Map pane jumps off-layout after zooming
//Todo Stats breakdown header squished
//Todo Stats breakdown needs padding left/right, but without shortening separators
//Todo Stats breakdown after selecting a stat - shouldn't expand X unlimited

internal class TabbedCityScreen(
    override val city: City,
    selectPage: CityScreenPages = CityScreenPages.default,
    selectConstruction: IConstruction? = null,
    selectTile: Tile? = null
): CityScreen() {
    private val cityAmbiencePlayer = CityAmbiencePlayer(city)

    private val wrapper: Table
    private val header: CityScreenHeader
    private val miniStats = CityScreenMiniStats(this) {
        updateStats()
        updateMap()
    }
    private val pager: TabbedPager

    private val infoPage = CityStatsTable(this)
    private val mapPage = CityScreenMapPage(this, selectTile)
    private val constructionPage = CityScreenConstructionsPage(this, selectConstruction)
    private val statsBreakdownPage = DetailedStatsContent(this)
    private val buildingsPage = CityScreenBuildingsTable(this) {
        selectConstruction(it)
        updateConstruction()
    }

    override val selectedConstruction get() = constructionPage.selectedConstruction

    val activePageID get() = CityScreenPages[pager.activePage]

    init {
        val settings = UncivGame.Current.settings
        if (city.isWeLoveTheKingDayActive() && settings.citySoundsVolume > 0) {
            SoundPlayer.play(UncivSound("WLTK"))
        }
        settings.addCompletedTutorialTask("Enter city screen")

        for (cell in infoPage.cells) cell.growX().fillY()

        val stageWidth = stage.width
        val stageHeight = stage.height
        header = CityScreenHeader(this)

        val pagerHeight = stageHeight - header.prefHeight - miniStats.prefHeight - 2f
        pager = TabbedPager(stageWidth, stageWidth, pagerHeight, pagerHeight, shortcutScreen = this, capacity = CityScreenPages.size)
        for (page in CityScreenPages.values()) {
            val content: Actor = when(page) {
                CityScreenPages.Info -> infoPage
                CityScreenPages.Map -> mapPage
                CityScreenPages.Construction -> constructionPage
                CityScreenPages.StatBreakdown -> statsBreakdownPage
                CityScreenPages.BuiltBuildings -> buildingsPage
            }
            pager.addPage(page.caption, content, page.getIcon(), shortcutKey = page.getKey())
        }
        pager.onSelection { _, _, _ -> update() }

        wrapper = Table().apply {
            add(header).growX().row()
            add(miniStats).center()
            addSeparator()
            add(pager).grow()
            setFillParent(true)
        }
        stage.addActor(wrapper)

        pager.selectPage(selectPage.ordinal)
    }

    fun exit() {
        val newScreen = game.popScreen()
        if (newScreen is WorldScreen) {
            newScreen.mapHolder.setCenterPosition(city.location, immediately = true)
            newScreen.bottomUnitTable.selectUnit()
        }
    }

    // We contain a map...
    override fun getShortcutDispatcherVetoer() = KeyShortcutDispatcherVeto.createTileGroupMapDispatcherVetoer()

    //override fun recreate(): BaseScreen = TabbedCityScreen(city, activePageID, constructionPage.selectedConstruction, mapPage.selectedTile)

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        update()
    }
    override fun dispose() {
        cityAmbiencePlayer.dispose()
        super.dispose()
    }

    override fun update() {
        header.update()
        updateStats()
        updateMap()
        updateConstruction()
    }

    override fun updateWithoutConstructionAndMap() {
        updateName()
        updateStats()
    }

    override fun askToBuyTile(selectedTile: Tile) = askToBuyTile(this, selectedTile) {
        // preselect the next tile on city screen rebuild so bulk buying can go faster
        mapPage.updateNextTileToOwn()
        updateStats()
        mapPage.update()
    }

    internal fun updateName() {
        header.update(name = true)
        updateMap()
    }
    internal fun updateAnnexOrRaze() {
        header.update(annexOrRaze = true)
        updateStats()
    }
    internal fun updateStats() {
        //todo check if city.cityStats.update() should move here
        miniStats.update()
        if (activePageID == CityScreenPages.Info)
            infoPage.update(pager.height - pager.headerScroll.height)
        //todo
    }
    private fun updateMap() {
        if (activePageID == CityScreenPages.Map)
            mapPage.update()
    }

    private fun updateConstruction() {
        if (activePageID == CityScreenPages.Construction)
            constructionPage.update()
    }

    internal fun askToBuyConstruction(building: Building, buyStat: Stat, tileInfo: Tile) {
        TODO()
    }

    internal fun addToQueue(buildingName: String) {
        TODO()
    }

    override fun clearSelection() {
        constructionPage.clearSelection()
        mapPage.clearSelection()
    }
    override fun selectConstruction(newConstruction: IConstruction) {
        constructionPage.selectConstruction(newConstruction)
        mapPage.selectConstruction(newConstruction)
    }
    override fun selectTile(newTile: Tile?) {
        constructionPage.clearSelection()
        mapPage.selectTile(newTile)
    }

    override fun startPickTileForCreatesOneImprovement(construction: Building, stat: Stat, isBuying: Boolean) =
        mapPage.startPickTileForCreatesOneImprovement(construction, stat, isBuying)

    override fun stopPickTileForCreatesOneImprovement() =
        mapPage.stopPickTileForCreatesOneImprovement()
}
