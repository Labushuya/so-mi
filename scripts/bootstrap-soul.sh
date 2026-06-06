#!/usr/bin/env bash
# scripts/bootstrap-soul.sh
#
# One-time, manual, optional. Generates knowledge/initial-memory.json by
# asking Claude (with prompt-caching on the soul.md prefix) to:
#   - Generate ~80 atomare Persona-Fakten als Memory-JSON
#   - Generate ~100 erwartbare Frage-Antwort-Paare im So-Mi-Stil
#   - Schlage Tag-Taxonomie für Notes vor
#   - Liste 50 typische Alltagssituations-Reaktionen
#
# The output is loaded into ObjectBox on first launch (see SPEC §8 →
# "Bootstrapping mit Claude").
#
# Phase 1: SCAFFOLD ONLY — refuses to run, prints a clear message. Phase 3
# wires the actual Anthropic-SDK call. Keeping the file in place so the
# script-tree stays stable across phases.
set -euo pipefail

cat >&2 <<'EOF'
bootstrap-soul.sh is a Phase 3 deliverable (SPEC §12).

Required when implemented:
  - $ANTHROPIC_API_KEY in env
  - soul/soul.md committed (used as prompt-cached prefix)
  - knowledge/seeds/*.md committed (initial seed knowledge)

Today this script is a no-op stub. Wire the Claude API call in Phase 3
("RAG + Persona-Memory") and have it write knowledge/initial-memory.json.

Refusing to run.
EOF
exit 1
