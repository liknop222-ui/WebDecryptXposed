#!/system/bin/sh
# WebDecrypt Pro v10.1 — 修复容器已存在的问题 + 自动权限

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

PROJPATH="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJPATH"

_step() { echo "${YELLOW}[*] $1${NC}"; }
_ok() { echo "${GREEN}[✓] $1${NC}"; }
_err() { echo "${RED}[✗] $1${NC}"; }

_ok "项目目录: $PROJPATH"

# ───────────────────────────────────────────────────────────────────
# 确保 build.sh 本身可执行（下次可 ./build.sh）
chmod +x "$0" 2>/dev/null || true

# ───────────────────────────────────────────────────────────────────
# 1. 强制覆盖正确的 build.gradle 和 settings.gradle
# ───────────────────────────────────────────────────────────────────
_step "修复 Gradle 仓库配置..."

cat > "$PROJPATH/build.gradle" <<'BUILD_EOF'
buildscript {
    repositories {
        maven { url "https://maven.aliyun.com/repository/google" }
        maven { url "https://maven.aliyun.com/repository/central" }
        maven { url "https://maven.aliyun.com/repository/gradle-plugin" }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.2.0'
    }
}
task clean(type: Delete) {
    delete rootProject.buildDir
}
BUILD_EOF

cat > "$PROJPATH/settings.gradle" <<'SETTINGS_EOF'
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url "https://maven.aliyun.com/repository/google" }
        maven { url "https://maven.aliyun.com/repository/central" }
        maven { url "https://maven.aliyun.com/repository/gradle-plugin" }
        maven { url "https://jitpack.io" }
    }
}
rootProject.name = "WebDecryptXposed"
include ':app'
SETTINGS_EOF

_ok "已写入正确的 build.gradle / settings.gradle"

# ───────────────────────────────────────────────────────────────────
# 2. 写入 gradle.properties（加固）
# ───────────────────────────────────────────────────────────────────
cat > "$PROJPATH/gradle.properties" <<'PROP_EOF'
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8
org.gradle.daemon=false
android.useAndroidX=true
android.enableJetifier=true
android.aapt2.useNewDaemon=false
PROP_EOF
_ok "已写入 gradle.properties"

# ───────────────────────────────────────────────────────────────────
# 3. 检测本机 AAPT2 是否可用
# ───────────────────────────────────────────────────────────────────
AAPT2_OK=0
SDK_PATH="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK_PATH" ]; then
    [ -d "$HOME/android-sdk" ] && SDK_PATH="$HOME/android-sdk"
    [ -d "$HOME/Android/Sdk" ] && SDK_PATH="$HOME/Android/Sdk"
fi

if [ -n "$SDK_PATH" ]; then
    AAPT2=$(find "$SDK_PATH/build-tools" -name "aapt2" -type f 2>/dev/null | sort -V | tail -1)
    if [ -n "$AAPT2" ]; then
        chmod +x "$AAPT2" 2>/dev/null
        if command -v termux-elf-cleaner >/dev/null 2>&1; then
            termux-elf-cleaner "$AAPT2" >/dev/null 2>&1
        fi
        if "$AAPT2" version >/dev/null 2>&1; then
            AAPT2_OK=1
            _ok "本机 AAPT2 可用: $AAPT2"
            echo "android.aapt2FromMavenOverride=$AAPT2" >> "$PROJPATH/gradle.properties"
        else
            _step "本机 AAPT2 不可用（库依赖问题）"
        fi
    else
        _step "未找到本机 AAPT2"
    fi
else
    _step "未设置 ANDROID_SDK_ROOT"
fi

# ───────────────────────────────────────────────────────────────────
# 4. 如果本机 AAPT2 不可用，使用 proot-distro Ubuntu 编译
# ───────────────────────────────────────────────────────────────────
if [ $AAPT2_OK -eq 0 ]; then
    _step "切换到 proot-distro Ubuntu 环境编译..."
    
    if ! command -v proot-distro >/dev/null 2>&1; then
        pkg install -y proot-distro
    fi
    
    # 检查容器是否已经存在，若不存在才安装
    if [ ! -d "$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu" ]; then
        _step "安装 Ubuntu 根文件系统（首次约 150MB）..."
        proot-distro install ubuntu
    else
        _ok "Ubuntu 容器已存在，跳过安装"
    fi
    
    # 生成容器内构建脚本（已包含修复后的配置文件）
    cat > "$PROJPATH/build-in-ubuntu.sh" <<'UBUNTU_SCRIPT'
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

# 再次确保项目中的配置文件正确（覆盖容器内可能存在的旧文件）
cat > build.gradle <<'BG'
buildscript {
    repositories {
        maven { url "https://maven.aliyun.com/repository/google" }
        maven { url "https://maven.aliyun.com/repository/central" }
        maven { url "https://maven.aliyun.com/repository/gradle-plugin" }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.2.0'
    }
}
task clean(type: Delete) {
    delete rootProject.buildDir
}
BG

cat > settings.gradle <<'SG'
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url "https://maven.aliyun.com/repository/google" }
        maven { url "https://maven.aliyun.com/repository/central" }
        maven { url "https://maven.aliyun.com/repository/gradle-plugin" }
        maven { url "https://jitpack.io" }
    }
}
rootProject.name = "WebDecryptXposed"
include ':app'
SG

cat > gradle.properties <<'GP'
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8
org.gradle.daemon=false
android.useAndroidX=true
android.enableJetifier=true
android.aapt2.useNewDaemon=false
GP

cat > local.properties <<'LP'
sdk.dir=/root/android-sdk
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
LP

# 清理缓存
rm -rf .gradle build app/build
chmod +x gradlew

# 编译
./gradlew clean assembleDebug --no-daemon --stacktrace

APK=$(find app/build/outputs -name "*.apk" | head -1)
if [ -n "$APK" ]; then
    cp "$APK" /data/data/com.termux/files/home/android-sdk/WebDecryptXposed/WebDecryptPro-ubuntu.apk
    echo "APK 已生成: WebDecryptPro-ubuntu.apk"
else
    echo "编译失败，未找到 APK"
    exit 1
fi
UBUNTU_SCRIPT

    chmod +x "$PROJPATH/build-in-ubuntu.sh"
    _step "启动 Ubuntu 并执行编译（首次会下载 SDK 约 500MB，请耐心等待）..."
    proot-distro login ubuntu -- bash /data/data/com.termux/files/home/android-sdk/WebDecryptXposed/build-in-ubuntu.sh
    
    if [ -f "$PROJPATH/WebDecryptPro-ubuntu.apk" ]; then
        _ok "编译成功！APK: $PROJPATH/WebDecryptPro-ubuntu.apk"
        exit 0
    else
        _err "Ubuntu 环境下编译失败"
        exit 1
    fi
fi

# ───────────────────────────────────────────────────────────────────
# 5. 本机 AAPT2 可用，直接编译
# ───────────────────────────────────────────────────────────────────
_step "使用本机环境编译..."

cat > "$PROJPATH/local.properties" <<LOCALEOF
sdk.dir=$SDK_PATH
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
LOCALEOF

_step "清理缓存..."
rm -rf "$PROJPATH/.gradle" "$PROJPATH/build" "$PROJPATH/app/build"
rm -rf "$HOME/.gradle/caches/transforms-3" 2>/dev/null || true
_ok "缓存清理完成"

if [ ! -f "$PROJPATH/gradlew" ]; then
    if command -v gradle >/dev/null 2>&1; then
        gradle wrapper
    else
        pkg install -y gradle
        gradle wrapper
    fi
    chmod +x "$PROJPATH/gradlew"
fi

_step "开始编译..."
if "$PROJPATH/gradlew" clean assembleDebug --no-daemon --stacktrace; then
    _ok "编译成功！"
    APK=$(find "$PROJPATH/app/build/outputs" -name "*.apk" 2>/dev/null | head -1)
    if [ -n "$APK" ]; then
        cp "$APK" "$PROJPATH/WebDecryptPro-local.apk"
        SIZE=$(du -sh "$APK" | awk '{print $1}')
        _ok "APK 已生成: WebDecryptPro-local.apk (${SIZE})"
    fi
else
    _err "编译失败"
    exit 1
fi