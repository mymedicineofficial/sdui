package com.sdui.test

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SduiApp() } }
    }
}

private sealed interface UiState {
    object Loading : UiState
    data class Error(val message: String) : UiState
    data class Ready(val screen: JSONObject) : UiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SduiApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val stack = remember { mutableStateListOf("home") }
    var reloadKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<UiState>(UiState.Loading) }
    val current = stack.last()

    LaunchedEffect(current, reloadKey) {
        state = UiState.Loading
        state = try {
            val json = withContext(Dispatchers.IO) { ServerConfig.fetchScreen(current) }
            UiState.Ready(json)
        } catch (e: Exception) {
            UiState.Error(e.message ?: "Unknown error")
        }
    }

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

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

    val screen = (state as? UiState.Ready)?.screen
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
