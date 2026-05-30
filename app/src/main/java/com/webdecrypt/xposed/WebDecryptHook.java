package com.webdecrypt.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * WebDecrypt Pro v8.0 — 通用Web本地加密HTML解密 Xposed模块
 *
 * 核心功能:
 * 1. 系统层级拦截 AssetManager — 拦截assets资源加载，直接获取解密后的原始数据
 * 2. 系统层级拦截 WebView 渲染引擎 — 拦截loadUrl/loadData/shouldInterceptRequest
 * 3. 系统层级拦截 Cipher/Base64 — 拦截加密解密操作，捕获解密后的HTML
 * 4. 系统层级拦截 InputStream/Inflater — 拦截内存中的解压数据
 * 5. 悬浮窗实时监控 — 可拖拽悬浮球 + 展开面板
 *
 * 原理: 当App从加密的assets加载HTML时（如压缩包密码未知），
 * App会在内存中解压/解密后交给WebView渲染，本模块拦截这些系统函数，
 * 直接获取解密后的原始数据。
 */
public class WebDecryptHook implements IXposedHookLoadPackage {

    private static final String TAG = "WebDecrypt";
    private static final String OUTPUT_DIR = "/sdcard/WebDecrypt/";
    private static final String LOG_FILE = OUTPUT_DIR + "log.txt";

    private static int capturedCount = 0;
    private static int decryptedCount = 0;
    private static int failedCount = 0;
    private static boolean autoCapture = true;
    private static boolean hooksInstalled = false;

    private static final List<String> TARGET_EXTENSIONS = Arrays.asList(
            ".vm", ".enc", ".dat", ".html", ".htm", ".js", ".css", ".json", ".xml"
    );

    private static final List<String> LOG_BUFFER = new ArrayList<>();
    private static final Map<String, Boolean> CAPTURED_FILES = new HashMap<>();

    // 悬浮窗相关
    private static WindowManager windowManager;
    private static View floatingView;
    private static View panelView;
    private static boolean isPanelExpanded = false;
    private static TextView statsTextView;
    private static TextView fileListTextView;
    private static Context moduleContext;

    // ═══════════════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════════════

    private static void log(String level, String msg) {
        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date());
        String line = "[" + timestamp + "] [" + level + "] " + msg;
        XposedBridge.log(line);
        Log.d(TAG, line);
        synchronized (LOG_BUFFER) {
            LOG_BUFFER.add(line);
            if (LOG_BUFFER.size() >= 100) {
                flushLog();
            }
        }
    }

    private static void info(String msg) { log("INFO", msg); }
    private static void ok(String msg) { capturedCount++; log("OK", "✅ " + msg); }
    private static void warn(String msg) { log("WARN", "⚠️ " + msg); }
    private static void err(String msg) { failedCount++; log("ERR", "❌ " + msg); }
    private static void dbg(String msg) { log("DBG", "🔍 " + msg); }
    private static void trace(String msg) { log("TRC", "→ " + msg); }

    private static void flushLog() {
        synchronized (LOG_BUFFER) {
            if (LOG_BUFFER.isEmpty()) return;
            try {
                StringBuilder sb = new StringBuilder();
                for (String line : LOG_BUFFER) {
                    sb.append(line).append("\n");
                }
                saveFile(LOG_FILE, sb.toString().getBytes("UTF-8"));
                LOG_BUFFER.clear();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private static void saveFile(String path, byte[] data) {
        try {
            File f = new File(path);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(data);
            fos.close();
        } catch (Exception e) {
            err("保存失败 " + path + ": " + e.getMessage());
        }
    }

    private static byte[] readInputStream(InputStream is) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) {
                bos.write(buf, 0, len);
            }
            is.close();
            byte[] data = bos.toByteArray();
            bos.close();
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isTargetFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        for (String ext : TARGET_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        if (lower.contains("index") || lower.contains("main") ||
                lower.contains("app") || lower.contains("view")) {
            return true;
        }
        return false;
    }

    private static String generateSafeFilename(String input) {
        if (input == null) input = "unknown";
        String safe = input.replaceAll("[^a-zA-Z0-9._\\-/]", "_")
                .replaceAll("_+", "_");
        return safe.substring(0, Math.min(safe.length(), 100));
    }

    private static boolean isHtmlContent(byte[] data) {
        if (data == null || data.length < 5) return false;
        try {
            String head = new String(data, 0, Math.min(data.length, 500), "UTF-8").toLowerCase();
            return head.contains("<html") || head.contains("<!doctype") ||
                    head.contains("<head") || head.contains("<body") ||
                    head.contains("<script") || head.contains("<div") ||
                    head.contains("function(") || head.contains("var ");
        } catch (Exception e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, 32); i++) {
            sb.append(String.format("%02x", bytes[i] & 0xff));
        }
        return sb.toString();
    }

    private static void ensureDirs() {
        String[] dirs = {
                OUTPUT_DIR,
                OUTPUT_DIR + "assets/",
                OUTPUT_DIR + "webview/",
                OUTPUT_DIR + "intercepted/",
                OUTPUT_DIR + "response/",
                OUTPUT_DIR + "decrypted/",
                OUTPUT_DIR + "decoded/",
                OUTPUT_DIR + "scanned/",
                OUTPUT_DIR + "chromium/"
        };
        for (String dir : dirs) {
            new File(dir).mkdirs();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ★ Hook 1: AssetManager — 拦截资源加载
    // ═══════════════════════════════════════════════════════════════════

    private static void hookAssetManager(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.content.res.AssetManager",
                    classLoader,
                    "open",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!autoCapture) return;
                            String filename = (String) param.args[0];
                            trace("AssetManager.open: " + filename);

                            if (isTargetFile(filename)) {
                                try {
                                    InputStream is = (InputStream) param.getResult();
                                    if (is != null) {
                                        byte[] data = readInputStream(is);
                                        if (data != null && data.length > 0 && data.length < 50 * 1024 * 1024) {
                                            String outputPath = OUTPUT_DIR + "assets/" + filename;
                                            saveFile(outputPath, data);
                                            ok("Asset捕获: " + filename + " (" + data.length + "B)");

                                            // 替换InputStream让App正常使用
                                            param.setResult(new ByteArrayInputStream(data));
                                        }
                                    }
                                } catch (Exception e) {
                                    dbg("Asset读取失败: " + e.getMessage());
                                }
                            }
                        }
                    }
            );

            // Hook open(String, int)
            try {
                XposedHelpers.findAndHookMethod(
                        "android.content.res.AssetManager",
                        classLoader,
                        "open",
                        String.class,
                        int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                String filename = (String) param.args[0];
                                int mode = (int) param.args[1];
                                trace("AssetManager.open(mode): " + filename + " mode=" + mode);
                            }
                        }
                );
            } catch (Exception e) {}

            info("✅ AssetManager Hook 完成");
        } catch (Exception e) {
            warn("AssetManager Hook 失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ★ Hook 2: WebView 渲染引擎拦截
    // ═══════════════════════════════════════════════════════════════════

    private static void hookWebView(ClassLoader classLoader) {
        try {
            // Hook WebView.loadUrl(String)
            XposedHelpers.findAndHookMethod(
                    "android.webkit.WebView",
                    classLoader,
                    "loadUrl",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String url = (String) param.args[0];
                            trace("WebView.loadUrl: " + url);

                            if (url != null && (url.contains("file://") || url.contains("localhost") ||
                                    url.contains("127.0.0.1") || url.contains("data:"))) {
                                info("🎯 检测到本地加载: " + url);
                            }
                        }
                    }
            );

            // Hook WebView.loadUrl(String, Map)
            try {
                XposedHelpers.findAndHookMethod(
                        "android.webkit.WebView",
                        classLoader,
                        "loadUrl",
                        String.class,
                        Map.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                String url = (String) param.args[0];
                                trace("WebView.loadUrl(headers): " + url);
                            }
                        }
                );
            } catch (Exception e) {}

            // Hook WebView.loadData — 拦截直接加载的HTML数据
            XposedHelpers.findAndHookMethod(
                    "android.webkit.WebView",
                    classLoader,
                    "loadData",
                    String.class, String.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String data = (String) param.args[0];
                            String mimeType = (String) param.args[1];
                            trace("WebView.loadData: mimeType=" + mimeType + " len=" + (data != null ? data.length() : 0));

                            if (data != null && !data.isEmpty()) {
                                String outputPath = OUTPUT_DIR + "webview/loadData_" + System.currentTimeMillis() + ".html";
                                saveFile(outputPath, data.getBytes("UTF-8"));
                                ok("loadData捕获: " + data.length() + " chars");
                            }
                        }
                    }
            );

            // Hook WebView.loadDataWithBaseURL
            XposedHelpers.findAndHookMethod(
                    "android.webkit.WebView",
                    classLoader,
                    "loadDataWithBaseURL",
                    String.class, String.class, String.class, String.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String baseUrl = (String) param.args[0];
                            String data = (String) param.args[1];
                            trace("WebView.loadDataWithBaseURL: baseUrl=" + baseUrl);

                            if (data != null && !data.isEmpty()) {
                                String outputPath = OUTPUT_DIR + "webview/loadDataWithBaseURL_" + System.currentTimeMillis() + ".html";
                                saveFile(outputPath, data.getBytes("UTF-8"));
                                ok("loadDataWithBaseURL捕获: baseUrl=" + baseUrl + " len=" + data.length());
                            }
                        }
                    }
            );

            // Hook WebView.evaluateJavascript
            try {
                XposedHelpers.findAndHookMethod(
                        "android.webkit.WebView",
                        classLoader,
                        "evaluateJavascript",
                        String.class,
                        android.webkit.ValueCallback.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                String script = (String) param.args[0];
                                if (script != null && script.length() > 10) {
                                    trace("evaluateJavascript: " + script.substring(0, Math.min(script.length(), 100)) + "...");
                                }
                            }
                        }
                );
            } catch (Exception e) {}

            info("✅ WebView.loadUrl/loadData Hook 完成");
        } catch (Exception e) {
            warn("WebView Hook 失败: " + e.getMessage());
        }

        // Hook WebViewClient.shouldInterceptRequest
        hookWebViewClient(classLoader);

        // Hook addJavascriptInterface
        hookJSBridge(classLoader);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ★ Hook 2b: WebViewClient.shouldInterceptRequest
    // ═══════════════════════════════════════════════════════════════════

    private static void hookWebViewClient(ClassLoader classLoader) {
        try {
            // 新版API: shouldInterceptRequest(WebView, WebResourceRequest)
            XposedHelpers.findAndHookMethod(
                    "android.webkit.WebViewClient",
                    classLoader,
                    "shouldInterceptRequest",
                    WebView.class,
                    WebResourceRequest.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!autoCapture) return;
                            try {
                                WebResourceRequest req = (WebResourceRequest) param.args[1];
                                String url = req.getUrl().toString();
                                trace("shouldInterceptRequest: " + url);

                                WebResourceResponse resp = (WebResourceResponse) param.getResult();
                                if (resp != null && isTargetFile(url)) {
                                    InputStream is = resp.getData();
                                    if (is != null) {
                                        byte[] data = readInputStream(is);
                                        if (data != null && data.length > 0) {
                                            String safeName = generateSafeFilename(url);
                                            String outputPath = OUTPUT_DIR + "intercepted/" + safeName;
                                            saveFile(outputPath, data);
                                            ok("拦截捕获: " + url + " (" + data.length + "B)");

                                            // 替换响应
                                            param.setResult(new WebResourceResponse(
                                                    resp.getMimeType(),
                                                    resp.getEncoding(),
                                                    new ByteArrayInputStream(data)
                                            ));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                dbg("shouldIntercept处理失败: " + e.getMessage());
                            }
                        }
                    }
            );

            // 旧版API
            try {
                XposedHelpers.findAndHookMethod(
                        "android.webkit.WebViewClient",
                        classLoader,
                        "shouldInterceptRequest",
                        WebView.class,
                        String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                String url = (String) param.args[1];
                                trace("shouldInterceptRequest(legacy): " + url);
                            }
                        }
                );
            } catch (Exception e) {}

            info("✅ WebViewClient.shouldInterceptRequest Hook 完成");
        } catch (Exception e) {
            warn("WebViewClient Hook 失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ★ Hook 2c: JS Bridge检测
    // ═══════════════════════════════════════════════════════════════════

    private static void hookJSBridge(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.webkit.WebView",
                    classLoader,
                    "addJavascriptInterface",
                    Object.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object obj = param.args[0];
                            String name = (String) param.args[1];
                            info("JS Bridge注入: " + name + " → " + obj.getClass().getName());

                            // 列出接口方法
                            Method[] methods = obj.getClass().getDeclaredMethods();
                            for (int i = 0; i < Math.min(methods.length, 15); i++) {
                                dbg("  方法: " + methods[i].getName() + "(" + methods[i].getParameterTypes().length + " params)");
                            }
                        }
                    }
            );
            info("✅ addJavascriptInterface Hook 完成");
        } catch (Exception e) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    // ★ Hook 3: Cipher/加密库拦截
    // ═══════════════════════════════════════════════════════════════════

    private static void hookCryptoLibraries(ClassLoader classLoader) {
        // Hook Cipher.doFinal
        try {
            XposedHelpers.findAndHookMethod(
                    "javax.crypto.Cipher",
                    classLoader,
                    "doFinal",
                    byte[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            byte[] input = (byte[]) param.args[0];
                            byte[] result = (byte[]) param.getResult();
                            dbg("Cipher.doFinal: input=" + input.length + " output=" + (result != null ? result.length : 0));

                            if (result != null && result.length > 0 && isHtmlContent(result)) {
                                String outputPath = OUTPUT_DIR + "decrypted/cipher_" + System.currentTimeMillis() + ".html";
                                saveFile(outputPath, result);
                                ok("Cipher解密捕获: " + result.length + "B");
                                decryptedCount++;
                            }
                        }
                    }
            );
            info("✅ Cipher Hook 完成");
        } catch (Exception e) {
            warn("Cipher Hook 失败: " + e.getMessage());
        }

        // Hook SecretKeySpec — 检测密钥
        try {
            XposedHelpers.findAndHookConstructor(
                    "javax.crypto.spec.SecretKeySpec",
                    classLoader,
                    byte[].class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            byte[] key = (byte[]) param.args[0];
                            String algorithm = (String) param.args[1];
                            info("🔑 检测到密钥: algorithm=" + algorithm + " keyLen=" + key.length);
                            dbg("  密钥(hex): " + bytesToHex(key));
                        }
                    }
            );
        } catch (Exception e) {}

        // Hook Base64.decode
        try {
            XposedHelpers.findAndHookMethod(
                    "android.util.Base64",
                    classLoader,
                    "decode",
                    byte[].class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            byte[] input = (byte[]) param.args[0];
                            byte[] result = (byte[]) param.getResult();
                            dbg("Base64.decode: input=" + input.length + " output=" + result.length);

                            if (result != null && result.length > 100 && isHtmlContent(result)) {
                                String outputPath = OUTPUT_DIR + "decoded/base64_" + System.currentTimeMillis() + ".html";
                                saveFile(outputPath, result);
                                ok("Base64解码捕获: " + result.length + "B");
                            }
                        }
                    }
            );
            info("✅ Base64 Hook 完成");
        } catch (Exception e) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    // ★ Hook 4: WebResourceResponse 构造拦截
    // ═══════════════════════════════════════════════════════════════════

    private static void hookWebResourceResponse(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookConstructor(
                    "android.webkit.WebResourceResponse",
                    classLoader,
                    String.class, String.class, InputStream.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!autoCapture) return;
                            try {
                                String mimeType = (String) param.args[0];
                                InputStream is = (InputStream) param.args[2];

                                if (is != null && mimeType != null &&
                                        (mimeType.contains("html") || mimeType.contains("javascript") ||
                                                mimeType.contains("css") || mimeType.contains("json"))) {
                                    byte[] data = readInputStream(is);
                                    if (data != null && data.length > 0 && data.length < 50 * 1024 * 1024) {
                                        String outputPath = OUTPUT_DIR + "response/response_" +
                                                System.currentTimeMillis() + "_" + mimeType.replace("/", "_") + ".dat";
                                        saveFile(outputPath, data);
                                        ok("Response捕获: mimeType=" + mimeType + " (" + data.length + "B)");

                                        // 替换InputStream
                                        // 注意: 这里不能直接替换构造参数,但数据已保存
                                    }
                                }
                            } catch (Exception e) {
                                // ignore - 不能影响原始流程
                            }
                        }
                    }
            );
            info("✅ WebResourceResponse Hook 完成");
        } catch (Exception e) {
            warn("WebResourceResponse Hook 失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ★ Hook 5: Chromium 内核深度拦截
    // ═══════════════════════════════════════════════════════════════════

    private static void hookChromium(ClassLoader classLoader) {
        // 尝试Hook各种Chromium内部类
        String[] chromiumClasses = {
                "com.android.webview.chromium.WebViewChromium",
                "com.android.org.chromium.android_webview.AwContents",
                "com.android.org.chromium.content_public.browser.ContentViewCore",
                "com.android.org.chromium.content_public.browser.WebContents",
                "com.android.org.chromium.base.JniUtil",
                "org.chromium.android_webview.AwContents",
                "org.chromium.content.browser.webcontents.WebContentsImpl",
                "org.chromium.base.JniUtil"
        };

        for (String className : chromiumClasses) {
            try {
                Class<?> clazz = Class.forName(className, false, classLoader);
                dbg("Chromium类已找到: " + className);

                // 尝试Hook loadUrl方法
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().equals("loadUrl")) {
                        try {
                            XposedBridge.hookMethod(m, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    trace("Chromium.loadUrl: " + param.method.getDeclaringClass().getName());
                                }
                            });
                            info("✅ Chromium Hook: " + className + ".loadUrl");
                        } catch (Exception e) {}
                    }
                }
            } catch (ClassNotFoundException e) {
                // 类不存在,跳过
            } catch (Exception e) {
                dbg("Chromium Hook异常: " + className + " → " + e.getMessage());
            }
        }

        info("✅ Chromium 内核扫描完成");
    }

    // ═══════════════════════════════════════════════════════════════════
    // ★ Hook 6: Activity生命周期 — 注入悬浮窗
    // ═══════════════════════════════════════════════════════════════════

    private static void hookActivity(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    classLoader,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            final Activity activity = (Activity) param.thisObject;
                            String activityName = activity.getClass().getName();
                            dbg("Activity.onResume: " + activityName);

                            // 只对目标Activity显示悬浮窗
                            if (activityName.contains("Web") || activityName.contains("Main") ||
                                    activityName.contains("Browser") || activityName.contains("App") ||
                                    activityName.contains("Home") || activityName.contains("Content")) {

                                if (!hooksInstalled) {
                                    hooksInstalled = true;
                                    info("🎯 检测到目标Activity: " + activityName);

                                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            showFloatingWindow(activity);
                                            scanAndDecrypt(activity);
                                        }
                                    }, 1500);
                                }
                            }
                        }
                    }
            );
            info("✅ Activity.onResume Hook 完成");
        } catch (Exception e) {
            warn("Activity Hook 失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ★ 悬浮窗服务
    // ═══════════════════════════════════════════════════════════════════

    private static void showFloatingWindow(final Activity activity) {
        try {
            windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

            // 创建悬浮球
            final View ballView = createFloatingBall(activity);
            // 创建展开面板
            panelView = createFloatingPanel(activity);

            final WindowManager.LayoutParams ballParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            ballParams.gravity = Gravity.TOP | Gravity.END;
            ballParams.x = 10;
            ballParams.y = 200;

            // 悬浮球拖拽
            ballView.setOnTouchListener(new View.OnTouchListener() {
                private int lastX, lastY;
                private int initialX, initialY;
                private long touchStartTime = 0;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            lastX = (int) event.getRawX();
                            lastY = (int) event.getRawY();
                            initialX = ballParams.x;
                            initialY = ballParams.y;
                            touchStartTime = System.currentTimeMillis();
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            int dx = (int) event.getRawX() - lastX;
                            int dy = (int) event.getRawY() - lastY;
                            ballParams.x = initialX - dx;
                            ballParams.y = initialY + dy;
                            windowManager.updateViewLayout(ballView, ballParams);
                            return true;

                        case MotionEvent.ACTION_UP:
                            // 短按 = 展开/收起面板
                            if (System.currentTimeMillis() - touchStartTime < 200) {
                                togglePanel(activity);
                            }
                            return true;
                    }
                    return false;
                }
            });

            windowManager.addView(ballView, ballParams);
            floatingView = ballView;

            info("✅ 悬浮窗已注入!");

            // 定时更新统计
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateStats();
                    handler.postDelayed(this, 2000);
                }
            }, 2000);

        } catch (Exception e) {
            warn("悬浮窗失败: " + e.getMessage());
            showFallbackDialog(activity);
        }
    }

    private static View createFloatingBall(Context ctx) {
        // 简单的圆形悬浮球
        TextView ball = new TextView(ctx);
        ball.setText("🔓");
        ball.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        ball.setBackgroundColor(Color.parseColor("#1A1A2E"));
        ball.setPadding(12, 12, 12, 12);

        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setBackgroundColor(Color.parseColor("#E94560"));
        wrapper.setPadding(4, 4, 4, 4);
        wrapper.addView(ball);

        return wrapper;
    }

    private static View createFloatingPanel(final Context ctx) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.parseColor("#E01A1A2E"));
        panel.setPadding(20, 20, 20, 20);
        panel.setMinimumWidth(300);

        // 标题
        TextView title = new TextView(ctx);
        title.setText("🔓 WebDecrypt Pro v8.0");
        title.setTextColor(Color.parseColor("#E94560"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setPadding(0, 0, 0, 10);
        panel.addView(title);

        // 统计
        statsTextView = new TextView(ctx);
        statsTextView.setText("捕获: 0 | 解密: 0 | 失败: 0");
        statsTextView.setTextColor(Color.WHITE);
        statsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statsTextView.setPadding(0, 0, 0, 10);
        panel.addView(statsTextView);

        // 按钮行
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 0, 0, 10);

        Button btnStart = new Button(ctx);
        btnStart.setText("▶ 监控");
        btnStart.setBackgroundColor(Color.parseColor("#0F3460"));
        btnStart.setTextColor(Color.WHITE);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoCapture = true;
                Toast.makeText(ctx, "监控已开启!", Toast.LENGTH_SHORT).show();
            }
        });
        btnRow.addView(btnStart);

        Button btnStop = new Button(ctx);
        btnStop.setText("⏸ 暂停");
        btnStop.setBackgroundColor(Color.parseColor("#16213E"));
        btnStop.setTextColor(Color.WHITE);
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoCapture = false;
                Toast.makeText(ctx, "监控已暂停", Toast.LENGTH_SHORT).show();
            }
        });
        btnRow.addView(btnStop);

        Button btnExport = new Button(ctx);
        btnExport.setText("📁 导出");
        btnExport.setBackgroundColor(Color.parseColor("#E94560"));
        btnExport.setTextColor(Color.WHITE);
        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flushLog();
                Toast.makeText(ctx, "日志已保存: " + LOG_FILE, Toast.LENGTH_LONG).show();
            }
        });
        btnRow.addView(btnExport);

        panel.addView(btnRow);

        // 文件列表
        fileListTextView = new TextView(ctx);
        fileListTextView.setText("等待捕获...");
        fileListTextView.setTextColor(Color.parseColor("#A7A7A7"));
        fileListTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        fileListTextView.setMaxLines(8);
        panel.addView(fileListTextView);

        // 关闭按钮
        Button btnClose = new Button(ctx);
        btnClose.setText("✕ 关闭面板");
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setTextColor(Color.parseColor("#E94560"));
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePanel(ctx);
            }
        });
        panel.addView(btnClose);

        return panel;
    }

    private static void togglePanel(Context ctx) {
        try {
            if (isPanelExpanded) {
                if (panelView != null && panelView.isShown()) {
                    windowManager.removeView(panelView);
                }
                isPanelExpanded = false;
            } else {
                WindowManager.LayoutParams panelParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );
                panelParams.gravity = Gravity.TOP | Gravity.END;
                panelParams.x = 10;
                panelParams.y = 260;
                windowManager.addView(panelView, panelParams);
                isPanelExpanded = true;
            }
        } catch (Exception e) {
            dbg("面板切换失败: " + e.getMessage());
        }
    }

    private static void updateStats() {
        if (statsTextView != null) {
            statsTextView.setText("捕获: " + capturedCount + " | 解密: " + decryptedCount + " | 失败: " + failedCount);
        }
    }

    private static void showFallbackDialog(final Activity activity) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    new AlertDialog.Builder(activity)
                            .setTitle("🔓 WebDecrypt Pro v8.0")
                            .setMessage(
                                    "系统层级拦截已激活!\n\n" +
                                            "功能:\n" +
                                            "• AssetManager拦截 - 资源加载\n" +
                                            "• WebView拦截 - 渲染引擎\n" +
                                            "• Cipher拦截 - 加密解密\n" +
                                            "• Chromium拦截 - 内核层\n\n" +
                                            "输出: " + OUTPUT_DIR + "\n" +
                                            "统计: 捕获" + capturedCount + " 解密" + decryptedCount
                            )
                            .setPositiveButton("导出日志", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    flushLog();
                                    Toast.makeText(activity, "日志已保存!", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("关闭", null)
                            .setNeutralButton("查看目录", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    try {
                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                        intent.setDataAndType(Uri.parse("file://" + OUTPUT_DIR), "resource/folder");
                                        activity.startActivity(intent);
                                    } catch (Exception e) {}
                                }
                            })
                            .show();
                } catch (Exception e) {}
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // ★ 全量扫描解密
    // ═══════════════════════════════════════════════════════════════════

    private static void scanAndDecrypt(Activity activity) {
        info("开始全量扫描...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 扫描APK内资源
                    String apkPath = activity.getApplicationInfo().sourceDir;
                    ZipFile zip = new ZipFile(apkPath);
                    java.util.Enumeration<? extends ZipEntry> entries = zip.entries();

                    int count = 0;
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();

                        if (name.contains("assets/") && isTargetFile(name)) {
                            try {
                                InputStream is = zip.getInputStream(entry);
                                byte[] data = readInputStream(is);
                                if (data != null && data.length > 0) {
                                    String outputPath = OUTPUT_DIR + "scanned/" +
                                            name.substring(name.indexOf("assets/") + 7);
                                    saveFile(outputPath, data);
                                    ok("扫描捕获: " + name + " (" + data.length + "B)");
                                    count++;
                                }
                            } catch (Exception e) {}
                        }
                    }
                    zip.close();
                    info("APK扫描完成: " + count + " 个文件");

                    // 扫描data目录
                    scanDataDir(activity);

                } catch (Exception e) {
                    err("扫描失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private static void scanDataDir(Activity activity) {
        try {
            String dataDir = activity.getApplicationInfo().dataDir;
            File[] targetDirs = {
                    new File(dataDir, "files"),
                    new File(dataDir, "cache"),
                    new File(dataDir, "app_webview"),
                    new File(dataDir, "app_flutter")
            };

            for (File dir : targetDirs) {
                if (dir.exists()) {
                    scanDirRecursive(dir, dir.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            dbg("Data目录扫描失败: " + e.getMessage());
        }
    }

    private static void scanDirRecursive(File dir, String basePath) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanDirRecursive(f, basePath);
            } else if (isTargetFile(f.getName()) && f.length() < 50 * 1024 * 1024) {
                try {
                    InputStream is = new java.io.FileInputStream(f);
                    byte[] data = readInputStream(is);
                    if (data != null && isHtmlContent(data)) {
                        String relativePath = f.getAbsolutePath().substring(basePath.length());
                        String outputPath = OUTPUT_DIR + "scanned/data" + relativePath;
                        saveFile(outputPath, data);
                        ok("Data目录捕获: " + relativePath + " (" + data.length + "B)");
                    }
                } catch (Exception e) {}
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ★ 主入口 — Xposed模块加载
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 跳过系统进程和自身
        if (lpparam.packageName.equals("android") ||
                lpparam.packageName.equals("com.webdecrypt.xposed") ||
                lpparam.packageName.equals("de.robv.android.xposed.installer") ||
                lpparam.packageName.startsWith("com.android.") ||
                lpparam.packageName.startsWith("com.google.") ||
                lpparam.packageName.startsWith("com.topjohnwu.")) {
            return;
        }

        info("╔══════════════════════════════════════════════════════════╗");
        info("║  WebDecrypt Pro v8.0 — 目标: " + lpparam.packageName);
        info("╚══════════════════════════════════════════════════════════╝");

        ensureDirs();

        ClassLoader classLoader = lpparam.classLoader;

        // 安装所有Hook
        hookAssetManager(classLoader);
        hookWebView(classLoader);
        hookWebResourceResponse(classLoader);
        hookCryptoLibraries(classLoader);
        hookChromium(classLoader);
        hookActivity(classLoader);

        // 定期保存日志
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                flushLog();
            }
        }, 30000, 30000);

        info("══════════════════════════════════════════════════════════");
        info("  所有Hook已安装完成! 目标: " + lpparam.packageName);
        info("  输出目录: " + OUTPUT_DIR);
        info("══════════════════════════════════════════════════════════");
    }
}
