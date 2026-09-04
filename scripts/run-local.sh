#!/usr/bin/env bash
# Loads .env into the process environment, then runs the app.
# Spring Boot does NOT read .env files on its own -- application.properties'
# `spring.config.import=optional:file:.env[.properties]` is what actually
# makes the values in .env reach the application. Sourcing it here too
# (rather than relying on that alone) keeps this consistent with how the
# other Konecta services are started, and covers anything that isn't
# read through Spring's Environment (e.g. tools invoked by the build).
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "No .env file found. Copy .env.example to .env and fill in real values first:"
  echo "  cp .env.example .env"
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

exec ./mvnw spring-boot:run
