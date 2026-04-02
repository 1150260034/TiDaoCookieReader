#!/bin/bash
set -euo pipefail

# 由 SrgGactionEmulatorenvsetupAndroid 自动注入，加默认值防止 set -u 报错
PACKAGE_NAME="${PACKAGE_NAME:-com.tidao.wuxia.app}"
ADB_DEVICE_SERIAL="${ADB_DEVICE_SERIAL:-emulator-5554}"

echo "=== 天刀助手 Cookie 读取器 - 冒烟测试 ==="

# 启动应用主界面（-W 等待 Activity 完全启动）
echo "正在启动 $PACKAGE_NAME ..."
adb -s "$ADB_DEVICE_SERIAL" shell am start -W -n "$PACKAGE_NAME/.ui.MainActivity"
sleep 5

# 检查应用是否在运行
PID=$(adb -s "$ADB_DEVICE_SERIAL" shell pidof "$PACKAGE_NAME" 2>/dev/null || true)
if [ -z "$PID" ]; then
  echo "❌ 应用未运行，可能已崩溃"
  echo "=== 最近崩溃日志 ==="
  adb -s "$ADB_DEVICE_SERIAL" logcat -d -t 200 | grep -iE "crash|fatal|exception|$PACKAGE_NAME" || true
  exit 1
fi
echo "✅ 应用正在运行，PID: $PID"

# 检查是否有 ANR（应用无响应）
ANR=$(adb -s "$ADB_DEVICE_SERIAL" shell dumpsys activity activities 2>/dev/null | grep -c "notResponding" || true)
if [ "$ANR" -gt 0 ]; then
  echo "❌ 检测到 ANR（应用无响应）"
  adb -s "$ADB_DEVICE_SERIAL" shell dumpsys activity activities | grep "notResponding" || true
  exit 1
fi
echo "✅ 无 ANR"

echo "=== 冒烟测试通过 ==="
