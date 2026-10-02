package com.littech.screenintel.core.classifier

import com.littech.screenintel.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class GoldenCase(
    val id: String,
    val expectedCategory: Category,
    val text: String,
    val description: String
)

data class CaseResult(
    val goldenCase: GoldenCase,
    val predictedCategory: Category,
    val entities: ExtractedEntities,
    val isCorrect: Boolean
)

class CategoryClassifierGoldenTest {

    @Test
    fun evaluateGoldenCorpusBaseline() {
        val cases = loadCorpus()
        assertTrue("Corpus must contain at least 40 cases", cases.size >= 40)

        // Verify at least 5 cases per category
        val categoryCounts = cases.groupBy { it.expectedCategory }
        Category.values().forEach { cat ->
            val count = categoryCounts[cat]?.size ?: 0
            assertTrue("Category $cat must have at least 5 cases, had $count", count >= 5)
        }

        val results = cases.map { case ->
            val entities = CategoryClassifier.extractEntities(case.text)
            val predicted = CategoryClassifier.classify(case.text, entities)
            CaseResult(
                goldenCase = case,
                predictedCategory = predicted,
                entities = entities,
                isCorrect = predicted == case.expectedCategory
            )
        }

        val total = results.size
        val correct = results.count { it.isCorrect }
        val accuracy = correct.toDouble() / total

        val report = buildString {
            appendLine("\n" + "=".repeat(80))
            appendLine("                      CATEGORY CLASSIFIER GOLDEN REPORT")
            appendLine("=".repeat(80))
            appendLine("Total Evaluated: $total | Correct: $correct | Overall Accuracy: ${"%.2f".format(accuracy * 100)}%\n")

            // Per Category Metrics
            appendLine("%-12s | %-8s | %-8s | %-8s | %-8s | %-8s".format("Category", "Support", "Correct", "Precision", "Recall", "F1 Score"))
            appendLine("-".repeat(70))

            val allCategories = Category.values()
            allCategories.forEach { cat ->
                val support = results.count { it.goldenCase.expectedCategory == cat }
                val tp = results.count { it.goldenCase.expectedCategory == cat && it.predictedCategory == cat }
                val fp = results.count { it.goldenCase.expectedCategory != cat && it.predictedCategory == cat }
                val fn = results.count { it.goldenCase.expectedCategory == cat && it.predictedCategory != cat }

                val precision = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
                val recall = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
                val f1 = if (precision + recall > 0) 2 * (precision * recall) / (precision + recall) else 0.0

                appendLine(
                    "%-12s | %-8d | %-8d | %-8.1f%% | %-8.1f%% | %-8.2f".format(
                        cat.name,
                        support,
                        tp,
                        precision * 100,
                        recall * 100,
                        f1
                    )
                )
            }

            // Confusion Matrix
            appendLine("\n" + "-".repeat(80))
            appendLine("CONFUSION MATRIX (Rows: Expected, Columns: Predicted)")
            appendLine("-".repeat(80))
            val header = "%-12s".format("") + allCategories.joinToString("") { "%-6s".format(it.name.take(5)) }
            appendLine(header)

            allCategories.forEach { rowCat ->
                val rowCounts = allCategories.map { colCat ->
                    results.count { it.goldenCase.expectedCategory == rowCat && it.predictedCategory == colCat }
                }
                appendLine("%-12s".format(rowCat.name) + rowCounts.joinToString("") { "%-6d".format(it) })
            }

            // Misclassified Cases
            val failures = results.filter { !it.isCorrect }
            appendLine("\n" + "-".repeat(80))
            appendLine("MISCLASSIFICATIONS (${failures.size} cases):")
            appendLine("-".repeat(80))
            failures.forEach { res ->
                val case = res.goldenCase
                appendLine(
                    "[MISMATCH] [${case.id}] Expected: ${case.expectedCategory} -> Predicted: ${res.predictedCategory}"
                )
                appendLine("   Desc    : ${case.description}")
                appendLine("   Entities: upi=${res.entities.upiIds}, amt=${res.entities.amounts}, pnr=${res.entities.pnrNumbers}, email=${res.entities.emails}, phone=${res.entities.phoneNumbers}, otp=${res.entities.otps}")
                appendLine("   Snippet : \"${case.text.replace("\n", " ").take(90)}...\"")
                appendLine()
            }
            appendLine("=".repeat(80))
        }

        println(report)

        // ── Automated CI/CD Regression Guardrails ──
        // 1. Minimum overall accuracy threshold (>= 98%)
        assertTrue(
            "Regression Guard: Overall accuracy must be >= 98.0%, was ${"%.2f".format(accuracy * 100)}%",
            accuracy >= 0.98
        )

        // 2. Strict zero-error policy on critical sensitive categories (CREDENTIAL, PAYMENT, TICKET)
        val criticalCategories = listOf(Category.CREDENTIAL, Category.PAYMENT, Category.TICKET)
        criticalCategories.forEach { criticalCat ->
            val criticalSupport = results.count { it.goldenCase.expectedCategory == criticalCat }
            val criticalCorrect = results.count { it.goldenCase.expectedCategory == criticalCat && it.predictedCategory == criticalCat }
            val criticalFalsePositives = results.count { it.goldenCase.expectedCategory != criticalCat && it.predictedCategory == criticalCat }

            assertEquals(
                "Regression Guard: Category $criticalCat recall failed (must be 100%)",
                criticalSupport,
                criticalCorrect
            )
            assertEquals(
                "Regression Guard: Category $criticalCat had false positives (precision must be 100%)",
                0,
                criticalFalsePositives
            )
        }
    }

    @Test
    fun benchmarkClassificationLatency() {
        val sampleTexts = listOf(
            "Paid successfully to Rahul Sharma ₹450.00 UPI Ref No 328491829101 Paid using PhonePe rahul@ybl",
            "Your OTP for SBI Netbanking is 492018. Valid for 10 minutes. Do not share this with anyone.",
            "Pune Metro Rail Ticket Details Outward Ticket Pune Metro MahaMetroPune billeasy.in ticket no: PM-9481029 From: Civil Court To: Vanaz Fare: Rs 20",
            "Amazon.in Tax Invoice GSTIN: 27AABCT3518Q1Z8 Order Summary Items Ordered: Wireless Mouse Bill Amount: ₹699.00 Subtotal: ₹699.00 Total Amount Due: ₹699.00 Billed to: John Doe",
            "Amit Verma Chief Technology Officer Acme Tech Solutions Mobile: +91 9823456789 Email: amit.verma@acmetech.com Address: Hinjawadi Phase 1, Pune",
            "public static void main(String[] args) {\n    System.out.println(\"Hello World\");\n    try {\n        doAction();\n    } catch (Exception e) {\n        e.printStackTrace();\n    }\n}",
            "Ramesh: Are you coming today?\nSuresh: Yes, leaving in 5 mins.\nToday at 4:15 PM\nMessage delivered\nTyping...",
            "Walk-in drive for Senior Android Developer at Cognizant Pune. Salary upto 18 LPA. Candidate must have 4+ years of experience with Kotlin and Jetpack Compose. Job details: Send resume to careers@cognizant.com. Walk-in date: 15th October."
        )

        // Warm up JIT compiler
        repeat(50) {
            sampleTexts.forEach { text ->
                val entities = CategoryClassifier.extractEntities(text)
                CategoryClassifier.classify(text, entities)
            }
        }

        // Benchmark 200 iterations over 8 diverse screenshots (1,600 total operations)
        val iterations = 200
        val startTime = System.nanoTime()

        repeat(iterations) {
            sampleTexts.forEach { text ->
                val entities = CategoryClassifier.extractEntities(text)
                CategoryClassifier.classify(text, entities)
            }
        }

        val totalTimeNanos = System.nanoTime() - startTime
        val totalOperations = iterations * sampleTexts.size
        val avgLatencyMicros = (totalTimeNanos / totalOperations) / 1000.0
        val avgLatencyMs = avgLatencyMicros / 1000.0

        println("\n" + "=".repeat(60))
        println("         CLASSIFIER IN-MEMORY LATENCY BENCHMARK")
        println("=".repeat(60))
        println("Total Operations : $totalOperations")
        println("Average Latency  : ${"%.2f".format(avgLatencyMicros)} µs (${"%.3f".format(avgLatencyMs)} ms) per screenshot")
        println("Throughput       : ${(1_000_000.0 / avgLatencyMicros).toInt()} screenshots/sec")
        println("=".repeat(60))

        // Ensure latency is strictly under 5 milliseconds per screenshot
        assertTrue(
            "Classification latency must be under 5ms, was ${avgLatencyMs}ms",
            avgLatencyMs < 5.0
        )
    }

    private fun loadCorpus(): List<GoldenCase> {
        val inputStream = javaClass.classLoader?.getResourceAsStream("golden/corpus.jsonl")
            ?: File("src/test/resources/golden/corpus.jsonl").takeIf { it.exists() }?.inputStream()
            ?: File("app/src/test/resources/golden/corpus.jsonl").takeIf { it.exists() }?.inputStream()
            ?: throw IllegalStateException("Could not locate golden/corpus.jsonl")

        val list = mutableListOf<GoldenCase>()
        BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("//")) {
                    val id = parseJsonString(trimmed, "id")
                    val expectedCategoryStr = parseJsonString(trimmed, "expectedCategory")
                    val text = parseJsonString(trimmed, "text")
                    val description = parseJsonString(trimmed, "description")

                    list.add(
                        GoldenCase(
                            id = id,
                            expectedCategory = Category.valueOf(expectedCategoryStr),
                            text = text,
                            description = description
                        )
                    )
                }
            }
        }
        return list
    }

    private fun parseJsonString(json: String, key: String): String {
        val regex = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val match = regex.find(json) ?: throw IllegalArgumentException("Key $key not found in JSON: $json")
        return match.groupValues[1]
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
    }
}
