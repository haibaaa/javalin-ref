# QWEN.md: Repository Documentation Protocol

## 🎯 Objective
Create a high-level, readable documentation mirror of this repository within a root-level `/docs` directory. The goal is to explain **how** the code works by breaking files into logical blocks and functional units.

---

## 🏗️ Structural Rules
1.  **Isolation:** All generated files **must** reside within the `/docs` directory.
2.  **Mirroring:** Replicate the repository's directory tree exactly inside `/docs`. 
    * *Example:* `src/main/App.java` -> `docs/src/main/App.java.md`.
3.  **No Pollution:** Strictly avoid modifying any source code files.
4.  **Exclusions:** Ignore `md_generation_guidelines.md`, the `/docs` folder, build artifacts (`target/`, `bin/`), and hidden system files.

---

## 📝 Documentation Requirements
Each generated `.md` file must follow this hierarchical structure:

### 1. Header & Executive Summary
* **Title:** `# Documentation: [Original Filename]`
* **Responsibility:** A 2-3 sentence summary of what this file does within the context of the larger system.

### 2. Logical Block Analysis
Instead of line-by-line tables, group the code into **Functional Chunks** (e.g., "Dependency Injection," "Routing Table," "Helper Methods").

For each block, provide:
* **Purpose:** What is this section trying to achieve?
* **Code Range:** The logical start and end (e.g., `Lines 48-107: API Routing`).
* **Technical Breakdown:** A concise explanation of the logic flow, key variables used, and how it interacts with other parts of the system.
* **Complexity Note:** If a function or block is exceptionally long/complex, break it into "Part A, B, C" sub-sections.

### 3. Dependency & Cross-Reference (The "Man Page" Style)
Identify every internal class or service used.
* Provide a direct relative Markdown link to its documentation.
* *Example:* "Uses `SecurityService` to validate tokens. See [SecurityService.java](docs/src/main/java/com/tccs/security/SecurityService.java.md)."

### 4. Standards Compliance
* **Tone:** Professional, technical, and clear.
* **Formatting:** Must strictly follow the styles defined in `md_generation_guidelines.md`.

---

## 🚀 Execution Logic
1.  **Scan:** Identify the directory level.
2.  **Parse:** Read the file and identify logical "breakpoints" (annotations, method signatures, or comments like `// 1. Initialize`).
3.  **Generate:** Build the `.md` using the Block Analysis method.
4.  **Recurse:** Move to the next subdirectory level.

---

**Safe Mode:** If a file is too large for the context window, summarize the overall architecture first, then process major blocks in separate turns to maintain high detail without losing coherence.
