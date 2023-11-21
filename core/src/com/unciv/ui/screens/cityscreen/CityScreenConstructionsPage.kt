package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.ruleset.IConstruction
import com.unciv.ui.components.widgets.TabbedPager

internal class CityScreenConstructionsPage(
    private val cityScreen: TabbedCityScreen,
    selectConstruction: IConstruction? = null
) : Table(), TabbedPager.IPageExtensions {
    private val city = cityScreen.city

    private val factory = CityConstructionsTable(cityScreen)
    private val infoPanel = ConstructionInfoTable(cityScreen)

    var selectedConstruction: IConstruction? = selectConstruction
        private set

    init {
        top()
        val constructionsQueueScrollPane = factory.constructionsQueueScrollPane
        val availableConstructionsScrollPane = factory.availableConstructionsScrollPane
        val buyButtonsTable = factory.buyButtonsTable
        constructionsQueueScrollPane.name = "constructionsQueueScrollPane" // debug
        availableConstructionsScrollPane.name = "availableConstructionsScrollPane" // debug
        constructionsQueueScrollPane.remove()
        availableConstructionsScrollPane.remove()
        buyButtonsTable.remove()
        val leftColumn = Table().apply {
            top()
            val minHeight = cityScreen.stage.height / 5f
            add(constructionsQueueScrollPane).top().minHeight(minHeight).growX().row()
            add(buyButtonsTable).growX().row()
            add().growY().row()
            add(infoPanel).bottom().growX().row()
        }
        add(leftColumn).top().grow().uniformX()
        add(availableConstructionsScrollPane).top().growX().uniformX()
    }

    fun update() = factory.update(selectedConstruction)

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(true)
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(false)
    }

    fun clearSelection() = selectConstruction(null)

    fun selectConstruction(newConstruction: IConstruction?) {
        selectedConstruction = newConstruction
        infoPanel.update(newConstruction)
    }
}
