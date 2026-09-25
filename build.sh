#!/usr/bin/env bash
# 构建 AutoTask APK（使用便携版工具链，无需 sudo）
set -e

export JAVA_HOME=/home/yao/android-build/jdk-17.0.20.1+1
export ANDROID_HOME=/home/yao/android-build/sdk
export ANDROID_SDK_ROOT=/home/yao/android-build/sdk
export PATH="$JAVA_HOME/bin:$PATH"

GRADLE=/home/yao/android-build/gradle-8.2/bin/gradle
cd /home/yao/文档/默认项目/AutoTask

echo "=== 开始构建 assembleDebug ==="
"$GRADLE" --no-daemon --console=plain assembleDebug

echo "=== 产物 ==="
ls -la app/build/outputs/apk/debug/
