#!/bin/bash
set -e
export DEBIAN_FRONTEND=noninteractive

apt update
apt install -y openjdk-17-jdk-headless wget curl unzip git

if [ ! -d "/root/android-sdk" ]; then
    mkdir -p /root/android-sdk
    cd /root/android-sdk
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    unzip -q commandlinetools-linux-11076708_latest.zip
    rm commandlinetools-linux-11076708_latest.zip
    echo "y" | ./cmdline-tools/bin/sdkmanager --sdk_root=/root/android-sdk "build-tools;34.0.0" "platforms;android-34"
fi

export ANDROID_SDK_ROOT=/root/android-sdk
export PATH=$ANDROID_SDK_ROOT/cmdline-tools/bin:$PATH

cd /data/data/com.termux/files/home/android-sdk/WebDecryptXposed

cat > local.properties <<EOF
sdk.dir=$ANDROID_SDK_ROOT
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
EOF

rm -rf .gradle build app/build
chmod +x gradlew
./gradlew clean assembleDebug --no-daemon --stacktrace

APK=$(find app/build/outputs -name "*.apk" | head -1)
if [ -n "$APK" ]; then
    cp "$APK" /data/data/com.termux/files/home/android-sdk/WebDecryptXposed/WebDecryptPro-ubuntu.apk
    echo "APK 已生成: WebDecryptPro-ubuntu.apk"
else
    echo "编译失败，未找到 APK"
    exit 1
fi
