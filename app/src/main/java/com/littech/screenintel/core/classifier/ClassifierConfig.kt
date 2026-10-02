package com.littech.screenintel.core.classifier

/**
 * Configuration options and scoring weights for the Category Classifier.
 * Allows tuning precision/recall thresholds without modifying rule logic.
 */
data class ClassifierConfig(
    val strongKeywordWeight: Int = 3,
    val weakKeywordWeight: Int = 1,
    val standardNegativeWeight: Int = 3,
    val strongNegativeWeight: Int = 4,

    // Category thresholds
    val credentialThreshold: Int = 3,
    val credentialOtpBonus: Int = 5,

    val ticketThreshold: Int = 3,
    val ticketPnrBonus: Int = 5,

    val paymentThreshold: Int = 3,
    val paymentUpiBonus: Int = 4,
    val paymentAmountBonus: Int = 1,

    val receiptThreshold: Int = 3,
    val receiptAmountBonus: Int = 1,

    val contactThreshold: Int = 4,
    val contactEmailBonus: Int = 2,
    val contactPhoneBonus: Int = 2,
    val contactCardMaxLength: Int = 300,

    val codeThreshold: Int = 3,
    val chatThreshold: Int = 3,

    val documentMinLength: Int = 150,
    val documentMinWords: Int = 15
)
