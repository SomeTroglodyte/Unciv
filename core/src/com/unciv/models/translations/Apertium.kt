package com.unciv.models.translations

import com.unciv.UncivGame
import java.nio.charset.Charset

object Apertium {
    const val suffix = "_(Apertium)"
    private const val cmd = "apertium "
    private val charset = Charset.forName("UTF-8")
    private val langMap = mapOf("Spanish" to "eng-spa", "Esperanto" to "en-eo")

    fun get(input: String, language: String): String? {
        val direction = langMap[language.removeSuffix(suffix)]
            ?: return null
        val process = Runtime.getRuntime().exec(cmd + direction)
        val stdin = process.outputStream
        stdin.write(input.toByteArray(charset))
        stdin.flush()
        stdin.close()
        process.waitFor()
        val output = process.inputStream.readBytes().toString(charset)  // if (output == "*" + input) return input
        if ('[' !in input) return output
        val params = input.getPlaceholderParameters().toTypedArray()
        return output.fillPlaceholders(*params)
    }

    fun getNewEntry(input: String, language: String): TranslationEntry? {
        if (!language.endsWith(suffix)) return null
        val output = get(input, language) ?: return null
        return TranslationEntry(input).apply {
            this[language] = output
            UncivGame.Current.translations[input] = this
        }
    }

    fun addToEntry(entry: TranslationEntry, input: String, language: String): String {
        if (!language.endsWith(suffix)) return input
        val output = get(input, language) ?: return input
        entry[language] = output
        return output
    }
}
