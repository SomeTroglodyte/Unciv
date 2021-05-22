package com.unciv.ui.pickerscreens

import com.badlogic.gdx.Application.ApplicationType
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Json
import com.unciv.JsonParser
import com.unciv.MainMenuScreen
import com.unciv.logic.GameSaver
import com.unciv.models.ruleset.ModOptions
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.mainmenu.Github
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.thread


/*
    TO-DO:
        * Raise queue priority when user selects a mod? Doable with current Deque impl?
        * Find out when new downloads trigger an atlas build on next launch and decide whether to prevent that
        * Sorting by header click (Name, Stars, Download order, Indicator)
        * Re-downloading a mod will not clean files deleted from the repo
 */

/**
 *      This function returns FileHandles into the application-private cache area,
 *      such that files created there will _disappear_ using
 *      Android's App-Info (settings) Storage->Clear Cache button.
 *
 * @param   path: a relative file path to a file or directory which may exist or not
 * @return  FileHandle accessor to the specified filesystem location
 */
private fun com.badlogic.gdx.Files.cache(path: String): FileHandle {
    return if(Gdx.app.type == ApplicationType.Android)
            Gdx.files.local("../cache").child(path)
        else Gdx.files.local(path)
}

// All picker screens auto-wrap the top table in a ScrollPane.
// Since we want the different parts to scroll separately, we disable the default ScrollPane, which would scroll everything at once.
class ModManagementScreen: PickerScreen(disableScroll = true) {

    val modTable = Table().apply { defaults().pad(10f) }
    val downloadTable = Table().apply { defaults().pad(10f) }
    val modActionTable = Table().apply { defaults().pad(10f) }

    val amountPerPage = 30

    var lastSelectedButton: TextButton? = null
    private var selectedModName = ""
    private var selectedAuthor = ""

    // keep running count of mods fetched from online search for comparison to total count as reported by GitHub
    private var downloadModCount = 0

    // Description data from installed mods and online search
    val modDescriptionsInstalled: HashMap<String, String> = hashMapOf()
    val modDescriptionsOnline: HashMap<String, String> = hashMapOf()
    private fun showModDescription(modName: String) {
        val online = modDescriptionsOnline[modName] ?: ""
        val installed = modDescriptionsInstalled[modName] ?: ""
        val separator = if(online.isEmpty() || installed.isEmpty()) "" else "\n"
        descriptionLabel.setText(online + separator + installed)
    }

    // cleanup - background processing needs to be stopped on exit and memory freed
    private var runningSearchThread: Thread? = null
    private var imageDownloadThread: Thread? = null
    private var scanSaveGamesThread: Thread? = null
    private var stopBackgroundTasks = false
    override fun dispose() {
        // make sure the worker threads will not continue trying their time-intensive job
        runningSearchThread?.interrupt()
        imageDownloadThread?.interrupt()
        scanSaveGamesThread?.interrupt()
        stopBackgroundTasks = true

        // PixMaps do not live on the GC'ed heap
        for (info in onlineImages.values) {
            info.pixmap?.dispose()
        }

        super.dispose()
    }

    // Stuff used for background loading of user avatars and mod splash screens:
    /** Task information mapped to an entry in the online image download queue (how to download)
     * @param url       The URL to download from
     * @param local     FileHandle to a local copy - existence not guaranteed
     * @param altKey    Alternate Key name (only with isAvatar=false when fallback to the user possible)
     * @param altUrl    Alternate URL
     * @property failed Indicates this was tried unsuccessfully
     * @property pixmap After successful download the binary image data as Gdx PixMap
     */
    private class OnlineImageInfo(
        val url: String,
        val local: FileHandle? = null,
        val altKey: String? = null,
        val altUrl: String? = null,
        var failed: Boolean = false,        // just in case as we do only remove keys from the Deque, not the info from the hashmap
        var pixmap: Pixmap? = null
    ) {
        override fun toString(): String {
            return when {
                failed -> "failed: $url"
                pixmap!=null -> "successful: $url"
                altKey==null -> "pending: $url"
                else -> "pending: $url (alt: $altKey->$altUrl)"
            }
        }
    }
    /** Key identifying an entry in the online image download queue (what to download)
     * @param isAvatar this represents a splashscreen.jpg (`false`) or an avatar (`true`)
     * @param name mod name or user login, respectively, for isAvatar false/true
     */
    private class OnlineImageKey( val isAvatar: Boolean, val name: String) {
        override fun toString(): String {
            return if(isAvatar) "Avatar for $name" else "Splash for $name"
        }
        override fun hashCode(): Int {
            return name.hashCode().let { if(isAvatar) -it else it }
        }
        override fun equals(other: Any?) =
            (other as? OnlineImageKey)?.let { isAvatar == it.isAvatar && name == it.name } ?: false
    }
    /** Queue-like class for the online image downloader
     *
     *  Double bookkeeping: HashMap for lookups and data, Deque for processing order
     */
    private class OnlineImageQueue: HashMap<OnlineImageKey, OnlineImageInfo>() {
        private val queue: Deque<OnlineImageKey> = LinkedList()
        fun add(key: OnlineImageKey, info: OnlineImageInfo) {
            if (containsKey(key)) return
            this[key] = info
            if (info.local != null) {
                queue.addFirst(key)
            } else {
                queue.addLast(key)
            }
        }
        fun requeue(key: OnlineImageKey, info: OnlineImageInfo) {
            this[key] = OnlineImageInfo(info.url, null, info.altKey, info.altUrl)
            queue.addLast(key)
        }
        fun next(): OnlineImageKey? {
            while (!queue.isEmpty()) {
                val key = queue.removeFirst()
                val info = this[key] ?: continue
                if (!info.failed && info.pixmap == null) return key
            }
            return null
        }
    }
    // the actual online image downloader queue instance
    private val onlineImages = OnlineImageQueue()

    /** Helper class keeps references to decoration images of installed mods to enable dynamic visibility
     * (actually we do not use isVisible but refill a container selectively which allows the aggregate height to adapt and the set to center vertically)
     * @param container the table containing the indicators (one per mod, narrow, arranges up to three indicators vertically)
     * @param visualImage   image indicating _enabled as permanent visual mod_
     * @param updatedImage  image indicating _online mod has been updated_
     * @param usedImage     image indicating _mod used in local savegames_
     */
    private class ModStateImages (
            val container: Table,
            val visualImage: Image,
            val updatedImage: Image,
            val usedImage: Image,
            var visualVisible: Boolean = true,
            var updatedVisible: Boolean = true,
            var usedVisible: Boolean = true
        ) {
        private val spacer = Table().apply { width = 20f; height = 0f }
        fun setVisual(visible: Boolean) {
            visualVisible = visible
            update()
        }
        fun setUpdated(visible: Boolean) {
            updatedVisible = visible
            update()
        }
        fun setUsed(visible: Boolean) {
            usedVisible = visible
            update()
        }
        fun update() {
            container.run {
                clear()
                if (visualVisible) add(visualImage).row()
                if (updatedVisible) add(updatedImage).row()
                if (usedVisible) add(usedImage).row()
                if (!visualVisible && !updatedVisible && !usedVisible) add(spacer)
                pack()
            }
        }
    }
    private val modStateImages: HashMap<String,ModStateImages> = hashMapOf()


    init {
        setDefaultCloseAction(MainMenuScreen())
        refreshInstalledModTable()
        updateInUseIndicators()

        // Header row
        topTable.add().expandX()                // empty cols left and right for separator
        topTable.add("Current mods".toLabel()).pad(5f).minWidth(200f).padLeft(25f)
            // 30 = 5 default pad + 20 to compensate for 'permanent visual mod' decoration icon
        topTable.add("Downloadable mods".toLabel()).pad(5f)
        topTable.add("".toLabel()).minWidth(200f)  // placeholder for "Mod actions"
        topTable.add().expandX()
        topTable.row()

        // horizontal separator looking like the SplitPane handle
        val separator = Table(skin)
        separator.background = skin.get("default-vertical", SplitPane.SplitPaneStyle::class.java).handle
        topTable.add(separator).minHeight(3f).fillX().colspan(5).row()

        // main row containing the three 'blocks' installed, online and information
        topTable.add()      // skip empty first column
        topTable.add(ScrollPane(modTable))

        downloadTable.add(getDownloadButton()).padBottom(15f).row()
        downloadTable.add("...".toLabel()).row()
        tryDownloadPage(1)
        topTable.add(ScrollPane(downloadTable))

        topTable.add(modActionTable)
    }


    /** background worker: querying GitHub for Mods (repos with 'unciv-mod' in its topics)
     *
     *  calls itself for the next page of search results
     */
    private fun tryDownloadPage(pageNum: Int) {
        runningSearchThread = thread(name="GitHubSearch") {
            val repoSearch: Github.RepoSearch
            try {
                repoSearch = Github.tryGetGithubReposWithTopic(amountPerPage, pageNum)!!
            } catch (ex: Exception) {
                Gdx.app.postRunnable {
                    ToastPopup("Could not download mod list", this)
                }
                runningSearchThread = null
                return@thread
            }

            Gdx.app.postRunnable {
                // clear and hide last cell if it is the "..." indicator
                val lastCell = downloadTable.cells.lastOrNull()
                if (lastCell != null && lastCell.actor is Label && (lastCell.actor as Label).text.toString() == "...") {
                    lastCell.setActor<Actor>(null)
                    lastCell.pad(0f)
                }

                for (repo in repoSearch.items) {
                    if (stopBackgroundTasks) return@postRunnable
                    repo.name = repo.name.replace('-', ' ')

                    fetchImages(repo)

                    modDescriptionsOnline[repo.name] =
                            (repo.description ?: "-No description provided-") +
                            "\n" + "[${repo.stargazers_count}]✯".tr()

                    var downloadButtonText = repo.name

                    val existingMod = RulesetCache.values.firstOrNull { it.name == repo.name }
                    if (existingMod != null) {
                        if (existingMod.modOptions.lastUpdated != "" && existingMod.modOptions.lastUpdated != repo.updated_at) {
                            downloadButtonText += " - {Updated}"
                            modStateImages[repo.name]?.setUpdated(true)
                        }
                        if (existingMod.modOptions.author.isEmpty()) {
                            rewriteModOptions(repo, Gdx.files.local("mods").child(repo.name))
                            existingMod.modOptions.author = repo.owner.login
                            existingMod.modOptions.modSize = repo.size
                        }
                    }

                    val downloadButton = downloadButtonText.toTextButton()
                    downloadButton.name = repo.name

                    downloadButton.onClick {
                        lastSelectedButton?.color = Color.WHITE
                        downloadButton.color = Color.BLUE
                        lastSelectedButton = downloadButton
                        showModDescription(repo.name)
                        removeRightSideClickListeners()
                        rightSideButton.enable()
                        rightSideButton.setText("Download [${repo.name}]".tr())
                        rightSideButton.onClick {
                            rightSideButton.setText("Downloading...".tr())
                            rightSideButton.disable()
                            downloadMod(repo) {
                                rightSideButton.setText("Downloaded!".tr())
                            }
                        }

                        modActionTable.clear()
                        addModInfoToActionTable(repo)
                    }

                    downloadTable.add(downloadButton).row()
                    downloadModCount++
                }

                // Now the tasks after the 'page' of search results has been fully processed
                if (repoSearch.items.size < amountPerPage) {
                    // The search has reached the last page!
                    // Check: due to time passing between github calls it is not impossible we get a mod twice
                    val checkedMods: MutableSet<String> = mutableSetOf()
                    val duplicates: MutableList<Cell<Actor>> = mutableListOf()
                    downloadTable.cells.forEach {
                        cell->
                        cell.actor?.name?.apply {
                            if (checkedMods.contains(this)) {
                                duplicates.add(cell)
                            } else checkedMods.add(this)
                        }
                    }
                    duplicates.forEach {
                        it.setActor(null)
                        it.pad(0f)  // the cell itself cannot be removed so stop it occupying height
                    }
                    downloadModCount -= duplicates.size
                    // Check: It is also not impossible we missed a mod - just inform user
                    if (repoSearch.total_count > downloadModCount || repoSearch.incomplete_results) {
                        downloadTable.add("Online query result is incomplete".toLabel(Color.RED))
                    }
                } else {
                    // the page was full so there may be more pages.
                    // indicate that search will be continued
                    downloadTable.add("...".toLabel()).row()
                }

                downloadTable.pack()
                // Shouldn't actor.parent.actor = actor be a no-op? No, it has side effects we need.
                // See [commit for #3317](https://github.com/yairm210/Unciv/commit/315a55f972b8defe22e76d4a2d811c6e6b607e57)
                (downloadTable.parent as ScrollPane).actor = downloadTable

                // continue search unless last page was reached
                if (repoSearch.items.size >= amountPerPage && !stopBackgroundTasks)
                    tryDownloadPage(pageNum + 1)
            }
            runningSearchThread = null
        }
    }

    /** Background loader for splash images (splashscreen.jpg within the mod with author avatar as fallback)
     *
     * This function is the controller: Queues entries and restarts worker if necessary
     * @param repo The GitHub api info for one mod repository
     */
    private fun fetchImages(repo: Github.Repo) {
        val splashKey = OnlineImageKey(false, repo.name)
        if (splashKey in onlineImages) return
        val localSplash = Gdx.files.local("mods").child(repo.name).child("splashscreen.jpg")
        // 'guess' the Url for splashscreen.jpg using GitHub's raw api
        // The full url for an avatar is explicitly given in GitHub's search response
        val splashUrl = "https://raw.githubusercontent.com/${repo.full_name}/${repo.default_branch}/splashscreen.jpg"
        val splashInfo = if(repo.owner.avatar_url == null) OnlineImageInfo(splashUrl, localSplash)
            else OnlineImageInfo(splashUrl, localSplash, repo.owner.login, repo.owner.avatar_url)
        onlineImages.add(splashKey, splashInfo)
        // start or restart worker (happens when the search response is slower than our worker e.g. due to rate limit)
        if (imageDownloadThread == null) startImageDownload()
    }

    /** Background loader for splash images: worker */
    private fun startImageDownload() {
        imageDownloadThread = thread(name="avatarDownload") {
            while (!stopBackgroundTasks && Thread.currentThread() == imageDownloadThread) {
                val key = onlineImages.next() ?: break
                val info = onlineImages[key]!!
                if (info.local != null) {
                    // fetch local copies (splashscreen.jpg from installed mod or avatar from cache)
                    try {
                        val data = info.local.readBytes()
                        info.pixmap = Pixmap(data!!, 0, data.size)
                        updateActionTableImage(key, info)
                    } catch (ex: Exception) {
                        onlineImages.requeue(key, info)     // removes local and re-adds to end
                    }
                    continue
                }
                // fetch online images
                with(URL(info.url).openConnection() as HttpURLConnection) {
                    try {
                        val data = this.inputStream.readBytes()
                        info.pixmap = Pixmap(data,0,data.size)
                        updateActionTableImage(key, info)
                        if (key.isAvatar) {
                            // save new user avatar to cache
                            val avatarCache = Gdx.files.cache("avatarCache")
                            val localAvatar = avatarCache.child(key.name)
                            if (!localAvatar.exists()) {
                                try {
                                    avatarCache.mkdirs()
                                    localAvatar.writeBytes(data, false)
                                } catch (ex: Exception){}
                            }
                        }
                    } catch (ex: Exception) {
                        info.failed = true
                        if (info.altKey != null) {
                            // when a splashscreen.jpg fetch fails requeue for the author avatar
                            val avatarKey = OnlineImageKey(true, info.altKey)
                            val localAvatar = Gdx.files.cache("avatarCache").child(info.altKey)
                            onlineImages.add(avatarKey, OnlineImageInfo(info.altUrl!!, localAvatar))
                        } else
                            println(ex.message)
                    }
                }
            }
            imageDownloadThread = null
        }
    }

    /** Recreate the information part of the right-hand column
     * @param repo: the repository instance as received from the GitHub api
     */
    private fun addModInfoToActionTable(repo: Github.Repo) {
        addModInfoToActionTable(repo.name, repo.html_url, repo.updated_at, repo.owner.login, repo.size)
    }
    /** Recreate the information part of the right-hand column
     * @param modName: The mod name (name from the RuleSet)
     * @param modOptions: The ModOptions as enriched by us with GitHub metadata when originally downloaded
     */
    private fun addModInfoToActionTable(modName: String, modOptions: ModOptions) {
        addModInfoToActionTable(modName, modOptions.modUrl, modOptions.lastUpdated, modOptions.author, modOptions.modSize)
    }
    private fun addModInfoToActionTable(modName: String, repoUrl: String, updatedAt: String, author: String, modSize: Int) {
        // remember selected mod - for now needed only to display a background-fetched image while the user is watching
        selectedModName = modName
        selectedAuthor = author

        // get an image to decorate the mod - either mod-specific splash screen or it's owner's avatar
        var pixmap: Pixmap? = null
        var cellSize = 120f
        val splash = onlineImages[OnlineImageKey(false, modName)]
        if (splash?.pixmap != null) {
            pixmap = splash.pixmap
            cellSize = 240f
        } else if (author.isNotEmpty()) {
            pixmap = onlineImages[OnlineImageKey(true, author)]?.pixmap
        }
        // Some users have transparent avatars (e.g. Subcher and Cavenir)
        // and some of those won't look good on our blue background - but giving a lighter background to all like the following won't look good on all either:
        //modActionTable.add(Table().apply{background = ImageGetter.getBackground(Color.GRAY); add(image).size(120f)}).center().row()

        // If no image available yet, use a placeholder so the background thread can fill in
        val image = if (pixmap != null) Image(Texture(pixmap)) else Image()
        modActionTable.add(image).size(cellSize).center().row()

        // Display text info
        if (author.isNotEmpty())
            modActionTable.add("Author: [$author]".toLabel()).row()
        if (modSize > 0)
            modActionTable.add("Size: [$modSize] kB".toLabel()).padBottom(15f).row()

        // offer link to open the repo itself in a browser
        if (repoUrl != "") {
            modActionTable.add("Open Github page".toTextButton().onClick {
                Gdx.net.openURI(repoUrl)
            }).row()
        }

        // display "updated" date - currently in US imperial format
        if (updatedAt != "") {
            // Everything under java.time is from Java 8 onwards, meaning older phones that use Java 7 won't be able to handle it :/
            // So we're forced to use ancient Java 6 classes instead of the newer and nicer LocalDateTime.parse :(
            // Direct solution from https://stackoverflow.com/questions/2201925/converting-iso-8601-compliant-string-to-java-util-date
            val df2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US) // example: 2021-04-11T14:43:33Z
            val date = df2.parse(updatedAt)

            val updateString = "{Updated}: " +DateFormat.getDateInstance(DateFormat.LONG).format(date)
            modActionTable.add(updateString.toLabel()).row()
        }
    }

    /** If the background image loader just loaded an image for the mod the user is looking at, display it immediately
     *
     * (Called by background image loader on its own thread.)
     */
    private fun updateActionTableImage(key: OnlineImageKey, info: OnlineImageInfo) {
        if (key.isAvatar && selectedAuthor != key.name
            || !key.isAvatar && selectedModName != key.name
            || info.pixmap == null
        ) return
        val cell = modActionTable.cells.firstOrNull { it.actor is Image } ?: return
        val image = cell.actor as Image
        Gdx.app.postRunnable {
            image.drawable = TextureRegionDrawable(Texture(info.pixmap))
            cell.size(if (key.isAvatar) 120f else 240f)
        }
    }

    /** Create the special "Download from URL" button */
    private fun getDownloadButton(): TextButton {
        val downloadButton = "Download mod from URL".toTextButton()
        downloadButton.onClick {
            val popup = Popup(this)
            val textArea = TextArea("https://github.com/...", skin)
            popup.add(textArea).width(stage.width / 2).row()
            val actualDownloadButton = "Download".toTextButton()
            actualDownloadButton.onClick {
                actualDownloadButton.setText("Downloading...".tr())
                actualDownloadButton.disable()
                downloadMod(Github.Repo().apply { html_url = textArea.text; default_branch = "master" }) { popup.close() }
            }
            popup.add(actualDownloadButton).row()
            popup.addCloseButton()
            popup.open()
        }
        return downloadButton
    }

    /** Download and install a mod in the background, called from the right-bottom button */
    private fun downloadMod(repo: Github.Repo, postAction: () -> Unit = {}) {
        thread(name="DownloadMod") { // to avoid ANRs - we've learnt our lesson from previous download-related actions
            try {
                val modFolder = Github.downloadAndExtract(repo.html_url, repo.default_branch,
                    Gdx.files.local("mods"))
                    ?: return@thread
                rewriteModOptions(repo, modFolder)
                Gdx.app.postRunnable {
                    ToastPopup("Downloaded!", this)
                    RulesetCache.loadRulesets()
                    refreshInstalledModTable()
                    showModDescription(repo.name)
                    unmarkUpdatedMod(repo.name)
                }
            } catch (ex: Exception) {
                Gdx.app.postRunnable {
                    ToastPopup("Could not download mod", this)
                }
            } finally {
                postAction()
            }
        }
    }

    /** Rewrite modOptions file for a mod we just installed to include metadata we got from the GitHub api
     *
     *  (called on background thread)
     */
    private fun rewriteModOptions(repo: Github.Repo, modFolder: FileHandle) {
        val modOptionsFile = modFolder.child("jsons/ModOptions.json")
        val modOptions = if (modOptionsFile.exists()) JsonParser().getFromJson(ModOptions::class.java, modOptionsFile) else ModOptions()
        modOptions.modUrl = repo.html_url
        modOptions.lastUpdated = repo.updated_at
        modOptions.author = repo.owner.login
        modOptions.modSize = repo.size
        Json().toJson(modOptions, modOptionsFile)
    }

    /** Remove the visual indicators for an 'updated' mod after re-downloading it.
     *  (" - Updated" on the button text in the online mod list and the icon beside the installed mod's button)
     *  It should be up to date now (unless the repo's date is in the future relative to system time)
     *
     *  (called under postRunnable posted by background thread)
     */
    private fun unmarkUpdatedMod(name: String) {
        modStateImages[name]?.setUpdated(false)
        val labelText = "$name - {Updated}".tr()
        val button = downloadTable.cells
            .map { it.actor }
            .filterIsInstance<TextButton>()
            .firstOrNull { it.text.toString() == labelText }
            ?: return
        button.setText(name)
    }

    /** Rebuild the right-hand column for clicks on installed mods
     *  Display single mod metadata, offer additional actions (delete is elsewhere)
    */
    private fun refreshModActions(mod: Ruleset) {
        modActionTable.clear()
        // show mod information first
        addModInfoToActionTable(mod.name, mod.modOptions)

        // offer 'permanent visual mod' toggle
        val visualMods = game.settings.visualMods
        val isVisual = visualMods.contains(mod.name)
        modStateImages[mod.name]?.setVisual(isVisual)
        if (!isVisual) {
            modActionTable.add("Enable as permanent visual mod".toTextButton().onClick {
                visualMods.add(mod.name)
                game.settings.save()
                ImageGetter.setNewRuleset(ImageGetter.ruleset)
                refreshModActions(mod)
            })
        } else {
            modActionTable.add("Disable as permanent visual mod".toTextButton().onClick {
                visualMods.remove(mod.name)
                game.settings.save()
                ImageGetter.setNewRuleset(ImageGetter.ruleset)
                refreshModActions(mod)
            })
        }
        modActionTable.row()
    }

    /** Rebuild the left-hand column containing all installed mods */
    private fun refreshInstalledModTable() {
        modTable.clear()
        // preserve accumulated info on used mods in case this is called before that worker finishes
        val oldUsedMods = HashSet<String>(modStateImages.filter { it.value.usedVisible }.map { it.key })
        modStateImages.clear()

        val currentMods = RulesetCache.values.asSequence().filter { it.name != "" }.sortedBy { it.name }
        for (mod in currentMods) {
            val summary = mod.getSummary()
            modDescriptionsInstalled[mod.name] = "Installed".tr() +
                    (if (summary.isEmpty()) "" else ": $summary")

            val decorationTable = Table().apply {
                defaults().size(20f).align(Align.topLeft)
                val imageMgr = ModStateImages(this,
                    ImageGetter.getImage("UnitPromotionIcons/Scouting"),
                    ImageGetter.getImage("OtherIcons/Mods"),
                    ImageGetter.getImage("OtherIcons/Resume"),
                    mod.name in game.settings.visualMods,
                    false,
                    mod.name in oldUsedMods
                    )
                modStateImages[mod.name] = imageMgr
                imageMgr.update()     // rebuilds decorationTable content
            }

            val button = mod.name.toTextButton()
            button.onClick {
                lastSelectedButton?.color = Color.WHITE
                button.color = Color.BLUE
                lastSelectedButton = button
                refreshModActions(mod)
                rightSideButton.setText("Delete [${mod.name}]".tr())
                rightSideButton.enable()
                showModDescription(mod.name)
                removeRightSideClickListeners()
                rightSideButton.onClick {
                    YesNoPopup("Are you SURE you want to delete this mod?",
                            { deleteMod(mod) }, this).open()
                }
            }

            val decoratedButton = Table()
            decoratedButton.add(button)
            decoratedButton.add(decorationTable).align(Align.center+Align.left)
            modTable.add(decoratedButton).row()
        }
    }

    /** Delete a Mod */
    private fun deleteMod(mod: Ruleset) {
        val modFileHandle = Gdx.files.local("mods").child(mod.name)
        if (modFileHandle.isDirectory) modFileHandle.deleteDirectory()
        else modFileHandle.delete()
        RulesetCache.loadRulesets()
        refreshInstalledModTable()
    }

    /** Background worker: Scans all savegames, looks up their mod requirements and flags those mods */
    private fun updateInUseIndicators() {
        scanSaveGamesThread = thread(name="ScanModsInUse") {
            for (file in GameSaver.getSaves()) {
                if (stopBackgroundTasks || Thread.currentThread() != scanSaveGamesThread) break
                try {
                    val game = GameSaver.loadGamePreviewFromFile(file)
                    for (modName in game.gameParameters.mods) {
                        Gdx.app.postRunnable {
                            modStateImages[modName]?.setUsed(true)
                        }
                    }
                } catch (ex: Exception) {
                    // ignore any games we cannot scan
                }
            }
            scanSaveGamesThread = null
        }
    }
}
