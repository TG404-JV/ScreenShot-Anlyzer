package com.littech.screenintel.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.annotation.StringRes
import com.littech.screenintel.R

enum class Category(
    @param:StringRes val labelRes: Int,
    val displayName: String,
    val hexColor: Long,
    val iconName: String
) {
    PAYMENT(R.string.cat_payments, "Payments", 0xFF0D9488, "Payment"),
    TICKET(R.string.cat_tickets, "Tickets", 0xFF2563EB, "ConfirmationNumber"),
    RECEIPT(R.string.cat_receipts, "Receipts", 0xFFD97706, "Receipt"),
    CREDENTIAL(R.string.cat_credentials, "Credentials", 0xFFE11D48, "Lock"),
    CONTACT(R.string.cat_contacts, "Contacts", 0xFF0284C7, "ContactPage"),
    CODE(R.string.cat_code, "Code", 0xFF7C3AED, "Code"),
    CHAT(R.string.cat_chats, "Chats", 0xFF059669, "Chat"),
    DOCUMENT(R.string.cat_documents, "Documents", 0xFF4B5563, "Description"),
    OTHER(R.string.cat_other, "Other", 0xFF6B7280, "Image");

    val color: Color
        get() = Color(hexColor)

    val icon: ImageVector
        get() = when (this) {
            PAYMENT -> Icons.Default.Payment
            TICKET -> Icons.Default.ConfirmationNumber
            RECEIPT -> Icons.Default.Receipt
            CREDENTIAL -> Icons.Default.Lock
            CONTACT -> Icons.Default.ContactPage
            CODE -> Icons.Default.Code
            CHAT -> Icons.AutoMirrored.Filled.Chat
            DOCUMENT -> Icons.Default.Description
            OTHER -> Icons.Default.Image
        }

    companion object {
        fun fromString(value: String): Category {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
        }
    }
}
