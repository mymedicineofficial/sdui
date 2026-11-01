@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.sdui.test

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/*
 * SDUI ENGINE v2
 * Is schema ko render karta hai: theme/tokens, strings, templates(use/args), layout tree,
 * {{binding}} expressions, visibleIf, Repeat, Grid, Pager, actions (navigate/setState/dial).
 *
 * NOT interpreted (real app me native rahega): lifecycle, services, FCM, biometric lock,
 * firestore dataSources. Data test project me "mockData" se aata hai.
 */

internal typealias Scope = Map<String, Any?>

// ------------------------------------------------------------------ defaults

private val DefaultTheme: Map<String, Color> = mapOf(
    "colorBg" to Color(0xFF0B0F1A),
    "colorSurface" to Color(0xFF141B2D),
    "colorSurfaceLight" to Color(0xFF1B2438),
    "colorIconBg" to Color(0xFF1F2A44),
    "colorBorder" to Color(0xFF2A3550),
    "colorPrimary" to Color(0xFF3B82F6),
    "colorTextPrimary" to Color(0xFFF1F5F9),
    "colorTextSecondary" to Color(0xFF94A3B8),
    "colorRichRed" to Color(0xFFEF4444),
    "colorGreen" to Color(0xFF22C55E),
    "colorYellow" to Color(0xFFFACC15)
)

private val IconMap: Map<String, String> = mapOf(
    "ADD" to "＋", "NOTIFICATION" to "🔔", "LOCATION" to "📍", "ARROW_DOWN" to "▾",
    "SEARCH" to "🔍", "CALENDAR" to "📅", "PLAY" to "▶", "ABOUT" to "ℹ",
    "ALERT" to "⚠", "LOCK" to "🔒", "AI_MAGIC" to "✨", "CHAT" to "💬"
)

// ------------------------------------------------------------------ expression engine

private fun nz(v: Any?): Any? = if (v === JSONObject.NULL) null else v

private fun lookup(path: String, scope: Scope): Any? {
    val parts = path.split(".")
    var cur: Any? = scope[parts[0]]
    for (k in parts.drop(1)) {
        cur = when (cur) {
            is JSONObject -> nz(cur.opt(k))
            is Map<*, *> -> cur[k]
            else -> null
        }
        if (cur == null) return null
    }
    return nz(cur)
}

private fun truthy(v: Any?): Boolean = when (v) {
    null -> false
    is Boolean -> v
    is Number -> v.toDouble() != 0.0
    is String -> v.isNotEmpty()
    else -> true
}

private fun str(v: Any?): String = when (v) {
    null -> ""
    is Double -> if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
    else -> v.toString()
}

private fun num(v: Any?): Double = when (v) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull() ?: 0.0
    is Boolean -> if (v) 1.0 else 0.0
    else -> 0.0
}

private fun looseEq(a: Any?, b: Any?): Boolean {
    if (a == null || b == null) return a == b
    if (a is Number && b is Number) return a.toDouble() == b.toDouble()
    return a.toString() == b.toString()
}

private class ExprParser(private val s: String, private val scope: Scope) {
    private var i = 0

    fun parse(): Any? = ternary()

    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
    private fun peek(t: String): Boolean { ws(); return s.startsWith(t, i) }
    private fun eat(t: String): Boolean { if (peek(t)) { i += t.length; return true }; return false }

    private fun ternary(): Any? {
        val c = or()
        if (eat("?")) {
            val a = ternary()
            eat(":")
            val b = ternary()
            return if (truthy(c)) a else b
        }
        return c
    }

    private fun or(): Any? {
        var l = and()
        while (peek("||")) { eat("||"); val r = and(); l = truthy(l) || truthy(r) }
        return l
    }

    private fun and(): Any? {
        var l = eq()
        while (peek("&&")) { eat("&&"); val r = eq(); l = truthy(l) && truthy(r) }
        return l
    }

    private fun eq(): Any? {
        var l = rel()
        while (true) {
            if (peek("==")) { eat("=="); val r = rel(); l = looseEq(l, r) }
            else if (peek("!=")) { eat("!="); val r = rel(); l = !looseEq(l, r) }
            else break
        }
        return l
    }

    private fun rel(): Any? {
        var l = add()
        while (true) {
            if (peek(">=")) { eat(">="); l = num(l) >= num(add()) }
            else if (peek("<=")) { eat("<="); l = num(l) <= num(add()) }
            else if (peek(">")) { eat(">"); l = num(l) > num(add()) }
            else if (peek("<")) { eat("<"); l = num(l) < num(add()) }
            else break
        }
        return l
    }

    private fun add(): Any? {
        var l = unary()
        while (peek("+")) {
            eat("+")
            val r = unary()
            l = if (l is Number && r is Number) l.toDouble() + r.toDouble() else str(l) + str(r)
        }
        return l
    }

    private fun unary(): Any? {
        if (peek("!") && !peek("!=")) { eat("!"); return !truthy(unary()) }
        return primary()
    }

    private fun primary(): Any? {
        ws()
        if (i >= s.length) return null
        if (eat("(")) { val v = ternary(); eat(")"); return v }
        val c = s[i]
        if (c == '\'' || c == '"') {
            i++
            val sb = StringBuilder()
            while (i < s.length && s[i] != c) { sb.append(s[i]); i++ }
            i++
            return sb.toString()
        }
        if (c.isDigit()) {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(st, i).toDoubleOrNull() ?: 0.0
        }
        val st = i
        while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '.')) i++
        val id = s.substring(st, i)
        if (id.isEmpty()) { i++; return null }
        ws()
        if (i < s.length && s[i] == '(') {
            i++
            val args = mutableListOf<Any?>()
            ws()
            if (!eat(")")) {
                do { args.add(ternary()) } while (eat(","))
                eat(")")
            }
            return call(id, args)
        }
        return when (id) {
            "true" -> true
            "false" -> false
            "null" -> null
            else -> lookup(id, scope)
        }
    }

    private fun call(name: String, a: List<Any?>): Any? = when (name) {
        "coalesce" -> a.firstOrNull { it != null }
        "upper" -> str(a.firstOrNull()).uppercase()
        "lower" -> str(a.firstOrNull()).lowercase()
        "toString" -> str(a.firstOrNull())
        "now" -> System.currentTimeMillis().toDouble()
        "max" -> a.maxOfOrNull { num(it) }
        "min" -> a.minOfOrNull { num(it) }
        else -> null
    }
}

private val Interp = Regex("\\{\\{(.+?)\\}\\}")
private val StringRef = Regex("@string/([A-Za-z0-9_]+)")

private fun evalExpr(s: String, scope: Scope): Any? =
    try { ExprParser(s, scope).parse() } catch (e: Exception) { null }

private fun interpolate(raw: String, scope: Scope): String =
    if (!raw.contains("{{")) raw else Interp.replace(raw) { m -> str(evalExpr(m.groupValues[1], scope)) }

private fun evalTyped(raw: String, scope: Scope): Any? {
    val t = raw.trim()
    if (t.startsWith("{{") && t.endsWith("}}") && t.indexOf("{{", 2) < 0) {
        return evalExpr(t.substring(2, t.length - 2), scope)
    }
    return interpolate(raw, scope)
}

// ------------------------------------------------------------------ context

internal class SduiCtx(
    val root: JSONObject,
    val context: Context,
    val state: SnapshotStateMap<String, Any?>
) {
    private val templates = root.optJSONObject("templates") ?: JSONObject()
    private val tokens = root.optJSONObject("theme")?.optJSONObject("tokens") ?: JSONObject()
    private val strings = root.optJSONObject("strings") ?: JSONObject()
    private val stringValues = root.optJSONObject("stringValues") ?: JSONObject()
    private val themeValues = root.optJSONObject("themeValues") ?: JSONObject()
    val pagerStates = mutableMapOf<String, PagerState>()

    fun baseScope(): Scope {
        val out = mutableMapOf<String, Any?>()
        val mock = root.optJSONObject("mockData")
        if (mock != null) {
            val ks = mock.keys()
            while (ks.hasNext()) { val k = ks.next(); out[k] = nz(mock.opt(k)) }
        }
        val ds = root.optJSONObject("dataSources")
        if (ds != null) {
            val ks = ds.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                val d = ds.optJSONObject(k)
                if (d != null && d.optString("type") == "static") out[k] = d.opt("items")
            }
        }
        out["currentUser"] = JSONObject().put("uid", "test-user")
        out["state"] = state
        return out
    }

    fun text(raw: String, scope: Scope): String {
        val out = interpolate(raw, scope)
        return StringRef.replace(out) { m -> lookupString(m.groupValues[1]) }
    }

    private fun lookupString(name: String): String {
        val ref = strings.optString(name, "")
        val key = if (ref.contains("/")) ref.substringAfter("/") else name
        return stringValues.optString(key, stringValues.optString(name, key))
    }

    fun colorString(s0: String, depth: Int = 0): Color? {
        val s = s0.trim()
        if (s.isEmpty() || depth > 6) return null
        if (s == "transparent") return Color.Transparent
        if (s.startsWith("#")) return runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrNull()
        if (s.startsWith("@token/")) {
            val body = s.removePrefix("@token/")
            val name = body.substringBefore("@")
            val alpha = if (body.contains("@")) body.substringAfter("@").removeSuffix("%").toFloatOrNull()?.div(100f) else null
            val base = colorString(tokens.optString(name, ""), depth + 1) ?: return null
            return if (alpha != null) base.copy(alpha = alpha) else base
        }
        if (s.startsWith("@theme/")) {
            val name = s.removePrefix("@theme/")
            val ov = themeValues.optString(name, "")
            if (ov.isNotEmpty()) return colorString(ov, depth + 1)
            return DefaultTheme[name]
        }
        return null
    }

    fun color(v: Any?, scope: Scope): Color? {
        if (v == null || v === JSONObject.NULL) return null
        if (v is JSONObject) {
            if (v.has("switch")) {
                val key = str(evalExpr(v.optString("switch"), scope))
                val cases = v.optJSONObject("cases")
                val pick = if (cases != null && cases.has(key)) cases.optString(key) else v.optString("default")
                return color(pick, scope)
            }
            return color(v.opt("color"), scope)
        }
        if (v is String) {
            val s = if (v.contains("{{")) str(evalTyped(v, scope)) else v
            return colorString(s)
        }
        return null
    }

    fun visible(n: JSONObject, scope: Scope): Boolean {
        val v = n.opt("visibleIf") ?: return true
        return when (v) {
            is Boolean -> v
            is String -> truthy(if (v.contains("{{")) evalTyped(v, scope) else evalExpr(v, scope))
            else -> true
        }
    }

    fun list(name: String, scope: Scope): List<Any?> {
        val v = if (name.contains("{{")) evalTyped(name, scope) else lookup(name, scope)
        return when (v) {
            is JSONArray -> (0 until v.length()).map { nz(v.opt(it)) }
            is List<*> -> v
            else -> emptyList()
        }
    }

    private fun evalArg(v: Any?, scope: Scope): Any? {
        if (v is String && v.contains("{{")) return evalTyped(v, scope)
        return nz(v)
    }

    fun expandUse(n: JSONObject, scope: Scope): Pair<JSONObject, Scope> {
        val use = n.optString("use")
        if (use.isEmpty()) return n to scope
        val tpl = templates.optJSONObject(use) ?: return JSONObject() to scope
        val params = tpl.optJSONArray("params")
        val args = n.optJSONObject("args") ?: JSONObject()
        val newScope = scope.toMutableMap()
        if (params != null) {
            for (i in 0 until params.length()) {
                val p = params.getString(i)
                newScope[p] = evalArg(args.opt(p), scope)
            }
        }
        return (tpl.optJSONObject("node") ?: JSONObject()) to newScope
    }

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun perform(a: Any?, scope: Scope) {
        when (a) {
            is JSONArray -> for (i in 0 until a.length()) perform(a.opt(i), scope)
            is JSONObject -> when (a.optString("do")) {
                "navigate" -> {
                    val extras = a.optJSONObject("extras")
                    val ex = if (extras == null) "" else " " + interpolate(extras.toString(), scope)
                    toast("➡ navigate: " + a.optString("to") + ex)
                }
                "setState" -> {
                    val key = a.optString("key")
                    val v = a.opt("value")
                    state[key] = if (v is String) {
                        if (v.contains("{{")) evalTyped(v, scope) else evalExpr(v, scope)
                    } else nz(v)
                }
                "dial" -> runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + a.optString("number")))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                "openDrawer" -> toast("Side menu: real app me native rahega")
                else -> { /* animate etc. ignored */ }
            }
        }
    }
}

// ------------------------------------------------------------------ modifier helpers

private fun JSONObject.kids(): List<JSONObject> {
    val a = optJSONArray("children") ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}

private fun JSONObject.insetsOf(key: String): PaddingValues {
    val v = opt(key)
    return when (v) {
        is Number -> PaddingValues(v.toDouble().dp)
        is JSONObject -> PaddingValues(
            start = v.optDouble("start", 0.0).dp,
            top = v.optDouble("top", 0.0).dp,
            end = v.optDouble("end", 0.0).dp,
            bottom = v.optDouble("bottom", 0.0).dp
        )
        else -> PaddingValues(0.dp)
    }
}

private fun parentFills(n: JSONObject, parent: String): Boolean =
    parent == "Column" || parent == "Root" || parent == "Card" || parent == "CardFixed" ||
        (parent == "Frame" && !n.has("frameGravity"))

private fun JSONObject.base(extra: Modifier, fillWidth: Boolean): Modifier {
    var m = extra.padding(insetsOf("marginDp"))
    val ws = optString("width")
    val hs = optString("height")
    m = when {
        has("widthDp") -> m.width(optDouble("widthDp").dp)
        ws == "match" -> m.fillMaxWidth()
        ws == "wrap" -> m
        fillWidth -> m.fillMaxWidth()
        else -> m
    }
    m = when {
        has("heightDp") -> m.height(optDouble("heightDp").dp)
        hs == "match" -> m.fillMaxHeight()
        else -> m
    }
    return m
}

private fun Modifier.nodeBg(ctx: SduiCtx, scope: Scope, n: JSONObject): Modifier {
    val bg = n.opt("background") ?: return this
    if (bg is JSONObject) {
        val shape: Shape = if (bg.optString("shape") == "oval") CircleShape
        else RoundedCornerShape(bg.optDouble("radiusDp", 0.0).dp)
        val g = bg.optJSONObject("gradient")
        if (g != null) {
            val arr = g.optJSONArray("colors")
            val cols: List<Color> = if (arr == null) emptyList()
            else (0 until arr.length()).mapNotNull { ctx.color(arr.optString(it), scope) }
            return if (cols.size >= 2) this.background(Brush.linearGradient(cols), shape) else this
        }
        val c = ctx.color(bg, scope) ?: return this
        return this.background(c, shape)
    }
    val c = ctx.color(bg, scope) ?: return this
    return this.background(c)
}

private fun Modifier.nodeClick(ctx: SduiCtx, scope: Scope, n: JSONObject): Modifier {
    val raw = n.opt("onClick") ?: return this
    val action: Any? = if (raw is String) {
        if (raw.contains("{{")) evalTyped(raw, scope) else null
    } else raw
    if (action == null) return this
    return this.clickable { ctx.perform(action, scope) }
}

private fun alignOf(g: String): Alignment? {
    if (g.isEmpty()) return null
    val parts = g.split("|")
    val v = when { "top" in parts -> 0; "bottom" in parts -> 2; else -> 1 }
    val h = when { "start" in parts -> 0; "end" in parts -> 2; else -> 1 }
    return when {
        v == 0 && h == 0 -> Alignment.TopStart
        v == 0 && h == 1 -> Alignment.TopCenter
        v == 0 && h == 2 -> Alignment.TopEnd
        v == 1 && h == 0 -> Alignment.CenterStart
        v == 1 && h == 1 -> Alignment.Center
        v == 1 && h == 2 -> Alignment.CenterEnd
        v == 2 && h == 0 -> Alignment.BottomStart
        v == 2 && h == 1 -> Alignment.BottomCenter
        else -> Alignment.BottomEnd
    }
}

private fun fontWeightOf(n: JSONObject): FontWeight {
    var w = when (n.optString("font")) {
        "sans-serif-black" -> FontWeight.Black
        "sans-serif-bold" -> FontWeight.Bold
        "sans-serif-medium" -> FontWeight.Medium
        else -> FontWeight.Normal
    }
    if (n.optBoolean("bold", false) && w.weight < FontWeight.Bold.weight) w = FontWeight.Bold
    return w
}

// ------------------------------------------------------------------ entry point

@Composable
fun SduiScreen(root: JSONObject, onReload: () -> Unit) {
    val context = LocalContext.current
    val state = remember(root) {
        mutableStateMapOf<String, Any?>().also { m ->
            val s = root.optJSONObject("state")
            if (s != null) {
                val ks = s.keys()
                while (ks.hasNext()) { val k = ks.next(); m[k] = nz(s.opt(k)) }
            }
        }
    }
    val ctx = remember(root) { SduiCtx(root, context, state) }
    val scope = remember(ctx) { ctx.baseScope() }
    val layout = root.optJSONObject("layout") ?: return
    val bg = ctx.colorString("@token/bg") ?: Color.Black

    Box(Modifier.fillMaxSize().background(bg)) {
        RenderNode(layout, ctx, scope, "Root", Modifier.fillMaxSize())
        SmallFloatingActionButton(
            onClick = onReload,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)
        ) { Text("⟳") }
    }
}

// ------------------------------------------------------------------ renderer

@Composable
internal fun RenderNode(node: JSONObject, ctx: SduiCtx, scope0: Scope, parent: String, extra: Modifier) {
    val (n, scope) = ctx.expandUse(node, scope0)
    if (n.length() == 0) return
    if (!ctx.visible(n, scope)) return
    when (n.optString("type")) {
        "Drawer" -> n.optJSONObject("main")?.let { RenderNode(it, ctx, scope, parent, extra) }
        "CoordinatorLayout" -> CoordinatorNode(n, ctx, scope, extra)
        "AppBar" -> AppBarNode(n, ctx, scope, extra)
        "PullToRefresh" -> Box(extra.fillMaxWidth()) {
            n.optJSONObject("child")?.let { RenderNode(it, ctx, scope, "Column", Modifier.fillMaxSize()) }
        }
        "Scroll" -> Column(extra.verticalScroll(rememberScrollState())) {
            n.optJSONObject("child")?.let { RenderNode(it, ctx, scope, "Column", Modifier) }
        }
        "Column" -> ColumnNode(n, ctx, scope, parent, extra)
        "Row" -> RowNode(n, ctx, scope, parent, extra)
        "Frame" -> FrameNode(n, ctx, scope, parent, extra)
        "Card" -> CardNode(n, ctx, scope, parent, extra)
        "Text" -> TextNode(n, ctx, scope, parent, extra)
        "Icon" -> IconNode(n, ctx, scope, extra)
        "Image" -> ImageNode(n, ctx, scope, parent, extra)
        "Divider" -> Box(n.base(extra, parentFills(n, parent)).background(ctx.color(n.opt("color"), scope) ?: Color.Gray))
        "Grid" -> GridNode(n, ctx, scope, parent, extra)
        "Repeat" -> RepeatNode(n, ctx, scope, parent)
        "HorizontalScroll" -> Row(
            n.base(extra, parentFills(n, parent)).padding(n.insetsOf("paddingDp")).horizontalScroll(rememberScrollState())
        ) {
            for (c in n.kids()) RenderNode(c, ctx, scope, "Row", Modifier)
        }
        "Pager" -> PagerNode(n, ctx, scope, extra)
        "PagerIndicator" -> IndicatorNode(n, ctx, scope, extra)
        else -> { /* unknown type: skip */ }
    }
}

@Composable
private fun CoordinatorNode(n: JSONObject, ctx: SduiCtx, scope: Scope, extra: Modifier) {
    Column(n.base(extra, true).nodeBg(ctx, scope, n)) {
        for (c in n.kids()) {
            val refresh = c.optString("type") == "PullToRefresh"
            RenderNode(c, ctx, scope, "Column", if (refresh) Modifier.weight(1f) else Modifier)
        }
    }
}

@Composable
private fun AppBarNode(n: JSONObject, ctx: SduiCtx, scope: Scope, extra: Modifier) {
    Column(n.base(extra, true).nodeBg(ctx, scope, n).statusBarsPadding()) {
        for (c in n.kids()) RenderNode(c, ctx, scope, "Column", Modifier)
    }
}

@Composable
private fun ColumnNode(n: JSONObject, ctx: SduiCtx, scope: Scope, parent: String, extra: Modifier) {
    val mod = n.base(extra, parentFills(n, parent)).nodeBg(ctx, scope, n).nodeClick(ctx, scope, n)
        .padding(n.insetsOf("paddingDp"))
    val g = n.optString("gravity")
    Column(
        modifier = mod,
        verticalArrangement = if (g == "center") Arrangement.Center else Arrangement.Top,
        horizontalAlignment = if (g == "center") Alignment.CenterHorizontally else Alignment.Start
    ) {
        for (c in n.kids()) {
            val w = c.optDouble("weight", 0.0)
            RenderNode(c, ctx, scope, "Column", if (w > 0) Modifier.weight(w.toFloat()) else Modifier)
        }
    }
}

@Composable
private fun RowNode(n: JSONObject, ctx: SduiCtx, scope: Scope, parent: String, extra: Modifier) {
    val mod = n.base(extra, parentFills(n, parent)).nodeBg(ctx, scope, n).nodeClick(ctx, scope, n)
        .padding(n.insetsOf("paddingDp"))
    val g = n.optString("gravity")
    Row(
        modifier = mod,
        horizontalArrangement = if (g == "center") Arrangement.Center else Arrangement.Start,
        verticalAlignment = if (g.contains("center", ignoreCase = true)) Alignment.CenterVertically else Alignment.Top
    ) {
        for (c in n.kids()) {
            val w = c.optDouble("weight", 0.0)
            RenderNode(c, ctx, scope, "Row", if (w > 0) Modifier.weight(w.toFloat()) else Modifier)
        }
    }
}

@Composable
private fun FrameNode(n: JSONObject, ctx: SduiCtx, scope: Scope, parent: String, extra: Modifier) {
    var mod = n.base(extra, parentFills(n, parent)).nodeBg(ctx, scope, n).nodeClick(ctx, scope, n)
        .padding(n.insetsOf("paddingDp"))
    if (parent == "CardFixed" && !n.has("heightDp")) mod = mod.fillMaxHeight()
    Box(mod) {
        for (c in n.kids()) {
            val a = alignOf(c.optString("frameGravity"))
                ?: if (c.optString("gravity") == "center") Alignment.Center else null
            RenderNode(c, ctx, scope, "Frame", if (a != null) Modifier.align(a) else Modifier)
        }
    }
}

@Composable
private fun CardNode(n: JSONObject, ctx: SduiCtx, scope: Scope, parent: String, extra: Modifier) {
    val shape = RoundedCornerShape(n.optDouble("radiusDp", 0.0).dp)
    val bg = ctx.color(n.opt("background"), scope) ?: Color.Transparent
    val stroke = n.optDouble("strokeDp", 0.0)
    val strokeColor = ctx.color(n.opt("strokeColor"), scope) ?: Color.Transparent
    val fixedH = n.has("heightDp")
    val mod = n.base(extra, parentFills(n, parent))
    Surface(
        modifier = mod,
        shape = shape,
        color = bg,
        border = if (stroke > 0) BorderStroke(stroke.dp, strokeColor) else null,
        shadowElevation = n.optDouble("elevationDp", 0.0).dp
    ) {
        Box(
            modifier = Modifier.nodeClick(ctx, scope, n).then(if (fixedH) Modifier.fillMaxSize() else Modifier),
            contentAlignment = if (fixedH) Alignment.Center else Alignment.TopStart
        ) {
            for (c in n.kids()) {
                val a = alignOf(c.optString("frameGravity"))
                RenderNode(c, ctx, scope, if (fixedH) "CardFixed" else "Card", if (a != null) Modifier.align(a) else Modifier)
            }
        }
    }
}

@Composable
private fun TextNode(n: JSONObject, ctx: SduiCtx, scope: Scope, parent: String, extra: Modifier) {
    var txt = ctx.text(n.optString("text"), scope)
    if (txt.isEmpty()) txt = ctx.text(n.optString("placeholder"), scope)
    val align = n.optString("align")
    val centerish = align == "center" || align == "end"
    val box = n.base(extra, parentFills(n, parent) && centerish).nodeBg(ctx, scope, n).nodeClick(ctx, scope, n)
        .alpha(n.optDouble("alpha", 1.0).toFloat())
    val size = n.optDouble("sizeSp", 14.0)
    val lh = n.optDouble("lineSpacingMultiplier", 0.0)
    Box(
        modifier = box,
        contentAlignment = if (n.optString("gravity") == "center") Alignment.Center else Alignment.TopStart
    ) {
        Text(
            text = txt,
            modifier = (if (centerish) Modifier.fillMaxWidth() else Modifier).padding(n.insetsOf("paddingDp")),
            color = ctx.color(n.opt("color"), scope) ?: ctx.colorString("@token/textPrimary") ?: Color.White,
            fontSize = size.sp,
            fontWeight = fontWeightOf(n),
            textAlign = when (align) {
                "center" -> TextAlign.Center
                "end" -> TextAlign.End
                else -> TextAlign.Start
            },
            maxLines = n.optInt("maxLines", Int.MAX_VALUE),
            overflow = if (n.optString("ellipsize") == "end") TextOverflow.Ellipsis else TextOverflow.Clip,
            lineHeight = if (lh > 0) (size * lh).sp else TextUnit.Unspecified
        )
    }
}

@Composable
private fun IconNode(n: JSONObject, ctx: SduiCtx, scope: Scope, extra: Modifier) {
    val glyph = IconMap[ctx.text(n.optString("icon"), scope)] ?: "•"
    val size = n.optDouble("sizeDp", 24.0)
    Box(
        modifier = extra.padding(n.insetsOf("marginDp")).size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            color = ctx.color(n.opt("tint"), scope) ?: Color.White,
            fontSize = (size * 0.75).sp
        )
    }
}

@Composable
private fun Placeholder(ctx: SduiCtx, mod: Modifier) {
    Box(
        modifier = mod.background(ctx.colorString("@token/surfaceLight") ?: Color.DarkGray),
        contentAlignment = Alignment.Center
    ) { Text("🖼️", fontSize = 20.sp) }
}

@Composable
private fun ImageNode(n: JSONObject, ctx: SduiCtx, scope: Scope, parent: String, extra: Modifier) {
    val appCtx = LocalContext.current
    var src = ctx.text(n.optString("src"), scope).trim()
    if (src.isEmpty()) src = n.optString("fallback")
    val hasDims = n.has("widthDp") || n.has("heightDp") || n.optString("width") == "match" || n.optString("height") == "match"
    var mod = n.base(extra, false)
    if (!hasDims && (parent == "Card" || parent == "CardFixed" || parent == "Frame")) mod = mod.fillMaxSize()
    if (n.optBoolean("circleCrop", false)) mod = mod.clip(CircleShape)
    val scale = if (n.optString("scaleType") == "fitCenter") ContentScale.Fit else ContentScale.Crop
    when {
        src.startsWith("http") -> AsyncImage(model = src, contentDescription = null, modifier = mod, contentScale = scale)
        src.startsWith("@drawable/") -> {
            val id = appCtx.resources.getIdentifier(src.removePrefix("@drawable/"), "drawable", appCtx.packageName)
            if (id != 0) Image(painter = painterResource(id), contentDescription = null, modifier = mod, contentScale = scale)
            else Placeholder(ctx, mod)
        }
        else -> Placeholder(ctx, mod)
    }
}

@Composable
private fun GridNode(n: JSONObject, ctx: SduiCtx, scope: Scope, parent: String, extra: Modifier) {
    val cols = n.optInt("columns", 2)
    val items = n.kids().map { ctx.expandUse(it, scope) }
    Column(n.base(extra, parentFills(n, parent))) {
        for (rowItems in items.chunked(cols)) {
            Row(Modifier.fillMaxWidth()) {
                for ((node, sc) in rowItems) {
                    val g = node.optJSONObject("grid")
                    val patched = if (g != null) {
                        JSONObject(node.toString()).apply {
                            put("heightDp", g.opt("heightDp"))
                            put("marginDp", g.opt("marginDp"))
                        }
                    } else node
                    RenderNode(patched, ctx, sc, "Row", Modifier.weight(1f))
                }
                repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RepeatNode(n: JSONObject, ctx: SduiCtx, scope: Scope, parent: String) {
    val items = ctx.list(n.optString("source"), scope)
    val name = n.optString("as")
    val item = n.optJSONObject("item")
    if (items.isEmpty()) {
        n.optJSONObject("empty")?.let { RenderNode(it, ctx, scope, parent, Modifier) }
    } else if (item != null) {
        for (x in items) RenderNode(item, ctx, scope + (name to x), parent, Modifier)
    }
}

@Composable
private fun PagerNode(n: JSONObject, ctx: SduiCtx, scope: Scope, extra: Modifier) {
    val items = ctx.list(n.optString("source"), scope)
    val h = n.optDouble("heightDp", 180.0).dp
    val mod = extra.fillMaxWidth().height(h)
    if (items.isEmpty()) {
        Placeholder(ctx, mod.clip(RoundedCornerShape(24.dp)))
        return
    }
    val st = rememberPagerState(pageCount = { items.size })
    val id = n.optString("id")
    if (id.isNotEmpty()) ctx.pagerStates[id] = st
    if (n.optBoolean("autoScroll", false) && items.size > 1) {
        LaunchedEffect(items.size) {
            while (true) {
                delay(3000)
                st.animateScrollToPage((st.currentPage + 1) % items.size)
            }
        }
    }
    HorizontalPager(state = st, modifier = mod, pageSpacing = 12.dp) { page ->
        val b = items[page] as? JSONObject
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(24.dp),
            color = ctx.colorString("@token/surface") ?: Color.DarkGray
        ) {
            Box(Modifier.fillMaxSize()) {
                val url = b?.optString("imageUrl").orEmpty()
                if (url.startsWith("http")) {
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else Placeholder(ctx, Modifier.fillMaxSize())
                val t = b?.optString("title").orEmpty()
                if (t.isNotEmpty()) {
                    Text(
                        t, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomStart)
                            .background(Color(0x99000000)).fillMaxWidth().padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IndicatorNode(n: JSONObject, ctx: SduiCtx, scope: Scope, extra: Modifier) {
    val st = ctx.pagerStates[n.optString("for")] ?: return
    val primary = ctx.colorString("@token/primary") ?: Color.Blue
    val border = ctx.colorString("@token/border") ?: Color.Gray
    Row(
        n.base(extra, true).padding(n.insetsOf("paddingDp")),
        horizontalArrangement = Arrangement.Center
    ) {
        for (i in 0 until st.pageCount) {
            val sel = st.currentPage == i
            Box(
                Modifier.padding(horizontal = 3.dp).height(8.dp).width(if (sel) 22.dp else 8.dp)
                    .clip(CircleShape).background(if (sel) primary else border)
            )
        }
    }
}
