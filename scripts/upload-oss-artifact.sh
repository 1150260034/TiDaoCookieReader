#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 4 ]; then
  echo "Usage: $0 <apk_file> <version_name> <version_code> <changelog>"
  exit 1
fi

APK_FILE="$1"
VERSION_NAME="$2"
VERSION_CODE="$3"
CHANGELOG="$4"

if [ -z "${OSS_ACCESS_KEY_ID:-}" ] || [ -z "${OSS_ACCESS_KEY_SECRET:-}" ] || [ -z "${OSS_BUCKET_NAME:-}" ] || [ -z "${OSS_REGION:-}" ]; then
  if [ "${ALLOW_MISSING_OSS_UPLOAD:-false}" = "true" ]; then
    echo "OSS secrets 未配置，ALLOW_MISSING_OSS_UPLOAD=true，跳过上传"
    exit 0
  fi
  echo "缺少 OSS 上传所需 secrets，终止发布" >&2
  exit 1
fi

if [ ! -f "$APK_FILE" ]; then
  echo "APK 文件不存在: $APK_FILE"
  exit 1
fi

# 统一 OSS_REGION 语义：允许 cn-hangzhou 或 oss-cn-hangzhou 两种输入格式。
if [[ "$OSS_REGION" == oss-* ]]; then
  OSS_REGION_HOST="$OSS_REGION"
else
  OSS_REGION_HOST="oss-$OSS_REGION"
fi

ENDPOINT="${OSS_REGION_HOST}.aliyuncs.com"
OBJECT_KEY="app-v${VERSION_NAME}.apk"

# 安装 ossutil
curl -fsSL https://gosspublic.alicdn.com/ossutil/1.7.19/ossutil-v1.7.19-linux-amd64.zip -o ossutil.zip
unzip -q ossutil.zip -d ossutil_tmp
chmod +x ossutil_tmp/ossutil-v1.7.19-linux-amd64/ossutil64
OSSUTIL="./ossutil_tmp/ossutil-v1.7.19-linux-amd64/ossutil64"

# 配置 AK/SK
$OSSUTIL config -e "$ENDPOINT" -i "$OSS_ACCESS_KEY_ID" -k "$OSS_ACCESS_KEY_SECRET"

# 通过自定义域名（CNAME）分发 APK，不触发 OSS ApkDownloadForbidden 限制
$OSSUTIL cp "$APK_FILE" "oss://${OSS_BUCKET_NAME}/${OBJECT_KEY}" -f
echo "Artifact 已上传: oss://${OSS_BUCKET_NAME}/${OBJECT_KEY}"

# 生成并上传 version.json（用 Python 转义 CHANGELOG 确保 JSON 合法）
export VERSION_NAME VERSION_CODE CHANGELOG OSS_BUCKET_NAME OSS_REGION_HOST OBJECT_KEY OSS_CUSTOM_DOMAIN
python3 - << 'PY'
import json, os
data = {
    "version": os.environ["VERSION_NAME"],
    "versionCode": int(os.environ["VERSION_CODE"]),
    "downloadUrl": f'https://{os.environ.get("OSS_CUSTOM_DOMAIN", os.environ["OSS_BUCKET_NAME"] + "." + os.environ["OSS_REGION_HOST"] + ".aliyuncs.com")}/{os.environ["OBJECT_KEY"]}',
    "changelog": os.environ["CHANGELOG"],
    "forceUpdate": False,
}
with open("version.json", "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY
$OSSUTIL cp version.json "oss://${OSS_BUCKET_NAME}/version.json" -f
echo "version.json 已更新"
