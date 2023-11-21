package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.KeyboardBindings
import com.unciv.ui.images.ImageGetter

internal enum class CityScreenPages(
    val caption: String,
    val icon: String,
    val binding: KeyboardBinding? = null //Todo
) {
    Info("Info", "StatIcons/Specialist"),
    Map("Map", "OtherIcons/Terrains"),
    Construction("Construction", "OtherIcons/Wonders"),
    StatBreakdown("Stats", "VictoryScreenIcons/Charts"),
    BuiltBuildings("Buildings", "OtherIcons/Cities"),
    ;
    companion object {
        val default = Info
        val size get() = values().size
        operator fun get(ordinal: Int) = values()[ordinal]
    }

    open fun getIcon(): Actor? = ImageGetter.getImage(icon)
    open fun getKey() = binding?.let { KeyboardBindings[it] } ?: KeyCharAndCode.UNKNOWN

}
