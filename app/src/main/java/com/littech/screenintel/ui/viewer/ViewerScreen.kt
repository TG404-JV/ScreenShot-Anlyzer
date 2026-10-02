package com.littech.screenintel.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataArray
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.littech.screenintel.R
import com.littech.screenintel.core.classifier.ExtractedEntities
import com.littech.screenintel.data.repository.ScreenshotRepository
import com.littech.screenintel.model.Screenshot
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    screenshotId: Long,
    onBack: () -> Unit,
    onDeleted: ((Screenshot) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ScreenshotRepository(context) }

    var screenshot by remember { mutableStateOf<Screenshot?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRedactedText by remember { mutableStateOf(true) }
    var showEntitiesBottomSheet by remember { mutableStateOf(false) }

    // Pinch-to-Zoom & Pan State
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isFullscreen by remember { mutableStateOf(false) }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(screenshotId) {
        isLoading = true
        screenshot = repository.getScreenshotById(screenshotId)
        isLoading = false
    }

    // Confirmation Alert Dialog
    if (showDeleteDialog && screenshot != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val itemToDelete = screenshot
                        if (itemToDelete != null) {
                            showDeleteDialog = false
                            coroutineScope.launch {
                                repository.deleteScreenshot(itemToDelete.id)
                                onDeleted?.invoke(itemToDelete)
                                onBack()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Entity Details Modal BottomSheet
    if (showEntitiesBottomSheet && screenshot != null) {
        val item = screenshot!!
        val entities = ExtractedEntities.fromJson(item.extractedDataJson)

        ModalBottomSheet(
            onDismissRequest = { showEntitiesBottomSheet = false },
            sheetState = bottomSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.extracted_intelligence),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showEntitiesBottomSheet = false }) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val copyToClip: (String) -> Unit = { text ->
                    clipboard.setPrimaryClip(ClipData.newPlainText("Extracted Data", text))
                    Toast.makeText(context, context.getString(R.string.toast_copied_item, text), Toast.LENGTH_SHORT).show()
                }

                EntitySection(
                    title = stringResource(R.string.phone_numbers),
                    items = entities.phoneNumbers,
                    onCopy = copyToClip,
                    actionIcon = Icons.Default.Call,
                    actionDescription = "Call",
                    onAction = { phone ->
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        context.startActivity(intent)
                    },
                    onSaveContact = { phone ->
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            type = ContactsContract.RawContacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                            if (entities.emails.isNotEmpty()) {
                                putExtra(ContactsContract.Intents.Insert.EMAIL, entities.emails.first())
                            }
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.toast_cannot_save_contact), Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                EntitySection(
                    title = stringResource(R.string.email_addresses),
                    items = entities.emails,
                    onCopy = copyToClip,
                    actionIcon = Icons.Default.Email,
                    actionDescription = "Send Email",
                    onAction = { email ->
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            copyToClip(email)
                        }
                    },
                    onSaveContact = { email ->
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            type = ContactsContract.RawContacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.EMAIL, email)
                            if (entities.phoneNumbers.isNotEmpty()) {
                                putExtra(ContactsContract.Intents.Insert.PHONE, entities.phoneNumbers.first())
                            }
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.toast_cannot_save_contact), Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                EntitySection(
                    title = stringResource(R.string.upi_payment_ids),
                    items = entities.upiIds,
                    onCopy = copyToClip,
                    actionIcon = Icons.Default.Payment,
                    actionDescription = "Pay UPI",
                    onAction = { upi ->
                        val uri = Uri.parse("upi://pay?pa=$upi")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.toast_no_upi_app), Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                EntitySection(
                    title = stringResource(R.string.web_urls),
                    items = entities.urls,
                    onCopy = copyToClip,
                    actionIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    actionDescription = "Open Link",
                    onAction = { url ->
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.toast_cannot_open_url), Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                EntitySection(
                    title = stringResource(R.string.transaction_amounts),
                    items = entities.amounts,
                    onCopy = copyToClip
                )

                EntitySection(
                    title = stringResource(R.string.pnr_booking_ids),
                    items = entities.pnrNumbers,
                    onCopy = copyToClip
                )

                EntitySection(
                    title = stringResource(R.string.otp_verification_codes),
                    items = entities.otps,
                    onCopy = copyToClip
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.raw_extracted_json),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.extractedDataJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    screenshot?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = it.category.color.copy(alpha = 0.2f),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = it.category.icon,
                                        contentDescription = null,
                                        tint = it.category.color,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(it.category.labelRes),
                                        color = it.category.color,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                actions = {
                    screenshot?.let { current ->
                        // Inspect Entities Action
                        IconButton(onClick = { showEntitiesBottomSheet = true }) {
                            Icon(imageVector = Icons.Default.DataArray, contentDescription = stringResource(R.string.extracted_intelligence))
                        }

                        // Share Text Action
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, current.redactedOcrText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share)))
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                        }

                        // Delete Action
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (screenshot == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.screenshot_not_found))
            }
        } else {
            val item = screenshot!!
            val entities = remember(item.extractedDataJson) {
                ExtractedEntities.fromJson(item.extractedDataJson)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState(), enabled = scale <= 1.05f)
                    .padding(16.dp)
            ) {
                // Interactive Zoom & Pan Image Box (Proportional Portrait Height)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .clip(RoundedCornerShape(16.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1.05f) {
                                            scale = 1f
                                            offsetX = 0f
                                            offsetY = 0f
                                        } else {
                                            scale = 2.8f
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1.05f) {
                                        val maxPanX = (scale - 1f) * 600f
                                        val maxPanY = (scale - 1f) * 900f
                                        offsetX = (offsetX + pan.x).coerceIn(-maxPanX, maxPanX)
                                        offsetY = (offsetY + pan.y).coerceIn(-maxPanY, maxPanY)
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.uriString)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.pinch_to_zoom),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )

                        // Top Controls Overlay: Reset Zoom + Fullscreen Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                                .align(Alignment.TopEnd),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (scale > 1.05f) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            scale = 1f
                                            offsetX = 0f
                                            offsetY = 0f
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = stringResource(R.string.reset_zoom),
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Fullscreen Button
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f)
                            ) {
                                IconButton(
                                    onClick = { isFullscreen = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Full Screen",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Bottom Zoom Controls & Hint
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .padding(10.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        scale = (scale - 0.5f).coerceAtLeast(1f)
                                        if (scale <= 1.05f) { offsetX = 0f; offsetY = 0f }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(16.dp))
                                }

                                Text(
                                    text = "${(scale * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                IconButton(
                                    onClick = {
                                        scale = (scale + 0.5f).coerceAtMost(5f)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actionable Quick Chips (UPI, Call, Email, Link, OTP)
                val hasActions = entities.upiIds.isNotEmpty() || entities.urls.isNotEmpty() ||
                        entities.phoneNumbers.isNotEmpty() || entities.otps.isNotEmpty() ||
                        entities.emails.isNotEmpty()

                if (hasActions) {
                    Text(
                        text = stringResource(R.string.quick_actions),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        entities.upiIds.forEach { upi ->
                            AssistChip(
                                onClick = {
                                    val uri = Uri.parse("upi://pay?pa=$upi")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.toast_no_upi_app), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text(stringResource(R.string.action_pay, upi)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Payment,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }

                        entities.urls.forEach { url ->
                            AssistChip(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.toast_cannot_open_url), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text(url) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null
                                    )
                                }
                            )
                        }

                        entities.phoneNumbers.forEach { phone ->
                            AssistChip(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                    context.startActivity(intent)
                                },
                                label = { Text(phone) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null
                                    )
                                }
                            )
                        }

                        entities.emails.forEach { email ->
                            AssistChip(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Email", email))
                                        Toast.makeText(context, context.getString(R.string.toast_copied_ocr_text), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text(email, maxLines = 1) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null
                                    )
                                }
                            )
                        }

                        if (entities.phoneNumbers.isNotEmpty() || entities.emails.isNotEmpty()) {
                            AssistChip(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_INSERT).apply {
                                        type = ContactsContract.RawContacts.CONTENT_TYPE
                                        if (entities.phoneNumbers.isNotEmpty()) {
                                            putExtra(ContactsContract.Intents.Insert.PHONE, entities.phoneNumbers.first())
                                        }
                                        if (entities.emails.isNotEmpty()) {
                                            putExtra(ContactsContract.Intents.Insert.EMAIL, entities.emails.first())
                                        }
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.toast_cannot_save_contact), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text(stringResource(R.string.action_save_contact)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }

                        val otpClipLabel = stringResource(R.string.clip_label_otp)
                        entities.otps.forEach { otp ->
                            AssistChip(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText(otpClipLabel, otp))
                                    Toast.makeText(context, context.getString(R.string.toast_copied_otp, otp), Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(stringResource(R.string.action_otp, otp)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Extracted OCR Text Card with Animated Crossfade
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.extracted_text_ocr),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // PII Redaction Toggle Switch
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (showRedactedText) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showRedactedText) stringResource(R.string.masked) else stringResource(R.string.raw),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = showRedactedText,
                                    onCheckedChange = { showRedactedText = it }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Crossfade animation between raw and masked text
                        Crossfade(targetState = showRedactedText, label = "pii_crossfade") { isMasked ->
                            val displayText = if (isMasked) item.redactedOcrText else item.rawOcrText
                            Text(
                                text = displayText.ifBlank { stringResource(R.string.no_text_recognized) },
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            val clipTextLabel = stringResource(R.string.clip_label_text)
                            OutlinedButton(
                                onClick = {
                                    val textToCopy = if (showRedactedText) item.redactedOcrText else item.rawOcrText
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText(clipTextLabel, textToCopy))
                                    Toast.makeText(context, context.getString(R.string.toast_copied_text), Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_copy_text))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Technical Metadata Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.screenshot_details), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        MetadataRow(label = stringResource(R.string.date_captured), value = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.dateAdded)))
                        MetadataRow(label = stringResource(R.string.dimensions), value = "${item.width} x ${item.height} px")
                        MetadataRow(label = stringResource(R.string.file_size), value = "${item.fileSize / 1024} KB")
                        MetadataRow(label = stringResource(R.string.visual_hash), value = "0x${java.lang.Long.toHexString(item.visualHash).uppercase()}")
                        MetadataRow(label = stringResource(R.string.file_path), value = item.filePath)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Immersive Fullscreen Gallery Zoom Dialog
    if (isFullscreen && screenshot != null) {
        val currentScreenshot = screenshot!!
        var fsScale by remember { mutableFloatStateOf(1f) }
        var fsOffsetX by remember { mutableFloatStateOf(0f) }
        var fsOffsetY by remember { mutableFloatStateOf(0f) }

        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (fsScale > 1.05f) {
                                    fsScale = 1f
                                    fsOffsetX = 0f
                                    fsOffsetY = 0f
                                } else {
                                    fsScale = 3f
                                    fsOffsetX = 0f
                                    fsOffsetY = 0f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            fsScale = (fsScale * zoom).coerceIn(1f, 6f)
                            if (fsScale > 1.05f) {
                                val maxX = (fsScale - 1f) * 800f
                                val maxY = (fsScale - 1f) * 1200f
                                fsOffsetX = (fsOffsetX + pan.x).coerceIn(-maxX, maxX)
                                fsOffsetY = (fsOffsetY + pan.y).coerceIn(-maxY, maxY)
                            } else {
                                fsOffsetX = 0f
                                fsOffsetY = 0f
                            }
                        }
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentScreenshot.uriString)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.pinch_to_zoom),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = fsScale,
                            scaleY = fsScale,
                            translationX = fsOffsetX,
                            translationY = fsOffsetY
                        )
                )

                // Top Floating Bar (Close button & Reset button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        IconButton(
                            onClick = { isFullscreen = false },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Fullscreen",
                                tint = Color.White
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (fsScale > 1.05f) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f)
                            ) {
                                IconButton(
                                    onClick = {
                                        fsScale = 1f
                                        fsOffsetX = 0f
                                        fsOffsetY = 0f
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = stringResource(R.string.reset_zoom),
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "${(fsScale * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntitySection(
    title: String,
    items: List<String>,
    onCopy: (String) -> Unit,
    onAction: ((String) -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionDescription: String? = null,
    onSaveContact: ((String) -> Unit)? = null
) {
    if (items.isNotEmpty()) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onAction != null && actionIcon != null) {
                                IconButton(
                                    onClick = { onAction(item) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = actionIcon,
                                        contentDescription = actionDescription,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (onSaveContact != null) {
                                IconButton(
                                    onClick = { onSaveContact(item) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = "Save to Contacts",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onCopy(item) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
