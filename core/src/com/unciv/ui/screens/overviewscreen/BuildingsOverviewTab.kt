package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Building
import com.unciv.models.stats.INamed
import com.unciv.models.translations.tr
import com.unciv.ui.components.ISortableGridContentProvider
import com.unciv.ui.components.ISortableGridContentProvider.Companion.toCenteredLabel
import com.unciv.ui.components.extensions.equalizeColumns
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.SortableGrid
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

class BuildingsOverviewTab(
    viewingPlayer: Civilization,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    enum class RowType { Buildings, Cities }
    class BuildingTabPersistableData : EmpireOverviewTabPersistableData() {
        var rowType: RowType = RowType.Buildings
        override fun isEmpty() = rowType == RowType.Buildings
        fun toggleRows() { rowType = when(rowType) { RowType.Buildings -> RowType.Cities; RowType.Cities -> RowType.Buildings } }
    }
    override val persistableData = (persistedData as? BuildingTabPersistableData) ?: BuildingTabPersistableData()

    companion object {
        const val iconSize = 24f
        val canAddColor: Color = Color.DARK_GRAY
        val queuedColor: Color = Color(0x70cf70ff)
        val isBuiltColor: Color = Color.WHITE
    }

    private val buildingsToShow = gameInfo.ruleset.buildings.values.filter { building ->
        viewingPlayer.cities.any { city ->
            city.cityConstructions.isBuilt(building.name)
                || city.cityConstructions.isBeingConstructedOrEnqueued(building.name)
                || city.cityConstructions.canAddToQueue(building)
        }
    }

    private val cityColumns by lazy {
        val sortedCities = viewingPlayer.cities.sortedWith(compareBy(ISortableGridContentProvider.collator) { it.name.tr() })
        val cityColumns = sortedCities.map { CityColumn(it) }.toTypedArray()
        listOf(BuildingNameColumn(::toggle), *cityColumns)
    }

    private val buildingColumns by lazy {
        val sortedBuildings = buildingsToShow.sortedWith(compareBy(ISortableGridContentProvider.collator) { it.name.tr() })
        val buildingColumns = sortedBuildings.map { BuildingColumn(it) }.toTypedArray()
        listOf(CityNameColumn(::toggle), *buildingColumns)
    }

    private val rowTypeBuildingsSortState by lazy {
        object : SortableGrid.ISortState<ISortableGridContentProvider<Building, EmpireOverviewScreen>> {
            override var sortedBy = cityColumns.first()
            override var direction = SortableGrid.SortDirection.Ascending
        }
    }

    private val rowTypeCitiesSortState by lazy {
        object : SortableGrid.ISortState<ISortableGridContentProvider<City, EmpireOverviewScreen>> {
            override var sortedBy = buildingColumns.first()
            override var direction = SortableGrid.SortDirection.Ascending
        }
    }

    private lateinit var grid: TransposableSortableGrid<*>
    private val headerWrapper: Container<Table> = Container() // TabbedPager fetches getFixedContent before we build one of the grids

    override fun getFixedContent() = headerWrapper

    fun toggle() {
        persistableData.toggleRows()
        update()
    }

    fun update() {
        ensureGrid()
        grid.update()
    }

    private fun ensureGrid() {
        if (::grid.isInitialized && grid.rowType == persistableData.rowType) return
        grid = when (persistableData.rowType) {
            RowType.Buildings -> BuildingRowsGrid()
            RowType.Cities -> CityRowsGrid()
        }
        clear()
        top()
        headerWrapper.actor = grid.getHeader()
        headerWrapper.height(headerWrapper.actor.prefHeight)
        headerWrapper.invalidateHierarchy()
        //todo There must be a better way... headerWrapper.parent is a LinkedScrollPane and thus does not propagate the height we just set (+5f some padding)
        (headerWrapper.parent.parent as Table).getCell(headerWrapper.parent).minHeight(headerWrapper.actor.prefHeight + 5f)
        add(grid)
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        update()
    }

    private abstract inner class TransposableSortableGrid<IT>(
        columns: Iterable<ISortableGridContentProvider<IT, EmpireOverviewScreen>>,
        data: Iterable<IT>,
        empireOverviewScreen: EmpireOverviewScreen,
        sortState: ISortState<ISortableGridContentProvider<IT, EmpireOverviewScreen>>
    ) : SortableGrid<IT, EmpireOverviewScreen, ISortableGridContentProvider<IT, EmpireOverviewScreen>>(
        columns, data, empireOverviewScreen,
        sortState = sortState,
        iconSize = iconSize,
        separateHeader = true,
        updateCallback = ::updateCallback
    ) {
        abstract val rowType: RowType
    }

    fun updateCallback(header: Table, details: Table, totals: Table) {
        // For comments, see CityOverviewTab or UnitOverviewTab. Moving the functionality to SortableGrid so far unsuccessful.
        equalizeColumns(details, header, totals)
        if (header.width < this.width) header.width = this.width
        this.validate()
    }

    private inner class BuildingRowsGrid : TransposableSortableGrid<Building>(
        cityColumns,
        buildingsToShow,
        overviewScreen,
        rowTypeBuildingsSortState
    ) {
        override val rowType = RowType.Buildings
    }

    private inner class CityRowsGrid : TransposableSortableGrid<City>(
        buildingColumns,
        viewingPlayer.cities,
        overviewScreen,
        rowTypeCitiesSortState
    ) {
        override val rowType = RowType.Cities
    }

    private abstract class NameColumn<IT: INamed> : ISortableGridContentProvider<IT, EmpireOverviewScreen> {
        override val headerTip get() = ""
        override val align get() = Align.left
        override val fillX get() = true
        override val expandX get() = false
        override val equalizeHeight get() = false
        override val defaultSort get() = SortableGrid.SortDirection.Ascending
        override fun getEntryValue(item: IT) =  0
        override fun getComparator() = compareBy<IT, String>(ISortableGridContentProvider.collator) { it.name.tr(hideIcons = true) }
        override fun getTotalsActor(items: Iterable<IT>) = items.count().toLabel()
        protected fun getHeaderActor(text: String, iconSize: Float, rotation: Float, toggle: ()->Unit) = Table().apply {
            val arrow = ImageGetter.getImage("OtherIcons/Turn right")
            arrow.color = BaseScreen.skinStrings.skinConfig.baseColor
            arrow.rotation = rotation
            val icon = arrow.surroundWithCircle(iconSize, color = Color.LIGHT_GRAY)
            icon.onClick(toggle)
            add(icon).padRight(5f)
            add(text.toLabel())
            left()
        }
    }
    private class BuildingNameColumn(private val toggle: ()->Unit) : NameColumn<Building>() {
        override fun getHeaderActor(iconSize: Float) = getHeaderActor("Building name", iconSize, 0f, toggle)
        override fun getEntryActor(item: Building, iconSize: Float, actionContext: EmpireOverviewScreen) =
            item.name.toLabel(if (item.isAnyWonder()) Color.GOLD else Color.WHITE, hideIcons = true)
                .onClick { actionContext.openCivilopedia(item.makeLink()) }
    }
    private class CityNameColumn(private val toggle: ()->Unit) : NameColumn<City>() {
        override fun getHeaderActor(iconSize: Float) = getHeaderActor("City name", iconSize, 90f, toggle)
        override fun getEntryActor(item: City, iconSize: Float, actionContext: EmpireOverviewScreen) =
            item.name.toLabel(if (item.isInResistance()) Color.FIREBRICK else Color.WHITE, hideIcons = true)
    }

    private class CityColumn(private val city: City) : ISortableGridContentProvider<Building, EmpireOverviewScreen> {
        override val headerTip get() = city.name
        override val align get() = Align.center
        override val fillX get() = false
        override val expandX get() = false
        override val equalizeHeight get() = false
        override val defaultSort get() = SortableGrid.SortDirection.Descending
        override fun getHeaderActor(iconSize: Float) = ImageGetter.getImage("OtherIcons/Cities").apply { setSize(iconSize) }
        override fun getEntryValue(item: Building) = when {
            city.cityConstructions.isBuilt(item.name) -> 3
            city.cityConstructions.isBeingConstructedOrEnqueued(item.name) -> 2
            city.cityConstructions.canAddToQueue(item) -> 1
            else -> 0
        }
        override fun getEntryActor(item: Building, iconSize: Float, actionContext: EmpireOverviewScreen): Actor? {
            val value = getEntryValue(item)
            if (value == 0) return null
            val icon = ImageGetter.getConstructionPortrait(item.name, iconSize)
            icon.color = when(value) {
                1 -> canAddColor
                2 -> queuedColor
                else -> isBuiltColor
            }
            return icon
        }
        override fun getTotalsActor(items: Iterable<Building>) = items.count { getEntryValue(it) == 3 }.toCenteredLabel()
    }

    private class BuildingColumn(private val building: Building) : ISortableGridContentProvider<City, EmpireOverviewScreen> {
        override val headerTip get() = building.name
        override val align get() = Align.center
        override val fillX get() = false
        override val expandX get() = false
        override val equalizeHeight get() = false
        override val defaultSort get() = SortableGrid.SortDirection.Descending
        override fun getHeaderActor(iconSize: Float) = ImageGetter.getConstructionPortrait(building.name, iconSize)
        override fun getEntryValue(item: City) = when {
            item.cityConstructions.isBuilt(building.name) -> 3
            item.cityConstructions.isBeingConstructedOrEnqueued(building.name) -> 2
            item.cityConstructions.canAddToQueue(building) -> 1
            else -> 0
        }
        override fun getEntryActor(item: City, iconSize: Float, actionContext: EmpireOverviewScreen): Actor? {
            val value = getEntryValue(item)
            if (value == 0) return null
            val icon = ImageGetter.getConstructionPortrait(building.name, iconSize)
            icon.color = when(value) {
                1 -> canAddColor
                2 -> queuedColor
                else -> isBuiltColor
            }
            return icon
        }
        override fun getTotalsActor(items: Iterable<City>) = items.count { getEntryValue(it) == 3 }.toCenteredLabel()
    }
}
