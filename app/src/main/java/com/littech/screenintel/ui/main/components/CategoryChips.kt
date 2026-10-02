package com.littech.screenintel.ui.main.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.littech.screenintel.R
import com.littech.screenintel.model.Category

@Composable
fun CategoryChips(
    selectedCategory: Category?,
    categoryCounts: Map<Category, Int>,
    totalCount: Int,
    onCategorySelected: (Category?) -> Unit,
    modifier: Modifier = Modifier
) {
    val allLabel = stringResource(R.string.cat_all)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "All" Chip
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            label = {
                Text(
                    text = if (totalCount > 0) "$allLabel ($totalCount)" else allLabel,
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = allLabel,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        // Individual Category Chips
        Category.entries.forEach { category ->
            val count = categoryCounts[category] ?: 0
            FilterChip(
                selected = selectedCategory == category,
                onClick = {
                    if (selectedCategory == category) onCategorySelected(null)
                    else onCategorySelected(category)
                },
                label = {
                    val catName = stringResource(category.labelRes)
                    Text(
                        text = if (count > 0) "$catName ($count)" else catName,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    val catName = stringResource(category.labelRes)
                    Icon(
                        imageVector = category.icon,
                        contentDescription = catName,
                        tint = if (selectedCategory == category) MaterialTheme.colorScheme.primary else category.color,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

