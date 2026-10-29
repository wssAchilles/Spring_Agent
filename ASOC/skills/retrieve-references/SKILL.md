---
name: retrieve-references
description: Retrieve grounded BibTeX references from Semantic Scholar Graph API, OpenAlex, or Google Scholar (`scholarly`) and write references.bib plus provenance JSON for paper generation.
---

# What the skill is about
Fetch references from Semantic Scholar Graph API, OpenAlex, or Google Scholar (via `scholarly`) and keep only real BibTeX entries.

# When to use the skill
- Final report/paper generation.
- Related-work section needs grounded citations.

# What to do
1. Build focused search queries from task goal, method names, and top experiment ideas.
2. Preferred path (Semantic Scholar Graph API):
```bash
python scripts/s2_fetch.py \
  --query "query one" \
  --query "query two" \
  --min-refs 8 \
  --max-refs 15 \
  --out-bib references.bib \
  --out-json references_provenance.json
```
3. Fallback path (OpenAlex):
```bash
python scripts/openalex_fetch.py \
  --query "query one" \
  --query "query two" \
  --min-refs 8 \
  --max-refs 15 \
  --search-limit 10 \
  --out-bib references.bib \
  --out-json references_provenance.json
```
   Optional:
   - `--mailto you@example.com` to enter the OpenAlex polite pool
   - Env fallback: `OPENALEX_EMAIL`
4. Alternate path (Google Scholar API wrapper with `scholarly`):
```bash
python -m pip install scholarly
python scripts/scholarly_fetch.py \
  --query "query one" \
  --query "query two" \
  --min-refs 8 \
  --max-refs 15 \
  --search-limit 25 \
  --sort-by relevance \
  --proxy-mode auto \
  --delay-seconds 0.5 \
  --out-bib references.bib \
  --out-json references_provenance.json
```
   Proxy options:
   - `--proxy-mode auto|none|free|single`
   - `--proxy-http <url>` / `--proxy-https <url>` for `single`
   - Env fallbacks: `SCHOLARLY_PROXY_MODE`, `SCHOLARLY_PROXY_HTTP`, `SCHOLARLY_PROXY_HTTPS`
   - Global `HTTP_PROXY` / `HTTPS_PROXY` are intentionally ignored to avoid accidental ambient proxying
5. Require `S2_API_KEY` when available for `s2_fetch.py` (script reads env automatically).
6. Keep only references with valid BibTeX and citation key.
7. Dedupe by citation key and paper id.
8. Fail hard if references found `< min-refs`.
9. Use `s2_fetch.py` first for stability, `openalex_fetch.py` as the next fallback, and `scholarly_fetch.py` last if the other sources fail.

# Outputs
- `references.bib`
- `references_provenance.json`

# Notes
- Use `templates/provenance.schema.json` for expected shape.
