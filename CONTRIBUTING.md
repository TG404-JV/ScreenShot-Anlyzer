# 🤝 Contributing to ScreenShot-Analyzer

First off, thank you for considering contributing to **ScreenShot-Analyzer**! 🎉 

ScreenShot-Analyzer is a 100% on-device, privacy-first Android app built to turn chaotic screenshot folders into a searchable, actionable personal knowledge graph.

Whether you want to fix a bug, add support for a new language, improve classification rules, optimize performance, or enhance UI animations, your contributions are warmly welcomed!

---

## 🛠️ Tech Stack & Philosophy

- **Zero Network**: The app strictly omits `android.permission.INTERNET`. Every PR must maintain this 100% offline, on-device privacy guarantee.
- **Language**: Kotlin 2.0+
- **UI Framework**: Modern Jetpack Compose & Material 3
- **Navigation**: `androidx.navigation3`
- **Database**: Custom lightweight SQLiteOpenHelper with FTS4 virtual tables (Zero external ORM overhead)
- **OCR Engine**: Google ML Kit On-Device Text Recognition
- **Architecture**: MVVM + Clean Architecture with Coroutines & StateFlow

---

## 🎯 Ways You Can Contribute

### 1. 🏷️ Add or Improve Categorization Rules (`CategoryClassifier`)
Our rule engine categorizes screenshots into 9 categories (Payments, Tickets, Receipts, Credentials, Contacts, Code, Chats, Documents, Other).
- Want to add support for regional receipts, international flight tickets, or new payment apps?
- Check out [`CategoryRules.kt`](app/src/main/java/com/littech/screenintel/core/classifier/rules/CategoryRules.kt) and add unit tests in [`CategoryClassifierGoldenTest.kt`](app/src/test/java/com/littech/screenintel/core/classifier/CategoryClassifierGoldenTest.kt).

### 2. 🛡️ Expand PII Sanitization Patterns (`PiiRedactor`)
- Support for regional ID formats (e.g., SSN, Driving License formats, Passport numbers).
- Additional regexes for financial identifiers and security tokens.

### 3. 📱 UI / UX & Jetpack Compose Improvements
- Quick Settings Tile: 1-tap trigger to index or search.
- Home screen glanceable App Widget (`androidx.glance`).
- Shared Element Transitions using Navigation 3.
- Dark / Dynamic theme refinements.

### 4. 📤 Export & Knowledge Graph Integrations
- Export selected screenshots & extracted text to Markdown / Obsidian vault / JSON.
- Share multiple extracted text snippets.

### 5. 🧪 OEM Device Testing & Edge Cases
- Test on Samsung OneUI, OnePlus OxygenOS, Xiaomi MIUI/HyperOS, Google Pixel, etc.
- Report or fix path discrepancies in `MediaStoreScanner.kt`.

---

## 💻 Development Setup

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1+) or Koala
- **JDK**: Java 17+
- **Android SDK**: API 36 Platform & Build Tools
- **Physical Device or Emulator**: Android 8.0+ (API 26+)

### Step-by-Step Guide

1. **Fork the Repository**:
   Click the **Fork** button on [GitHub](https://github.com/TG404-JV/ScreenShot-Anlyzer).

2. **Clone your fork**:
   ```bash
   git clone https://github.com/YOUR_USERNAME/ScreenShot-Anlyzer.git
   cd ScreenShot-Anlyzer
   ```

3. **Create a Feature Branch**:
   ```bash
   git checkout -b feat/your-feature-name
   # or
   git checkout -b fix/issue-description
   ```

4. **Verify the Project Builds**:
   ```bash
   ./gradlew assembleDebug
   ```

5. **Run Existing Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

6. **Make your changes & Add Tests**:
   Ensure all existing and new tests pass with:
   ```bash
   ./gradlew testDebugUnitTest
   ```

7. **Commit & Push**:
   ```bash
   git add .
   git commit -m "feat(classifier): add support for international boarding passes"
   git push origin feat/your-feature-name
   ```

8. **Open a Pull Request**:
   Go to your fork on GitHub and click **"Compare & pull request"**. Describe what your PR accomplishes and link any relevant issues!

---

## 📜 Code Style & Commit Conventions

- Follow standard [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use conventional commit messages:
  - `feat:` New features or enhancements
  - `fix:` Bug fixes
  - `perf:` Performance optimizations
  - `test:` Adding or updating tests
  - `docs:` Documentation improvements
  - `refactor:` Code cleanup without behavioral changes

---

## 💬 Community & Questions

Have an idea before writing code? Feel free to open an **[Issue](https://github.com/TG404-JV/ScreenShot-Anlyzer/issues)** or start a discussion on GitHub!

Thank you for helping make on-device personal memory faster, safer, and open for everyone! 🚀
