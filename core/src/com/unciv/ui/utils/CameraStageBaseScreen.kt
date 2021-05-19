package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.*
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.unciv.UncivGame
import com.unciv.models.Tutorial
import com.unciv.ui.tutorials.TutorialController
import com.unciv.ui.utils.Fonts.addMsdfFont

open class CameraStageBaseScreen : Screen {

    var game: UncivGame = UncivGame.Current
    var stage: Stage

    protected val tutorialController by lazy { TutorialController(this) }

    val keyPressDispatcher = KeyPressDispatcher()

    init {
        val resolutions: List<Float> = game.settings.resolution.split("x").map { it.toInt().toFloat() }
        val width = resolutions[0]
        val height = resolutions[1]

        stage = Stage(ExtendViewport(width, height), SpriteBatch())

        stage.addListener(
                object : InputListener() {
                    override fun keyTyped(event: InputEvent?, character: Char): Boolean {
                        val key = KeyCharAndCode(event, character)

                        if (key !in keyPressDispatcher || hasOpenPopups())
                            return super.keyTyped(event, character)

                        //try-catch mainly for debugging. Breakpoints in the vicinity can make the event fire twice in rapid succession, second time the context can be invalid
                        try {
                            keyPressDispatcher[key]?.invoke()
                        } catch (ex: Exception) {}
                        return true
                    }
                }
        )
    }

    override fun show() {}

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0f, 0f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        stage.act()
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun pause() {}

    override fun resume() {}

    override fun hide() {}

    override fun dispose() {}

    fun displayTutorial(tutorial: Tutorial, test: (() -> Boolean)? = null) {
        if (!game.settings.showTutorials) return
        if (game.settings.tutorialsShown.contains(tutorial.name)) return
        if (test != null && !test()) return
        tutorialController.showTutorial(tutorial)
    }

    companion object {
        lateinit var skin:Skin
        fun setSkin() {
            Fonts.resetFont()
            skin = Skin().apply {
                add("Nativefont", Fonts.font, BitmapFont::class.java)
                addMsdfFont()
                add("Button", ImageGetter.getRoundedEdgeTableBackground(), Drawable::class.java)
                addRegions(TextureAtlas("skin/flat-earth-ui.atlas"))
                load(Gdx.files.internal("skin/flat-earth-ui.json"))
            }
            skin.get(TextButton.TextButtonStyle::class.java).font = Fonts.font.apply { data.setScale(20 / Fonts.ORIGINAL_FONT_SIZE) }
            skin.get(CheckBox.CheckBoxStyle::class.java).font = Fonts.font.apply { data.setScale(20 / Fonts.ORIGINAL_FONT_SIZE) }
            skin.get(CheckBox.CheckBoxStyle::class.java).fontColor = Color.WHITE
            skin.get(Label.LabelStyle::class.java).font = Fonts.font.apply { data.setScale(18 / Fonts.ORIGINAL_FONT_SIZE) }
            skin.get(Label.LabelStyle::class.java).fontColor = Color.WHITE
            skin.get(TextField.TextFieldStyle::class.java).font = Fonts.font.apply { data.setScale(18 / Fonts.ORIGINAL_FONT_SIZE) }
            skin.get(SelectBox.SelectBoxStyle::class.java).font = Fonts.font.apply { data.setScale(20 / Fonts.ORIGINAL_FONT_SIZE) }
            skin.get(SelectBox.SelectBoxStyle::class.java).listStyle.font = Fonts.font.apply { data.setScale(20 / Fonts.ORIGINAL_FONT_SIZE) }
            skin
        }
        internal var batch: Batch = SpriteBatch()
    }

    /** It returns the assigned [InputListener] */
    fun onBackButtonClicked(action: () -> Unit): InputListener {
        val listener = object : InputListener() {
            override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    action()
                    return true
                }
                return false
            }
        }
        stage.addListener(listener)
        return listener
    }

}
