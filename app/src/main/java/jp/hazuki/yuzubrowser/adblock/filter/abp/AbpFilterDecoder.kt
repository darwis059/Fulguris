/*
 * Copyright (C) 2017-2019 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.hazuki.yuzubrowser.adblock.filter.abp

import com.google.re2j.Pattern
import jp.hazuki.yuzubrowser.adblock.*
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.ModifyFilter.Companion.REMOVEHEADER_NOT_ALLOWED
import jp.hazuki.yuzubrowser.adblock.filter.unified.ModifyFilter.Companion.RESPONSEHEADER_ALLOWED
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.*
import java.io.BufferedReader
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

class AbpFilterDecoder {
    private val contentRegex = Pattern.compile(CONTENT_FILTER_REGEX)

    fun checkHeader(reader: BufferedReader, charset: Charset): Boolean {
        reader.mark(1024)
        when (charset) {
            Charsets.UTF_8, Charsets.UTF_16, Charsets.UTF_16LE, Charsets.UTF_16BE -> {
                if (reader.read() == 0xfeff) { // Skip BOM
                    reader.mark(1024)
                } else {
                    reader.reset()
                }
            }
        }
        val header = reader.readLine() ?: return false
        if (header.isNotEmpty()) {
            return if (header[0] == '!') {
                reader.reset()
                true
            } else {
                header.startsWith(HEADER)
            }
        }
        return false
    }

    // taken from jp.hazuki.yuzubrowser.core.utility.extensions.forEachLine
    inline fun BufferedReader.forEachLine(block: (String) -> Unit) {
        while (true) {
            block(readLine() ?: return)
        }
    }

    fun decode(reader: BufferedReader, url: String?): UnifiedFilterSet {
        val info = DecoderInfo()
        val black = mutableListOf<UnifiedFilter>()
        val white = mutableListOf<UnifiedFilter>()
        val elementDisableFilter = mutableListOf<UnifiedFilter>()
        val elementFilter = mutableListOf<ElementFilter>()
        val modifyExceptionList = mutableListOf<UnifiedFilter>()
        val modifyList = mutableListOf<UnifiedFilter>()
        val importantList = mutableListOf<UnifiedFilter>()
        val importantAllowList = mutableListOf<UnifiedFilter>()
        reader.forEachLine { line ->
            if (line.isEmpty()) return@forEachLine
            val trimmedLine = line.trim()
            when {
                trimmedLine[0] == '!' -> trimmedLine.decodeComment(url, info)?.let {
                    throw OnRedirectException(it)
                }
                else -> {
                    val matcher = contentRegex.matcher(trimmedLine)
                    if (matcher.matches()) {
                        decodeContentFilter(
                            matcher.group(1),
                            matcher.group(2),
                            matcher.group(3),
                            elementFilter,
                        )
                    } else {
                        trimmedLine.decodeFilter(black, white, elementDisableFilter, modifyList, modifyExceptionList, importantList, importantAllowList)
                    }
                }
            }
        }
        return UnifiedFilterSet(info, black, white, elementDisableFilter, elementFilter, modifyList, modifyExceptionList, importantList, importantAllowList)
    }

    private fun decodeContentFilter(
        domains: String?,
        type: String?,
        body: String,
        elementFilterList: MutableList<ElementFilter>,
    ) {
        if (body.startsWith("+js")) return

        if (domains == null && type == "@") return

        var domainList = domains?.run {
            splitToSequence(',')
                .map { it.trim() }
                .toList()
        } ?: emptyList()

        if (domainList.size >= 2) {
            domainList.forEach {
                if (it.startsWith('~')) return
            }
        }

        if (domainList.isEmpty()) {
            domainList = listOf("")
        } else {
            var starIndex = domainList.indexOf("*")
            while (starIndex != -1) {
                domainList = domainList.subList(0, starIndex) + "" +
                    domainList.subList(starIndex, domainList.size)

                starIndex = domainList.indexOf("*")
            }
        }

        val isHide = when (type) {
            "@" -> false
            null -> true
            else -> return
        }

        domainList.forEach {
            var domain = it
            val isNot = domain.startsWith('~')
            if (isNot) {
                domain = domain.substring(1)
            }

            elementFilterList += if (domain.endsWith('*')) {
                domain = domain.substring(0, it.length - 1)
                TldRemovedElementFilter(domain, isHide, isNot, body.sanitizeSelector())
            } else {
                PlaneElementFilter(domain, isHide, isNot, body.sanitizeSelector())
            }
        }
    }

    private fun String.sanitizeSelector() = trim().replace("\\", "\\\\").replace("'", "\'")

    private fun String.decodeFilter(
        blackList: MutableList<UnifiedFilter>,
        whiteList: MutableList<UnifiedFilter>,
        elementFilterList: MutableList<UnifiedFilter>,
        modifyList: MutableList<UnifiedFilter>,
        modifyExceptionList: MutableList<UnifiedFilter>,
        importantList: MutableList<UnifiedFilter>,
        importantAllowList: MutableList<UnifiedFilter>
    ) {
        var contentType = 0
        var ignoreCase = false
        var domain: String? = null
        var thirdParty = -1
        var filter = this
        var elementFilter = false
        var modify: ModifyFilter? = null
        var important = false
        val blocking = if (filter.startsWith("@@")) {
            filter = substring(2)
            false
        } else {
            true
        }
        // re-write uBo responseheader to allow easier parsing
        val responseHeaderStart = filter.indexOf("##^responseheader(")
        if (responseHeaderStart > -1) {
            val end = filter.indexOf(')', responseHeaderStart)
            val header = filter.substring(responseHeaderStart + 18, end)
            if (!RESPONSEHEADER_ALLOWED.contains(header))
                return
            val other = filter.substringAfter(header)
            filter = if (other.length > 2 && other[2] == '$')
                filter.substring(0, responseHeaderStart) + "\$removeheader=" + header + "," + other.substring(2)
            else
                filter.substring(0, responseHeaderStart) + "\$removeheader=" + header
        }

        val optionsIndex = filter.lastIndexOf('$')
        if (optionsIndex >= 0) {
            val options = filter.substring(optionsIndex + 1).split(',').toMutableList()
            // all is equal to: document, popup, inline-script, inline-font
            //  but on mobile / webview there are no popups anyway (all opened in the same window/tab)
            if (options.contains("all")) {
                options.remove("all")
                contentType = contentType or ContentRequest.TYPE_DOCUMENT or ContentRequest.TYPE_STYLE_SHEET or ContentRequest.TYPE_IMAGE or ContentRequest.TYPE_OTHER or ContentRequest.TYPE_SCRIPT or ContentRequest.TYPE_XHR or ContentRequest.TYPE_FONT or ContentRequest.TYPE_MEDIA or ContentRequest.TYPE_WEB_SOCKET
                when {
                    options.contains("~inline-font") && options.contains("~inline-script") -> Unit // ignore both
                    options.contains("~inline-font") -> { // ignore inline-font only
                        options.add("inline-script")
                    }
                    options.contains("~inline-script") -> { // ignore inline-script only
                        options.add("inline-font")
                    }
                    else -> options.add("csp=font-src *; script-src 'unsafe-eval' * blob: data:") // take both
                }
                options.remove("~inline-font")
                options.remove("~inline-script")
            }

            options.forEach {
                var option = it
                var value: String? = null
                val separatorIndex = option.indexOf('=')
                if (separatorIndex >= 0) {
                    value = option.substring(separatorIndex + 1)
                    option = option.substring(0, separatorIndex)
                }
                if (option.isEmpty() || (option.startsWith("_") && option.matches("^_+$".toRegex()))) return@forEach

                val inverse = option[0] == '~'
                if (inverse) {
                    option = option.substring(1)
                }

                option = option.lowercase()
                val type = option.getOptionBit()
                if (type == -1) return

                when {
                    type > 0x00ff_ffff -> {
                        elementFilter = true
                    }
                    type > 0 -> {
                        contentType = if (inverse) {
                            if (contentType == 0) contentType = 0xffff
                            contentType and (type.inv())
                        } else {
                            contentType or type
                        }
                    }
                    type == 0 -> {
                        when (option) {
                            "match-case" -> ignoreCase = inverse
                            "domain" -> {
                                if (value == null) return
                                domain = value
                            }
                            "third-party", "3p" -> thirdParty = if (inverse) 0 else 1
                            "first-party", "1p" -> thirdParty = if (inverse) 1 else 0
                            "strict3p" -> thirdParty = if (inverse) 3 else 2
                            "strict1p" -> thirdParty = if (inverse) 2 else 3
                            "sitekey" -> Unit
                            "removeparam", "queryprune" -> {
                                modify = if (value == null || value.isEmpty()) RemoveparamFilter(null, false)
                                else {
                                    if (value.startsWith('~'))
                                        getRemoveparamFilter(value.substring(1), true)
                                    else
                                        getRemoveparamFilter(value, false)
                                }
                            }
                            "csp" -> {
                                modify = CspFilter(value)
                                contentType = contentType or (ContentRequest.TYPE_DOCUMENT and ContentRequest.TYPE_SUB_DOCUMENT) // uBo documentation: It can be applied to main document and documents in frames
                            }
                            "inline-font" -> {
                                modify = CspFilter("font-src *")
                                contentType = contentType or (ContentRequest.TYPE_DOCUMENT and ContentRequest.TYPE_SUB_DOCUMENT)
                            }
                            "inline-script" -> {
                                modify = CspFilter("script-src 'unsafe-eval' * blob: data:")
                                contentType = contentType or (ContentRequest.TYPE_DOCUMENT and ContentRequest.TYPE_SUB_DOCUMENT)
                            }
                            // currently no difference between redirect and redirect-rule
                            // actually: redirect-rule does only redirect if target is blocked by some other filter
                            //  more work to implement, currently blocking is completely independent from redirecting
                            //  and redirect will never be checked if request is blocked
                            // TODO: have redirect-rule separate and put it in a different filter container
                            //  to be checked if request is blocked
                            "redirect", "redirect-rule" -> if (value != null) modify = RedirectFilter(value)
                            "empty" -> modify = RedirectFilter("empty")
                            "mp4" -> {
                                modify = RedirectFilter("noopmp4-1s")
                                contentType = ContentRequest.TYPE_MEDIA // uBo documentation: media type will be assumed
                            }
                            "important" -> important = true
                            "removeheader" -> {
                                value = value?.lowercase() ?: return
                                val request = value.startsWith("request:")
                                val header = if (request) value.substringAfter("request:") else value
                                if (header in REMOVEHEADER_NOT_ALLOWED) return
                                modify = RemoveHeaderFilter(header, request)
                            }
                            else -> return
                        }
                    }
                }
            }
            filter = filter.substring(0, optionsIndex)

            // some lists use * to match all, some use empty
            //  convert * to empty since it will result in a simple contains filter
            if (filter == "*") filter = ""
        }

        val domains = domain?.domainsToDomainMap('|')
        if (contentType == 0) contentType = 0xffff

        if (elementFilter) {
            return
        }

        val abpFilter =
            if (filter.length >= 2 && filter[0] == '/' && filter[filter.lastIndex] == '/' && filter.mayContainRegexChars()) {
                createRegexFilter(filter.substring(1, filter.lastIndex), contentType, ignoreCase, domains, thirdParty) ?: return
            } else {
                val isStartsWith = filter.startsWith("||")
                val isEndWith = filter.endsWith('^')
                val content = filter.substring(
                    if (isStartsWith) 2 else 0,
                    if (isEndWith) filter.length - 1 else filter.length
                )
                val isLiteral = content.isLiteralFilter()
                if (isLiteral) {
                    when {
                        isStartsWith && isEndWith -> StartEndFilter(
                            content,
                            contentType,
                            ignoreCase,
                            domains,
                            thirdParty
                        )
                        isStartsWith -> StartsWithFilter(content, contentType, ignoreCase, domains, thirdParty)
                        isEndWith -> {
                            if (ignoreCase) {
                                PatternMatchFilter(
                                    filter,
                                    contentType,
                                    ignoreCase,
                                    domains,
                                    thirdParty
                                )
                            } else {
                                EndWithFilter(content, contentType, domains, thirdParty)
                            }
                        }
                        else -> {
                            if (ignoreCase) {
                                PatternMatchFilter(
                                    filter,
                                    contentType,
                                    ignoreCase,
                                    domains,
                                    thirdParty
                                )
                            } else {
                                ContainsFilter(content, contentType, domains, thirdParty)
                            }
                        }
                    }
                } else {
                    PatternMatchFilter(filter, contentType, ignoreCase, domains, thirdParty)
                }
            }

        when {
            elementFilter -> elementFilterList += abpFilter
            modify != null && blocking -> {
                // only removeparam may have no parameter when blocking
                if (modify !is RemoveparamFilter && modify!!.parameter == null) return
                abpFilter.modify = modify
                modifyList += abpFilter
            }
            important && blocking -> importantList += abpFilter
            blocking -> blackList += abpFilter
            modify != null -> {
                abpFilter.modify = modify
                modifyExceptionList += abpFilter
            }
            important -> importantAllowList += abpFilter
            else -> whiteList += abpFilter
        }
    }

    private fun String.mayContainRegexChars(): Boolean {
        forEach {
            when (it.lowercaseChar()) {
                in 'a'..'z', in '0'..'9', '%', '/', '_', '-' -> Unit
                else -> return true
            }
        }
        return false
    }

    private fun String.domainsToDomainMap(delimiter: Char): DomainMap? {
        if (length == 0) return null

        val items = split(delimiter)
        return if (items.size == 1) {
            if (items[0][0] == '~') {
                SingleDomainMap(false, items[0].substring(1))
            } else {
                SingleDomainMap(true, items[0])
            }
        } else {
            val domains = ArrayDomainMap(items.size)
            items.forEach { domain ->
                if (domain.isEmpty()) return@forEach
                if (domain[0] == '~') {
                    domains[domain.substring(1)] = false
                } else {
                    domains[domain] = true
                    domains.include = true
                }
            }
            domains
        }
    }

    private fun String.isLiteralFilter(): Boolean {
        forEach {
            when (it) {
                '*', '^', '|' -> return false
            }
        }
        return true
    }

    private fun String.getOptionBit(): Int {
        return when (this) {
            "other", "xbl", "dtd" -> ContentRequest.TYPE_OTHER
            "script" -> ContentRequest.TYPE_SCRIPT
            "image", "background" -> ContentRequest.TYPE_IMAGE
            "stylesheet", "css" -> ContentRequest.TYPE_STYLE_SHEET
            "subdocument", "frame" -> ContentRequest.TYPE_SUB_DOCUMENT
            "document" -> ContentRequest.TYPE_DOCUMENT
            "websocket" -> ContentRequest.TYPE_WEB_SOCKET
            "media" -> ContentRequest.TYPE_MEDIA
            "font" -> ContentRequest.TYPE_FONT
            "popup" -> ContentRequest.TYPE_POPUP
            "xmlhttprequest", "xhr" -> ContentRequest.TYPE_XHR
            "object", "webrtc", "ping",
            "object-subrequest", "genericblock" -> -1
            "elemhide", "ehide" -> ContentRequest.TYPE_ELEMENT_HIDE
            "generichide", "ghide" -> ContentRequest.TYPE_ELEMENT_GENERIC_HIDE
            else -> 0
        }
    }

    private fun String.decodeComment(url: String?, info: DecoderInfo): String? {
        val comment = split(':')
        if (comment.size < 2) return null

        when (comment[0].substring(1).trim().lowercase()) {
            "title" -> info.title = comment[1].trim()
            "homepage" -> info.homePage = comment[1].trim()
            "last updated" -> info.lastUpdate = comment[1].trim()
            "expires" -> info.expires = comment[1].trim().decodeExpires()
            "version" -> info.version = comment[1].trim()
            "redirect" -> {
                val redirect = comment[1].trim()
                if (url != null && url != redirect) {
                    return url
                }
            }
        }
        return null
    }

    private fun String.decodeExpires(): Int {
        val hours = indexOf("hours")
        if (hours > 0) {
            return try {
                substring(0, hours).trim().toInt()
            } catch (e: NumberFormatException) {
                -1
            }
        }
        val days = indexOf("days")
        if (days > 0) {
            return try {
                substring(0, days).trim().toInt() * 24
            } catch (e: NumberFormatException) {
                -1
            }
        }
        return -1
    }

    class OnRedirectException(val url: String) : IOException()

    private class DecoderInfo : UnifiedFilterInfo(null, null, null, null, null) {
        override var expires: Int? = null
        override var homePage: String? = null
        override var lastUpdate: String? = null
        override var title: String? = null
        override var version: String? = null
    }

    companion object {
        const val HEADER = "[Adblock Plus"

        private const val CONTENT_FILTER_REGEX = "^([^/*|@\"!]*?)#([@?\$])?#(.+)\$"
    }
}
