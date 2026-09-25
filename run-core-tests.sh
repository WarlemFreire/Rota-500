#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
java --source 17 "$project_dir/core-tests/CompileAndRun.java" "$project_dir"
