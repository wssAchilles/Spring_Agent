---
description: "Compiles a LaTeX document, identifies all overfull/underfull box warnings, and fixes them. Trigger: /fix-latex-overflow [tex-file-or-dir]"
---

# Fix LaTeX Overflow — Overfull/Underfull Box Repair

You are fixing all overfull and underfull box warnings in a LaTeX project.

## Input

- **Target**: `$1` — a `.tex` file, or a directory containing `.tex` files (defaults to current directory)
- If a directory is given, find the main `.tex` file (the one with `\documentclass`) and compile that

## Procedure

1. **Locate the main .tex file**
   - If `$1` is a file, use it directly
   - If `$1` is a directory, find the main document: `grep -rl '\\documentclass' *.tex | head -1`
   - Read the preamble to understand the document class, packages, and custom commands

2. **First compilation pass**
   - Run: `xelatex -interaction=nonstopmode <main>.tex 2>&1`
   - If xelatex is not available, try `pdflatex` or `lualatex`
   - Capture the full log output

3. **Parse warnings**
   - Extract all lines matching `Overfull \hbox`, `Overfull \vbox`, `Underfull \hbox`, `Underfull \vbox`
   - For each warning, record: file, line number, box type, excess amount, offending content
   - Group warnings by source file

4. **Fix each warning** (prioritize by severity — largest overflows first)

   Common fixes:
   - **Overfull \hbox** (line too long):
     - Add `\\` or `\linebreak` at natural break points
     - Use `\sloppy` or `\fussy` locally
     - Adjust `\emergencystretch`
     - For URLs/long words: use `\url{}`, `\path{}`, or `breaklinks` option
     - For code listings: use `breaklines=true` in `lstlisting`/`minted`
     - For tables: use `tabularx`, `p{}` columns, or `\resizebox`
     - For images: reduce width or use `\includegraphics[width=\textwidth]`
   - **Underfull \hbox** (line too short):
     - Usually caused by forced `\\` — remove unnecessary line breaks
     - Adjust `\tolerance` or `\emergencystretch`
     - For justified text, ensure proper hyphenation patterns
   - **Overfull \vbox** (page overflow):
     - Reduce content, adjust `\textheight`, or use `\enlargethispage`
   - **Underfull \vbox** (page underfull):
     - Usually benign; add `\vfill` or adjust `\pagebreak` placement

5. **Second compilation pass**
   - Recompile to verify all warnings are resolved
   - If warnings remain, iterate (max 3 rounds)

6. **Report**
   - List all warnings found and fixes applied
   - Note any warnings that could not be automatically fixed (manual intervention needed)
   - Confirm final compilation output (pages generated, no errors)

## Output

A summary table:
| File | Line | Warning | Fix Applied |
|------|------|---------|-------------|
| chap01.tex | 42 | Overfull \hbox (15pt) | Added \linebreak |

## Example Usage

```
/fix-latex-overflow                          # fix .tex files in current directory
/fix-latex-overflow thesis.tex               # fix a specific file
/fix-latex-overflow ./docs/report/           # fix main doc in a directory
```
