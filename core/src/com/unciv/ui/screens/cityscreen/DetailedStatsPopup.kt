package com.unciv.ui.screens.cityscreen

import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.popups.Popup

class DetailedStatsPopup(
    cityScreen: CityScreen
) : Popup(cityScreen, Scrollability.None) {
    private val content = DetailedStatsContent(cityScreen)

    init {
        add(content.getFixedContent()).padBottom(0f).row()

        val scrollPane = AutoScrollPane(content)
        scrollPane.setOverscroll(false, false)
        val scrollPaneCell = add(scrollPane).padTop(0f)
        scrollPaneCell.maxHeight(cityScreen.stage.height * 3 / 4)

        row()
        addCloseButton(additionalKey = KeyCharAndCode.SPACE)
        content.update()
    }
}
