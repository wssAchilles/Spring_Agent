---
description: "Spawns parallel subagents to deeply read a codebase from multiple angles and produce a structured report. Trigger: /deep-read [path] [focus...]"
---

# Deep Read — Multi-agent Codebase Analysis

You are launching a deep multi-agent reading of a codebase. The goal is to understand the project thoroughly from multiple angles in parallel.

## Input

- **Target path**: `$1` (defaults to current directory if not given)
- **Focus areas**: `$2..$N` (optional; if omitted, scan all dimensions)

## Procedure

1. **Discover the project structure**
   - List the top-level directory, key config files (package.json, pom.xml, Cargo.toml, pyproject.toml, go.mod, etc.)
   - Identify the tech stack, language, framework
   - Read README.md, AGENT_GUIDELINES.md, or any docs/ if present

2. **Spawn parallel subagents** — one per focus area. Use the Task tool with `subagent_type: "explore"` or direct Task spawns. Each subagent gets a focused read directive:

   | Focus Area | What the Subagent Reads |
   |------------|------------------------|
   | **architecture** | Module structure, entry points, dependency graph, build config |
   | **backend** | Controllers, services, data layer, API endpoints, middleware |
   | **frontend** | Routes, pages, components, state management, API calls |
   | **database** | Schema, migrations, ORM models, key queries |
   | **security** | Auth flow, permissions, token handling, secrets management |
   | **deployment** | Docker, CI/CD, scripts, environment config |
   | **docs** | README, plans, design docs, ADRs, changelogs |
   | **tests** | Test structure, coverage, test patterns |
   | **ai** | AI/ML integration, model calls, embeddings, vector stores |
   | **tools** | Tool definitions, MCP adapters, plugin systems |

   If the user specified focus areas, spawn only those. Otherwise, spawn all that are relevant to the discovered tech stack (e.g., skip "frontend" for a pure CLI tool).

3. **Each subagent returns** a structured summary:
   - Key files and their roles
   - Core patterns and conventions
   - Notable design decisions
   - Potential issues or technical debt

4. **Synthesize** all subagent reports into a single coherent overview:
   - Project purpose and architecture (1-2 paragraphs)
   - Tech stack table
   - Module/directory map with descriptions
   - Key entry points and data flows
   - Notable patterns and conventions
   - Issues or areas for improvement

## Output

Present the synthesized report to the user. Do not ask follow-up questions unless the user's original request included specific questions to answer after reading.

## Example Usage

```
/deep-read                                    # scan current directory, all dimensions
/deep-read ./backend                          # scan backend only
/deep-read . architecture backend database    # specific focus areas
/deep-read . "以产品经理视角介绍所有功能"        # custom angle passed to subagents
```
