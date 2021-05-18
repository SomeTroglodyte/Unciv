/*  "unused" is for CivilopediaFormatting.bold,italic.strike - these are future expansion
 *      and for the "FC" typealias - which is there for readability only, and indeed used,
 *      but the compiler (1.4) will incorrectly flag it.
 *  "not_null" is for CivilopediaFormatting.render(), call category.getImage: That Enum custom
 *      property is a nullable reference to a lambda which in turn is allowed to return null.
 *      Sorry, but without `!!` the code won't compile and with we'd get the incorrect warning.
 */
@file:Suppress("unused", "UNNECESSARY_NOT_NULL_ASSERTION")

package com.unciv.ui.civilopedia

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.stats.INamed
import com.unciv.ui.utils.*
import kotlin.math.max

/* Ideas:
 *    - Now we're using a Table container and inside one Table per line. Rendering order, in view of
 *      texture swaps, is per Group, as this goes by ZIndex and that is implemented as actual index
 *      into the parent's children array. So, we're SOL to get the number of texture switches down
 *      with this structure, as many lines will require at least 2 texture switches.
 *      So we should go for one big table with 4 columns (3 images, plus rest) and use colspan -
 *      then group all images separate from labels via ZIndex.
 *    - Do bold using Distance field fonts wrapped in something like [maltaisn/msdf-gdx](https://github.com/maltaisn/msdf-gdx)
 *    - Do strikethrough by stacking a line on top (as rectangle with background like the separator but thinner)
 *    - Do shadowed instead of italic (again Distance field fonts)
 */

/** just a shorthand as it's used often in this file */
private typealias FC = MarkupRenderer.FormattingConstants

/** Link types that can be expressed in markup */
private enum class LinkType {None, Internal, External, Image}

/** Makes [renderer][render] available outside [ICivilopediaText] */
object MarkupRenderer {
    /** Build a Gdx [Table] showing [formatted][FormattingConstants] [content][lines]. */
    fun render (
        lines: Collection<String>,
        skin: Skin,
        labelWidth: Float = 0f,
        emptyLineHeight: Float = 10f,
        noLinkImages: Boolean = false,
        linkAction: ((id: String)->Unit)? = null
    ): Table {
        val table = Table(skin).apply { defaults().pad(2.5f).align(Align.left) }
        for (line in lines) {
            if (line.isEmpty()) {
                table.add().padTop(emptyLineHeight).row()
                continue
            }
            if (line == FC.separatorSymbol) {
                table.addSeparator().pad(5f,0f,15f,0f)
                continue
            }
            val formatting = parseFormatting(line)
            val actor = formatting.render(skin, labelWidth, noLinkImages)
            if (formatting.linkType == LinkType.Internal && formatting.linkTo!=null && linkAction != null)
                actor.onClick {
                    linkAction(formatting.linkTo)
                }
            else if (formatting.linkType == LinkType.External)
                actor.onClick {
                    Gdx.net.openURI(line)
                }
            val align = if (formatting.centered) Align.center else Align.left
            if (labelWidth == 0f)
                table.add(actor).align(align).row()
            else
                table.add(actor).width(labelWidth).align(align).row()
        }
        return table.apply { pack() }
    }

    // Tricky: using unicode lookalikes to avoid triggering markdown
    /** ### Formatting rules:
     * A line can start with zero or more of the following formatting instructions, order does not matter:
     *
     * - **[** `category/entryname` **⁆** (pair of square brackets) - create a civilopedia link (max 1 per line). Renders a link icon and a matching object icon if found.
     * - **(** `category/entryname` **)** (brackets) - include icon but do not link
     * - **@** `size` - Modifies `(image)` which now renders an image from ExtraImages in the specified size, centered, nothing else for this row
     * - **#** (hash)- Increase header level by 1: applies to font size of whole line - 0 is 100% normal, 1=200%, 2=175%, 3=150%, 4=133%, 5=117%, 6=83%, 7=67%, 8=50%.
     * - **+** `######` (plus followed by a 6-digit hex number) - sets colour of whole line or, if set, the star only.
     * - **✯** (U+272F) - Adds a star icon, optionally coloured
     * - **₋** (underscore) - Make whole line italic (_not implemented_)
     * - **✶** (asterisk) - Make whole line bold (_not implemented_)
     * - **~** (tilde) - Make whole line strikethrough (_not implemented_)
     * - ' ' (space) - Ends formatting explicitly and is not included in the resulting text. 2 or more increases indentation level, which places text at uniform indent independent of icon presence.
     *
     * The first character not matching one of these rules ends formatting, the rest of the string is rendered as text, and goes through translation as is.
     *
     * Icon ordering is always link - object - star.
     *
     * Special case: "---" creates a separator line.
     *
     * Special case: lines starting with http://, https:// or mailto: will default to sky-blue and get linked.
     */
    object FormattingConstants {            // public only to allow FC typealias
        const val defaultSize = 18
        val headerSizes = arrayOf(18,36,32,27,24,21,15,12,9)    // pretty arbitrary, yes
        val defaultColor: Color = Color.WHITE
        const val linkImage = "OtherIcons/Link"
        const val starImage = "OtherIcons/Star"
        const val separatorSymbol = "---"
        const val linkSymbol = '['
        const val linkClose = ']'
        const val iconSymbol = '('
        const val iconClose = ')'
        const val iconSizeSymbol = '@'
        const val headerSymbol = '#'
        const val colorSymbol = '+'
        const val italicSymbol = '_'
        const val boldSymbol = '*'
        const val strikeSymbol = '~'
        const val starredSymbol = '✯'
        const val centerSymbol = '^'
        const val endFormattingSymbol = ' '
        const val imageSize = 30f
        const val indentPad = 30f
    }

    /** Helper class stores the parsing result of [parseFormatting] -
     * line text without [formatting symbols][FormattingConstants] and its discrete formatting info */
    private class CivilopediaFormatting (
        val linkType: LinkType = LinkType.None,
        val linkTo: String? = null,
        val imageSize: Int = 40,
        val header: Int = 0,
        val color: Color = Color.WHITE,
        val italic: Boolean = false,        // Not implemented
        val bold: Boolean = false,          // Not implemented
        val strike: Boolean = false,        // Not implemented
        val starred: Boolean = false,
        val centered: Boolean = false,
        val indent: Int = 0,
        val line: String = ""
    ) {
        fun render(skin: Skin, labelWidth: Float, noLinkImages: Boolean = false): Actor {
            if (linkType == LinkType.Image && linkTo != null) {
                val table = Table(skin)
                val image = when {
                    ImageGetter.imageExists(linkTo) ->
                        ImageGetter.getImage(linkTo)
                    Gdx.files.internal("ExtraImages/$linkTo.png").exists() ->
                        ImageGetter.getExternalImage("$linkTo.png")
                    Gdx.files.internal("ExtraImages/$linkTo.jpg").exists() ->
                        ImageGetter.getExternalImage("$linkTo.jpg")
                    else -> return table
                }
                table.add(image).size(imageSize.toFloat())
                return table
            }
            val fontSize = if (header>= FC.headerSizes.size) FC.defaultSize else FC.headerSizes[header]
            val labelColor = if(starred) FC.defaultColor else color
            val label = if (fontSize == FC.defaultSize && labelColor == FC.defaultColor) line.toLabel()
            else line.toLabel(labelColor,fontSize)
            label.wrap = !centered && labelWidth > 0f
            val table = Table(skin)
            var iconCount = 0
            val imageSize = max(FC.imageSize, fontSize * 1.5f)
            if (linkType != LinkType.None && !noLinkImages) {
                table.add( ImageGetter.getImage(FC.linkImage) ).size(imageSize).padRight(5f)
                iconCount++
            }
            if (linkTo != null && !noLinkImages) {
                val parts = linkTo.split('/', limit = 2)
                if (parts.size == 2) {
                    val category = CivilopediaCategories.fromLink(parts[0])
                    if (category != null) {
                        if (category.getImage != null) {
                            val image = category.getImage!!(parts[1], imageSize)
                            if (image != null) {
                                table.add(image).size(imageSize).padRight(5f)
                                iconCount++
                            }
                        }
                    }
                }
            }
            if (starred) {
                val image = ImageGetter.getImage(FC.starImage)
                image.color = this.color
                table.add(image).size(imageSize).padRight(5f)
                iconCount++
            }
            val align = if (centered) Align.center else Align.left
            label.setAlignment(align)
            val usedWidth = iconCount * (imageSize + 5f)
            val padIndent = when {
                centered -> -usedWidth
                indent == 0 && iconCount == 0 -> 0f
                indent == 0 -> 5f
                else -> (indent-1) * FC.indentPad + 3 * FC.imageSize - usedWidth + 20f
            }
            if (labelWidth == 0f)
                table.add(label).padLeft(padIndent).align(align)
            else
                table.add(label).width(labelWidth - usedWidth - padIndent).padLeft(padIndent).align(align)
            return table
        }
    }

    /** Parse [formatting symbols][FormattingConstants] from a line and store result in a [CivilopediaFormatting] instance. */
    private fun parseFormatting(line: String): CivilopediaFormatting {
        var linkType = LinkType.None
        var linkTo: String? = null
        var header = 0
        var color = FC.defaultColor
        var italic = false
        var bold = false
        var strike = false
        var starred = false
        var centered = false
        var imageSize = 40
        var indent = 0

        var i = 0
        while (i < line.length) {
            when (line[i]) {
                FC.linkSymbol -> {
                    val endPos = line.indexOf(FC.linkClose, i+1)
                    if (endPos > i) {
                        linkType = LinkType.Internal
                        linkTo = line.substring(i+1,endPos)
                        i = endPos
                    }
                }
                FC.iconSymbol -> {
                    val endPos = line.indexOf(FC.iconClose, i+1)
                    if (endPos > i) {
                        linkTo = line.substring(i+1,endPos)
                        i = endPos
                    }
                }
                FC.iconSizeSymbol -> {
                    var endPos = i + 1
                    while (endPos < line.length && line[endPos].isDigit()) endPos++
                    if (endPos > i + 1) {
                        linkType = LinkType.Image
                        imageSize = line.substring(i + 1, endPos).toInt()
                        i = endPos - 1
                    }
                }
                FC.headerSymbol -> header++
                FC.colorSymbol -> {
                    if (line.isHex(i+1,6)) {
                        val hex = line.substring(i+1,i+7)
                        color = Color.valueOf(hex)
                        i += 6
                    } else if (line.isHex(i+1,3)) {
                        val hex = line.substring(i+1,i+4)
                        val hex6 = String(charArrayOf(hex[0], hex[0], hex[1], hex[1], hex[2], hex[2]))
                        color = Color.valueOf(hex6)
                        i += 3
                    }
                }
                FC.italicSymbol -> italic = true
                FC.boldSymbol -> bold = true
                FC.strikeSymbol -> strike = true
                FC.starredSymbol -> starred = true
                FC.centerSymbol -> centered = true
                FC.endFormattingSymbol -> {
                    i++
                    while (i < line.length && line[i] == FC.endFormattingSymbol) {
                        indent++
                        i++
                    }
                    break
                }
                else -> break
            }
            i++
        }

        val text = line.substring(i)
        if (linkType == LinkType.None && text.hasProtocol()) {
            italic = true
            color = Color.SKY
            linkType = LinkType.External
        }
        if (linkType == LinkType.Image && linkTo == null) linkType = LinkType.None
        return CivilopediaFormatting(linkType, linkTo, imageSize, header, color, italic, bold, strike, starred, centered, indent, text)
    }
}

/** Storage class for interface [ICivilopediaText] */
open class CivilopediaText : ICivilopediaText {
    override var civilopediaText = listOf<String>()
}

/** Addon common to most ruleset game objects managing civilopedia display
 *
 * ### Usage:
 * 1. Let [Ruleset] object implement this (e.g. by inheriting class [CivilopediaText] or adding var [civilopediaText] itself)
 * 2. Add `"civilopediaText": ["",…],` in the json for these objects
 * 3. Optionally override [getCivilopediaTextHeader] to supply a header line
 * 4. Optionally override [getCivilopediaTextLines] to supply automatic stuff like tech prerequisites, uniques, etc.
 * 4. Optionally override [assembleCivilopediaText] to handle assembly of the final set of lines yourself.
 */
interface ICivilopediaText {
    /** List of strings supporting simple [formatting rules][MarkupRenderer.FormattingConstants] that [CivilopediaScreen] can render.
     * May later be merged with automatic lines generated by the deriving class
     *  through overridden [getCivilopediaTextHeader] and/or [getCivilopediaTextLines] methods.
     *
     */
    var civilopediaText: List<String>

    /** Generate header line from object metadata.
     * Default implementation will pull [INamed.name] and render it in 150% normal font size.
     * @return A string conforming to our [formatting rules][MarkupRenderer.FormattingConstants] that will be inserted on top
     */
    fun getCivilopediaTextHeader(): String? =
        if (this is INamed) "##$name"
        else null

    /** Generate automatic lines from object metadata.
     *
     * Default implementation is empty - no need to call super in overrides.
     *
     * @param ruleset The current ruleset for the Civilopedia viewer
     * @return A list of strings conforming to our [formatting rules][MarkupRenderer.FormattingConstants] that will be inserted before
     *         the first line of [civilopediaText] beginning with a [link][FormattingConstants]
     */
    fun getCivilopediaTextLines(ruleset: Ruleset): List<String> = listOf()

    /** Override this and return true to tell the Civilopedia that the legacy description is no longer needed */
    fun replacesCivilopediaDescription() = false
    /** Override this and return true to tell the Civilopedia that this is not empty even if nothing came from json */
    fun hasCivilopediaTextLines() = false
    /** Indicates that neither json nor getCivilopediaTextLines have content */
    fun isCivilopediaTextEmpty() = civilopediaText.isEmpty() && !hasCivilopediaTextLines()

    /** Build a Gdx [Table] showing our [formatted][MarkupRenderer.FormattingConstants] [content][civilopediaText]. */
    fun renderCivilopediaText (skin: Skin, labelWidth: Float, linkAction: ((id: String)->Unit)? = null): Table {
        return MarkupRenderer.render(civilopediaText, skin, labelWidth, linkAction = linkAction)
    }

    /** Assemble json-supplied lines with automatically generated ones.
     *
     * The default implementation will insert [getCivilopediaTextLines] before the first [linked][MarkupRenderer.FormattingConstants] [civilopediaText] line and [getCivilopediaTextHeader] on top.
     *
     * @param ruleset The current ruleset for the Civilopedia viewer
     * @return A new CivilopediaText instance containing original [civilopediaText] lines merged with those from [getCivilopediaTextHeader] and [getCivilopediaTextLines] calls.
     */
    fun assembleCivilopediaText(ruleset: Ruleset): CivilopediaText {
        val outerLines = civilopediaText.iterator()
        val newLines = sequence {
            var middleDone = false
            var outerNotEmpty = false
            val header = getCivilopediaTextHeader()
            if (header != null) {
                yield(header)
                yield(FC.separatorSymbol)
            }
            while (outerLines.hasNext()) {
                val next = outerLines.next()
                if (!middleDone && next.isNotEmpty() && (next[0] == FC.linkSymbol || next.hasProtocol())) {
                    middleDone = true
                    if (hasCivilopediaTextLines()) {
                        if (outerNotEmpty) yield("")
                        yieldAll(getCivilopediaTextLines(ruleset))
                        yield("")
                    }
                }
                outerNotEmpty = true
                yield(next)
            }
            if (!middleDone) {
                if (outerNotEmpty && hasCivilopediaTextLines()) yield("")
                yieldAll(getCivilopediaTextLines(ruleset))
            }
        }
        val newCivilopediaText = CivilopediaText()
        newCivilopediaText.civilopediaText = newLines.toList()
        return newCivilopediaText
    }
}

/** Extension: determines if a [String] looks like a link understood by the OS */
private fun String.hasProtocol() = startsWith("http://") || startsWith("https://") || startsWith("mailto:")
/** Extension: determines if a section of a [String] is composed entirely of hex digits
 * @param start starting index
 * @param length length of section - if =0 [isHex]=true but if receiver too short [isHex]=false
 */
private fun String.isHex(start: Int, length: Int) =
    when {
        length == 0 -> false
        start + length > this.length -> false
        substring(start, start + length).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } -> true
        else -> false
    }
