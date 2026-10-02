package com.littech.screenintel.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.littech.screenintel.core.classifier.ExtractedEntities
import com.littech.screenintel.data.repository.IndexingProgress
import com.littech.screenintel.data.repository.ScreenshotRepository
import com.littech.screenintel.model.Category
import com.littech.screenintel.model.Screenshot
import com.littech.screenintel.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.util.Calendar
import java.util.Locale

enum class DateRangeFilter {
    ALL,
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    THIS_YEAR
}

enum class FileSizeFilter {
    ALL,
    SMALL,   // < 500 KB
    MEDIUM,  // 500 KB - 2 MB
    LARGE    // > 2 MB
}

enum class SortOrder {
    NEWEST,
    OLDEST,
    RELEVANCE,
    LARGEST,
    SMALLEST,
    NAME_ASC
}

data class FilterCriteria(
    val query: String = "",
    val category: Category? = null,
    val dateFilter: DateRangeFilter = DateRangeFilter.ALL,
    val sizeFilter: FileSizeFilter = FileSizeFilter.ALL,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val actionableOnly: Boolean = false,
    val minLen: Int = 0,
    val fileNameQuery: String = ""
)

@OptIn(FlowPreview::class)
class MainScreenViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ScreenshotRepository = ScreenshotRepository(application)
) : AndroidViewModel(application) {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainScreenViewModel(application, ScreenshotRepository.getInstance(application))
            }
        }
    }

    private val _filterCriteria = MutableStateFlow(FilterCriteria())

    val searchQuery: StateFlow<String> = MutableStateFlow("")
    private val _searchQuery = searchQuery as MutableStateFlow<String>

    val selectedCategory: StateFlow<Category?> = MutableStateFlow(null)
    private val _selectedCategory = selectedCategory as MutableStateFlow<Category?>

    val dateRangeFilter: StateFlow<DateRangeFilter> = MutableStateFlow(DateRangeFilter.ALL)
    private val _dateRangeFilter = dateRangeFilter as MutableStateFlow<DateRangeFilter>

    val fileSizeFilter: StateFlow<FileSizeFilter> = MutableStateFlow(FileSizeFilter.ALL)
    private val _fileSizeFilter = fileSizeFilter as MutableStateFlow<FileSizeFilter>

    val sortOrder: StateFlow<SortOrder> = MutableStateFlow(SortOrder.NEWEST)
    private val _sortOrder = sortOrder as MutableStateFlow<SortOrder>

    val onlyActionable: StateFlow<Boolean> = MutableStateFlow(false)
    private val _onlyActionable = onlyActionable as MutableStateFlow<Boolean>

    val minTextLength: StateFlow<Float> = MutableStateFlow(0f)
    private val _minTextLength = minTextLength as MutableStateFlow<Float>

    val fileNameQuery: StateFlow<String> = MutableStateFlow("")
    private val _fileNameQuery = fileNameQuery as MutableStateFlow<String>

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _categoryCounts = MutableStateFlow<Map<Category, Int>>(emptyMap())
    val categoryCounts: StateFlow<Map<Category, Int>> = _categoryCounts.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    val indexingProgress: StateFlow<IndexingProgress> = repository.indexingState as StateFlow<IndexingProgress>

    // ─── Multi-select state ───────────────────────────────────────────────────
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = MutableStateFlow(false)
    private val _isSelectionMode = isSelectionMode as MutableStateFlow<Boolean>

    fun enterSelectionMode(id: Long) {
        _isSelectionMode.value = true
        _selectedIds.value = setOf(id)
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) current - id else current + id
        if (_selectedIds.value.isEmpty()) _isSelectionMode.value = false
    }

    fun selectAll() {
        _selectedIds.value = _searchResults.value.map { it.screenshot.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun bulkDelete() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val count = repository.bulkDeleteScreenshots(ids)
            clearSelection()
            _scanMessage.value = "Deleted $count screenshot(s)"
            refreshData()
        }
    }

    // ─── Filter Events ────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            combine(
                _filterCriteria.debounce(150),
                repository.screenshotsFlow
            ) { criteria, _ ->
                criteria
            }.collect { criteria ->
                performSearchWithFilters(criteria)
            }
        }

        refreshData()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _filterCriteria.update { it.copy(query = query) }
    }

    fun onCategorySelected(category: Category?) {
        _selectedCategory.value = category
        _filterCriteria.update { it.copy(category = category) }
    }

    fun onDateRangeFilterChanged(filter: DateRangeFilter) {
        _dateRangeFilter.value = filter
        _filterCriteria.update { it.copy(dateFilter = filter) }
    }

    fun onFileSizeFilterChanged(filter: FileSizeFilter) {
        _fileSizeFilter.value = filter
        _filterCriteria.update { it.copy(sizeFilter = filter) }
    }

    fun onSortOrderChanged(order: SortOrder) {
        _sortOrder.value = order
        _filterCriteria.update { it.copy(sortOrder = order) }
    }

    fun onOnlyActionableToggled(enabled: Boolean) {
        _onlyActionable.value = enabled
        _filterCriteria.update { it.copy(actionableOnly = enabled) }
    }

    fun onMinTextLengthChanged(len: Float) {
        _minTextLength.value = len
        _filterCriteria.update { it.copy(minLen = len.toInt()) }
    }

    fun onFileNameQueryChanged(name: String) {
        _fileNameQuery.value = name
        _filterCriteria.update { it.copy(fileNameQuery = name) }
    }

    fun resetFilters() {
        _dateRangeFilter.value = DateRangeFilter.ALL
        _fileSizeFilter.value = FileSizeFilter.ALL
        _sortOrder.value = SortOrder.NEWEST
        _onlyActionable.value = false
        _minTextLength.value = 0f
        _fileNameQuery.value = ""
        _filterCriteria.update {
            it.copy(
                dateFilter = DateRangeFilter.ALL,
                sizeFilter = FileSizeFilter.ALL,
                sortOrder = SortOrder.NEWEST,
                actionableOnly = false,
                minLen = 0,
                fileNameQuery = ""
            )
        }
    }

    val hasActiveFilters: Boolean
        get() = _dateRangeFilter.value != DateRangeFilter.ALL ||
                _fileSizeFilter.value != FileSizeFilter.ALL ||
                _sortOrder.value != SortOrder.NEWEST ||
                _fileNameQuery.value.isNotBlank() ||
                _onlyActionable.value ||
                _minTextLength.value > 0f

    /**
     * Parses natural-language date expressions embedded in a search query.
     * Returns: (cleanQuery, targetMonth, targetDay, targetYear)
     */
    private data class ParsedDate(
        val cleanQuery: String,
        val targetMonth: Int? = null,
        val targetDay: Int? = null,
        val targetYear: Int? = null,
        val exactRangeStart: Long? = null,
        val exactRangeEnd: Long? = null
    )

    private fun parseDateQuery(rawQuery: String): ParsedDate {
        val lower = rawQuery.trim().lowercase(Locale.getDefault())
        val cal = Calendar.getInstance()

        if (lower == "today") {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            return ParsedDate(cleanQuery = "", exactRangeStart = start, exactRangeEnd = start + 86400000L)
        }
        if (lower == "yesterday") {
            cal.add(Calendar.DATE, -1)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            return ParsedDate(cleanQuery = "", exactRangeStart = start, exactRangeEnd = start + 86400000L)
        }

        val monthMap = mapOf(
            "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3,
            "may" to 4, "jun" to 5, "jul" to 6, "aug" to 7,
            "sep" to 8, "oct" to 9, "nov" to 10, "dec" to 11,
            "january" to 0, "february" to 1, "march" to 2, "april" to 3,
            "june" to 5, "july" to 6, "august" to 7, "september" to 8,
            "october" to 9, "november" to 10, "december" to 11
        )

        val monthDayPattern = Regex("""\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|january|february|march|april|june|july|august|september|october|november|december)\s+(\d{1,4})\b""", RegexOption.IGNORE_CASE)
        val dayMonthPattern = Regex("""\b(\d{1,2})\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|january|february|march|april|june|july|august|september|october|november|december)\b""", RegexOption.IGNORE_CASE)

        monthDayPattern.find(lower)?.let { match ->
            val monthStr = match.groupValues[1]
            val num = match.groupValues[2].toIntOrNull() ?: return@let
            val monthIdx = monthMap[monthStr] ?: return@let
            val cleaned = rawQuery.replace(match.value, "", ignoreCase = true).trim()
            return if (num > 31) {
                // Year match (e.g. "Sep 2024")
                ParsedDate(cleanQuery = cleaned, targetMonth = monthIdx, targetYear = num)
            } else {
                // Day of month (e.g. "Sep 20")
                ParsedDate(cleanQuery = cleaned, targetMonth = monthIdx, targetDay = num)
            }
        }

        dayMonthPattern.find(lower)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            val monthStr = match.groupValues[2]
            val monthIdx = monthMap[monthStr] ?: return@let
            val cleaned = rawQuery.replace(match.value, "", ignoreCase = true).trim()
            return ParsedDate(cleanQuery = cleaned, targetMonth = monthIdx, targetDay = day)
        }

        return ParsedDate(cleanQuery = rawQuery)
    }

    private suspend fun performSearchWithFilters(criteria: FilterCriteria) {
        val filtered = withContext(Dispatchers.Default) {
            val parsedDate = parseDateQuery(criteria.query)

            // When searching text, search across the repository
            val baseResults = repository.search(parsedDate.cleanQuery, criteria.category)
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()

            val list = baseResults.filter { res ->
                val s = res.screenshot

                // 1. Natural language date match
                val matchesDateQuery = when {
                    parsedDate.exactRangeStart != null && parsedDate.exactRangeEnd != null -> {
                        s.dateAdded in parsedDate.exactRangeStart until parsedDate.exactRangeEnd
                    }
                    parsedDate.targetMonth != null || parsedDate.targetDay != null || parsedDate.targetYear != null -> {
                        cal.timeInMillis = s.dateAdded
                        val m = cal.get(Calendar.MONTH)
                        val d = cal.get(Calendar.DAY_OF_MONTH)
                        val y = cal.get(Calendar.YEAR)
                        (parsedDate.targetMonth == null || m == parsedDate.targetMonth) &&
                        (parsedDate.targetDay == null || d == parsedDate.targetDay) &&
                        (parsedDate.targetYear == null || y == parsedDate.targetYear)
                    }
                    else -> true
                }

                // 2. Date Range Filter
                val matchesDateRange = when (criteria.dateFilter) {
                    DateRangeFilter.ALL -> true
                    DateRangeFilter.TODAY -> (now - s.dateAdded) <= 86400000L
                    DateRangeFilter.YESTERDAY -> {
                        val diff = now - s.dateAdded
                        diff in 86400000L..(86400000L * 2)
                    }
                    DateRangeFilter.THIS_WEEK -> (now - s.dateAdded) <= 86400000L * 7
                    DateRangeFilter.THIS_MONTH -> (now - s.dateAdded) <= 86400000L * 30
                    DateRangeFilter.THIS_YEAR -> (now - s.dateAdded) <= 86400000L * 365
                }

                // 3. File Size Filter
                val matchesSize = when (criteria.sizeFilter) {
                    FileSizeFilter.ALL -> true
                    FileSizeFilter.SMALL -> s.fileSize < 500L * 1024L
                    FileSizeFilter.MEDIUM -> s.fileSize in (500L * 1024L)..(2048L * 1024L)
                    FileSizeFilter.LARGE -> s.fileSize > 2048L * 1024L
                }

                // 4. File Name Filter
                val matchesFileName = if (criteria.fileNameQuery.isNotBlank()) {
                    s.filePath.contains(criteria.fileNameQuery, ignoreCase = true)
                } else true

                // 5. Minimum Text Length
                val matchesLength = s.rawOcrText.length >= criteria.minLen

                // 6. Actionable Only
                val matchesActionable = if (criteria.actionableOnly) {
                    val entities = ExtractedEntities.fromJson(s.extractedDataJson)
                    entities.upiIds.isNotEmpty() || entities.amounts.isNotEmpty() ||
                            entities.pnrNumbers.isNotEmpty() || entities.otps.isNotEmpty() ||
                            entities.urls.isNotEmpty() || entities.phoneNumbers.isNotEmpty()
                } else true

                matchesDateQuery && matchesDateRange && matchesSize && matchesFileName && matchesLength && matchesActionable
            }

            // 7. Sorting
            when (criteria.sortOrder) {
                SortOrder.NEWEST -> list.sortedByDescending { it.screenshot.dateAdded }
                SortOrder.OLDEST -> list.sortedBy { it.screenshot.dateAdded }
                SortOrder.RELEVANCE -> list.sortedBy { it.rank }
                SortOrder.LARGEST -> list.sortedByDescending { it.screenshot.fileSize }
                SortOrder.SMALLEST -> list.sortedBy { it.screenshot.fileSize }
                SortOrder.NAME_ASC -> list.sortedBy { it.screenshot.filePath.substringAfterLast("/") }
            }
        }

        _searchResults.value = filtered
        updateCounts()
    }

    private fun updateCounts() {
        viewModelScope.launch {
            val counts = repository.getCategoryCounts()
            _categoryCounts.value = counts
            _totalCount.value = counts.values.sum()
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            repository.refreshScreenshots()
        }
    }

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage.asStateFlow()

    fun clearScanMessage() {
        _scanMessage.value = null
    }

    private var isScanRunning = false

    fun scanScreenshots(forceReclassify: Boolean = false) {
        if (isScanRunning || repository.isIndexing()) {
            android.util.Log.i("ScreenIntel", "ViewModel.scanScreenshots: Already running, ignoring duplicate request.")
            return
        }
        viewModelScope.launch {
            isScanRunning = true
            try {
                android.util.Log.i("ScreenIntel", "ViewModel.scanScreenshots: Starting scan (forceReclassify=$forceReclassify)...")
                val newCount = repository.scanAndIndex()
                android.util.Log.i("ScreenIntel", "ViewModel.scanScreenshots: scanAndIndex returned $newCount")

                if (newCount == -1) {
                    android.util.Log.i("ScreenIntel", "ViewModel.scanScreenshots: Indexing active in repository, skipping message.")
                    return@launch
                }

                if (newCount > 0) {
                    _scanMessage.value = "Indexed $newCount new screenshot(s)"
                } else if (forceReclassify) {
                    val updated = repository.reclassifyAllScreenshots()
                    _scanMessage.value = "Re-categorized $updated screenshot(s)"
                } else {
                    val updated = repository.reclassifyAllScreenshots()
                    if (updated > 0) {
                        _scanMessage.value = "Re-categorized $updated screenshot(s)"
                    } else {
                        val total = repository.getAllScreenshotCount()
                        if (total > 0) {
                            _scanMessage.value = "All screenshots up to date ($total items)"
                        } else {
                            _scanMessage.value = "No screenshots found on device"
                        }
                    }
                }
                refreshData()
            } catch (e: Exception) {
                android.util.Log.e("ScreenIntel", "ViewModel.scanScreenshots failed: ${e.message}", e)
                _scanMessage.value = "Scan error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isScanRunning = false
            }
        }
    }

    fun reclassifyAll() {
        scanScreenshots(forceReclassify = true)
    }

    fun deleteScreenshot(id: Long) {
        viewModelScope.launch {
            repository.deleteScreenshot(id)
            refreshData()
        }
    }

    fun restoreScreenshot(screenshot: Screenshot) {
        viewModelScope.launch {
            repository.restoreScreenshot(screenshot)
            refreshData()
        }
    }

    private val _lastDeletedScreenshot = MutableStateFlow<Screenshot?>(null)
    val lastDeletedScreenshot: StateFlow<Screenshot?> = _lastDeletedScreenshot.asStateFlow()

    fun onScreenshotDeleted(screenshot: Screenshot) {
        _lastDeletedScreenshot.value = screenshot
        refreshData()
    }

    fun clearLastDeletedScreenshot() {
        _lastDeletedScreenshot.value = null
    }
}
