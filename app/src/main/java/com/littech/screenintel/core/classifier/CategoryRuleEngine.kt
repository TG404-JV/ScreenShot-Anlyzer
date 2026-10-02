package com.littech.screenintel.core.classifier

import com.littech.screenintel.core.classifier.rules.CategoryRule
import com.littech.screenintel.core.classifier.rules.ChatRule
import com.littech.screenintel.core.classifier.rules.CodeRule
import com.littech.screenintel.core.classifier.rules.ContactRule
import com.littech.screenintel.core.classifier.rules.CredentialRule
import com.littech.screenintel.core.classifier.rules.DocumentRule
import com.littech.screenintel.core.classifier.rules.OtherRule
import com.littech.screenintel.core.classifier.rules.PaymentRule
import com.littech.screenintel.core.classifier.rules.ReceiptRule
import com.littech.screenintel.core.classifier.rules.RuleResult
import com.littech.screenintel.core.classifier.rules.TicketRule
import com.littech.screenintel.model.Category

/**
 * Orchestrates evaluation of prioritized category rules.
 */
class CategoryRuleEngine(
    private val config: ClassifierConfig = ClassifierConfig(),
    rules: List<CategoryRule> = DEFAULT_RULES
) {
    private val sortedRules = rules.sortedBy { it.priority }

    /**
     * Evaluates rules in deterministic priority order and returns the first matching Category.
     */
    fun classify(text: String, entities: ExtractedEntities): Category {
        for (rule in sortedRules) {
            val result = rule.evaluate(text, entities, config)
            if (result.matched) {
                return result.category
            }
        }
        return Category.OTHER
    }

    /**
     * Evaluates all rules and returns diagnostic results for observability and debugging.
     */
    fun evaluateDetailed(text: String, entities: ExtractedEntities): List<RuleResult> {
        return sortedRules.map { it.evaluate(text, entities, config) }
    }

    companion object {
        val DEFAULT_RULES: List<CategoryRule> = listOf(
            CredentialRule(),
            TicketRule(),
            PaymentRule(),
            ReceiptRule(),
            ContactRule(),
            CodeRule(),
            ChatRule(),
            DocumentRule(),
            OtherRule()
        )
    }
}
