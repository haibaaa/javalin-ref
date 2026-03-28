
TUI-FRIENDLY MARKDOWN WRITING GUIDELINES
========================================

These guidelines define how Markdown documents should be structured so they
render cleanly in terminal-based readers such as:

  glow
  bat
  less
  mdcat
  neovim
  vim
  ranger previews

The goal is readability within a typical terminal width (80–100 columns),
minimal horizontal scrolling, and clean visual hierarchy.

---------------------------------------------------------------------

1. TERMINAL WIDTH CONSTRAINTS
-----------------------------

Target width:
    80–100 characters per line

Recommended:
    72–90 characters

Rules:
    - Wrap long paragraphs.
    - Avoid long inline code blocks.
    - Avoid wide tables.
    - Never rely on horizontal scrolling.

Example (good):

    This paragraph wraps naturally and fits inside a standard
    terminal window without forcing the reader to scroll
    horizontally.

Example (bad):

    This paragraph is extremely long and extends beyond the typical width of a terminal which forces horizontal scrolling and ruins readability in TUI markdown viewers.

---------------------------------------------------------------------

2. HEADING STRUCTURE
--------------------

Use a clear hierarchy.

Allowed levels:

    # Title
    ## Section
    ### Subsection
    #### Minor subsection

Avoid deeper levels.

Rules:
    - Leave one blank line before and after headings.
    - Use sentence case or title case consistently.

Example:

    # Software Design

    ## Cohesion

    ### Types of Cohesion

---------------------------------------------------------------------

3. SECTION SEPARATORS
---------------------

Use horizontal separators to break major sections.

Preferred forms:

    ---
    -----
    ===== (rare)

Example:

    ---
    ## Coupling

---------------------------------------------------------------------

4. PARAGRAPH FORMATTING
-----------------------

Rules:

    - Keep paragraphs short (3–6 lines).
    - Separate paragraphs with one blank line.
    - Avoid dense text blocks.

Good:

    Modularity improves maintainability.

    It allows developers to understand the system
    piece by piece rather than all at once.

Bad:

    A huge block of text that continues for many lines without breaks
    reduces readability significantly in terminal viewers.

---------------------------------------------------------------------

5. LISTS
--------

Use lists heavily because they render cleanly in TUIs.

Preferred bullet:

    -

Avoid:

    *
    +

Example:

    Advantages of modularity:

    - Improved readability
    - Reduced complexity
    - Better testing
    - Reusability

Numbered lists:

    1. First step
    2. Second step
    3. Third step

---------------------------------------------------------------------

6. TABLES
---------

Avoid wide tables because they break in terminals.

Bad:

    | Feature | Description | Example | Notes |
    |--------|-------------|---------|------|

Instead convert tables to structured lists:

    Feature: Cohesion
    Meaning: Strength of relationship within a module
    Example: calculateAverage()

If tables are necessary:

    - Limit to 2–3 columns
    - Keep cells short
    - Ensure total width < 90 characters

---------------------------------------------------------------------

7. CODE BLOCKS
--------------

Use fenced code blocks.

    ```language
    code
    ```

Rules:

    - Keep lines under 90 characters
    - Prefer monospace examples
    - Avoid wrapping inside code blocks

Example:

    ```
    readData → processData → printResults
    ```

---------------------------------------------------------------------

8. INLINE CODE
--------------

Use backticks for:

    - commands
    - identifiers
    - functions
    - filenames

Example:

    The function `calculateRMS()` computes the result.

---------------------------------------------------------------------

9. ASCII DIAGRAMS
-----------------

Prefer ASCII diagrams instead of images.

Reason:
    Images render poorly in TUIs.

Example:

    Afferent → Central Transform → Efferent

Or:

    Input
      |
      v
    [Validate]
      |
      v
    [Compute]
      |
      v
    Output

---------------------------------------------------------------------

10. EMPHASIS
------------

Use minimal emphasis.

Allowed:

    **bold**
    `inline code`

Avoid:

    excessive italics
    nested formatting

Example:

    **Important:** DFDs show data flow only.

---------------------------------------------------------------------

11. QUOTES AND NOTES
--------------------

Use block quotes for tips or definitions.

Example:

    > Important:
    > A context diagram must contain exactly one bubble.

---------------------------------------------------------------------

12. DATA DEFINITIONS
--------------------

Structured definitions work well in TUI.

Example:

    numbers = a + b + c

    a : integer
        first input number

    b : integer
        second input number

---------------------------------------------------------------------

13. FILE ORGANIZATION
---------------------

Large documents should contain clear top sections.

Example layout:

    # Title

    ## Overview

    ## Concepts

    ## Examples

    ## Rules

    ## Summary

---------------------------------------------------------------------

14. CONSISTENT SPACING
----------------------

Use consistent whitespace.

Rules:

    - One blank line after headings
    - One blank line between paragraphs
    - One blank line before lists

Avoid dense layouts.

---------------------------------------------------------------------

15. TERMINAL COMPATIBILITY CHECK
--------------------------------

Before finalizing a TUI markdown document,
preview it in a terminal viewer.

Recommended test commands:

    glow file.md
    bat file.md
    less file.md
    nvim file.md

Verify:

    - no horizontal scrolling
    - readable hierarchy
    - diagrams aligned
    - lists formatted correctly

---------------------------------------------------------------------

16. OPTIMAL DOCUMENT STYLE
--------------------------

A good TUI markdown document should:

    - fit within 80–100 columns
    - prefer lists over tables
    - use ASCII diagrams
    - maintain consistent heading hierarchy
    - avoid large text blocks
    - remain readable in plain text without rendering

If the Markdown still reads well in `less`,
then it is properly TUI-friendly.

---------------------------------------------------------------------

END OF GUIDELINES
---------------------------------------------------------------------
