#!/usr/bin/env bash
# Redeploy JobMaster on the server: pull latest code, rebuild, restart.
# Run from the project root on the VM. See docs/deployment-plan.md.
set -euo pipefail
cd "$(dirname "$0")"

if [ -d .git ]; then
  git pull
else
  echo "No git repo here — skipping git pull. Copy new files onto the server yourself, then re-run this script." >&2
fi

docker compose -f compose.yaml -f compose.prod.yaml up -d --build
docker compose ps
