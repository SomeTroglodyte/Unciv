package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.city.CityFlags
import com.unciv.logic.city.CityResources
import com.unciv.logic.city.GreatPersonPointsBreakdown
import com.unciv.models.Counter
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.toGroup
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.ceil
import com.unciv.ui.components.widgets.AutoScrollPane as ScrollPane

class CityStatsTable(private val cityScreen: CityScreen) : Table() {
    private val innerTable = Table() // table within this Table. Slightly smaller creates border
    private val upperTable = Table() // fixed position table
    private val lowerTable = Table() // table that will be in the ScrollPane
    private val lowerPane: ScrollPane
    private val city = cityScreen.city
    private val lowerCell: Cell<ScrollPane>

    private val detailedStatsButton = "Stats".toTextButton().apply {
        labelCell.pad(10f)
        if (cityScreen is ClassicCityScreen)
            onActivation(binding = KeyboardBinding.ShowStats) {
                DetailedStatsPopup(cityScreen).open()
            }
    }

    init {
        top()
        pad(2f)
        background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CityStatsTable/Background",
            tintColor = colorFromRGB(194, 180, 131)
        )

        innerTable.pad(5f)
        innerTable.background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CityStatsTable/InnerTable",
            tintColor = Color.BLACK.cpy().apply { a = 0.8f }
        )
        innerTable.add(upperTable).row()

        upperTable.defaults().pad(2f)
        lowerTable.defaults().pad(2f)
        lowerPane = ScrollPane(lowerTable)
        lowerPane.setOverscroll(false, false)
        lowerPane.setScrollingDisabled(true, false)
        lowerCell = innerTable.add(lowerPane)

        add(innerTable).growY()
    }

    fun update(height: Float) {
        upperTable.clear()
        lowerTable.clear()

        if (cityScreen is ClassicCityScreen) {
            upperTable.add(CityScreenMiniStats(cityScreen) {
                cityScreen.update()
            })
            upperTable.addSeparator()
            upperTable.add(detailedStatsButton).row()
        }

        addText()

        // begin lowerTable
        addCitizenManagement()
        addGreatPersonPointInfo(city)
        if (!city.population.getMaxSpecialists().isEmpty()) {
            addSpecialistInfo()
        }
        if (city.religion.getNumberOfFollowers().isNotEmpty() && city.civ.gameInfo.isReligionEnabled())
            addReligionInfo()

        if (cityScreen is ClassicCityScreen)
            addBuildingsInfo()

        upperTable.pack()
        lowerTable.pack()
        lowerPane.layout()
        lowerPane.updateVisualScroll()
        lowerCell.maxHeight(height - upperTable.height - 8f) // 2 on each side of each cell in innerTable

        innerTable.pack()  // update innerTable
        pack()  // update self last
    }

    private fun onContentResize() {
        pack()
        setPosition(
            stage.width - CityScreen.posFromEdge,
            stage.height - CityScreen.posFromEdge,
            Align.topRight
        )
    }

    private fun addText() {
        val unassignedPopString = "{Unassigned population}: ".tr() +
                city.population.getFreePopulation().toString() + "/" + city.population.population
        val unassignedPopLabel = unassignedPopString.toLabel()
        if (cityScreen.canChangeState)
            unassignedPopLabel.onClick { city.reassignPopulation(); cityScreen.update() }

        var turnsToExpansionString =
                if (city.cityStats.currentCityStats.culture > 0 && city.expansion.getChoosableTiles().any()) {
                    val remainingCulture = city.expansion.getCultureToNextTile() - city.expansion.cultureStored
                    var turnsToExpansion = ceil(remainingCulture / city.cityStats.currentCityStats.culture).toInt()
                    if (turnsToExpansion < 1) turnsToExpansion = 1
                    "[$turnsToExpansion] turns to expansion".tr()
                } else "Stopped expansion".tr()
        if (city.expansion.getChoosableTiles().any())
            turnsToExpansionString +=
                    " (${city.expansion.cultureStored}${Fonts.culture}/${city.expansion.getCultureToNextTile()}${Fonts.culture})"

        var turnsToPopString =
                when {
                    city.isStarving() -> "[${city.population.getNumTurnsToStarvation()}] turns to lose population"
                    city.getRuleset().units[city.cityConstructions.currentConstructionFromQueue]
                        .let { it != null && it.hasUnique(UniqueType.ConvertFoodToProductionWhenConstructed) }
                    -> "Food converts to production"
                    city.isGrowing() -> "[${city.population.getNumTurnsToNewPopulation()}] turns to new population"
                    else -> "Stopped population growth"
                }.tr()
        turnsToPopString += " (${city.population.foodStored}${Fonts.food}/${city.population.getFoodToNextPopulation()}${Fonts.food})"

        upperTable.add(unassignedPopLabel).row()
        upperTable.add(turnsToExpansionString.toLabel()).row()
        upperTable.add(turnsToPopString.toLabel()).row()

        val tableWithIcons = Table()
        tableWithIcons.defaults().pad(2f)
        if (city.isInResistance()) {
            tableWithIcons.add(ImageGetter.getImage("StatIcons/Resistance")).size(20f)
            tableWithIcons.add("In resistance for another [${city.getFlag(CityFlags.Resistance)}] turns".toLabel()).row()
        }

        val resourceTable = Table()

        val resourceCounter = Counter<TileResource>()
        for (resourceSupply in CityResources.getCityResourcesAvailableToCity(city))
            resourceCounter.add(resourceSupply.resource, resourceSupply.amount)
        for ((resource, amount) in resourceCounter)
            if (resource.hasUnique(UniqueType.CityResource)) {
                resourceTable.add(amount.toLabel())
                resourceTable.add(ImageGetter.getResourcePortrait(resource.name, 20f))
                    .padRight(5f)
                }
        if (resourceTable.cells.notEmpty())
            tableWithIcons.add(resourceTable)

        val (wltkIcon: Actor?, wltkLabel: Label?) = when {
            city.isWeLoveTheKingDayActive() ->
                ImageGetter.getStatIcon("Food") to
                "We Love The King Day for another [${city.getFlag(CityFlags.WeLoveTheKing)}] turns".toLabel(Color.LIME)
            city.demandedResource.isNotEmpty() ->
                ImageGetter.getResourcePortrait(city.demandedResource, 20f) to
                "Demanding [${city.demandedResource}]".toLabel(Color.CORAL, hideIcons = true)
            else -> null to null
        }
        if (wltkLabel != null) {
            tableWithIcons.add(wltkIcon!!).size(20f).padRight(5f)
            wltkLabel.onClick {
                cityScreen.openCivilopedia("Tutorial/We Love The King Day")
            }
            tableWithIcons.add(wltkLabel).row()
        }

        upperTable.add(tableWithIcons).row()
    }

    private fun addCitizenManagement() {
        val expanderTab = CitizenManagementTable(cityScreen).asExpander { onContentResize() }
        lowerTable.add(expanderTab).growX().row()
    }

    private fun addSpecialistInfo() {
        val expanderTab = SpecialistAllocationTable(cityScreen).asExpander { onContentResize() }
        lowerTable.add(expanderTab).growX().row()
    }

    private fun addReligionInfo() {
        val expanderTab = CityReligionInfoTable(city.religion).asExpander { onContentResize() }
        lowerTable.add(expanderTab).growX().row()
    }

    private fun addBuildingsInfo() {
        val totalTable = CityScreenBuildingsTable(cityScreen) {
            cityScreen.selectConstruction(it)
            cityScreen.update()
        }
        totalTable.update()
        lowerTable.addCategory("Buildings", totalTable, KeyboardBinding.BuildingsDetail, false)
    }

    private fun Table.addCategory(
        category: String,
        showHideTable: Table,
        toggleKey: KeyboardBinding,
        startsOpened: Boolean = true
    ) : ExpanderTab {
        val expanderTab = ExpanderTab(
            title = category,
            fontSize = Constants.defaultFontSize,
            persistenceID = "CityInfo.$category",
            startsOutOpened = startsOpened,
            toggleKey = toggleKey,
            onChange = { onContentResize() }
        ) {
            it.add(showHideTable).fillX().right()
        }
        add(expanderTab).growX().row()
        return expanderTab
    }

    private fun addGreatPersonPointInfo(city: City) {

        val greatPeopleTable = Table()

        val gppBreakdown = GreatPersonPointsBreakdown(city)
        if (gppBreakdown.allNames.isEmpty())
            return
        val greatPersonPoints = gppBreakdown.sum()

        // Iterating over allNames instead of greatPersonPoints will include those where the aggregation had points but ended up zero
        for (greatPersonName in gppBreakdown.allNames) {
            val gppPerTurn = greatPersonPoints[greatPersonName]

            val info = Table()

            info.add(ImageGetter.getUnitIcon(greatPersonName, Color.GOLD).toGroup(20f))
                .left().padBottom(4f).padRight(5f)
            info.add("{$greatPersonName} (+$gppPerTurn)".toLabel(hideIcons = true)).left().padBottom(4f).expandX().row()

            val gppCurrent = city.civ.greatPeople.greatPersonPointsCounter[greatPersonName]
            val gppNeeded = city.civ.greatPeople.getPointsRequiredForGreatPerson(greatPersonName)

            val percent = gppCurrent / gppNeeded.toFloat()

            val progressBar = ImageGetter.ProgressBar(300f, 25f, false)
            progressBar.setBackground(Color.BLACK.cpy().apply { a = 0.8f })
            progressBar.setProgress(Color.ORANGE, percent)
            progressBar.apply {
                val bar = ImageGetter.getWhiteDot()
                bar.color = Color.GRAY
                bar.setSize(width+5f, height+5f)
                bar.center(this)
                addActor(bar)
                bar.toBack()
            }
            progressBar.setLabel(Color.WHITE, "$gppCurrent/$gppNeeded", fontSize = 14)

            info.add(progressBar).colspan(2).left().expandX().row()
            info.onClick {
                GreatPersonPointsBreakdownPopup(cityScreen, gppBreakdown, greatPersonName)
            }
            greatPeopleTable.add(info).growX().top().padBottom(10f)
            val icon = ImageGetter.getConstructionPortrait(greatPersonName, 50f)
            icon.onClick {
                GreatPersonPointsBreakdownPopup(cityScreen, gppBreakdown, null)
            }
            greatPeopleTable.add(icon).row()
        }

        lowerTable.addCategory("Great People", greatPeopleTable, KeyboardBinding.GreatPeopleDetail)
    }

}
