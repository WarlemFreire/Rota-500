#!/usr/bin/env sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
java --source 17 "$project_dir/core-tests/SyntaxCheck.java" "$project_dir"
if rg -n -i 'gpt[- ]?6' "$project_dir/app/src"; then
  echo "Modelo proibido encontrado no aplicativo" >&2
  exit 1
fi
if rg -n 'BIND_ACCESSIBILITY_SERVICE|AccessibilityService|performAction' "$project_dir/app/src"; then
  echo "A versão de instalação fácil ainda contém automação por acessibilidade" >&2
  exit 1
fi
rg -q 'FOREGROUND_SERVICE_MEDIA_PROJECTION' "$project_dir/app/src/main/AndroidManifest.xml"
rg -q 'foregroundServiceType="mediaProjection"' "$project_dir/app/src/main/AndroidManifest.xml"
echo "StaticCheck: nenhum modelo proibido embutido"
