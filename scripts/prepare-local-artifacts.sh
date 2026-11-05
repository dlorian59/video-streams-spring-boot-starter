#!/usr/bin/env bash
set -euo pipefail
starter_root=$(cd "$(dirname "$0")/.." && pwd)
repos_root=$(dirname "$starter_root")
# EN: These four existing checkouts must be siblings; this script never downloads source or publishes remotely.
# RU: Четыре существующие рабочие копии должны лежать рядом; скрипт не скачивает исходники и не публикует удалённо.
for project in file-streams file-streams-spring-boot-starter video-streams video-streams-spring-boot-starter; do
  test -f "$repos_root/$project/settings.gradle.kts" || { echo "Missing sibling checkout: $project" >&2; exit 1; }
  "${GRADLE_COMMAND:-$starter_root/gradlew}" -p "$repos_root/$project" \
    --init-script "$starter_root/scripts/local-publication.gradle" \
    publishAllPublicationsToOss014LocalRepository publishToMavenLocal --no-daemon --max-workers=2
done
