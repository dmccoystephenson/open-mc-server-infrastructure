#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
    echo "ERROR: .env not found — copy sample.env to .env and configure it." >&2
    exit 1
fi

docker compose up -d --build

echo "Services started — run 'docker compose ps' to check status."
