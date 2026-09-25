#!/usr/bin/env sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
java --source 17 "$project_dir/core-tests/SyntaxCheck.java" "$project_dir"
if rg -n -i 'gpt[- ]?6' "$project_dir/app/src"; then
  echo "Modelo proibido encontrado no aplicativo" >&2
  exit 1
fi
echo "StaticCheck: nenhum modelo proibido embutido"
