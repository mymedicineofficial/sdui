package com.sdui.test

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Server ka JSON -> Compose UI.
 * Supported types: text, button, image, card, row, column, spacer
 * Unknown type aaye to ignore (purane app version crash na ho).
 *
 * Common props: padding, margin, bg, radius, fillWidth, action
 * Action: {"type":"toast","message":".."} | {"type":"open_url","url":".."} | {"type":"open_screen","screen":".."}
 */
@Composable
fun RenderNode(node: JSONObject, onAction: (JSONObject) -> Unit) {
    when (node.optString("type")) {
        "text" -> TextNode(node, onAction)
        "button" -> ButtonNode(node, onAction)
        "image" -> ImageNode(node, onAction)
        "card" -> CardNode(node, onAction)
        "row" -> RowNode(node, onAction)
        "column" -> ColumnNode(node, onAction)
        "spacer" -> Spacer(Modifier.height(node.optInt("height", 8).dp))
        else -> { /* unknown component: skip */ }
    }
}

// ---------- helpers ----------

private fun String?.toColorOrNull(): Color? {
    if (this.isNullOrBlank()) return null
    return try { Color(android.graphics.Color.parseColor(this)) } catch (e: Exception) { null }
}

private fun JSONObject.children(): List<JSONObject> {
    val arr: JSONArray = optJSONArray("children") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
}

private fun JSONObject.baseModifier(onAction: (JSONObject) -> Unit): Modifier {
    var m: Modifier = Modifier
    if (optBoolean("fillWidth", false)) m = m.fillMaxWidth()
    m = m.padding(optInt("margin", 0).dp)
    val radius = RoundedCornerShape(optInt("radius", 0).dp)
    optString("bg").toColorOrNull()?.let { m = m.background(it, radius) }
    optJSONObject("action")?.let { a -> m = m.clip(radius).clickable { onAction(a) } }
    m = m.padding(optInt("padding", 0).dp)
    return m
}

// ---------- components ----------

@Composable
private fun TextNode(n: JSONObject, onAction: (JSONObject) -> Unit) {
    Text(
        text = n.optString("text"),
        modifier = n.baseModifier(onAction),
        color = n.optString("color").toColorOrNull() ?: Color.Unspecified,
        fontSize = n.optInt("size", 16).sp,
        fontWeight = if (n.optBoolean("bold", false)) FontWeight.Bold else FontWeight.Normal,
        textAlign = when (n.optString("align")) {
            "center" -> TextAlign.Center
            "end" -> TextAlign.End
            else -> TextAlign.Start
        }
    )
}

@Composable
private fun ButtonNode(n: JSONObject, onAction: (JSONObject) -> Unit) {
    val bg = n.optString("bg").toColorOrNull()
    Button(
        onClick = { n.optJSONObject("action")?.let(onAction) },
        modifier = Modifier
            .then(if (n.optBoolean("fillWidth", false)) Modifier.fillMaxWidth() else Modifier)
            .padding(n.optInt("margin", 0).dp),
        colors = if (bg != null) ButtonDefaults.buttonColors(containerColor = bg) else ButtonDefaults.buttonColors()
    ) {
        Text(n.optString("text"))
    }
}

@Composable
private fun ImageNode(n: JSONObject, onAction: (JSONObject) -> Unit) {
    AsyncImage(
        model = n.optString("url"),
        contentDescription = n.optString("description", ""),
        contentScale = ContentScale.Crop,
        modifier = n.baseModifier(onAction)
            .fillMaxWidth()
            .height(n.optInt("height", 160).dp)
            .clip(RoundedCornerShape(n.optInt("radius", 0).dp))
    )
}

@Composable
private fun CardNode(n: JSONObject, onAction: (JSONObject) -> Unit) {
    Card(
        modifier = Modifier
            .then(if (n.optBoolean("fillWidth", true)) Modifier.fillMaxWidth() else Modifier)
            .padding(n.optInt("margin", 0).dp)
            .then(n.optJSONObject("action")?.let { a -> Modifier.clickable { onAction(a) } } ?: Modifier),
        shape = RoundedCornerShape(n.optInt("radius", 12).dp),
        colors = CardDefaults.cardColors(
            containerColor = n.optString("bg").toColorOrNull() ?: Color(0xFFF2F2F7)
        )
    ) {
        Column(
            Modifier.padding(n.optInt("padding", 12).dp),
            verticalArrangement = Arrangement.spacedBy(n.optInt("spacing", 0).dp)
        ) {
            n.children().forEach { RenderNode(it, onAction) }
        }
    }
}

@Composable
private fun RowNode(n: JSONObject, onAction: (JSONObject) -> Unit) {
    Row(
        modifier = n.baseModifier(onAction),
        horizontalArrangement = Arrangement.spacedBy(n.optInt("spacing", 0).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        n.children().forEach { RenderNode(it, onAction) }
    }
}

@Composable
private fun ColumnNode(n: JSONObject, onAction: (JSONObject) -> Unit) {
    Column(
        modifier = n.baseModifier(onAction),
        verticalArrangement = Arrangement.spacedBy(n.optInt("spacing", 0).dp)
    ) {
        n.children().forEach { RenderNode(it, onAction) }
    }
}
