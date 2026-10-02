package com.littech.screenintel.core.classifier.rules

import com.littech.screenintel.core.classifier.ClassifierConfig
import com.littech.screenintel.core.classifier.ExtractedEntities
import com.littech.screenintel.model.Category

class CredentialRule : CategoryRule {
    override val category: Category = Category.CREDENTIAL
    override val priority: Int = 100

    private val strongMatcher = KeywordMatcher(
        listOf(
            "one time password", "otp is", "otp:", "your otp", "verification code",
            "do not share this", "do not share otp", "login verification",
            "temporary password", "auth code", "2fa code", "two-factor",
            "passkey", "backup codes", "recovery code", "recovery key",
            "authenticator code", "totp", "secret key"
        )
    )
    private val weakMatcher = KeywordMatcher(
        listOf(
            "security code", "access code", "pin:", "password reset", "verify your",
            "security key", "verification pin"
        )
    )

    override fun evaluate(text: String, entities: ExtractedEntities, config: ClassifierConfig): RuleResult {
        val strong = strongMatcher.countMatches(text)
        val weak = weakMatcher.countMatches(text)
        val otpBonus = if (entities.otps.isNotEmpty()) config.credentialOtpBonus else 0
        val score = strong * config.strongKeywordWeight + weak * config.weakKeywordWeight + otpBonus
        val matched = score >= config.credentialThreshold

        return RuleResult(
            category = category,
            matched = matched,
            score = score,
            reason = "strong=$strong, weak=$weak, otpBonus=$otpBonus, score=$score, threshold=${config.credentialThreshold}"
        )
    }
}

class TicketRule : CategoryRule {
    override val category: Category = Category.TICKET
    override val priority: Int = 200

    private val strongMatcher = KeywordMatcher(
        listOf(
            "boarding pass", "irctc", "pnr no", "pnr number", "train ticket",
            "railway ticket", "e-ticket", "seat no", "berth no", "coach no",
            "reservation slip", "booking confirmed", "flight ticket",
            "movie ticket", "event ticket", "show ticket", "concert ticket",
            "gate no", "departure date", "arrival date", "terminal",
            "metro ticket", "pune metro", "metro rail", "outward ticket",
            "ticket no:", "ticket details", "billeasy.in", "mahametropune",
            "delhi metro", "dmrc", "namma metro", "mumbai metro", "hyderabad metro",
            "bookmyshow", "cinepolis", "flight no", "web check-in"
        )
    )
    private val weakMatcher = KeywordMatcher(
        listOf(
            "flight", "departure", "arrival", "boarding", "check-in",
            "cinema", "showtime", "seat", "berth", "coach", "pnr",
            "booking id", "booking ref", "confirmed booking", "ticket no",
            "passenger name", "bus ticket", "metro", "abhibus", "redbus", "ticket",
            "pvr", "inox", "audi", "screen 1", "screen 2"
        )
    )
    private val negMatcher = KeywordMatcher(
        listOf(
            "support ticket", "help ticket", "ticket raised", "issue ticket",
            "job details", "salary", "interview", "walk-in",
            "search results", "flights search", "flight search", "select flight",
            "train search", "bus search", "fare rules"
        )
    )

    override fun evaluate(text: String, entities: ExtractedEntities, config: ClassifierConfig): RuleResult {
        val strong = strongMatcher.countMatches(text)
        val weak = weakMatcher.countMatches(text)
        val neg = negMatcher.countMatches(text)
        val pnrBonus = if (entities.pnrNumbers.isNotEmpty()) config.ticketPnrBonus else 0
        val score = strong * config.strongKeywordWeight + weak * config.weakKeywordWeight + pnrBonus - (neg * config.standardNegativeWeight)
        val matched = score >= config.ticketThreshold

        return RuleResult(
            category = category,
            matched = matched,
            score = score,
            reason = "strong=$strong, weak=$weak, neg=$neg, pnrBonus=$pnrBonus, score=$score, threshold=${config.ticketThreshold}"
        )
    }
}

class PaymentRule : CategoryRule {
    override val category: Category = Category.PAYMENT
    override val priority: Int = 300

    private val strongMatcher = KeywordMatcher(
        listOf(
            "payment successful", "payment success", "paid successfully",
            "money sent", "amount debited", "debited from account",
            "transaction successful", "transfer successful", "transfer complete",
            "upi transaction", "upi ref", "upi ref no", "sent via upi",
            "credited to your account", "debit alert", "credited rs", "debited rs",
            "your account has been debited", "account balance", "available balance",
            "payment received", "received payment", "paid to merchant",
            "paise bhej diye", "paise mil gaye", "paise transfer", "payment ho gaya",
            "khate me credit", "khate se debit", "upi lite", "auto-debit",
            "autopay successful", "credited by inr", "debited by inr"
        )
    )
    private val weakMatcher = KeywordMatcher(
        listOf(
            "paid to", "sent to", "received from", "transaction id", "transaction ref",
            "bank transfer", "neft", "rtgs", "imps", "bank statement",
            "gpay", "google pay", "phonepe", "paytm", "bhim upi", "bhim",
            "cred", "bharatpe"
        )
    )
    private val negMatcher = KeywordMatcher(
        listOf(
            "payment terms", "payment schedule", "payment mode", "payment method",
            "payment on delivery", "advance payment", "partial payment", "payment policy",
            "invoice for", "quotation", "proposal", "contract", "search in emails",
            "sent a post", "salary upto", "walk-in drive", "job details", "order summary",
            "shopping stalls", "terms and conditions"
        )
    )

    override fun evaluate(text: String, entities: ExtractedEntities, config: ClassifierConfig): RuleResult {
        val strong = strongMatcher.countMatches(text)
        val weak = weakMatcher.countMatches(text)
        val neg = negMatcher.countMatches(text)
        val upiBonus = if (entities.upiIds.isNotEmpty()) config.paymentUpiBonus else 0
        val amtBonus = if (entities.amounts.isNotEmpty()) config.paymentAmountBonus else 0
        val score = strong * config.strongKeywordWeight + weak * config.weakKeywordWeight - (neg * config.strongNegativeWeight) + upiBonus + amtBonus

        // Require at least one confirmed transactional indicator (strong verb, or weak verb + amount, or upi + amount)
        val hasTransactionSignal = strong > 0 || (weak > 0 && entities.amounts.isNotEmpty()) || (entities.amounts.isNotEmpty() && entities.upiIds.isNotEmpty())
        val matched = score >= config.paymentThreshold && hasTransactionSignal

        return RuleResult(
            category = category,
            matched = matched,
            score = score,
            reason = "strong=$strong, weak=$weak, neg=$neg, upiBonus=$upiBonus, amtBonus=$amtBonus, txSignal=$hasTransactionSignal, score=$score, threshold=${config.paymentThreshold}"
        )
    }
}

class ReceiptRule : CategoryRule {
    override val category: Category = Category.RECEIPT
    override val priority: Int = 400

    private val strongMatcher = KeywordMatcher(
        listOf(
            "tax invoice", "gst invoice", "gstin", "gst no", "hsn code",
            "items ordered", "order summary", "order total", "bill amount",
            "subtotal", "total amount due", "billed to", "delivery address",
            "amazon order", "flipkart order", "swiggy order", "zomato order",
            "big basket", "myntra", "nykaa", "meesho", "auto ride",
            "blinkit", "zepto", "swiggy instamart", "instamart", "bigbasket",
            "bill details", "item total", "delivery partner tip", "platform fee",
            "taxes and charges", "packaging charges", "invoice value", "download invoice",
            "view invoice", "trip fare", "ride details"
        )
    )
    private val weakMatcher = KeywordMatcher(
        listOf(
            "invoice", "receipt", "order #", "order id", "grand total",
            "discount applied", "coupon applied", "delivery charge",
            "amazon.in", "flipkart", "swiggy", "zomato", "uber eats",
            "ola", "rapido", "dunzo", "blinkit", "zepto", "bb daily", "milkbasket",
            "uber", "namma yatri", "eatclub"
        )
    )
    private val fiscalMatcher = KeywordMatcher(
        listOf("gst", "tax", "hsn", "subtotal", "bill amount", "ride id", "total amount", "item total")
    )

    override fun evaluate(text: String, entities: ExtractedEntities, config: ClassifierConfig): RuleResult {
        val strong = strongMatcher.countMatches(text)
        val weak = weakMatcher.countMatches(text)
        val amtBonus = if (entities.amounts.isNotEmpty()) config.receiptAmountBonus else 0
        val score = strong * config.strongKeywordWeight + weak * config.weakKeywordWeight + amtBonus
        val hasFiscalSignal = entities.amounts.isNotEmpty() || fiscalMatcher.anyMatch(text)
        val matched = score >= config.receiptThreshold && hasFiscalSignal

        return RuleResult(
            category = category,
            matched = matched,
            score = score,
            reason = "strong=$strong, weak=$weak, amtBonus=$amtBonus, fiscal=$hasFiscalSignal, score=$score, threshold=${config.receiptThreshold}"
        )
    }
}

class ContactRule : CategoryRule {
    override val category: Category = Category.CONTACT
    override val priority: Int = 500

    private val strongMatcher = KeywordMatcher(
        listOf(
            "mobile:", "mobile no:", "contact:", "email:", "e-mail:",
            "tel:", "ph:", "phone:", "website:", "linkedin:", "address:",
            "designation:", "department:", "company name:", "contact us at",
            "search contacts", "call history", "add call", "call details",
            "video call", "call recorded", "outgoing:", "incoming:"
        )
    )
    private val weakMatcher = KeywordMatcher(
        listOf(
            "reach us", "reach out", "write to us", "call us", "whatsapp us",
            "contact number", "head office", "registered office"
        )
    )
    private val phoneScreenMatcher = KeywordMatcher(
        listOf(
            "search contacts", "call history", "add call", "call details",
            "video call", "call recorded", "outgoing:", "incoming:"
        )
    )

    override fun evaluate(text: String, entities: ExtractedEntities, config: ClassifierConfig): RuleResult {
        val strong = strongMatcher.countMatches(text)
        val weak = weakMatcher.countMatches(text)
        val emailBonus = if (entities.emails.isNotEmpty()) config.contactEmailBonus else 0
        val phoneBonus = if (entities.phoneNumbers.isNotEmpty()) config.contactPhoneBonus else 0
        val score = strong * config.strongKeywordWeight + weak * config.weakKeywordWeight + emailBonus + phoneBonus

        // Exclude narrative business emails/reports using zero-allocation word counter
        val wordCount = WordCounter.countMeaningfulWords(text)
        val isNarrativeDocument = text.length > 200 && (text.contains(".") || text.contains(",")) && wordCount >= 25

        val isLikelyContactCard = entities.emails.isNotEmpty() &&
                entities.phoneNumbers.isNotEmpty() &&
                text.length < config.contactCardMaxLength &&
                !isNarrativeDocument
        val isPhoneScreen = phoneScreenMatcher.anyMatch(text)
        val matched = (score >= config.contactThreshold && !isNarrativeDocument) || isLikelyContactCard || isPhoneScreen

        return RuleResult(
            category = category,
            matched = matched,
            score = score,
            reason = "strong=$strong, weak=$weak, emailBonus=$emailBonus, phoneBonus=$phoneBonus, contactCard=$isLikelyContactCard, phoneScreen=$isPhoneScreen, narrativeDoc=$isNarrativeDocument, score=$score, threshold=${config.contactThreshold}"
        )
    }
}

class CodeRule : CategoryRule {
    override val category: Category = Category.CODE
    override val priority: Int = 600

    private val strongMatcher = KeywordMatcher(
        listOf(
            "public static void main", "def __init__", "console.log(",
            "traceback (most recent", "exception in thread",
            "null pointer exception", "adb shell", "git commit", "npm run",
            "kubectl apply", "docker run", "sudo apt", "pip install",
            "cannot read property", "referenceerror", "syntaxerror", "uncaught error",
            "failed to push", "git@github.com", "fatal: remote error", "github.com:",
            "remote contains work", "updates were rejected"
        )
    )
    private val weakMatcher = KeywordMatcher(
        listOf(
            "class ", "fun ", "def ", "import ", "package ",
            "val ", "var ", "const ", "return ", "function(",
            "public static", "private static", "void ",
            "npm install", "yarn add", "git push", "git pull",
            "repository", "git clone", "git status", "git log"
        )
    )

    override fun evaluate(text: String, entities: ExtractedEntities, config: ClassifierConfig): RuleResult {
        val strong = strongMatcher.countMatches(text)
        val weak = weakMatcher.countMatches(text)
        val score = strong * config.strongKeywordWeight + weak * config.weakKeywordWeight
        val matched = score >= config.codeThreshold

        return RuleResult(
            category = category,
            matched = matched,
            score = score,
            reason = "strong=$strong, weak=$weak, score=$score, threshold=${config.codeThreshold}"
        )
    }
}

class ChatRule : CategoryRule {
    override val category: Category = Category.CHAT
    override val priority: Int = 700

    private val strongMatcher = KeywordMatcher(
        listOf(
            "typing...", "message delivered", "read receipts",
            "telegram", "instagram dm", "facebook messenger",
            "group admin", "you were added", "blocked this contact",
            "end-to-end encrypted", "disappearing messages",
            "yesterday at", "today at", "online now", "last seen today",
            "last seen yesterday"
        )
    )
    private val weakMatcher = KeywordMatcher(
        listOf(
            "yesterday", "today at", "typing", "online", "seen",
            "delivered", "unread", "reply", "forward message"
        )
    )
    private val negMatcher = KeywordMatcher(
        listOf(
            "mahametropune", "billeasy", "metro rail", "outward ticket", "ticket no",
            "screen time", "digital wellbeing", "unlocks", "app activity details",
            "salary upto", "walk-in drive", "job details", "search in emails"
        )
    )

    override fun evaluate(text: String, entities: ExtractedEntities, config: ClassifierConfig): RuleResult {
        val strong = strongMatcher.countMatches(text)
        val weak = weakMatcher.countMatches(text)
        val neg = negMatcher.countMatches(text)
        val score = strong * config.strongKeywordWeight + weak * config.weakKeywordWeight - (neg * config.strongNegativeWeight)
        val matched = score >= config.chatThreshold

        return RuleResult(
            category = category,
            matched = matched,
            score = score,
            reason = "strong=$strong, weak=$weak, neg=$neg, score=$score, threshold=${config.chatThreshold}"
        )
    }
}

class DocumentRule : CategoryRule {
    override val category: Category = Category.DOCUMENT
    override val priority: Int = 800

    override fun evaluate(text: String, entities: ExtractedEntities, config: ClassifierConfig): RuleResult {
        val wordCount = WordCounter.countMeaningfulWords(text)
        val hasSentenceStructure = text.contains(".") || text.contains(",") || text.contains("\n")
        val matched = text.length > config.documentMinLength && hasSentenceStructure && wordCount >= config.documentMinWords

        return RuleResult(
            category = category,
            matched = matched,
            score = wordCount,
            reason = "length=${text.length}, minLength=${config.documentMinLength}, wordCount=$wordCount, minWords=${config.documentMinWords}, punctuation=$hasSentenceStructure"
        )
    }
}

class OtherRule : CategoryRule {
    override val category: Category = Category.OTHER
    override val priority: Int = 900

    override fun evaluate(text: String, entities: ExtractedEntities, config: ClassifierConfig): RuleResult {
        return RuleResult(
            category = category,
            matched = true,
            score = 0,
            reason = "Default fallback"
        )
    }
}
