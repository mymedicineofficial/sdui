package com.sdui.test

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * HTML ENGINE (native, no WebView, no JS)
 * HTML + CSS ka ek subset parse karke seedha Compose UI banata hai.
 *
 * Tags      : div span p h1-h6 a button img ul ol li br hr b strong i em u s small + koi bhi unknown tag (div jaisa)
 * CSS       : <style> block (tag, .class, #id, descendant, a,b lists, :root + var(--x)) aur inline style=""
 * Properties: display(block|flex|grid|inline|inline-block|none), flex-direction, flex-wrap, justify-content,
 *             align-items, gap, flex/flex-grow, grid-template-columns, width/height(px,%), min/max, margin, padding,
 *             background(color|linear-gradient), border, border-radius, box-shadow, opacity, overflow-x:auto (h-scroll),
 *             color, font-size, font-weight, font-style, text-align, text-decoration, text-transform,
 *             line-height, letter-spacing, white-space:nowrap, text-overflow:ellipsis, object-fit, aspect-ratio
 * Actions   : data-action="toast:Hi" | "url:https://.." | "dial:112" | "screen:profile" | "back"  (ya <a href>)
 * Nahi hai  : JavaScript, position/absolute/fixed, calc(), :hover/::before, @media, animations, web fonts
 */

// ------------------------------------------------------------------ DOM + parser

private class HNode(val tag: String, val attrs: Map<String, String>, val parent: HNode?) {
    val children = mutableListOf<HNode>()
    var text: String? = null
    var style: Map<String, String> = emptyMap()
    val isText: Boolean get() = text != null
}

private val VOID_TAGS = setOf("br", "img", "hr", "input", "meta", "link", "source", "wbr")
private val INLINE_TAGS = setOf("span", "b", "strong", "i", "em", "u", "s", "small", "label", "code", "sup", "sub", "mark", "br")
private val ATTR_RE = Regex("([a-zA-Z_:@][-a-zA-Z0-9_:.]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+)))?")

private fun decodeEntities(s: String): String {
    if (!s.contains('&')) return s
    var out = s.replace("&nbsp;", "\u00A0").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&copy;", "©").replace("&hellip;", "…").replace("&rarr;", "→")
        .replace("&larr;", "←").replace("&middot;", "·").replace("&bull;", "•").replace("&times;", "×")
    out = Regex("&#(\\d+);").replace(out) { m ->
        m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
    }
    out = Regex("&#[xX]([0-9a-fA-F]+);").replace(out) { m ->
        m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
    }
    return out.replace("&amp;", "&")
}

private fun findTagEnd(s: String, from: Int): Int {
    var q = '\u0000'
    var j = from + 1
    while (j < s.length) {
        val c = s[j]
        if (q != '\u0000') {
            if (c == q) q = '\u0000'
        } else if (c == '"' || c == '\'') {
            q = c
        } else if (c == '>') {
            return j
        }
        j++
    }
    return -1
}

private fun parseHtml(src: String): Pair<HNode, String> {
    val root = HNode("root", emptyMap(), null)
    var cur = root
    val css = StringBuilder()
    var i = 0
    val n = src.length
    while (i < n) {
        if (src[i] == '<' && i + 1 < n) {
            val nx = src[i + 1]
            if (src.startsWith("<!--", i)) {
                val e = src.indexOf("-->", i + 4)
                i = if (e < 0) n else e + 3
                continue
            }
            if (nx == '!' || nx == '?') {
                val e = src.indexOf('>', i)
                i = if (e < 0) n else e + 1
                continue
            }
            if (nx == '/') {
                val e = src.indexOf('>', i)
                val end = if (e < 0) n else e
                val name = src.substring(i + 2, end).trim().lowercase()
                i = if (e < 0) n else e + 1
                var p: HNode? = cur
                while (p != null && p.tag != name) p = p.parent
                if (p != null) {
                    val up = p.parent
                    if (up != null) cur = up
                }
                continue
            }
            if (nx.isLetter()) {
                val e = findTagEnd(src, i)
                if (e < 0) break
                var inner = src.substring(i + 1, e)
                val selfClose = inner.endsWith("/")
                if (selfClose) inner = inner.dropLast(1)
                inner = inner.trim()
                val sp = inner.indexOfFirst { it.isWhitespace() }
                val name = (if (sp < 0) inner else inner.substring(0, sp)).lowercase()
                val attrStr = if (sp < 0) "" else inner.substring(sp)
                val attrs = HashMap<String, String>()
                for (m in ATTR_RE.findAll(attrStr)) {
                    val k = m.groupValues[1].lowercase()
                    val v = m.groups[2]?.value ?: m.groups[3]?.value ?: m.groups[4]?.value ?: ""
                    attrs[k] = decodeEntities(v)
                }
                i = e + 1
                if (name == "style" || name == "script") {
                    val close = src.indexOf("</$name", i, ignoreCase = true)
                    val cEnd = if (close < 0) n else close
                    if (name == "style") css.append(src, i, cEnd).append('\n')
                    val gt = if (close < 0) -1 else src.indexOf('>', close)
                    i = if (gt < 0) n else gt + 1
                    continue
                }
                if (name == "li" && cur.tag == "li") cur = cur.parent ?: cur
                if (name == "p" && cur.tag == "p") cur = cur.parent ?: cur
                val node = HNode(name, attrs, cur)
                cur.children.add(node)
                if (name !in VOID_TAGS && !selfClose) cur = node
                continue
            }
        }
        var j = src.indexOf('<', i + 1)
        if (j < 0) j = n
        val raw = src.substring(i, j)
        if (raw.isNotEmpty()) {
            val t = HNode("#text", emptyMap(), cur)
            t.text = decodeEntities(raw)
            cur.children.add(t)
        }
        i = j
    }
    return root to css.toString()
}

// ------------------------------------------------------------------ CSS

private class HSel(val tag: String?, val id: String?, val classes: List<String>) {
    val spec: Int get() = (if (id != null) 100 else 0) + classes.size * 10 + (if (tag != null) 1 else 0)
}

private class HRule(val chain: List<HSel>, val decls: Map<String, String>, val order: Int, val spec: Int)

private fun splitTop(s: String, sep: Char = ','): List<String> {
    val out = mutableListOf<String>()
    var d = 0
    val b = StringBuilder()
    for (c in s) {
        if (c == '(') d++
        if (c == ')') d--
        if (c == sep && d == 0) {
            out.add(b.toString())
            b.setLength(0)
        } else b.append(c)
    }
    out.add(b.toString())
    return out
}

private fun parseDecls(s: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    val cur = StringBuilder()
    var depth = 0
    fun flush() {
        val d = cur.toString()
        cur.setLength(0)
        val c = d.indexOf(':')
        if (c > 0) {
            val k = d.substring(0, c).trim().lowercase()
            val v = d.substring(c + 1).replace("!important", "").trim()
            if (k.isNotEmpty() && v.isNotEmpty()) out[k] = v
        }
    }
    for (ch in s) {
        if (ch == '(') depth++
        if (ch == ')') depth--
        if (ch == ';' && depth <= 0) flush() else cur.append(ch)
    }
    flush()
    return out
}

private fun parseSimple(tok: String): HSel? {
    if (tok == ":root") return HSel("html", null, emptyList())
    var tag: String? = null
    var id: String? = null
    val classes = mutableListOf<String>()
    var i = 0
    val sb = StringBuilder()
    while (i < tok.length && (tok[i].isLetterOrDigit() || tok[i] == '-' || tok[i] == '_' || tok[i] == '*')) {
        sb.append(tok[i]); i++
    }
    if (sb.isNotEmpty() && sb.toString() != "*") tag = sb.toString().lowercase()
    while (i < tok.length) {
        val c = tok[i]
        if (c == '.' || c == '#') {
            i++
            val b = StringBuilder()
            while (i < tok.length && (tok[i].isLetterOrDigit() || tok[i] == '-' || tok[i] == '_')) {
                b.append(tok[i]); i++
            }
            if (c == '.') classes.add(b.toString()) else id = b.toString()
        } else return null
    }
    return HSel(tag, id, classes)
}

private val SPLIT_WS = Regex("\\s+(?![^(]*\\))")

private fun parseCss(src0: String): List<HRule> {
    val src = src0.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
    val rules = mutableListOf<HRule>()
    var i = 0
    var order = 0
    while (i < src.length) {
        val open = src.indexOf('{', i)
        if (open < 0) break
        val selText = src.substring(i, open).trim()
        var depth = 1
        var j = open + 1
        while (j < src.length && depth > 0) {
            if (src[j] == '{') depth++ else if (src[j] == '}') depth--
            j++
        }
        val body = src.substring(open + 1, if (depth == 0) j - 1 else src.length)
        i = j
        if (selText.startsWith("@")) continue
        val decls = parseDecls(body)
        for (one in selText.split(",")) {
            val parts = one.trim().split(Regex("[\\s>+~]+")).filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue
            val chain = parts.map { parseSimple(it) }
            if (chain.any { it == null }) continue
            val sels = chain.filterNotNull()
            rules.add(HRule(sels, decls, order++, sels.sumOf { it.spec }))
        }
    }
    return rules
}

private fun simpleMatch(s: HSel, n: HNode): Boolean {
    if (n.isText) return false
    if (s.tag != null && s.tag != n.tag && !(s.tag == "html" && n.tag == "root")) return false
    if (s.id != null && n.attrs["id"] != s.id) return false
    if (s.classes.isNotEmpty()) {
        val have = (n.attrs["class"] ?: "").split(Regex("\\s+"))
        if (!have.containsAll(s.classes)) return false
    }
    return true
}

private fun matches(chain: List<HSel>, node: HNode): Boolean {
    if (!simpleMatch(chain.last(), node)) return false
    var idx = chain.size - 2
    var p = node.parent
    while (idx >= 0 && p != null) {
        if (simpleMatch(chain[idx], p)) idx--
        p = p.parent
    }
    return idx < 0
}

private val INHERIT = setOf(
    "color", "font-size", "font-weight", "font-style", "text-align", "line-height",
    "letter-spacing", "text-transform", "white-space", "font-family"
)
private val VAR_RE = Regex("var\\(\\s*(--[-a-zA-Z0-9_]+)\\s*(?:,\\s*([^)]*))?\\)")

private fun uaStyle(tag: String): Map<String, String> = when (tag) {
    "h1" -> mapOf("font-size" to "32px", "font-weight" to "700", "margin" to "12px 0")
    "h2" -> mapOf("font-size" to "24px", "font-weight" to "700", "margin" to "10px 0")
    "h3" -> mapOf("font-size" to "20px", "font-weight" to "700", "margin" to "8px 0")
    "h4" -> mapOf("font-size" to "18px", "font-weight" to "700", "margin" to "8px 0")
    "h5", "h6" -> mapOf("font-size" to "16px", "font-weight" to "700", "margin" to "6px 0")
    "p" -> mapOf("margin" to "8px 0")
    "ul", "ol" -> mapOf("margin" to "8px 0", "padding-left" to "16px")
    "b", "strong" -> mapOf("font-weight" to "700")
    "i", "em" -> mapOf("font-style" to "italic")
    "u" -> mapOf("text-decoration" to "underline")
    "s" -> mapOf("text-decoration" to "line-through")
    "small" -> mapOf("font-size" to "0.85em")
    "a" -> mapOf("display" to "inline-block", "color" to "#3B82F6")
    "button" -> mapOf("display" to "inline-block", "padding" to "10px 18px", "font-weight" to "600", "text-align" to "center")
    "hr" -> mapOf("height" to "1px", "background-color" to "#888888", "margin" to "12px 0", "opacity" to "0.4")
    "body" -> mapOf("margin" to "0")
    "head", "title", "meta", "link" -> mapOf("display" to "none")
    else -> emptyMap()
}

private fun pxOf(v: String?, fontPx: Float): Float? {
    if (v == null) return null
    val t = v.trim().lowercase()
    return when {
        t.endsWith("px") -> t.dropLast(2).toFloatOrNull()
        t.endsWith("rem") -> t.dropLast(3).toFloatOrNull()?.times(16f)
        t.endsWith("em") -> t.dropLast(2).toFloatOrNull()?.times(fontPx)
        t.endsWith("pt") -> t.dropLast(2).toFloatOrNull()?.times(1.333f)
        else -> t.toFloatOrNull()
    }
}

private fun computeStyles(node: HNode, inherited: Map<String, String>, rules: List<HRule>) {
    if (node.isText) return
    val m = HashMap<String, String>()
    for ((k, v) in inherited) if (k in INHERIT || k.startsWith("--")) m[k] = v
    if (node.tag in INLINE_TAGS) m["display"] = "inline"
    m.putAll(uaStyle(node.tag))
    for (r in rules) if (matches(r.chain, node)) m.putAll(r.decls)
    val inline = node.attrs["style"]
    if (inline != null) m.putAll(parseDecls(inline))
    for ((k, v) in m.entries.toList()) {
        if (v.contains("var(")) {
            m[k] = VAR_RE.replace(v) { r -> m[r.groupValues[1]] ?: r.groupValues[2].trim() }
        }
    }
    val parentFs = pxOf(inherited["font-size"], 16f) ?: 16f
    val fsRaw = m["font-size"]
    if (fsRaw != null && fsRaw != inherited["font-size"]) {
        val t = fsRaw.trim()
        val fs = if (t.endsWith("%")) t.dropLast(1).toFloatOrNull()?.times(parentFs / 100f) else pxOf(t, parentFs)
        m["font-size"] = "${fs ?: parentFs}px"
    }
    node.style = m
    for (c in node.children) computeStyles(c, m, rules)
}

private fun findTag(n: HNode, tag: String): HNode? {
    if (n.tag == tag) return n
    for (c in n.children) findTag(c, tag)?.let { return it }
    return null
}

// ------------------------------------------------------------------ value helpers

private val NAMED_COLORS: Map<String, Long> = mapOf(
    "white" to 0xFFFFFFFFL, "black" to 0xFF000000L, "red" to 0xFFFF0000L, "green" to 0xFF008000L,
    "blue" to 0xFF0000FFL, "yellow" to 0xFFFFFF00L, "orange" to 0xFFFFA500L, "purple" to 0xFF800080L,
    "gray" to 0xFF808080L, "grey" to 0xFF808080L, "pink" to 0xFFFFC0CBL, "transparent" to 0x00000000L
)

private fun parseColor(v0: String?): Color? {
    if (v0 == null) return null
    val v = v0.trim().lowercase()
    if (v.isEmpty()) return null
    NAMED_COLORS[v]?.let { return Color(it) }
    if (v.startsWith("#")) {
        var h = v.drop(1)
        if (h.length == 3 || h.length == 4) h = h.map { "$it$it" }.joinToString("")
        return when (h.length) {
            6 -> h.toLongOrNull(16)?.let { Color(0xFF000000L or it) }
            8 -> h.toLongOrNull(16)?.let { Color(((it and 0xFFL) shl 24) or (it shr 8)) }
            else -> null
        }
    }
    if (v.startsWith("rgb")) {
        val nums = Regex("[-+]?[0-9]*\\.?[0-9]+").findAll(v.substringAfter("(")).map { it.value.toFloat() }.toList()
        if (nums.size >= 3) {
            val a = if (nums.size >= 4) nums[3] else 1f
            return Color(nums[0] / 255f, nums[1] / 255f, nums[2] / 255f, a)
        }
    }
    return null
}

private fun gradientOf(v: String): Brush? {
    val t = v.trim()
    if (!t.startsWith("linear-gradient")) return null
    val inner = t.substringAfter("(").substringBeforeLast(")")
    val parts = splitTop(inner)
    var angle = 180f
    var idx = 0
    val first = parts[0].trim().lowercase()
    if (first.endsWith("deg")) {
        angle = first.dropLast(3).toFloatOrNull() ?: 180f
        idx = 1
    } else if (first.startsWith("to ")) {
        angle = when (first) {
            "to right" -> 90f
            "to left" -> 270f
            "to top" -> 0f
            "to bottom" -> 180f
            "to bottom right", "to right bottom" -> 135f
            "to top right", "to right top" -> 45f
            else -> 180f
        }
        idx = 1
    }
    val colors = parts.drop(idx).mapNotNull { parseColor(it.trim().split(SPLIT_WS)[0]) }
    if (colors.size < 2) return null
    return when (angle) {
        90f -> Brush.horizontalGradient(colors)
        270f -> Brush.horizontalGradient(colors.reversed())
        0f -> Brush.verticalGradient(colors.reversed())
        180f -> Brush.verticalGradient(colors)
        else -> Brush.linearGradient(colors)
    }
}

private fun bgOf(st: Map<String, String>): Pair<Color?, Brush?> {
    val short = st["background"]
    val imgVal = st["background-image"] ?: short?.takeIf { it.contains("gradient") }
    val brush = imgVal?.let { gradientOf(it) }
    var color = parseColor(st["background-color"])
    if (color == null && short != null && !short.contains("gradient") && !short.contains("url(")) {
        color = parseColor(short) ?: parseColor(short.trim().split(SPLIT_WS)[0])
    }
    return color to brush
}

private fun fontPx(st: Map<String, String>): Float = pxOf(st["font-size"], 16f) ?: 16f

private class HSides(val l: Float, val t: Float, val r: Float, val b: Float, val autoH: Boolean)

private fun sidesOf(style: Map<String, String>, prop: String, fp: Float): HSides {
    var t = 0f
    var r = 0f
    var b = 0f
    var l = 0f
    var auto = false
    val v = style[prop]
    fun px(s: String): Float = if (s == "auto") 0f else (pxOf(s, fp) ?: 0f)
    if (v != null) {
        val toks = v.trim().split(SPLIT_WS).filter { it.isNotEmpty() }
        when (toks.size) {
            1 -> { t = px(toks[0]); r = t; b = t; l = t; auto = toks[0] == "auto" }
            2 -> { t = px(toks[0]); b = t; r = px(toks[1]); l = r; auto = toks[1] == "auto" }
            3 -> { t = px(toks[0]); r = px(toks[1]); l = r; b = px(toks[2]); auto = toks[1] == "auto" }
            4 -> { t = px(toks[0]); r = px(toks[1]); b = px(toks[2]); l = px(toks[3]); auto = toks[1] == "auto" && toks[3] == "auto" }
        }
    }
    style["$prop-top"]?.let { t = pxOf(it, fp) ?: t }
    style["$prop-right"]?.let { r = pxOf(it, fp) ?: r }
    style["$prop-bottom"]?.let { b = pxOf(it, fp) ?: b }
    style["$prop-left"]?.let { l = pxOf(it, fp) ?: l }
    if (style["$prop-left"] == "auto" && style["$prop-right"] == "auto") auto = true
    return HSides(l.coerceAtLeast(0f), t.coerceAtLeast(0f), r.coerceAtLeast(0f), b.coerceAtLeast(0f), auto)
}

private fun borderOf(st: Map<String, String>, fp: Float): Pair<Float, Color>? {
    val b = st["border"]
    var w: Float? = pxOf(st["border-width"], fp)
    var c: Color? = parseColor(st["border-color"])
    var kind: String? = st["border-style"]
    if (b != null) {
        for (tk in b.trim().split(SPLIT_WS)) {
            val lt = tk.lowercase()
            if (lt == "solid" || lt == "dashed" || lt == "dotted" || lt == "double" || lt == "none") kind = lt
            else if (lt.firstOrNull()?.let { it.isDigit() || it == '.' } == true) w = pxOf(lt, fp)
            else parseColor(lt)?.let { c = it }
        }
    }
    if (kind == "none") return null
    val width = w ?: return null
    if (width <= 0f) return null
    return width to (c ?: Color.Black)
}

private fun shapeOf(st: Map<String, String>, fp: Float): Shape {
    val v = st["border-radius"]?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: return RectangleShape
    if (v.endsWith("%")) {
        val p = v.dropLast(1).toFloatOrNull() ?: 0f
        return if (p <= 0f) RectangleShape else RoundedCornerShape(percent = p.coerceAtMost(50f).toInt())
    }
    val px = pxOf(v, fp) ?: 0f
    return when {
        px <= 0f -> RectangleShape
        px >= 999f -> RoundedCornerShape(percent = 50)
        else -> RoundedCornerShape(px.dp)
    }
}

private fun shadowOf(st: Map<String, String>, fp: Float): Float {
    val v = st["box-shadow"] ?: return 0f
    if (v.trim() == "none") return 0f
    val toks = splitTop(v.trim(), ' ').filter { it.isNotBlank() }
    val nums = toks.mapNotNull { pxOf(it, fp) }
    val blur = nums.getOrNull(2) ?: 0f
    return (blur / 2f).coerceIn(0f, 24f)
}

private fun weightOf(v: String?): FontWeight? = when (v?.trim()?.lowercase()) {
    null -> null
    "bold", "bolder" -> FontWeight.Bold
    "normal" -> FontWeight.Normal
    else -> v?.trim()?.toIntOrNull()?.let { FontWeight(it.coerceIn(1, 1000)) }
}

private fun spanOf(st: Map<String, String>): SpanStyle {
    val fp = fontPx(st)
    val td = st["text-decoration"] ?: ""
    val inlineBg = if (st["display"] == "inline") parseColor(st["background-color"] ?: st["background"]) else null
    return SpanStyle(
        color = parseColor(st["color"]) ?: Color.Unspecified,
        fontSize = fp.sp,
        fontWeight = weightOf(st["font-weight"]),
        fontStyle = if (st["font-style"] == "italic") FontStyle.Italic else null,
        textDecoration = when {
            td.contains("underline") -> TextDecoration.Underline
            td.contains("line-through") -> TextDecoration.LineThrough
            else -> null
        },
        letterSpacing = pxOf(st["letter-spacing"], fp)?.sp ?: TextUnit.Unspecified,
        background = inlineBg ?: Color.Unspecified
    )
}

private fun actionOf(n: HNode): Pair<String, String>? {
    val da = n.attrs["data-action"]
    if (da != null) {
        val i = da.indexOf(':')
        return if (i < 0) (da.trim() to "") else (da.substring(0, i).trim() to da.substring(i + 1).trim())
    }
    val href = n.attrs["href"] ?: return null
    return when {
        href.startsWith("http") -> "url" to href
        href.startsWith("tel:") -> "dial" to href.removePrefix("tel:")
        href.startsWith("screen:") -> "screen" to href.removePrefix("screen:")
        else -> null
    }
}

// ------------------------------------------------------------------ inline text

private class HSeg(val text: String, val style: SpanStyle)

private val WS_RUN = Regex("[ \\t\\r\\n\\u000C]+")

private fun collectInline(nodes: List<HNode>, out: MutableList<HSeg>) {
    for (nd in nodes) {
        if (nd.isText) {
            val st = nd.parent?.style ?: emptyMap()
            var t = (nd.text ?: "").replace(WS_RUN, " ")
            t = when (st["text-transform"]) {
                "uppercase" -> t.uppercase()
                "lowercase" -> t.lowercase()
                "capitalize" -> t.split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
                else -> t
            }
            if (t.isNotEmpty()) out.add(HSeg(t, spanOf(st)))
        } else if (nd.style["display"] == "none") {
            continue
        } else if (nd.tag == "br") {
            out.add(HSeg("\n", SpanStyle()))
        } else collectInline(nd.children, out)
    }
}

private fun normalizeSegs(segs: List<HSeg>): List<HSeg> {
    val out = mutableListOf<HSeg>()
    var prevSpace = true
    for (s in segs) {
        var t = s.text
        if (t == "\n") {
            if (out.isNotEmpty()) {
                val last = out.last()
                if (last.text.endsWith(" ")) out[out.size - 1] = HSeg(last.text.dropLast(1), last.style)
            }
            out.add(s)
            prevSpace = true
            continue
        }
        if (prevSpace && t.startsWith(" ")) t = t.drop(1)
        if (t.isEmpty()) continue
        out.add(HSeg(t, s.style))
        prevSpace = t.endsWith(" ")
    }
    if (out.isNotEmpty()) {
        val last = out.last()
        if (last.text.endsWith(" ")) out[out.size - 1] = HSeg(last.text.trimEnd(' '), last.style)
    }
    return out.filter { it.text.isNotEmpty() }
}

@Composable
private fun InlineText(nodes: List<HNode>, container: HNode, fills: Boolean) {
    val raw = mutableListOf<HSeg>()
    collectInline(nodes, raw)
    val segs = normalizeSegs(raw)
    if (segs.isEmpty()) return
    val st = container.style
    val fp = fontPx(st)
    val annotated = buildAnnotatedString {
        for (s in segs) {
            pushStyle(s.style)
            append(s.text)
            pop()
        }
    }
    val align = when (st["text-align"]) {
        "center" -> TextAlign.Center
        "right", "end" -> TextAlign.End
        "justify" -> TextAlign.Justify
        else -> TextAlign.Start
    }
    val lhRaw = st["line-height"]?.trim()
    val lh: Float? = if (lhRaw == null) null
    else if (lhRaw.endsWith("px") || lhRaw.endsWith("em") || lhRaw.endsWith("rem")) pxOf(lhRaw, fp)
    else lhRaw.toFloatOrNull()?.times(fp)
    val nowrap = st["white-space"] == "nowrap"
    Text(
        text = annotated,
        modifier = if (fills && align != TextAlign.Start) Modifier.fillMaxWidth() else Modifier,
        color = parseColor(st["color"]) ?: Color.Black,
        fontSize = fp.sp,
        textAlign = align,
        lineHeight = if (lh != null) lh.sp else TextUnit.Unspecified,
        maxLines = if (nowrap) 1 else Int.MAX_VALUE,
        overflow = if (nowrap || st["text-overflow"] == "ellipsis") TextOverflow.Ellipsis else TextOverflow.Clip
    )
}

// ------------------------------------------------------------------ boxes

internal class HEnv(val onAction: (String, String) -> Unit)

private enum class HPk { BLOCK, ROW, COL_STRETCH, COL, GRID }

private sealed class HItem {
    class Txt(val nodes: List<HNode>) : HItem()
    class Elem(val node: HNode) : HItem()
}

private fun displayOf(n: HNode): String = n.style["display"] ?: "block"

private fun blockItems(node: HNode): List<HItem> {
    val items = mutableListOf<HItem>()
    var pending = mutableListOf<HNode>()
    fun flush() {
        if (pending.isNotEmpty()) {
            items.add(HItem.Txt(pending))
            pending = mutableListOf()
        }
    }
    for (c in node.children) {
        if (c.isText || displayOf(c) == "inline") pending.add(c)
        else {
            flush()
            if (displayOf(c) != "none") items.add(HItem.Elem(c))
        }
    }
    flush()
    return items
}

private fun Modifier.boxSize(st: Map<String, String>, fp: Float, defaultFill: Boolean): Modifier {
    var m = this
    val w = st["width"]?.trim() ?: ""
    val pxW = if (w.isNotEmpty() && w != "auto" && !w.endsWith("%")) pxOf(w, fp) else null
    val frac = if (w.endsWith("%")) w.dropLast(1).toFloatOrNull()?.div(100f) else null
    m = when {
        pxW != null -> m.width(pxW.dp)
        frac != null -> if (frac >= 1f) m.fillMaxWidth() else m.fillMaxWidth(frac)
        defaultFill -> m.fillMaxWidth()
        else -> m
    }
    pxOf(st["max-width"], fp)?.let { m = m.widthIn(max = it.dp) }
    pxOf(st["min-width"], fp)?.let { m = m.widthIn(min = it.dp) }
    val h = pxOf(st["height"], fp)
    if (h != null) m = m.height(h.dp)
    pxOf(st["min-height"], fp)?.let { m = m.heightIn(min = it.dp) }
    return m
}

private fun defaultFillOf(pk: HPk, display: String): Boolean = when (pk) {
    HPk.BLOCK -> !display.startsWith("inline")
    HPk.ROW -> false
    HPk.COL_STRETCH -> true
    HPk.COL -> false
    HPk.GRID -> true
}

@Composable
private fun HBox(node: HNode, env: HEnv, pk: HPk, extra: Modifier) {
    val st = node.style
    val display = displayOf(node)
    if (display == "none") return
    if (node.tag == "img") {
        HImage(node, env, pk, extra)
        return
    }
    val fp = fontPx(st)
    val margin = sidesOf(st, "margin", fp)
    val padding = sidesOf(st, "padding", fp)
    val defaultFill = defaultFillOf(pk, display)
    val w = st["width"]?.trim() ?: ""
    val explicitW = w.isNotEmpty() && w != "auto"
    val fills = if (explicitW) w.endsWith("%") else defaultFill

    var m = extra.padding(start = margin.l.dp, top = margin.t.dp, end = margin.r.dp, bottom = margin.b.dp)
    st["opacity"]?.toFloatOrNull()?.let { m = m.alpha(it) }
    m = m.boxSize(st, fp, defaultFill)
    val shape = shapeOf(st, fp)
    val elev = shadowOf(st, fp)
    if (elev > 0f) m = m.shadow(elev.dp, shape)
    val bg = bgOf(st)
    val brush = bg.second
    val bgColor = bg.first
    if (brush != null) m = m.background(brush, shape) else if (bgColor != null) m = m.background(bgColor, shape)
    val border = borderOf(st, fp)
    if (border != null) m = m.border(border.first.dp, border.second, shape)
    if (shape !== RectangleShape) m = m.clip(shape)
    val action = actionOf(node)
    if (action != null) m = m.clickable { env.onAction(action.first, action.second) }
    m = m.padding(start = padding.l.dp, top = padding.t.dp, end = padding.r.dp, bottom = padding.b.dp)

    if (node.tag == "li") {
        val idx = (node.parent?.children?.filter { it.tag == "li" }?.indexOf(node) ?: 0) + 1
        val bullet = if (node.parent?.tag == "ol") "$idx. " else "•  "
        Row(m) {
            Text(bullet, color = parseColor(st["color"]) ?: Color.Black, fontSize = fp.sp)
            BlockChildren(node, env, Modifier.weight(1f), true)
        }
        return
    }
    when (display) {
        "flex", "inline-flex" -> FlexChildren(node, env, m, fills)
        "grid" -> GridChildren(node, env, m)
        else -> BlockChildren(node, env, m, fills)
    }
}

@Composable
private fun BlockChildren(node: HNode, env: HEnv, modifier: Modifier, fills: Boolean) {
    val items = blockItems(node)
    Column(modifier) {
        for (item in items) {
            when (item) {
                is HItem.Txt -> InlineText(item.nodes, node, fills)
                is HItem.Elem -> {
                    val c = item.node
                    val auto = sidesOf(c.style, "margin", fontPx(c.style)).autoH
                    HBox(c, env, HPk.BLOCK, if (auto) Modifier.align(Alignment.CenterHorizontally) else Modifier)
                }
            }
        }
    }
}

private fun hArr(jc: String, gap: Dp): Arrangement.Horizontal = when (jc) {
    "center" -> Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
    "flex-end", "end" -> Arrangement.spacedBy(gap, Alignment.End)
    "space-between" -> Arrangement.SpaceBetween
    "space-around" -> Arrangement.SpaceAround
    "space-evenly" -> Arrangement.SpaceEvenly
    else -> Arrangement.spacedBy(gap)
}

private fun vArr(jc: String, gap: Dp): Arrangement.Vertical = when (jc) {
    "center" -> Arrangement.spacedBy(gap, Alignment.CenterVertically)
    "flex-end", "end" -> Arrangement.spacedBy(gap, Alignment.Bottom)
    "space-between" -> Arrangement.SpaceBetween
    "space-around" -> Arrangement.SpaceAround
    "space-evenly" -> Arrangement.SpaceEvenly
    else -> Arrangement.spacedBy(gap)
}

private fun growOf(c: HNode): Float {
    if (c.isText) return 0f
    val g = c.style["flex-grow"]?.toFloatOrNull()
    if (g != null) return g
    val f = c.style["flex"]?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toFloatOrNull()
    return f ?: 0f
}

@Composable
private fun FlexItem(c: HNode, parent: HNode, env: HEnv, pk: HPk, extra: Modifier) {
    if (c.isText) InlineText(listOf(c), parent, false) else HBox(c, env, pk, extra)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlexChildren(node: HNode, env: HEnv, modifier: Modifier, fills: Boolean) {
    val st = node.style
    val fp = fontPx(st)
    val col = st["flex-direction"]?.startsWith("column") == true
    val gapTok = st["gap"]?.trim()?.split(Regex("\\s+"))?.firstOrNull()
    val gap = (pxOf(gapTok, fp) ?: 0f).dp
    val jc = st["justify-content"] ?: "flex-start"
    val ai = st["align-items"] ?: "stretch"
    val wrap = st["flex-wrap"] == "wrap"
    val hs = st["overflow-x"] == "auto" || st["overflow-x"] == "scroll" || st["overflow"] == "auto"
    val scroll = rememberScrollState()
    val items = node.children.filter { c ->
        if (c.isText) !(c.text ?: "").isBlank() else displayOf(c) != "none"
    }
    if (!col) {
        val va = when (ai) {
            "center" -> Alignment.CenterVertically
            "flex-end", "end" -> Alignment.Bottom
            else -> Alignment.Top
        }
        if (wrap) {
            FlowRow(
                modifier,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                for (c in items) FlexItem(c, node, env, HPk.ROW, Modifier)
            }
        } else {
            Row(
                if (hs) modifier.horizontalScroll(scroll) else modifier,
                horizontalArrangement = hArr(jc, gap),
                verticalAlignment = va
            ) {
                for (c in items) {
                    val g = growOf(c)
                    FlexItem(c, node, env, HPk.ROW, if (g > 0f && !hs) Modifier.weight(g) else Modifier)
                }
            }
        }
    } else {
        val ha = when (ai) {
            "center" -> Alignment.CenterHorizontally
            "flex-end", "end" -> Alignment.End
            else -> Alignment.Start
        }
        val stretch = ai == "stretch" || ai == "normal"
        Column(modifier, verticalArrangement = vArr(jc, gap), horizontalAlignment = ha) {
            for (c in items) {
                val g = growOf(c)
                FlexItem(c, node, env, if (stretch) HPk.COL_STRETCH else HPk.COL, if (g > 0f) Modifier.weight(g) else Modifier)
            }
        }
    }
}

private fun parseGridCols(v: String): List<Float> {
    val t = v.trim()
    val rep = Regex("repeat\\(\\s*(\\d+)\\s*,\\s*([^)]+)\\)").find(t)
    if (rep != null) {
        val n = rep.groupValues[1].toInt().coerceIn(1, 6)
        val w = rep.groupValues[2].trim().removeSuffix("fr").toFloatOrNull() ?: 1f
        return List(n) { w }
    }
    val toks = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return toks.map { it.removeSuffix("fr").toFloatOrNull() ?: 1f }.ifEmpty { listOf(1f, 1f) }.take(6)
}

@Composable
private fun GridChildren(node: HNode, env: HEnv, modifier: Modifier) {
    val st = node.style
    val fp = fontPx(st)
    val weights = parseGridCols(st["grid-template-columns"] ?: "1fr 1fr")
    val gaps = (st["gap"] ?: st["grid-gap"])?.trim()?.split(Regex("\\s+")) ?: emptyList()
    val rowGap = (pxOf(gaps.getOrNull(0), fp) ?: 0f).dp
    val colGap = (pxOf(gaps.getOrNull(1) ?: gaps.getOrNull(0), fp) ?: 0f).dp
    val cells = node.children.filter { !it.isText && displayOf(it) != "none" }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(rowGap)) {
        for (rowCells in cells.chunked(weights.size)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(colGap)) {
                for ((i, c) in rowCells.withIndex()) {
                    HBox(c, env, HPk.GRID, Modifier.weight(weights[i]))
                }
                for (k in rowCells.size until weights.size) Spacer(Modifier.weight(weights[k]))
            }
        }
    }
}

@Composable
private fun HImage(node: HNode, env: HEnv, pk: HPk, extra: Modifier) {
    val st = HashMap(node.style)
    for (dim in listOf("width", "height")) {
        val a = node.attrs[dim]
        if (a != null && !st.containsKey(dim)) st[dim] = if (a.all { it.isDigit() }) a + "px" else a
    }
    val fp = fontPx(st)
    val margin = sidesOf(st, "margin", fp)
    var m = extra.padding(start = margin.l.dp, top = margin.t.dp, end = margin.r.dp, bottom = margin.b.dp)
    st["opacity"]?.toFloatOrNull()?.let { m = m.alpha(it) }
    m = m.boxSize(st, fp, defaultFillOf(pk, displayOf(node)))
    val hasH = st.containsKey("height")
    val ratio = st["aspect-ratio"]?.let { v ->
        val p = v.split("/").map { it.trim().toFloatOrNull() }
        if (p.size == 2 && p[0] != null && p[1] != null && p[1] != 0f) p[0]!! / p[1]!! else v.trim().toFloatOrNull()
    }
    if (!hasH && ratio != null && ratio > 0f) m = m.aspectRatio(ratio)
    val shape = shapeOf(st, fp)
    if (shape !== RectangleShape) m = m.clip(shape)
    val action = actionOf(node)
    if (action != null) m = m.clickable { env.onAction(action.first, action.second) }
    val scale = when (st["object-fit"]) {
        "contain" -> ContentScale.Fit
        "fill" -> ContentScale.FillBounds
        else -> if (hasH || ratio != null) ContentScale.Crop else ContentScale.FillWidth
    }
    val src = node.attrs["src"] ?: ""
    if (src.startsWith("http")) {
        AsyncImage(model = src, contentDescription = node.attrs["alt"], modifier = m, contentScale = scale)
    } else {
        Box(m.background(Color(0x22888888)), contentAlignment = Alignment.Center) { Text("🖼️") }
    }
}

// ------------------------------------------------------------------ entry point

private fun buildDoc(html: String): Pair<HNode, Pair<Color?, Brush?>> {
    val parsed = parseHtml(html)
    val root = parsed.first
    val rules = parseCss(parsed.second).sortedWith(compareBy<HRule>({ it.spec }, { it.order }))
    computeStyles(root, mapOf("font-size" to "16px", "color" to "#000000"), rules)
    val body = findTag(root, "body") ?: root
    val bg = bgOf(body.style)
    body.style = body.style.filterKeys { !it.startsWith("background") }
    return body to bg
}

@Composable
fun HtmlScreen(html: String, onAction: (String, String) -> Unit, onReload: () -> Unit) {
    val doc = remember(html) { buildDoc(html) }
    val body = doc.first
    val bgColor = doc.second.first
    val bgBrush = doc.second.second
    val env = HEnv(onAction)
    val pageBg = if (bgBrush != null) Modifier.fillMaxSize().background(bgBrush)
    else Modifier.fillMaxSize().background(bgColor ?: Color.White)
    Box(pageBg) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            HBox(body, env, HPk.BLOCK, Modifier)
            Spacer(Modifier.navigationBarsPadding().height(72.dp))
        }
        SmallFloatingActionButton(
            onClick = onReload,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)
        ) { Text("⟳") }
    }
}
