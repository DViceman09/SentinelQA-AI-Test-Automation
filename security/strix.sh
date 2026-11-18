#!/usr/bin/env bash
# Strix AI pentest agents against ShopLite: white-box (reads ./shoplite source) + black-box (attacks the running app).
# Needs: Docker running, ShopLite up (docker compose up -d --wait), Strix installed
#   (curl -sSL https://strix.ai/install | bash  -- Linux/macOS/WSL), and an LLM endpoint in .env.
# Findings land in ./strix_runs/<run>. Exit code is non-zero when vulnerabilities are found.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then set -a; . ./.env; set +a; fi

# Reuse the same OpenAI-compatible endpoint as the Java AI features (OmniRoute, OpenRouter, Groq, Ollama...).
export LLM_API_BASE="${LLM_API_BASE:-${AI_BASE_URL:-}}"
export LLM_API_KEY="${LLM_API_KEY:-${AI_API_KEY:-}}"
export STRIX_LLM="${STRIX_LLM:-openai/${AI_MODEL:-auto}}"
export STRIX_API_TYPE="${STRIX_API_TYPE:-chat_completions}" # gateways like OmniRoute only serve chat completions
: "${LLM_API_BASE:?Set AI_BASE_URL (or LLM_API_BASE) in .env}"

# Strix's tools run inside a Docker sandbox, where "localhost" is the sandbox itself.
TARGET_URL="${TARGET_URL:-http://host.docker.internal:8080}"

strix -n --scan-mode "${SCAN_MODE:-quick}" -t ./shoplite -t "$TARGET_URL" --instruction "\
ShopLite is a demo web shop (Spring Boot + PostgreSQL). Test accounts: alice/alice123 and bob/bob123. \
OpenAPI spec: $TARGET_URL/v3/api-docs. Prioritise OWASP Top 10: injection in product search, \
broken access control between customers' orders, authentication weaknesses and missing security headers."
