package com.littech.screenintel.core.classifier.rules

import com.littech.screenintel.core.classifier.ClassifierConfig
import com.littech.screenintel.core.classifier.ExtractedEntities
import com.littech.screenintel.model.Category

/**
 * Diagnostic result returned by evaluating a CategoryRule.
 */
data class RuleResult(
    val category: Category,
    val matched: Boolean,
    val score: Int = 0,
    val reason: String = ""
)

/**
 * Pluggable contract for category classification rules.
 */
interface CategoryRule {
    val category: Category
    val priority: Int // Lower priority number is evaluated first
    fun evaluate(text: String, entities: ExtractedEntities, config: ClassifierConfig): RuleResult
}
