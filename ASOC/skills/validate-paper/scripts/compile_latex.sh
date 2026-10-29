#!/bin/bash
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: bash compile_latex.sh <paper.tex>" >&2
  exit 2
fi

TEX_ARGUMENT="$1"
if [ ! -f "$TEX_ARGUMENT" ]; then
  echo "Missing TeX file: $TEX_ARGUMENT" >&2
  exit 2
fi

if ! command -v pdflatex >/dev/null 2>&1; then
  echo "Missing required command: pdflatex" >&2
  exit 2
fi
if ! command -v bibtex >/dev/null 2>&1; then
  echo "Missing required command: bibtex" >&2
  exit 2
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "Missing required command: python3" >&2
  exit 2
fi
if ! command -v pdftotext >/dev/null 2>&1; then
  echo "Missing required command: pdftotext" >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PAPER_DIR="$(cd "$(dirname "$TEX_ARGUMENT")" && pwd)"
TEX_FILE="$(basename "$TEX_ARGUMENT")"
BASE="${TEX_FILE%.tex}"
RECORDER_FILE="${BASE}.fls"
AUX_FILE="${BASE}.aux"

cd "$PAPER_DIR"

run_sanitized() {
  python3 "$SCRIPT_DIR/validate_compile_inputs.py" run -- "$@"
}

run_pdflatex() {
  rm -f -- "$RECORDER_FILE"
  set +e
  run_sanitized pdflatex \
    -recorder -no-shell-escape -interaction=nonstopmode -halt-on-error "$TEX_FILE"
  local return_code=$?
  set -e
  if [ -f "$RECORDER_FILE" ]; then
    python3 "$SCRIPT_DIR/validate_compile_inputs.py" recorder \
      --paper-dir "$PAPER_DIR" --fls "$RECORDER_FILE"
  elif [ "$return_code" -eq 0 ]; then
    echo "LaTeX recorder output missing: $PAPER_DIR/$RECORDER_FILE" >&2
    return 1
  fi
  return "$return_code"
}

run_pdflatex
python3 "$SCRIPT_DIR/validate_compile_inputs.py" bibtex \
  --paper-dir "$PAPER_DIR" --aux "$AUX_FILE"
run_sanitized bibtex "$BASE"
run_pdflatex
run_pdflatex
python3 "$SCRIPT_DIR/validate_rendered_si.py" --pdf "${BASE}.pdf"
