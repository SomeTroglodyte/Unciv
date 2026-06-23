package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.github.GithubAPI
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.validation.ModCompatibility
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.widgets.WrappableLabel
import com.unciv.ui.popups.ContextMenus
import com.unciv.ui.screens.modmanager.ModManagementScreen
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.modmanager.ModPreviewImageHolder
import com.unciv.ui.screens.newgamescreen.ModCheckboxTable.ModContextMenuDescriptor.addContextMenu
import com.unciv.utils.Concurrency

/**
 * A widget containing one expander for extension mods.
 * Manages compatibility checks, warns or prevents incompatibilities.
 *
 * @param mods **Reference**: In/out set of active mods, modified in place: If this needs to change, call [changeGameParameters]
 * @param initialBaseRuleset The selected base Ruleset, only for running mod checks against. Use [setBaseRuleset] to change on the fly.
 * @param screen Parent screen, used to show [ToastPopup]s or call [NewGameScreen.getColumnWidth]
 * @param isPortrait Used only for minor layout tweaks, arrangement is always vertical
 * @param onUpdate Callback, parameter is the mod name, called after any checks that may prevent mod selection succeed.
 */
class ModCheckboxTable(
    private var mods: LinkedHashSet<String>,
    initialBaseRuleset: String,
    private val screen: BaseScreen,
    isPortrait: Boolean = false,
    private val onUpdate: (String) -> Unit
): Table() {
    private var baseRulesetName = ""
    private lateinit var baseRuleset: Ruleset

    private val modWidgets = ArrayList<ModCheckBox>()

    /** Saved result from any complex mod check unless the causing selection has already been reverted.
     *  In other words, this can contain the text for an "Error" level check only if the Widget was
     *  initialized with such an invalid mod combination.
     *  This Widget reverts User changes that cause an Error severity immediately and this field is nulled.
     */
    var savedModcheckResult: String? = null

    private var disableChangeEvents = false

    private val expanderPadTop = if (isPortrait) 0f else 16f

    private val modRepoCache = buildMap {
        for (item in UncivGame.Current.files.loadModCache())
            if (item.repo != null && item.name in RulesetCache)
                this[item.name] = item.repo
        for (mod in RulesetCache)
            if (mod.key !in this)
                this[mod.key] = GithubAPI.Repo().apply { owner.login = mod.value.modOptions.author }
    }

    init {
        val modRulesets = RulesetCache.values.filter {
            ModCompatibility.isExtensionMod(it)
        }

        for (mod in modRulesets.sortedBy { it.name }) {
            modWidgets += ModCheckBox(mod)
        }

        setBaseRuleset(initialBaseRuleset)
    }

    /**
     *  CheckBox for an extension mod
     *  * Mod name is **not** translated
     */
    private inner class ModCheckBox(val mod: Ruleset) : CheckBox(ModManagementScreen.cleanModName(mod.name), BaseScreen.skin) {
        init {
            left()
            isChecked = mod.name in mods
            onChange {
                if (disableChangeEvents) return@onChange // checkBoxChanged checks again to be sure
                // Checks are run in parallel thread to avoid ANRs
                Concurrency.run { checkBoxChanged(this@ModCheckBox) }
            }
            modRepoCache[mod.name]?.let { addDescription(it) }
        }
        private fun addDescription(repo: GithubAPI.Repo) {
            val maxWidth = (screen as? NewGameScreen)?.getColumnWidth() ?: (screen.stage.width / 3f)
            if (repo.description != null) {
                val text = "${repo.description}: {[${repo.stargazers_count}]${Fonts.star}}"
                val label = WrappableLabel(text, maxWidth, BaseScreen.skinStrings.skinConfig.baseColor)
                label.wrap = true
                addTooltip(label)
            }
            addContextMenu(ModContextMenuDescriptor.Context(screen.stage, maxWidth, this, repo))
        }
    }

    private object ModContextMenuDescriptor : ContextMenus.IDescriptor<ModContextMenuDescriptor.Menu, ModContextMenuDescriptor.Context> {
        class Context(val stage: Stage, val maxWidth: Float, val checkBox: ModCheckBox, val repo: GithubAPI.Repo) : ContextMenus.IContext

        override fun createMenu(context: Context) = Menu(context)

        class Menu(val context: Context) : ContextMenus.Menu(context.stage, context.checkBox) {
            val mod = context.checkBox.mod
            override fun createContentTable() = super.createContentTable()!!.apply {
                padTop(15f).defaults().pad(0f).space(15f)

                val maxPreviewSize = context.maxWidth.coerceAtMost(context.stage.height * 0.4f)
                val preview = ModPreviewImageHolder(maxPreviewSize)
                preview.addLocalPreviewImage(mod.name)
                add(preview).row()

                fun addWrappedLabel(text: String, fontColor: Color = Color.WHITE) =
                    add(WrappableLabel(text, context.maxWidth, fontColor).apply {
                        wrap = true
                        setAlignment(Align.center)
                    }).center().row()
                addWrappedLabel("Author: [${context.repo.owner.login}]", Color.GOLDENROD)
                context.repo.description?.let { addWrappedLabel(it) }
                addWrappedLabel(mod.getSummary())
                onRightClick { close() }
            }
        }
    }

    fun updateSelection() {
        savedModcheckResult = null
        disableChangeEvents = true
        for (mod in modWidgets) {
            mod.isChecked = mod.mod.name in mods
        }
        disableChangeEvents = false
        deselectIncompatibleMods(null)
    }

    fun setBaseRuleset(newBaseRulesetName: String) {
        val newBaseRuleset = RulesetCache[newBaseRulesetName]
            // We're calling this from init, baseRuleset is lateinit, and the mod may have been deleted: Must make sure baseRuleset is initialized
            ?: return setBaseRuleset(BaseRuleset.Civ_V_GnK.fullName)
        baseRulesetName = newBaseRulesetName
        baseRuleset = newBaseRuleset
        savedModcheckResult = null
        clear()
        mods.clear()  // We'll regenerate this from checked widgets

        val compatibleMods = modWidgets
            .filter { ModCompatibility.meetsBaseRequirements(it.mod, baseRuleset) }

        if (compatibleMods.none()) return

        for (mod in compatibleMods) {
            if (mod.isChecked) mods += mod.mod.name
        }

        add(ExpanderTab("Extension mods", persistenceID = "NewGameExpansionMods", defaultPad = 0f) {
            it.defaults().pad(5f,0f)

            val searchModsTextField = UncivTextField("Search mods")

            if (compatibleMods.size > 10)
                it.add(searchModsTextField).row()

            val modsTable = Table()
            modsTable.defaults().pad(5f)
            it.add(modsTable)

            fun populateModsTable(){
                modsTable.clear()
                val searchText = searchModsTextField.text.lowercase()
                for (mod in compatibleMods)
                    if (searchText.isEmpty() || mod.mod.name.lowercase().contains(searchText))
                        modsTable.add(mod).left().row()
            }
            populateModsTable()
            searchModsTextField.onChange { populateModsTable() }
        }).padTop(expanderPadTop).growX().row()

        disableIncompatibleMods()

        Concurrency.run { complexModCheckReturnsErrors() }
    }

    fun disableAllCheckboxes() {
        disableChangeEvents = true
        for (mod in modWidgets) {
            mod.isChecked = false
        }
        mods.clear()
        disableChangeEvents = false

        savedModcheckResult = null
        disableIncompatibleMods()
        onUpdate("-")  // should match no mod
    }

    /** Runs in parallel thread */
    private fun complexModCheckReturnsErrors(): Boolean {
        // Check over complete combination of selected mods
        val (_, complexModLinkCheck) = RulesetCache.checkCombinedModLinks(mods, baseRulesetName)
        if (!complexModLinkCheck.isWarnUser()){
            savedModcheckResult = null
            return false
        }
        savedModcheckResult = complexModLinkCheck.getErrorText()
        complexModLinkCheck.showWarnOrErrorToast(screen)
        return complexModLinkCheck.isError()
    }

    /** Runs in parallel thread so as not to block main thread - running complex mod check can be expensive */
    private fun checkBoxChanged(checkBox: ModCheckBox) {
        if (disableChangeEvents) return

        val mod = checkBox.mod
        if (checkBox.isChecked) {
            // First the quick standalone check
            val modLinkErrors = mod.getErrorList()
            if (modLinkErrors.isError()) {
                modLinkErrors.showWarnOrErrorToast(screen)
                Concurrency.runOnGLThread { checkBox.isChecked = false } // Cancel event to reset to previous state
                return
            }

            mods.add(mod.name)

            // Check over complete combination of selected mods
            if (complexModCheckReturnsErrors()) {
                // Cancel event to reset to previous state
                Concurrency.runOnGLThread { checkBox.isChecked = false } // Cancel event to reset to previous state
                mods.remove(mod.name)
                savedModcheckResult = null  // we just fixed it
                return
            }

        } else {
            /**
             * Turns out we need to check ruleset when REMOVING a mod as well, since if mod A references something in mod B (like a promotion),
             *   and then we remove mod B, then the entire ruleset is now broken!
             */

            mods.remove(mod.name)

            if (complexModCheckReturnsErrors()) {
                // Cancel event to reset to previous state
                Concurrency.runOnGLThread { checkBox.isChecked = true }
                mods.add(mod.name)
                savedModcheckResult = null  // we just fixed it
                return
            }

        }

        Concurrency.runOnGLThread {
            disableIncompatibleMods()
            onUpdate(mod.name) // Only run if we can the checks and they succeeded
        }
    }

    /** Deselect incompatible mods after [skipCheckBox] was selected.
     *
     *  Note: Inactive - we don't even allow a conflict to be turned on using [disableIncompatibleMods].
     *  But if we want the alternative UX instead - use this in [checkBoxChanged] near `mods.add` and skip disabling...
     */
    private fun deselectIncompatibleMods(skipCheckBox: ModCheckBox?) {
        disableChangeEvents = true
        for (modWidget in modWidgets) {
            if (modWidget == skipCheckBox) continue
            if (!ModCompatibility.meetsAllRequirements(modWidget.mod, baseRuleset, getSelectedMods())) {
                modWidget.isChecked = false
                mods.remove(modWidget.mod.name)
            }
        }
        disableChangeEvents = false
    }

    /** Disable incompatible mods - those that could not be turned on with the current selection */
    private fun disableIncompatibleMods() {
        for (modWidget in modWidgets) {
            val enable = ModCompatibility.meetsAllRequirements(modWidget.mod, baseRuleset, getSelectedMods())
            if (!enable && modWidget.isChecked) modWidget.isChecked = false  // mod widgets can't, but selecting a map can cause this situation
            modWidget.isDisabled = !enable  // isEnabled is only for TextButtons
        }
    }

    private fun getSelectedMods() =
        modWidgets.asSequence()
            .filter { it.isChecked }
            .map { it.mod }
            .asIterable()

    fun changeGameParameters(newGameParameters: GameParameters) {
        mods = newGameParameters.mods
    }
}
