package com.unciv.ui.screens.modmanager

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.GdxRuntimeException
import com.unciv.UncivGame
import com.unciv.logic.github.Github
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlin.math.max

/**
 *  Widget with the ability to show a mod preview image
 *
 *  - Caches found textures (in case the widget is reused, for single-shots the overhead is negligible)
 *  - For remote repos, the image loaading runs in the background.
 *
 *  API: [showPreview], [addLocalPreviewImage]
 *  @param isModNameCurrent Provide this in case the containing UI can "switch" to another mod while the image is retrieved in the background. The result is not shown if this returns false.
 */
class ModPreviewImageHolder(
    private val maxAllowedPreviewImageSize: Float,
    private val isModNameCurrent: (String) -> Boolean = { true }
) : Table(), Disposable {
    private val repoUrlToPreviewImage = HashMap<String, Texture?>()
    private var outerJob: Job? = null
    private var innerJob: Job? = null

    fun showPreview(modName: String, isBuiltin: Boolean, isLocal: Boolean, repoUrl: String, defaultBranch: String, avatarUrl: String?) {
        clear()
        when {
            isBuiltin -> addUncivLogo(modName)
            isLocal -> addLocalPreviewImage(modName)
            else -> addPreviewImage(modName, repoUrl, defaultBranch, avatarUrl)
        }
    }

    private fun addPreviewImage(modName: String, repoUrl: String, defaultBranch: String, avatarUrl: String?) {
        if (!repoUrl.startsWith("http")) return // invalid url
        cancelJobs()

        if (repoUrlToPreviewImage.containsKey(repoUrl)) {
            val texture = repoUrlToPreviewImage[repoUrl]
            if (texture != null) setTextureAsPreview(texture, modName)
            return
        }

        outerJob = Concurrency.run {
            val imagePixmap = Github.getPreviewImageOrNull(repoUrl, defaultBranch, avatarUrl)
            if (!isActive) {
                imagePixmap?.dispose()
                return@run
            }

            if (imagePixmap == null) {
                repoUrlToPreviewImage[repoUrl] = null
                return@run
            }
            innerJob = Concurrency.runOnGLThread {
                if (!isActive) {
                    imagePixmap.dispose()
                    return@runOnGLThread
                }
                val texture = Texture(imagePixmap)
                imagePixmap.dispose()
                repoUrlToPreviewImage[repoUrl] = texture
                setTextureAsPreview(texture, modName)
            }.apply { invokeOnCompletion { innerJob = null } }
        }.apply { invokeOnCompletion { outerJob = null } }
    }

    fun addLocalPreviewImage(modName: String) {
        // No concurrency, order of magnitude 20ms
        val modFolder = UncivGame.Current.files.getModFolder(modName)
        val previewFile = modFolder.child("preview.jpg").takeIf { it.exists() }
            ?: modFolder.child("preview.png").takeIf { it.exists() }
            ?: return
        try {
            setTextureAsPreview(Texture(previewFile), modName)
        } catch (ex: Throwable) {
            val cause = if (ex is GdxRuntimeException) ex.cause else ex
            Log.debug("Could not load local preview file %s: %s", previewFile.path(), cause)
            if (cause is IOException) previewFile.delete() // File content invalid and not loadable as pixmap gives this
        }
    }

    private fun addUncivLogo(modName: String) {
        setTextureAsPreview(Texture(Gdx.files.internal("ExtraImages/banner.png")), modName)
    }

    private fun setTextureAsPreview(texture: Texture, modName: String) {
        val image = Image(texture)
        if (!isModNameCurrent(modName)) return // user has selected another mod in the meantime, and cancelling the old job didn't catch
        val cell = add(image)
        val largestImageSize = max(texture.width, texture.height)
        val resizeRatio = if (largestImageSize > maxAllowedPreviewImageSize)
                maxAllowedPreviewImageSize / largestImageSize
            else image.prefWidth / texture.width
        cell.size(texture.width * resizeRatio, texture.height * resizeRatio)
    }

    fun cancelJobs() {
        outerJob?.cancel()
        outerJob = null
        innerJob?.cancel()
        innerJob = null
    }

    override fun dispose() = cancelJobs()
}
