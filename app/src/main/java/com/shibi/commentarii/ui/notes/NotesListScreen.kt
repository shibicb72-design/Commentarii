package com.shibi.commentarii.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.theme.AppColors
import com.shibi.commentarii.ui.theme.ThemedPage

/** Notes list wired to [NotesViewModel]. [onNewNote] receives the id of the freshly created note. */
@Composable
fun NotesListScreen(
    container: AppContainer,
    onOpenNote: (Long) -> Unit,
    onNewNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: NotesViewModel = viewModel(factory = NotesViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val actions = remember(vm, onOpenNote, onNewNote) {
        NotesActions(
            onOpenNote = onOpenNote,
            onNewNote = { vm.createNote(onNewNote) },
            onSelectFolder = vm::selectFolder,
            onSelectTag = vm::selectTag,
        )
    }
    NotesListContent(state, actions, modifier)
}

@Composable
fun NotesListContent(state: NotesUiState, actions: NotesActions, modifier: Modifier = Modifier) {
    val colors = AppColors.current
    ThemedPage(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    stringResource(R.string.nt_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                )
                FilterRows(state, actions)
                val visible = state.visibleNotes()
                if (state.loaded && visible.isEmpty()) {
                    val filtered = state.selectedFolderId != null || state.selectedTagId != null
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (filtered) R.string.nt_empty_filtered else R.string.nt_empty),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visible, key = { it.id }) { note ->
                            NoteCard(note, onClick = { actions.onOpenNote(note.id) })
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = actions.onNewNote,
                containerColor = colors.tab,
                contentColor = colors.onTab,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.nt_new))
            }
        }
    }
}

@Composable
private fun FilterRows(state: NotesUiState, actions: NotesActions) {
    val colors = AppColors.current
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = colors.tab,
        selectedLabelColor = colors.onTab,
    )
    if (state.folders.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.selectedFolderId == null,
                    onClick = { actions.onSelectFolder(null) },
                    label = { Text(stringResource(R.string.nt_all)) },
                    colors = chipColors,
                )
            }
            items(state.folders, key = { it.id }) { folder ->
                FilterChip(
                    selected = state.selectedFolderId == folder.id,
                    onClick = { actions.onSelectFolder(if (state.selectedFolderId == folder.id) null else folder.id) },
                    label = { Text(folder.name) },
                    colors = chipColors,
                )
            }
        }
    }
    if (state.tags.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.tags, key = { it.id }) { tag ->
                FilterChip(
                    selected = state.selectedTagId == tag.id,
                    onClick = { actions.onSelectTag(if (state.selectedTagId == tag.id) null else tag.id) },
                    label = { Text("#${tag.name}") },
                    colors = chipColors,
                )
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteListItem, onClick: () -> Unit) {
    val colors = AppColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        contentColor = colors.onCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    note.title.ifBlank { stringResource(R.string.nt_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.snippet.isNotBlank()) {
                    Text(
                        note.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (note.tags.isNotEmpty()) {
                    Text(
                        note.tags.joinToString("  ") { "#${it.name}" },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (note.pinned) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = stringResource(R.string.nt_pinned),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
