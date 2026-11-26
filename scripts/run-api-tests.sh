#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

CREATED_ENV=false
if [[ ! -f .env ]]; then
    cp .env.example .env
    CREATED_ENV=true
fi

cleanup() {
    docker compose down
    if [[ "$CREATED_ENV" == true ]]; then
        rm -f .env
    fi
}
trap cleanup EXIT

docker compose up -d --build --wait \
    postgres rabbitmq bin-lookup card-management authorization \
    transaction-logger switch gateway terminal-simulator merchant-acquirer

mvn -f services/pom.xml -Papi-tests test
