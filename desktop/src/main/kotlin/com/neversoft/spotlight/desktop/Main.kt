package com.neversoft.spotlight.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.neversoft.spotlight.desktop.model.FilterSelection
import com.neversoft.spotlight.desktop.model.PrimaryFilter
import com.neversoft.spotlight.desktop.model.SearchResult
import com.neversoft.spotlight.desktop.search.FileIndexProvider
import com.neversoft.spotlight.desktop.search.NotepadTabsProvider
import com.neversoft.spotlight.desktop.search.SearchEngine
import com.neversoft.spotlight.desktop.search.StartMenuAppsProvider
import com.neversoft.spotlight.desktop.search.StickyNotesProvider
import com.neversoft.spotlight.desktop.search.TextNotesProvider
import com.neversoft.spotlight.desktop.win.ResultLauncher
import com.neversoft.spotlight.desktop.win.WindowsPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NeverSoftRed = Color(0xFFE53935)
private val ChipBackground = Color(0xFF1E1E1E)
private val SubtleText = Color(0xFF9E9E9E)

private val SpotlightColors = darkColors(
    primary = NeverSoftRed,
    background = Color.Black,
    surface = Color(0xFF121212),
    onBackground = Color.White,
    onSurface = Color.White,
)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 820.dp, height = 680.dp),
        title = "Spotlight",
    ) {
        MaterialTheme(colors = SpotlightColors) {
            SpotlightApp()
        }
    }
}

@Composable
private fun SpotlightApp() {
    val paths = remember { WindowsPaths() }
    val fileIndex = remember { FileIndexProvider(paths.indexRoots(), paths.downloadsDir()) }
    val apps = remember { StartMenuAppsProvider(paths.startMenuRoots()) }
    val sticky = remember { StickyNotesProvider(paths.stickyNotesDb()) }
    val notepad = remember { NotepadTabsProvider(paths.notepadTabStateDir()) }
    val textNotes = remember { TextNotesProvider(paths.noteRoots()) }
    val engine = remember { SearchEngine(apps, fileIndex, sticky, notepad, textNotes) }

    var query by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(FilterSelection()) }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var indexStatus by remember { mutableStateOf(FileIndexProvider.IndexStatus()) }
    var indexVersion by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Build the file index once in the background; results refresh when done.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { fileIndex.rebuildIndex() }
        indexStatus = fileIndex.status
        indexVersion++
    }

    // Each keystroke / filter change cancels the previous search — and an empty
    // query browses everything, so results populate before the user types.
    LaunchedEffect(query, selection, indexVersion) {
        delay(120)
        results = engine.search(query, selection)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun refreshEverything() {
        scope.launch {
            withContext(Dispatchers.IO) {
                apps.refresh()
                sticky.refresh()
                notepad.refresh()
                textNotes.refresh()
                fileIndex.rebuildIndex()
            }
            indexStatus = fileIndex.status
            indexVersion++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        HeaderRow(onRefresh = ::refreshEverything)

        SearchBar(
            query = query,
            onQueryChange = { query = it },
            focusRequester = focusRequester,
            onEnter = { results.firstOrNull()?.let { ResultLauncher.launch(it.launch) } },
            onEscape = { query = "" },
        )

        FilterChips(selection = selection, onSelectionChange = { selection = it })

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(results, key = { it.id }) { result ->
                ResultRow(result) { ResultLauncher.launch(result.launch) }
            }
        }

        StatusFooter(indexStatus, results.size)
    }
}

@Composable
private fun HeaderRow(onRefresh: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("SPOTLIGHT", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(
            "⟳",
            color = SubtleText,
            fontSize = 16.sp,
            modifier = Modifier.clickable(onClick = onRefresh).padding(horizontal = 8.dp),
        )
        Text("NeverSoft Services", color = NeverSoftRed, fontSize = 11.sp)
    }
    Spacer(Modifier.size(10.dp))
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onEnter: () -> Unit,
    onEscape: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF181818),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("🔍", fontSize = 15.sp)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search your entire PC", color = SubtleText, fontSize = 15.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(NeverSoftRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            when {
                                event.type != KeyEventType.KeyDown -> false
                                event.key == Key.Enter -> {
                                    onEnter()
                                    true
                                }
                                event.key == Key.Escape -> {
                                    onEscape()
                                    true
                                }
                                else -> false
                            }
                        },
                )
            }
        }
    }
    Spacer(Modifier.size(12.dp))
}

@Composable
private fun FilterChips(selection: FilterSelection, onSelectionChange: (FilterSelection) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryFilter.entries.forEach { filter ->
            Chip(
                label = filter.label,
                selected = selection.primary == filter,
                onClick = { onSelectionChange(selection.withPrimary(filter)) },
            )
        }
    }
    val subFilters = selection.primary.subFilters()
    if (subFilters.isNotEmpty()) {
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subFilters.forEach { sub ->
                Chip(
                    label = sub.label,
                    selected = selection.sub == sub,
                    onClick = { onSelectionChange(selection.withSub(sub)) },
                )
            }
        }
    }
    Spacer(Modifier.size(12.dp))
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) NeverSoftRed else ChipBackground,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Text(result.type.glyph, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(result.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
            Text(result.subtitle, color = SubtleText, fontSize = 11.sp, maxLines = 1)
            result.snippet?.let {
                Text(it, color = SubtleText, fontSize = 11.sp, fontStyle = FontStyle.Italic, maxLines = 2)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(result.type.label, color = SubtleText, fontSize = 10.sp)
    }
}

@Composable
private fun StatusFooter(status: FileIndexProvider.IndexStatus, resultCount: Int) {
    val text = when {
        !status.ready -> "Indexing your files…"
        status.truncated -> "$resultCount results · indexed ${status.indexed} entries (capped — refine with filters)"
        else -> "$resultCount results · indexed ${status.indexed} entries"
    }
    Text(text, color = SubtleText, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
}
