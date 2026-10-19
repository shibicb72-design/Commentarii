package com.shibi.commentarii.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.R
import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.ui.theme.AppColors

/** Note editor wired to [NoteEditorViewModel]; edits autosave (500 ms debounce) and flush when leaving. */
@Composable
fun NoteEditorScreen(
    container: AppContainer,
    noteId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: NoteEditorViewModel = viewModel(
        key = "note-$noteId",
        factory = NoteEditorViewModel.factory(container, noteId),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushNow() }
    val actions = remember(vm, onBack) {
        NoteEditorActions(
            onBack = {
                vm.flushNow()
                onBack()
            },
            onTitle = vm::onTitle,
            onBlockText = vm::onBlockText,
            onSelection = vm::onSelection,
            onFocusHandled = vm::onFocusHandled,
            onToggleChecked = vm::onToggleChecked,
            onBackspaceAtStart = vm::onBackspaceAtStart,
            onBold = vm::onBold,
            onSetType = vm::onSetType,
            onTogglePin = vm::onTogglePin,
            onSetFolder = vm::onSetFolder,
            onCreateFolder = vm::onCreateFolder,
            onToggleTag = vm::onToggleTag,
            onCreateTag = vm::onCreateTag,
            onDelete = { vm.delete(onBack) },
        )
    }
    NoteEditorContent(state, actions, modifier)
}

@Composable
fun NoteEditorContent(state: NoteEditorUiState, actions: NoteEditorActions, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        if (state.notFound) {
            Column(Modifier.fillMaxSize()) {
                TopBar(state, actions, showActions = false)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.ed_not_found), style = MaterialTheme.typography.bodyLarge)
                }
            }
            return@Surface
        }
        var showOrganize by rememberSaveable { mutableStateOf(false) }
        var showDelete by rememberSaveable { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            TopBar(
                state,
                actions,
                showActions = true,
                onOrganize = { showOrganize = true },
                onDelete = { showDelete = true },
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                TitleField(state.title, actions.onTitle)
                OrganizeSummary(state, onClick = { showOrganize = true })
                if (state.loaded) {
                    state.blocks.forEach { item ->
                        key(item.key) {
                            BlockRow(item, state.focusRequest, showHint = state.blocks.size == 1, actions = actions)
                        }
                    }
                }
                Box(Modifier.padding(bottom = 24.dp))
            }
            FormatToolbar(actions)
        }
        if (showOrganize) {
            OrganizeDialog(state, actions, onDismiss = { showOrganize = false })
        }
        if (showDelete) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text(stringResource(R.string.ed_delete_title)) },
                text = { Text(stringResource(R.string.ed_delete_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDelete = false
                        actions.onDelete()
                    }) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}

@Composable
private fun TopBar(
    state: NoteEditorUiState,
    actions: NoteEditorActions,
    showActions: Boolean,
    onOrganize: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ed_back))
        }
        Box(Modifier.weight(1f))
        if (showActions) {
            IconButton(onClick = actions.onTogglePin) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = stringResource(if (state.pinned) R.string.ed_unpin else R.string.ed_pin),
                    tint = if (state.pinned) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
            IconButton(onClick = onOrganize) {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = stringResource(R.string.ed_organize))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.ed_delete))
            }
        }
    }
}

@Composable
private fun TitleField(title: String, onTitle: (String) -> Unit) {
    val content = LocalContentColor.current
    val style = MaterialTheme.typography.headlineMedium.copy(color = content)
    BasicTextField(
        value = title,
        onValueChange = onTitle,
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(content),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        decorationBox = { inner ->
            Box {
                if (title.isEmpty()) {
                    Text(stringResource(R.string.ed_title_hint), style = style.copy(color = content.copy(alpha = 0.5f)))
                }
                inner()
            }
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrganizeSummary(state: NoteEditorUiState, onClick: () -> Unit) {
    val folder = state.folders.firstOrNull { it.id == state.folderId }
    val tags = state.allTags.filter { it.id in state.tagIds }
    FlowRow(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (folder != null) {
            AssistChip(onClick = onClick, label = { Text(folder.name) })
        }
        tags.forEach { tag -> AssistChip(onClick = onClick, label = { Text("#${tag.name}") }) }
        if (folder == null && tags.isEmpty()) {
            AssistChip(onClick = onClick, label = { Text(stringResource(R.string.ed_add_folder_tag)) })
        }
    }
}

@Composable
private fun BlockRow(
    item: EditorBlock,
    focusRequest: FocusRequest?,
    showHint: Boolean,
    actions: NoteEditorActions,
) {
    val b = item.block
    val content = LocalContentColor.current
    val base = MaterialTheme.typography.bodyLarge
    val style: TextStyle = when (b.type) {
        BlockType.H1 -> base.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
        BlockType.H2 -> base.copy(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
        else -> base
    }.copy(
        color = if (b.type == BlockType.CHECKBOX && b.checked) content.copy(alpha = 0.6f) else content,
        textDecoration = if (b.type == BlockType.CHECKBOX && b.checked) TextDecoration.LineThrough else TextDecoration.None,
    )

    var local by remember(item.key) { mutableStateOf(TextFieldValue(b.text, TextRange(b.text.length))) }
    // The ViewModel text wins (split / merge / type change); the local value only carries selection and IME state.
    val value = if (local.text == b.text) local else TextFieldValue(b.text, TextRange(b.text.length))
    val requester = remember { FocusRequester() }

    LaunchedEffect(focusRequest) {
        if (focusRequest != null && focusRequest.key == item.key) {
            local = TextFieldValue(b.text, TextRange(focusRequest.cursor.coerceIn(0, b.text.length)))
            runCatching { requester.requestFocus() }
            actions.onSelection(item.key, local.selection.start, local.selection.end)
            actions.onFocusHandled()
        }
    }

    val transformation = remember(b.spans) {
        VisualTransformation { text ->
            val len = text.length
            val styles = b.spans.filter { it.bold }.mapNotNull { sp ->
                val s = sp.start.coerceIn(0, len)
                val e = sp.end.coerceIn(0, len)
                if (e > s) AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), s, e) else null
            }
            TransformedText(AnnotatedString(text.text, spanStyles = styles), OffsetMapping.Identity)
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        when (b.type) {
            BlockType.BULLET -> Text("•", style = style, modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 6.dp))
            BlockType.CHECKBOX -> Checkbox(
                checked = b.checked,
                onCheckedChange = { actions.onToggleChecked(item.key) },
                modifier = Modifier.padding(end = 2.dp),
            )
            else -> Box(Modifier.width(0.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = { v ->
                local = v
                actions.onSelection(item.key, v.selection.start, v.selection.end)
                if (v.text != b.text) actions.onBlockText(item.key, v.text)
            },
            textStyle = style,
            cursorBrush = SolidColor(content),
            visualTransformation = transformation,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
                .focusRequester(requester)
                .onFocusChanged { if (it.isFocused) actions.onSelection(item.key, value.selection.start, value.selection.end) }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace &&
                        value.selection.collapsed && value.selection.start == 0
                    ) {
                        actions.onBackspaceAtStart(item.key)
                        true
                    } else {
                        false
                    }
                },
            decorationBox = { inner ->
                Box {
                    if (showHint && b.text.isEmpty()) {
                        Text(stringResource(R.string.ed_body_hint), style = style.copy(color = content.copy(alpha = 0.5f)))
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun FormatToolbar(actions: NoteEditorActions) {
    val colors = AppColors.current
    Surface(color = colors.tab, contentColor = colors.onTab, modifier = Modifier.fillMaxWidth().imePadding()) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.onBold) {
                Icon(Icons.Filled.FormatBold, contentDescription = stringResource(R.string.ed_bold))
            }
            TextButton(onClick = { actions.onSetType(BlockType.H1) }) {
                Text("H1", color = colors.onTab, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { actions.onSetType(BlockType.H2) }) {
                Text("H2", color = colors.onTab, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { actions.onSetType(BlockType.BULLET) }) {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = stringResource(R.string.ed_bullet))
            }
            IconButton(onClick = { actions.onSetType(BlockType.CHECKBOX) }) {
                Icon(Icons.Filled.CheckBox, contentDescription = stringResource(R.string.ed_checkbox))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrganizeDialog(state: NoteEditorUiState, actions: NoteEditorActions, onDismiss: () -> Unit) {
    val colors = AppColors.current
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = colors.tab,
        selectedLabelColor = colors.onTab,
    )
    var newFolder by remember { mutableStateOf("") }
    var newTag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ed_organize)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ed_folder), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.folderId == null,
                        onClick = { actions.onSetFolder(null) },
                        label = { Text(stringResource(R.string.ed_no_folder)) },
                        colors = chipColors,
                    )
                    state.folders.forEach { f ->
                        FilterChip(
                            selected = state.folderId == f.id,
                            onClick = { actions.onSetFolder(f.id) },
                            label = { Text(f.name) },
                            colors = chipColors,
                        )
                    }
                }
                AddRow(newFolder, { newFolder = it }, stringResource(R.string.ed_new_folder)) {
                    actions.onCreateFolder(newFolder)
                    newFolder = ""
                }
                Text(stringResource(R.string.ed_tags), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.allTags.forEach { t ->
                        FilterChip(
                            selected = t.id in state.tagIds,
                            onClick = { actions.onToggleTag(t.id) },
                            label = { Text("#${t.name}") },
                            colors = chipColors,
                        )
                    }
                }
                AddRow(newTag, { newTag = it }, stringResource(R.string.ed_new_tag)) {
                    actions.onCreateTag(newTag)
                    newTag = ""
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

@Composable
private fun AddRow(value: String, onValue: (String) -> Unit, label: String, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAdd, enabled = value.isNotBlank()) { Text(stringResource(R.string.action_add)) }
    }
}
