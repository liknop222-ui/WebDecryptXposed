# WebDecrypt Pro v8.0 — 通用Web本地加密HTML解密工具

## 项目概述

本项目包含两个版本:

### 1. Frida脚本版 (`web_decrypt_hook_enhanced.js`)
- 即插即用,无需编译
- 通过Frida注入到目标App
- 适合快速分析和调试

### 2. Xposed/LSPosed模块版 (`WebDecryptXposed/`)
- 持久化Hook,重启后自动生效
- 通过LSPosed/Xposed框架激活
- 适合长期监控和分析

## 核心原理

当App从加密的assets加载HTML时(如压缩包密码未知):
1. App从assets读取加密文件
2. 在内存中解压/解密
3. 将解密后的HTML交给WebView渲染

**本工具在步骤2和3之间拦截**,直接获取解密后的原始数据:

```
加密assets → [App解密] → ★ 拦截点 ★ → WebView渲染
                              ↓
                         保存到SD卡
```

## 系统层级拦截点

| 层级 | Hook目标 | 作用 |
|------|---------|------|
| 资源层 | `AssetManager.open()` | 拦截assets资源加载 |
| 解压层 | `ZipInputStream/Inflater` | 拦截ZIP/GZIP解压 |
| 渲染层 | `WebView.loadUrl/loadData` | 拦截WebView加载 |
| 网络层 | `WebViewClient.shouldInterceptRequest` | 拦截请求响应 |
| 响应层 | `WebResourceResponse` | 拦截响应构造 |
| 加密层 | `Cipher.doFinal` | 拦截AES/DES解密 |
| 编码层 | `Base64.decode` | 拦截Base64解码 |
| 内核层 | `AwContents/ContentViewCore` | Chromium内核拦截 |

## Frida脚本使用方法

```bash
# 1. 连接设备
adb forward tcp:27042 tcp:27042

# 2. 注入到目标App
frida -U -f com.target.app -l web_decrypt_hook_enhanced.js

# 或附加到运行中的App
frida -U com.target.app -l web_decrypt_hook_enhanced.js

# 3. 查看输出
# 日志: /sdcard/WebDecrypt/log.txt
# 文件: /sdcard/WebDecrypt/
```

## Xposed模块编译方法

### 环境要求
- Android Studio (最新稳定版)
- JDK 8+
- Android SDK (API 24+)
- LSPosed 或 Xposed Framework

### 编译步骤

1. **克隆项目到本地**
   ```bash
   # 将 WebDecryptXposed/ 目录复制到本地
   ```

2. **用Android Studio打开**
   - 打开 Android Studio
   - File → Open → 选择 `WebDecryptXposed/` 目录
   - 等待Gradle同步完成

3. **可能需要修复的问题**
   - 如果Gradle同步失败,检查 `build.gradle` 中的Gradle插件版本
   - 确保 `maven { url 'https://api.xposed.info/' }` 仓库可访问
   - 如果Xposed API下载失败,手动下载jar放到 `app/libs/` 并修改build.gradle:
     ```gradle
     compileOnly files('libs/api-82.jar')
     ```

4. **编译APK**
   - Build → Build APK(s)
   - 输出: `app/build/outputs/apk/debug/app-debug.apk`

5. **安装到设备**
   ```bash
   adb install app-debug.apk
   ```

### LSPosed激活步骤

1. 安装 LSPosed (需要Magisk + Zygisk)
2. 安装本模块APK
3. 打开 LSPosed Manager
4. 找到 "WebDecrypt Pro"
5. **启用模块**
6. **设置作用域** → 勾选目标App
7. **重启目标App** (或重启手机)
8. 打开目标App,悬浮窗自动出现

## 输出目录结构

```
/sdcard/WebDecrypt/
├── assets/           # AssetManager拦截的资源
├── webview/          # WebView.loadData捕获
├── intercepted/      # shouldInterceptRequest拦截
├── response/         # WebResourceResponse捕获
├── decrypted/        # Cipher解密捕获
├── decoded/          # Base64解码捕获
├── scanned/          # 全量扫描结果
│   └── data/         # App data目录扫描
├── chromium/         # Chromium内核捕获
└── log.txt           # 运行日志
```

## 悬浮窗功能

- **🔓 悬浮球** — 可拖拽,短按展开面板
- **▶ 监控** — 开启自动捕获
- **⏸ 暂停** — 暂停捕获
- **📁 导出** — 保存日志到文件
- **实时统计** — 显示捕获/解密/失败数量

## 注意事项

1. **存储权限**: 目标App需要有存储写入权限
2. **悬浮窗权限**: 需要开启"显示在其他应用上层"权限
3. **性能影响**: Hook会带来轻微性能开销
4. **兼容性**: 支持Android 7.0+ (API 24+)
5. **作用域**: 在LSPosed中可以精确选择目标App

## 项目文件说明

```
WebDecryptXposed/
├── app/
│   ├── build.gradle                    # App构建配置
│   ├── proguard-rules.pro              # Proguard规则
│   └── src/main/
│       ├── AndroidManifest.xml         # 清单文件(Xposed模块声明)
│       ├── assets/
│       │   └── xposed_init             # Xposed入口类声明
│       ├── java/com/webdecrypt/xposed/
│       │   ├── WebDecryptHook.java     # ★ 核心Hook代码
│       │   └── MainActivity.java       # 模块设置界面
│       └── res/values/
│           ├── strings.xml             # 字符串资源
│           └── styles.xml              # 主题样式
├── build.gradle                        # 项目构建配置
├── settings.gradle                     # 项目设置
└── gradle.properties                   # Gradle属性
```

## 免责声明

本工具仅供网络安全研究和开发分析使用。请确保在合法授权范围内使用。
