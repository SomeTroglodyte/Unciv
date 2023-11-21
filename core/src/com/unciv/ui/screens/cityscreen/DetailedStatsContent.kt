package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.StatTreeNode
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.equalizeColumns
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.screens.basescreen.BaseScreen
import java.text.DecimalFormat

class DetailedStatsContent(
    private val cityScreen: CityScreen
) : Table(), TabbedPager.IPageExtensions {
    private val header = Table()

    private var sourceHighlighted: String? = null
    private var onlyWithStat: Stat? = null
    private var isDetailed: Boolean = false

    private val colorTotal: Color = Color.BLUE.brighten(0.5f)
    private val colorSelector: Color = Color.GREEN.darken(0.5f)

    private val percentFormatter = DecimalFormat("0.#%").apply { positivePrefix = "+"; multiplier = 1 }
    private val decimalFormatter = DecimalFormat("0.#")

    init {
        header.defaults().pad(3f, 0f)
        defaults().pad(3f, 0f)
    }

    override fun getFixedContent() = header

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        update()
    }

    internal fun update() {
        header.clear()
        clear()

        val cityStats = cityScreen.city.cityStats
        val showFaith = cityScreen.city.civ.gameInfo.isReligionEnabled()

        val stats = when {
            onlyWithStat != null -> listOfNotNull(onlyWithStat)
            !showFaith -> Stat.values().filter { it != Stat.Faith }
            else -> Stat.values().toList()
        }
        val columnCount = stats.size + 1
        val statColMinWidth = if (onlyWithStat != null) 150f else 110f

        header.add(getToggleButton(isDetailed)).minWidth(150f).grow()

        for (stat in stats) {
            val label = stat.name.toLabel()
            label.onClick {
                onlyWithStat = if (onlyWithStat == null) stat else null
                update()
            }
            header.add(wrapInTable(label, if (onlyWithStat == stat) colorSelector else null))
                .minWidth(statColMinWidth).grow()
        }
        header.row()
        header.addSeparator().padBottom(2f)

        add("Base values".toLabel().apply { setAlignment(Align.center) })
            .colspan(columnCount).growX().row()
        addSeparator(colSpan = columnCount).padTop(2f)
        traverseTree(stats, cityStats.baseStatTree, mergeHappiness = true, percentage = false)

        addSeparator().padBottom(2f)
        add("Bonuses".toLabel().apply { setAlignment(Align.center) })
            .colspan(columnCount).growX().row()
        addSeparator().padTop(2f)
        traverseTree(stats, cityStats.statPercentBonusTree, percentage = true)

        addSeparator().padBottom(2f)
        add("Final".toLabel().apply { setAlignment(Align.center) })
            .colspan(columnCount).growX().row()
        addSeparator().padTop(2f)

        val final = LinkedHashMap<Stat, Float>()
        val map = cityStats.finalStatList.toSortedMap()

        for ((key, value) in cityScreen.city.cityStats.happinessList) {
            if (!map.containsKey(key)) {
                map[key] = Stats(happiness = value)
            } else if (map[key]!![Stat.Happiness] == 0f) {
                map[key]!![Stat.Happiness] = value
            }
        }

        for ((source, finalStats) in map) {

            if (finalStats.isEmpty())
                continue

            if (onlyWithStat != null && finalStats[onlyWithStat!!] == 0f)
                continue

            val label = source.toLabel(hideIcons = true).apply {
                setAlignment(Align.left)
                onClick {
                    sourceHighlighted = if (sourceHighlighted == source) null else source
                    update()
                }
            }

            val color = colorSelector.takeIf { sourceHighlighted == source }
            add(wrapInTable(label, color, Align.left)).grow()

            for (stat in stats) {
                val value = finalStats[stat]
                val cell = when (value) {
                    0f -> "-".toLabel()
                    else -> value.toOneDecimalLabel()
                }

                add(wrapInTable(cell, color)).grow()

                var f = final[stat]
                if (f == null)
                    f = 0f
                f += value
                final[stat] = f

            }
            row()
        }

        add(wrapInTable("Total".toLabel(), colorTotal)).grow()
        for (stat in stats) {
            add(wrapInTable(final[stat]?.toOneDecimalLabel(), colorTotal))
                .minWidth(statColMinWidth).grow()
        }
        row()

        equalizeColumns(this, header)
    }

    private fun getToggleButton(showDetails: Boolean): IconCircleGroup {
        val label = (if (showDetails) "-" else "+").toLabel()
        label.setAlignment(Align.center)
        val button = label
            .surroundWithCircle(25f, color = BaseScreen.skinStrings.skinConfig.baseColor)
            .surroundWithCircle(27f, false)
        button.onActivation(binding = KeyboardBinding.ShowStatDetails) {
            isDetailed = !isDetailed
            update()
        }
        button.keyShortcuts.add(Input.Keys.PLUS)  //todo Choose alternative (alt binding, remove, auto-equivalence, multikey bindings)
        return button
    }

    private fun traverseTree(
        stats: List<Stat>,
        statTreeNode: StatTreeNode,
        mergeHappiness: Boolean = false,
        percentage: Boolean = false,
        indentation: Int = 0
    ) {

        val total = LinkedHashMap<Stat, Float>()
        val map = statTreeNode.children.toSortedMap()

        if (mergeHappiness) {
            for ((key, value) in cityScreen.city.cityStats.happinessList) {
                if (!map.containsKey(key)) {
                    map[key] = StatTreeNode()
                    map[key]?.setInnerStat(Stat.Happiness, value)
                } else if (map[key]!!.totalStats.happiness == 0f) {
                    map[key]?.setInnerStat(Stat.Happiness, value)
                }
            }
        }

        for ((name, child) in map) {

            val text = "- ".repeat(indentation) + name.tr()

            if (child.totalStats.all { it.value == 0f }) {
                row()
                continue
            }

            if (onlyWithStat != null && child.totalStats[onlyWithStat!!] == 0f) {
                row()
                continue
            }

            val label = text.toLabel(hideIcons = true).apply {
                setAlignment(Align.left)
                onClick {
                    sourceHighlighted = if (sourceHighlighted == text) null else text
                    update()
                }
            }

            var color: Color? = null

            if (sourceHighlighted == text)
                color = colorSelector

            add(wrapInTable(label, color, Align.left)).fill().left()

            for (stat in stats) {
                val value = child.totalStats[stat]
                val cell = when {
                    value == 0f -> "-".toLabel()
                    percentage ->  value.toPercentLabel()
                    else -> value.toOneDecimalLabel()
                }

                add(wrapInTable(cell, color)).grow()

                if (indentation == 0) {
                    var current = total[stat]
                    if (current == null)
                        current = 0f
                    total[stat] = current + value
                }
            }

            row()
            if (isDetailed)
                traverseTree(stats, child, percentage = percentage, indentation = indentation + 1)

        }

        if (indentation == 0) {
            add(wrapInTable("Total".toLabel(), colorTotal)).grow()
            for (stat in stats) {
                if (percentage)
                    add(wrapInTable(total[stat]?.toPercentLabel(), colorTotal)).grow()
                else
                    add(wrapInTable(total[stat]?.toOneDecimalLabel(), colorTotal)).grow()
            }
            row()
        }

    }

    private fun wrapInTable(label: Label?, color: Color? = null, align: Int = Align.center) : Table {
        val tbl = Table()
        label?.setAlignment(align)
        if (color != null)
            tbl.background = BaseScreen.skinStrings.getUiBackground("General/Border", tintColor = color)
        tbl.add(label).growX()
        return tbl
    }

    private fun Float.toPercentLabel() = percentFormatter.format(this).toLabel()
    private fun Float.toOneDecimalLabel() = decimalFormatter.format(this).toLabel()

}
