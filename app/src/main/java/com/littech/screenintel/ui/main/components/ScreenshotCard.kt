package com.littech.screenintel.ui.main.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.littech.screenintel.R
import com.littech.screenintel.model.SearchResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScreenshotCard(
    searchResult: SearchResult,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val screenshot = searchResult.screenshot
    val context = LocalContext.current
    val categoryColor = screenshot.category.color

    // Animate card border when selected
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(150),
        label = "selection_border"
    )
    val cardBg by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(150),
        label = "card_bg"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 6.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Screenshot Preview Thumbnail with category left-border accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                // Category colour accent strip on left edge (3dp wide)
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxSize()
                        .background(categoryColor)
                        .align(Alignment.CenterStart)
                )

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(screenshot.uriString)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.cd_screenshot_preview),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 3.dp) // avoid covering accent strip
                )

                // Category Badge Overlay (top-left)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = categoryColor.copy(alpha = 0.88f),
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val catName = stringResource(screenshot.category.labelRes)
                        Icon(
                            imageVector = screenshot.category.icon,
                            contentDescription = catName,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = catName,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Selection Overlay
                if (isSelectionMode || isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isSelected)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else
                                    Color.Transparent
                            )
                    )
                    // Checkbox circle — top right
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color.White, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .border(2.dp, Color.White, CircleShape)
                                    .background(Color.Black.copy(alpha = 0.25f), CircleShape)
                            )
                        }
                    }
                }
            }

            // Card Body: OCR Snippet with highlighted match + Date
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                // Snippet or first 2 lines of OCR text
                val displayText = if (searchResult.snippet.isNotBlank()) {
                    formatSnippet(searchResult.snippet)
                } else {
                    buildAnnotatedString {
                        append(
                            screenshot.redactedOcrText
                                .lines()
                                .filter { it.isNotBlank() }
                                .take(2)
                                .joinToString(" · ")
                        )
                    }
                }

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Footer: Date and Quick Copy (hidden during selection mode to keep things clean)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDate(context, screenshot.dateAdded),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isSelectionMode) {
                        val toastCopied = stringResource(R.string.toast_copied_ocr_text)
                        val clipLabel = stringResource(R.string.clip_label_text)
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, screenshot.redactedOcrText))
                                Toast.makeText(context, toastCopied, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.cd_copy_text),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Highlights <b>matched terms</b> in snippet returned by FTS5
 */
private fun formatSnippet(snippet: String) = buildAnnotatedString {
    val parts = snippet.split("<b>", "</b>")
    var isBold = snippet.startsWith("<b>")
    for (part in parts) {
        if (isBold) {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))) {
                append(part)
            }
        } else {
            append(part)
        }
        isBold = !isBold
    }
}

private fun formatDate(context: Context, timestamp: Long): String {
    if (timestamp <= 0) return context.getString(R.string.time_recently)
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 3600000L -> context.getString(R.string.time_minutes_ago, (diff / 60000L).coerceAtLeast(1))
        diff < 86400000L -> context.getString(R.string.time_hours_ago, diff / 3600000L)
        diff < 86400000L * 7 -> context.getString(R.string.time_days_ago, diff / 86400000L)
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
