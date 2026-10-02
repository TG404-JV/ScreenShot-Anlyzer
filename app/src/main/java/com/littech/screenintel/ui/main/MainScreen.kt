package com.littech.screenintel.ui.main

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.foundation.horizontalScroll
import com.littech.screenintel.Cleanup
import com.littech.screenintel.R
import com.littech.screenintel.Viewer
import com.littech.screenintel.ui.main.components.CategoryChips
import com.littech.screenintel.ui.main.components.ScreenshotCard
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat

private fun hasImagePermission(context: Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        }
        else -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}

private fun getImagePermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.POST_NOTIFICATIONS
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.POST_NOTIFICATIONS
        )
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(factory = MainScreenViewModel.Factory)
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val categoryCounts by viewModel.categoryCounts.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val indexingProgress by viewModel.indexingProgress.collectAsStateWithLifecycle()

    // Filter states
    val dateRangeFilter by viewModel.dateRangeFilter.collectAsStateWithLifecycle()
    val fileSizeFilter by viewModel.fileSizeFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val fileNameQuery by viewModel.fileNameQuery.collectAsStateWithLifecycle()
    val onlyActionable by viewModel.onlyActionable.collectAsStateWithLifecycle()
    val minTextLength by viewModel.minTextLength.collectAsStateWithLifecycle()

    // Multi-select state
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()

    var showFilterBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Storage permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val isGranted = permissionsMap.values.any { it } || hasImagePermission(context)
        if (isGranted) {
            viewModel.scanScreenshots()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val lastDeleted by viewModel.lastDeletedScreenshot.collectAsStateWithLifecycle()

    LaunchedEffect(lastDeleted) {
        val deleted = lastDeleted
        if (deleted != null) {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.msg_screenshot_deleted),
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreScreenshot(deleted)
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.msg_screenshot_restored),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            viewModel.clearLastDeletedScreenshot()
        }
    }

    val scanMsg by viewModel.scanMessage.collectAsStateWithLifecycle()
    LaunchedEffect(scanMsg) {
        val msg = scanMsg
        if (msg != null) {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearScanMessage()
        }
    }

    val triggerScan = {
        if (hasImagePermission(context)) {
            viewModel.scanScreenshots()
        } else {
            permissionLauncher.launch(getImagePermissions())
        }
    }

    // Auto-scan on launch if permission is already granted and no items indexed yet
    LaunchedEffect(Unit) {
        if (hasImagePermission(context) && totalCount == 0 && !indexingProgress.isIndexing) {
            viewModel.scanScreenshots()
        }
    }

    // ── Filter / Sort BottomSheet ─────────────────────────────────────────────
    if (showFilterBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterBottomSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.title_filter_and_sort),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { viewModel.resetFilters() }) {
                        Text(stringResource(R.string.action_reset))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sort Order
                Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortOrder.entries.forEach { order ->
                        val label = when (order) {
                            SortOrder.NEWEST -> stringResource(R.string.filter_sort_newest)
                            SortOrder.OLDEST -> stringResource(R.string.filter_sort_oldest)
                            SortOrder.RELEVANCE -> stringResource(R.string.filter_sort_relevance)
                            SortOrder.LARGEST -> stringResource(R.string.filter_sort_largest)
                            SortOrder.SMALLEST -> stringResource(R.string.filter_sort_smallest)
                            SortOrder.NAME_ASC -> stringResource(R.string.filter_sort_name)
                        }
                        FilterChip(
                            selected = sortOrder == order,
                            onClick = { viewModel.onSortOrderChanged(order) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date Range Section
                Text(stringResource(R.string.date_captured), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateRangeFilter.entries.forEach { filter ->
                        val label = when (filter) {
                            DateRangeFilter.ALL -> stringResource(R.string.filter_date_all)
                            DateRangeFilter.TODAY -> stringResource(R.string.filter_date_today)
                            DateRangeFilter.YESTERDAY -> stringResource(R.string.filter_date_yesterday)
                            DateRangeFilter.THIS_WEEK -> stringResource(R.string.filter_date_week)
                            DateRangeFilter.THIS_MONTH -> stringResource(R.string.filter_date_month)
                            DateRangeFilter.THIS_YEAR -> stringResource(R.string.filter_date_year)
                        }
                        FilterChip(
                            selected = dateRangeFilter == filter,
                            onClick = { viewModel.onDateRangeFilterChanged(filter) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // File Size Section (Sizewise Filter)
                Text(stringResource(R.string.file_size_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FileSizeFilter.entries.forEach { sizeOption ->
                        val label = when (sizeOption) {
                            FileSizeFilter.ALL -> stringResource(R.string.filter_size_all)
                            FileSizeFilter.SMALL -> stringResource(R.string.filter_size_small)
                            FileSizeFilter.MEDIUM -> stringResource(R.string.filter_size_medium)
                            FileSizeFilter.LARGE -> stringResource(R.string.filter_size_large)
                        }
                        FilterChip(
                            selected = fileSizeFilter == sizeOption,
                            onClick = { viewModel.onFileSizeFilterChanged(sizeOption) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // File Name / Path Filter (Namewise Filter)
                Text(stringResource(R.string.filter_name_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = fileNameQuery,
                    onValueChange = { viewModel.onFileNameQueryChanged(it) },
                    placeholder = { Text(stringResource(R.string.filter_name_placeholder), fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (fileNameQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onFileNameQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear file name filter", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Actionable Only Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.filter_actionable_only), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.filter_actionable_only_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = onlyActionable,
                        onCheckedChange = { viewModel.onOnlyActionableToggled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Minimum Text Length Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.filter_min_text_len), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        val lenInt = minTextLength.roundToInt()
                        Text(
                            text = if (lenInt == 0) stringResource(R.string.filter_any_length) else stringResource(R.string.filter_chars_format, lenInt),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = minTextLength,
                        onValueChange = { viewModel.onMinTextLengthChanged(it) },
                        valueRange = 0f..300f,
                        steps = 5
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                            showFilterBottomSheet = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_apply_filters))
                }
            }
        }
    }

    // ── Main Scaffold ─────────────────────────────────────────────────────────
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedContent(
                targetState = isSelectionMode,
                label = "top_bar_transition"
            ) { inSelection ->
                if (inSelection) {
                    // Selection mode top bar
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Exit selection")
                            }
                        },
                        title = {
                            Text(
                                text = "${selectedIds.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        actions = {
                            // Select All
                            IconButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                            }
                            // Bulk Delete
                            IconButton(
                                onClick = { viewModel.bulkDelete() },
                                enabled = selectedIds.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete selected",
                                    tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            // Share selected (open share sheet for first selected URI)
                            IconButton(
                                onClick = {
                                    val uris = searchResults
                                        .filter { it.screenshot.id in selectedIds }
                                        .map { Uri.parse(it.screenshot.uriString) }
                                    if (uris.isNotEmpty()) {
                                        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                            type = "image/*"
                                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share screenshots"))
                                    }
                                    viewModel.clearSelection()
                                },
                                enabled = selectedIds.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share selected")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                } else {
                    // Normal top bar — clean, 2 actions + overflow menu
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = stringResource(R.string.offline_guarantee_cd),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = stringResource(R.string.offline_guarantee),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            // Filter button with active indicator badge
                            IconButton(onClick = { showFilterBottomSheet = true }) {
                                BadgedBox(
                                    badge = {
                                        if (viewModel.hasActiveFilters) {
                                            Badge(modifier = Modifier.size(8.dp))
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = stringResource(R.string.action_filter_options),
                                        tint = if (viewModel.hasActiveFilters) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Overflow menu (⋮) — Cleanup, Re-classify, Rescan
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_scan_screenshots)) },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            triggerScan()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_reclassify)) },
                                        leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.reclassifyAll()
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_cleanup_duplicates)) },
                                        leadingIcon = { Icon(Icons.Default.AutoDelete, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigate(Cleanup)
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            // FAB only shown when NOT in selection mode and NOT indexing
            AnimatedVisibility(
                visible = !isSelectionMode && !indexingProgress.isIndexing,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                FloatingActionButton(
                    onClick = triggerScan,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = stringResource(R.string.action_scan_gallery)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Indexing Progress Card
            AnimatedVisibility(
                visible = indexingProgress.isIndexing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.indexing_in_progress),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${indexingProgress.current} / ${indexingProgress.total}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (indexingProgress.total > 0)
                                    indexingProgress.current.toFloat() / indexingProgress.total.toFloat()
                                else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.action_clear_search)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Category Filter Chips (scrollable pill row)
            CategoryChips(
                selectedCategory = selectedCategory,
                categoryCounts = categoryCounts,
                totalCount = totalCount,
                onCategorySelected = { viewModel.onCategorySelected(it) }
            )

            // Active Filters Summary Bar
            if (viewModel.hasActiveFilters) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dateRangeFilter != DateRangeFilter.ALL) {
                        val label = when (dateRangeFilter) {
                            DateRangeFilter.TODAY -> stringResource(R.string.filter_date_today)
                            DateRangeFilter.YESTERDAY -> stringResource(R.string.filter_date_yesterday)
                            DateRangeFilter.THIS_WEEK -> stringResource(R.string.filter_date_week)
                            DateRangeFilter.THIS_MONTH -> stringResource(R.string.filter_date_month)
                            DateRangeFilter.THIS_YEAR -> stringResource(R.string.filter_date_year)
                            DateRangeFilter.ALL -> ""
                        }
                        InputChip(
                            selected = true,
                            onClick = { viewModel.onDateRangeFilterChanged(DateRangeFilter.ALL) },
                            label = { Text("Date: $label", fontSize = 11.sp) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp)) }
                        )
                    }

                    if (fileSizeFilter != FileSizeFilter.ALL) {
                        val label = when (fileSizeFilter) {
                            FileSizeFilter.SMALL -> stringResource(R.string.filter_size_small)
                            FileSizeFilter.MEDIUM -> stringResource(R.string.filter_size_medium)
                            FileSizeFilter.LARGE -> stringResource(R.string.filter_size_large)
                            FileSizeFilter.ALL -> ""
                        }
                        InputChip(
                            selected = true,
                            onClick = { viewModel.onFileSizeFilterChanged(FileSizeFilter.ALL) },
                            label = { Text("Size: $label", fontSize = 11.sp) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp)) }
                        )
                    }

                    if (fileNameQuery.isNotBlank()) {
                        InputChip(
                            selected = true,
                            onClick = { viewModel.onFileNameQueryChanged("") },
                            label = { Text("Name: $fileNameQuery", fontSize = 11.sp) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp)) }
                        )
                    }

                    if (sortOrder != SortOrder.NEWEST) {
                        val label = when (sortOrder) {
                            SortOrder.OLDEST -> stringResource(R.string.filter_sort_oldest)
                            SortOrder.RELEVANCE -> stringResource(R.string.filter_sort_relevance)
                            SortOrder.LARGEST -> stringResource(R.string.filter_sort_largest)
                            SortOrder.SMALLEST -> stringResource(R.string.filter_sort_smallest)
                            SortOrder.NAME_ASC -> stringResource(R.string.filter_sort_name)
                            SortOrder.NEWEST -> ""
                        }
                        InputChip(
                            selected = true,
                            onClick = { viewModel.onSortOrderChanged(SortOrder.NEWEST) },
                            label = { Text("Sort: $label", fontSize = 11.sp) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp)) }
                        )
                    }

                    if (onlyActionable) {
                        InputChip(
                            selected = true,
                            onClick = { viewModel.onOnlyActionableToggled(false) },
                            label = { Text(stringResource(R.string.filter_actionable_only), fontSize = 11.sp) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp)) }
                        )
                    }

                    if (minTextLength > 0f) {
                        InputChip(
                            selected = true,
                            onClick = { viewModel.onMinTextLengthChanged(0f) },
                            label = { Text("≥ ${minTextLength.toInt()} chars", fontSize = 11.sp) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp)) }
                        )
                    }

                    TextButton(onClick = { viewModel.resetFilters() }) {
                        Text(stringResource(R.string.filter_clear_all), fontSize = 11.sp)
                    }
                }
            }

            // Content Grid or Empty State
            if (searchResults.isEmpty()) {
                EmptyStateView(
                    isSearchActive = searchQuery.isNotBlank() || selectedCategory != null || viewModel.hasActiveFilters,
                    hasPermission = hasImagePermission(context),
                    onScanClick = triggerScan
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .animateContentSize()
                ) {
                    items(searchResults, key = { it.screenshot.id }) { result ->
                        val id = result.screenshot.id
                        ScreenshotCard(
                            searchResult = result,
                            isSelected = id in selectedIds,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(id)
                                } else {
                                    onNavigate(Viewer(id))
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    viewModel.enterSelectionMode(id)
                                } else {
                                    viewModel.toggleSelection(id)
                                }
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    isSearchActive: Boolean,
    hasPermission: Boolean,
    onScanClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val icon = when {
                        isSearchActive -> Icons.Default.Search
                        !hasPermission -> Icons.Default.Lock
                        else -> Icons.Default.DocumentScanner
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val titleText = when {
                isSearchActive -> stringResource(R.string.no_matching_found)
                !hasPermission -> stringResource(R.string.permission_required_title)
                else -> stringResource(R.string.no_screenshots_yet)
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            val descText = when {
                isSearchActive -> stringResource(R.string.no_matching_found_desc)
                !hasPermission -> stringResource(R.string.permission_required_desc)
                else -> stringResource(R.string.no_screenshots_yet_desc)
            }
            Text(
                text = descText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!isSearchActive) {
                Button(
                    onClick = onScanClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val btnIcon = if (!hasPermission) Icons.Default.Lock else Icons.Default.DocumentScanner
                    val btnText = if (!hasPermission) stringResource(R.string.action_grant_permission) else stringResource(R.string.action_scan_device_gallery)
                    Icon(imageVector = btnIcon, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(btnText)
                }
            }
        }
    }
}
