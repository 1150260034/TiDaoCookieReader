#!/usr/bin/env bash

set -euo pipefail

FILE="app/src/main/res/xml/file_paths.xml"

if [[ ! -f "$FILE" ]]; then
  echo "缺少 $FILE"
  exit 1
fi

if ! grep -Eq '<external-files-path\b' "$FILE"; then
  echo "$FILE 必须包含 external-files-path 节点"
  exit 1
fi

if ! grep -Eq '<external-files-path[^>]*\bname[[:space:]]*=[[:space:]]*"[^"]*"[^>]*\bpath[[:space:]]*=[[:space:]]*"[^"]*"' "$FILE" \
  && ! grep -Eq '<external-files-path[^>]*\bpath[[:space:]]*=[[:space:]]*"[^"]*"[^>]*\bname[[:space:]]*=[[:space:]]*"[^"]*"' "$FILE"; then
  echo "$FILE 中的 external-files-path 必须声明 name 和 path 属性"
  exit 1
fi

if grep -Eq '<external-files-path[^>]*\bandroid:(name|path)[[:space:]]*=' "$FILE"; then
  echo "$FILE 中的 external-files-path 不能使用 android:name 或 android:path"
  exit 1
fi

echo "file_paths.xml 校验通过"