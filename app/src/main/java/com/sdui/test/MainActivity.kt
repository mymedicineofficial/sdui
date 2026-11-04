package com.sdui.test

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { SduiApp() } }
    }
}

private sealed interface UiState {
    object Loading : UiState
    data class Error(val message: String) : UiState
    data class Ready(val screen: JSONObject) : UiState
    data class Html(val html: String) : UiState
}

// JSON me "html" key ho to wo inline HTML screen hai
private fun toState(j: JSONObject): UiState =
    if (j.has("html")) UiState.Html(j.optString("html")) else UiState.Ready(j)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SduiApp() {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf("page.html") }
    var reloadKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<UiState>(UiState.Loading) }
    val current = stack.last()

    LaunchedEffect(current, reloadKey) {
        val isHtml = current.endsWith(".html")
        val cachedState: UiState? =
            if (isHtml) ServerConfig.loadCachedText(context, "html:$current")?.let { UiState.Html(it) }
            else ServerConfig.loadCached(context, current)?.let { toState(it) }
        state = cachedState ?: UiState.Loading
        try {
            val fresh: UiState = withContext(Dispatchers.IO) {
                val bust = reloadKey > 0
                if (isHtml) {
                    val t = ServerConfig.fetchText(current, bust)
                    ServerConfig.saveCachedText(context, "html:$current", t)
                    UiState.Html(t)
                } else {
                    val j = ServerConfig.fetchScreen(current, bust)
                    // layout schema ho to "<screen>.mock.json" bhi merge karo
                    if (j.has("layout")) {
                        val extra = runCatching { ServerConfig.fetchScreen("$current.mock", bust) }.getOrNull()
                        if (extra != null) {
                            val ks = extra.keys()
                            while (ks.hasNext()) {
                                val k = ks.next()
                                j.put(k, extra.opt(k))
                            }
                        }
                    }
                    ServerConfig.saveCache(context, current, j)
                    toState(j)
                }
            }
            state = fresh
        } catch (e: Exception) {
            if (cachedState == null) state = UiState.Error(e.message ?: "Unknown error")
        }
    }

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    // ---- HTML screen (native HTML engine) ----
    val onHtmlAction: (String, String) -> Unit = { type, arg ->
        when (type) {
            "toast" -> Toast.makeText(context, arg, Toast.LENGTH_SHORT).show()
            "url" -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(arg))) }
            "dial" -> runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$arg"))) }
            "screen" -> stack.add(arg)
            "back" -> if (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
    }
    val cur = state
    if (cur is UiState.Html) {
        HtmlScreen(html = cur.html, onAction = onHtmlAction, onReload = { reloadKey++ })
        return
    }

    // ---- Naya engine (layout/templates schema) ----
    val ready = state as? UiState.Ready
    if (ready != null && ready.screen.has("layout")) {
        SduiScreen(root = ready.screen, onReload = { reloadKey++ })
        return
    }

    // ---- Purana simple schema (components list) ----
    val onAction: (JSONObject) -> Unit = { action ->
        when (action.optString("type")) {
            "toast" -> Toast.makeText(context, action.optString("message"), Toast.LENGTH_SHORT).show()
            "open_url" -> runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.optString("url"))))
            }
            "open_screen" -> stack.add(action.optString("screen"))
            "back" -> if (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
    }

    val screen = ready?.screen
    val title = screen?.optString("title").takeUnless { it.isNullOrBlank() } ?: current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = { TextButton(onClick = { reloadKey++ }) { Text("Refresh") } }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is UiState.Html -> { /* upar already render ho chuka */ }
                is UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is UiState.Error -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Load nahi hua:", style = MaterialTheme.typography.titleMedium)
                    Text(s.message)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { reloadKey++ }) { Text("Retry") }
                }
                is UiState.Ready -> {
                    val bg = runCatching {
                        Color(android.graphics.Color.parseColor(s.screen.optString("background")))
                    }.getOrDefault(Color.White)
                    val comps = s.screen.optJSONArray("components")
                    LazyColumn(
                        Modifier.fillMaxSize().background(bg),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val count = comps?.length() ?: 0
                        items(count) { i ->
                            comps?.optJSONObject(i)?.let { RenderNode(it, onAction) }
                        }
                    }
                }
            }
        }
    }
}
