package com.webdecrypt.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class WebDecryptHook implements IXposedHookLoadPackage {

    private static final String TAG = "WebDecrypt";
    private static final String OUTPUT_DIR = "/sdcard/WebDecrypt/";
    private static final String LOG_FILE = OUTPUT_DIR + "log.txt";
    private static final String PREFS_NAME = "webdecrypt_prefs";
    private static final String KEY_JS_SCRIPTS = "js_scripts";
    private static final String KEY_AUTO_CAPTURE = "auto_capture";
    private static final String KEY_AUTO_INJECT = "auto_inject";
    private static final String KEY_CAPTURE_HTML = "capture_html";
    private static final String KEY_TARGET_KEYWORDS = "target_keywords";

    private static final AtomicInteger capturedCount = new AtomicInteger(0);
    private static final AtomicInteger decryptedCount = new AtomicInteger(0);
    private static final AtomicInteger failedCount = new AtomicInteger(0);
    private static final AtomicBoolean autoCapture = new AtomicBoolean(true);
    private static volatile boolean hooksInstalled = false;

    private static final List<String> TARGET_EXTENSIONS = Arrays.asList(
            ".vm", ".enc", ".dat", ".html", ".htm", ".js", ".css", ".json", ".xml"
    );

    private static final LinkedList<String> LOG_BUFFER = new LinkedList<>();
    private static final Set<String> CAPTURED_FILES = new HashSet<>();

    private static WeakReference<Activity> currentActivityRef;
    private static WindowManager windowManager;
    private static View floatingBallView;
    private static View floatingPanelView;
    private static volatile boolean isPanelExpanded = false;
    private static volatile boolean isFloatingShown = false;
    private static TextView statsTextView;
    private static TextView fileListTextView;
    private static TextView optimizationTextView;
    private static Handler mainHandler;
    private static SharedPreferences modulePrefs;

    private static final List<String> capturedFileNames = new ArrayList<>();
    private static final List<WebView> trackedWebViews = new ArrayList<>();

    private static final String OPTIMIZATION_JS =
        "(function() {" +
        "  var result = {};" +
        "  var all = document.querySelectorAll('*');" +
        "  result.totalNodes = all.length;" +
        "  var maxDepth = 0;" +
        "  function getDepth(el, d) { if(d > maxDepth) maxDepth = d; for(var i=0;i<el.children.length;i++) getDepth(el.children[i], d+1); }" +
        "  getDepth(document.documentElement, 0);" +
        "  result.maxDomDepth = maxDepth;" +
        "  var httpLinks = document.querySelectorAll('a[href^=\"http://\"]');" +
        "  result.insecureLinks = httpLinks.length;" +
        "  var passwordFields = document.querySelectorAll('input[type=\"password\"]');" +
        "  result.passwordFields = passwordFields.length;" +
        "  var largeImages = document.querySelectorAll('img');" +
        "  var bigImgCount = 0;" +
        "  for(var i=0;i<largeImages.length;i++) { if(largeImages[i].width > 500 || largeImages[i].height > 500) bigImgCount++; }" +
        "  result.largeImages = bigImgCount;" +
        "  var scripts = document.querySelectorAll('script');" +
        "  result.scriptCount = scripts.length;" +
        "  var inlineScripts = 0;" +
        "  for(var i=0;i<scripts.length;i++) { if(scripts[i].textContent && scripts[i].textContent.trim().length > 0) inlineScripts++; }" +
        "  result.inlineScripts = inlineScripts;" +
        "  var viewport = document.querySelector('meta[name=\"viewport\"]');" +
        "  result.hasViewport = viewport !== null;" +
        "  result.docSize = document.documentElement.outerHTML.length;" +
        "  result.title = document.title || '(no title)';" +
        "  result.url = location.href;" +
        "  return JSON.stringify(result);" +
        "})();";

    private static final String HTML_CAPTURE_JS =
        "(function() { return document.documentElement.outerHTML; })();";

    private static final String CAPTURE_BRIDGE_JS =
        "window.__wd_capture = function(html) {" +
        "  if(window.__wd_android && window.__wd_android.captureHtml) {" +
        "    window.__wd_android.captureHtml(html);" +
        "  }" +
        "};" +
        "window.__wd_log = function(msg) {" +
        "  if(window.__wd_android && window.__wd_android.log) {" +
        "    window.__wd_android.log(msg);" +
        "  }" +
        "};";

    private static void log(String level, String msg) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
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
    private static void ok(String msg) { capturedCount.incrementAndGet(); log("OK", "✅ " + msg); }
    private static void warn(String msg) { log("WARN", "⚠️ " + msg); }
    private static void err(String msg) { failedCount.incrementAndGet(); log("ERR", "❌ " + msg); }
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
                saveFile(LOG_FILE, sb.toString().getBytes("UTF-8"), true);
                LOG_BUFFER.clear();
            } catch (Exception e) {}
        }
    }

    private static void saveFile(String path, byte[] data, boolean append) {
        try {
            File f = new File(path);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileOutputStream fos = new FileOutputStream(f, append);
            fos.write(data);
            fos.close();
        } catch (Exception e) {
            err("保存失败 " + path + ": " + e.getMessage());
        }
    }

    private static void saveFile(String path, byte[] data) {
        saveFile(path, data, false);
    }

    private static byte[] readInputStream(InputStream is) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) {
                bos.write(buf, 0, len);
            }
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
                OUTPUT_DIR + "chromium/",
                OUTPUT_DIR + "captured/"
        };
        for (String dir : dirs) {
            new File(dir).mkdirs();
        }
    }

    private static SharedPreferences getModulePrefs(Context ctx) {
        if (modulePrefs == null) {
            try {
                modulePrefs = ctx.createPackageContext(
                        "com.webdecrypt.xposed",
                        Context.CONTEXT_IGNORE_SECURITY
                ).getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
            } catch (Exception e) {
                warn("无法读取模块配置: " + e.getMessage());
            }
        }
        return modulePrefs;
    }

    private static List<String> getTargetKeywords(Context ctx) {
        List<String> keywords = new ArrayList<>();
        try {
            SharedPreferences sp = getModulePrefs(ctx);
            if (sp != null) {
                String kw = sp.getString(KEY_TARGET_KEYWORDS, "Web,Main,Browser,Home,Content");
                if (kw != null && !kw.trim().isEmpty()) {
                    for (String k : kw.split(",")) {
                        String trimmed = k.trim();
                        if (!trimmed.isEmpty()) keywords.add(trimmed);
                    }
                }
            }
        } catch (Exception e) {}
        if (keywords.isEmpty()) {
            keywords.addAll(Arrays.asList("Web", "Main", "Browser", "Home", "Content"));
        }
        return keywords;
    }

    private static boolean shouldTargetActivity(String activityName, Context ctx) {
        List<String> keywords = getTargetKeywords(ctx);
        for (String kw : keywords) {
            if (activityName.contains(kw)) return true;
        }
        return false;
    }

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
                            if (!autoCapture.get()) return;
                            String filename = (String) param.args[0];
                            trace("AssetManager.open: " + filename);

                            if (isTargetFile(filename)) {
                                try {
                                    InputStream is = (InputStream) param.getResult();
                                    if (is != null) {
                                        byte[] data = readInputStream(is);
                                        if (data != null && data.length > 0 && data.length < 50 * 1024 * 1024) {
                                            String outputPath = OUTPUT_DIR + "assets/" + generateSafeFilename(filename);
                                            synchronized (CAPTURED_FILES) {
                                                if (CAPTURED_FILES.add(outputPath)) {
                                                    saveFile(outputPath, data);
                                                    ok("Asset捕获: " + filename + " (" + data.length + "B)");
                                                    addCapturedFileName("assets/" + filename);
                                                }
                                            }
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

    private static void hookWebView(ClassLoader classLoader) {
        try {
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
                                addCapturedFileName("webview/loadData_" + System.currentTimeMillis() + ".html");
                            }

                            if (param.thisObject instanceof WebView) {
                                trackWebView((WebView) param.thisObject);
                            }
                        }
                    }
            );

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
                                addCapturedFileName("webview/loadDataWithBaseURL_" + System.currentTimeMillis() + ".html");
                            }

                            if (param.thisObject instanceof WebView) {
                                trackWebView((WebView) param.thisObject);
                            }
                        }
                    }
            );

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

        hookWebViewClient(classLoader);
        hookJSBridge(classLoader);
    }

    private static void hookWebViewClient(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.webkit.WebViewClient",
                    classLoader,
                    "shouldInterceptRequest",
                    WebView.class,
                    WebResourceRequest.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!autoCapture.get()) return;
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
                                            addCapturedFileName("intercepted/" + safeName);

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

    private static void hookCryptoLibraries(ClassLoader classLoader) {
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
                                decryptedCount.incrementAndGet();
                                addCapturedFileName("decrypted/cipher_" + System.currentTimeMillis() + ".html");
                            }
                        }
                    }
            );
            info("✅ Cipher Hook 完成");
        } catch (Exception e) {
            warn("Cipher Hook 失败: " + e.getMessage());
        }

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
                                addCapturedFileName("decoded/base64_" + System.currentTimeMillis() + ".html");
                            }
                        }
                    }
            );
            info("✅ Base64 Hook 完成");
        } catch (Exception e) {}
    }

    private static void hookWebResourceResponse(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookConstructor(
                    "android.webkit.WebResourceResponse",
                    classLoader,
                    String.class, String.class, InputStream.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!autoCapture.get()) return;
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
                                        addCapturedFileName("response/response_" + System.currentTimeMillis() + ".dat");

                                        try {
                                            param.args[2] = new ByteArrayInputStream(data);
                                        } catch (Exception ignore) {}
                                    }
                                }
                            } catch (Exception e) {}
                        }
                    }
            );
            info("✅ WebResourceResponse Hook 完成");
        } catch (Exception e) {
            warn("WebResourceResponse Hook 失败: " + e.getMessage());
        }
    }

    private static void hookChromium(ClassLoader classLoader) {
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
            } catch (Exception e) {
                dbg("Chromium Hook异常: " + className + " → " + e.getMessage());
            }
        }

        info("✅ Chromium 内核扫描完成");
    }

    private static void trackWebView(WebView webView) {
        synchronized (trackedWebViews) {
            if (!trackedWebViews.contains(webView)) {
                trackedWebViews.add(webView);
                info("追踪WebView: " + webView.hashCode());

                injectBridgeIntoWebView(webView);
            }
        }
    }

    private static void injectBridgeIntoWebView(WebView webView) {
        try {
            final Object bridge = new Object() {
                @JavascriptInterface
                public void captureHtml(String html) {
                    if (html != null && !html.isEmpty()) {
                        String outputPath = OUTPUT_DIR + "captured/js_capture_" + System.currentTimeMillis() + ".html";
                        saveFile(outputPath, html.getBytes());
                        ok("JS捕捉HTML: " + html.length() + " chars");
                        addCapturedFileName("captured/js_capture_" + System.currentTimeMillis() + ".html");
                    }
                }

                @JavascriptInterface
                public void log(String msg) {
                    info("[JS] " + msg);
                }
            };

            webView.addJavascriptInterface(bridge, "__wd_android");

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        webView.evaluateJavascript(CAPTURE_BRIDGE_JS, null);
                    } catch (Exception e) {}
                }
            }, 500);

            dbg("Bridge注入完成: " + webView.hashCode());
        } catch (Exception e) {
            warn("Bridge注入失败: " + e.getMessage());
        }
    }

    private static void injectCustomJs(WebView webView, Context ctx) {
        try {
            SharedPreferences sp = getModulePrefs(ctx);
            if (sp == null) return;

            boolean autoInject = sp.getBoolean(KEY_AUTO_INJECT, false);
            if (!autoInject) return;

            String scripts = sp.getString(KEY_JS_SCRIPTS, "");
            if (scripts == null || scripts.trim().isEmpty()) return;

            String[] parts = scripts.split("#---#");
            for (String script : parts) {
                String trimmed = script.trim();
                if (!trimmed.isEmpty()) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                webView.evaluateJavascript(trimmed, null);
                                info("自定义JS注入: " + trimmed.substring(0, Math.min(trimmed.length(), 80)) + "...");
                            } catch (Exception e) {
                                warn("JS注入失败: " + e.getMessage());
                            }
                        }
                    }, 1500);
                }
            }
        } catch (Exception e) {
            warn("读取自定义JS失败: " + e.getMessage());
        }
    }

    private static void captureCurrentHtml(WebView webView) {
        try {
            webView.evaluateJavascript(HTML_CAPTURE_JS, new android.webkit.ValueCallback<String>() {
                @Override
                public void onReceiveValue(String html) {
                    if (html != null && !html.isEmpty()) {
                        String unquoted = html;
                        if (unquoted.startsWith("\"") && unquoted.endsWith("\"")) {
                            unquoted = unquoted.substring(1, unquoted.length() - 1);
                        }
                        unquoted = unquoted.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"");

                        String outputPath = OUTPUT_DIR + "captured/live_" + System.currentTimeMillis() + ".html";
                        saveFile(outputPath, unquoted.getBytes());
                        ok("实时HTML捕捉: " + unquoted.length() + " chars");
                        addCapturedFileName("captured/live_" + System.currentTimeMillis() + ".html");
                    }
                }
            });
        } catch (Exception e) {
            warn("HTML捕捉失败: " + e.getMessage());
        }
    }

    private static void runOptimizationAnalysis(WebView webView) {
        try {
            webView.evaluateJavascript(OPTIMIZATION_JS, new android.webkit.ValueCallback<String>() {
                @Override
                public void onReceiveValue(String result) {
                    if (result != null && !result.isEmpty()) {
                        String unquoted = result;
                        if (unquoted.startsWith("\"") && unquoted.endsWith("\"")) {
                            unquoted = unquoted.substring(1, unquoted.length() - 1);
                        }
                        unquoted = unquoted.replace("\\\"", "\"").replace("\\n", "\n");

                        updateOptimizationView(unquoted);
                    }
                }
            });
        } catch (Exception e) {
            warn("优化分析失败: " + e.getMessage());
        }
    }

    private static void updateOptimizationView(String jsonResult) {
        if (optimizationTextView == null || mainHandler == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("📊 页面分析结果\n\n");

                    String[] pairs = jsonResult.replace("{", "").replace("}", "").replace("\"", "").split(",");
                    Map<String, String> map = new HashMap<>();
                    for (String pair : pairs) {
                        String[] kv = pair.split(":", 2);
                        if (kv.length == 2) {
                            map.put(kv[0].trim(), kv[1].trim());
                        }
                    }

                    String title = map.getOrDefault("title", "(unknown)");
                    String url = map.getOrDefault("url", "(unknown)");
                    sb.append("📄 标题: ").append(title).append("\n");
                    sb.append("🔗 URL: ").append(url).append("\n\n");

                    int totalNodes = Integer.parseInt(map.getOrDefault("totalNodes", "0"));
                    int maxDepth = Integer.parseInt(map.getOrDefault("maxDomDepth", "0"));
                    sb.append("📊 DOM节点: ").append(totalNodes).append(" | 深度: ").append(maxDepth).append("\n");

                    int insecureLinks = Integer.parseInt(map.getOrDefault("insecureLinks", "0"));
                    if (insecureLinks > 0) {
                        sb.append("🔒 ⚠️ 不安全HTTP链接: ").append(insecureLinks).append("\n");
                    } else {
                        sb.append("🔒 ✅ 无不安全HTTP链接\n");
                    }

                    int passwordFields = Integer.parseInt(map.getOrDefault("passwordFields", "0"));
                    if (passwordFields > 0) {
                        sb.append("🔑 密码字段: ").append(passwordFields).append("\n");
                    }

                    int largeImages = Integer.parseInt(map.getOrDefault("largeImages", "0"));
                    if (largeImages > 0) {
                        sb.append("🖼 ⚠️ 大尺寸图片: ").append(largeImages).append(" (建议压缩)\n");
                    }

                    int scriptCount = Integer.parseInt(map.getOrDefault("scriptCount", "0"));
                    int inlineScripts = Integer.parseInt(map.getOrDefault("inlineScripts", "0"));
                    sb.append("📜 脚本: ").append(scriptCount).append(" (内联: ").append(inlineScripts).append(")\n");

                    boolean hasViewport = Boolean.parseBoolean(map.getOrDefault("hasViewport", "false"));
                    if (!hasViewport) {
                        sb.append("📱 ⚠️ 缺少viewport meta标签\n");
                    } else {
                        sb.append("📱 ✅ viewport已设置\n");
                    }

                    int docSize = Integer.parseInt(map.getOrDefault("docSize", "0"));
                    sb.append("📏 文档大小: ").append(docSize / 1024).append(" KB\n");

                    sb.append("\n💡 优化建议:\n");
                    if (insecureLinks > 0) sb.append("• 将HTTP链接升级为HTTPS\n");
                    if (largeImages > 0) sb.append("• 压缩大尺寸图片资源\n");
                    if (inlineScripts > 3) sb.append("• 将内联脚本提取到外部文件\n");
                    if (!hasViewport) sb.append("• 添加viewport meta标签\n");
                    if (maxDepth > 15) sb.append("• 简化DOM结构，当前深度过深\n");
                    if (docSize > 500 * 1024) sb.append("• 页面过大，考虑懒加载\n");

                    optimizationTextView.setText(sb.toString());
                } catch (Exception e) {
                    optimizationTextView.setText("分析结果解析失败: " + e.getMessage());
                }
            }
        });
    }

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

                            if (shouldTargetActivity(activityName, activity)) {
                                currentActivityRef = new WeakReference<>(activity);

                                if (!isFloatingShown) {
                                    info("🎯 检测到目标Activity: " + activityName);

                                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            Activity current = currentActivityRef.get();
                                            if (current != null && !current.isFinishing()) {
                                                showFloatingWindow(current);
                                                scanAndDecrypt(current);
                                            }
                                        }
                                    }, 1500);
                                }
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    classLoader,
                    "onDestroy",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Activity activity = (Activity) param.thisObject;
                            if (currentActivityRef != null && currentActivityRef.get() == activity) {
                                removeFloatingWindow();
                            }
                        }
                    }
            );

            info("✅ Activity生命周期 Hook 完成");
        } catch (Exception e) {
            warn("Activity Hook 失败: " + e.getMessage());
        }
    }

    private static void removeFloatingWindow() {
        try {
            if (windowManager != null) {
                if (floatingBallView != null && floatingBallView.isShown()) {
                    windowManager.removeView(floatingBallView);
                }
                if (floatingPanelView != null && floatingPanelView.isShown()) {
                    windowManager.removeView(floatingPanelView);
                }
            }
        } catch (Exception e) {}
        floatingBallView = null;
        floatingPanelView = null;
        isPanelExpanded = false;
        isFloatingShown = false;
    }

    private static void showFloatingWindow(final Activity activity) {
        try {
            windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            mainHandler = new Handler(Looper.getMainLooper());

            final View ballView = createFloatingBall(activity);
            floatingPanelView = createFloatingPanel(activity);

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

            ballView.setOnTouchListener(new View.OnTouchListener() {
                private int lastX, lastY;
                private int initialX, initialY;
                private long touchStartTime = 0;
                private float touchStartX, touchStartY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            lastX = (int) event.getRawX();
                            lastY = (int) event.getRawY();
                            initialX = ballParams.x;
                            initialY = ballParams.y;
                            touchStartTime = System.currentTimeMillis();
                            touchStartX = event.getRawX();
                            touchStartY = event.getRawY();
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            int dx = (int) event.getRawX() - lastX;
                            int dy = (int) event.getRawY() - lastY;
                            ballParams.x = initialX - dx;
                            ballParams.y = initialY + dy;
                            try {
                                windowManager.updateViewLayout(ballView, ballParams);
                            } catch (Exception e) {}
                            return true;

                        case MotionEvent.ACTION_UP:
                            float moveDistance = Math.abs(event.getRawX() - touchStartX) +
                                    Math.abs(event.getRawY() - touchStartY);
                            if (System.currentTimeMillis() - touchStartTime < 200 && moveDistance < 20) {
                                togglePanel(activity);
                            }
                            return true;
                    }
                    return false;
                }
            });

            windowManager.addView(ballView, ballParams);
            floatingBallView = ballView;
            isFloatingShown = true;

            info("✅ 悬浮窗已注入!");

            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateStats();
                    if (isFloatingShown) {
                        mainHandler.postDelayed(this, 2000);
                    }
                }
            }, 2000);

        } catch (Exception e) {
            warn("悬浮窗失败: " + e.getMessage());
            showFallbackDialog(activity);
        }
    }

    private static View createFloatingBall(Context ctx) {
        TextView ball = new TextView(ctx);
        ball.setText("🔓");
        ball.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        ball.setBackgroundColor(Color.parseColor("#1A1A2E"));
        ball.setPadding(10, 10, 10, 10);

        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setBackgroundColor(Color.parseColor("#E94560"));
        wrapper.setPadding(3, 3, 3, 3);
        wrapper.addView(ball);

        return wrapper;
    }

    private static View createFloatingPanel(Context ctx) {
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setBackgroundColor(Color.parseColor("#E01A1A2E"));

        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(16, 16, 16, 16);
        panel.setMinimumWidth(420);

        TextView title = new TextView(ctx);
        title.setText("🔓 WebDecrypt Pro v9.0");
        title.setTextColor(Color.parseColor("#E94560"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 8);
        panel.addView(title);

        statsTextView = new TextView(ctx);
        statsTextView.setText("捕获: 0 | 解密: 0 | 失败: 0");
        statsTextView.setTextColor(Color.WHITE);
        statsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        statsTextView.setPadding(0, 0, 0, 8);
        panel.addView(statsTextView);

        LinearLayout btnRow1 = new LinearLayout(ctx);
        btnRow1.setOrientation(LinearLayout.HORIZONTAL);
        btnRow1.setPadding(0, 0, 0, 4);

        Button btnStart = createPanelButton(ctx, "▶ 监控", "#0F3460");
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoCapture.set(true);
                Toast.makeText(ctx, "监控已开启!", Toast.LENGTH_SHORT).show();
            }
        });
        btnRow1.addView(btnStart, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnStop = createPanelButton(ctx, "⏸ 暂停", "#16213E");
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoCapture.set(false);
                Toast.makeText(ctx, "监控已暂停", Toast.LENGTH_SHORT).show();
            }
        });
        btnRow1.addView(btnStop, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnExport = createPanelButton(ctx, "📁 导出", "#E94560");
        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flushLog();
                Toast.makeText(ctx, "日志已保存: " + LOG_FILE, Toast.LENGTH_LONG).show();
            }
        });
        btnRow1.addView(btnExport, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        panel.addView(btnRow1);

        LinearLayout btnRow2 = new LinearLayout(ctx);
        btnRow2.setOrientation(LinearLayout.HORIZONTAL);
        btnRow2.setPadding(0, 0, 0, 4);

        Button btnCaptureHtml = createPanelButton(ctx, "🌐 捕捉HTML", "#0F3460");
        btnCaptureHtml.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (trackedWebViews) {
                    if (trackedWebViews.isEmpty()) {
                        Toast.makeText(ctx, "暂无WebView", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    for (WebView wv : trackedWebViews) {
                        captureCurrentHtml(wv);
                    }
                }
                Toast.makeText(ctx, "HTML捕捉已触发!", Toast.LENGTH_SHORT).show();
            }
        });
        btnRow2.addView(btnCaptureHtml, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnAnalyze = createPanelButton(ctx, "🧠 优化分析", "#0F3460");
        btnAnalyze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (trackedWebViews) {
                    if (trackedWebViews.isEmpty()) {
                        Toast.makeText(ctx, "暂无WebView", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    for (WebView wv : trackedWebViews) {
                        runOptimizationAnalysis(wv);
                    }
                }
                Toast.makeText(ctx, "优化分析已触发!", Toast.LENGTH_SHORT).show();
            }
        });
        btnRow2.addView(btnAnalyze, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnInjectJs = createPanelButton(ctx, "💉 注入JS", "#0F3460");
        btnInjectJs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showJsInjectDialog(ctx);
            }
        });
        btnRow2.addView(btnInjectJs, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        panel.addView(btnRow2);

        TextView optTitle = new TextView(ctx);
        optTitle.setText("🧠 优化建议");
        optTitle.setTextColor(Color.parseColor("#E94560"));
        optTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        optTitle.setTypeface(null, Typeface.BOLD);
        optTitle.setPadding(0, 8, 0, 4);
        panel.addView(optTitle);

        optimizationTextView = new TextView(ctx);
        optimizationTextView.setText("点击「🧠 优化分析」获取建议...");
        optimizationTextView.setTextColor(Color.parseColor("#AADDAA"));
        optimizationTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        optimizationTextView.setMaxLines(12);
        optimizationTextView.setPadding(8, 8, 8, 8);
        optimizationTextView.setBackgroundColor(Color.parseColor("#0A0A1A"));
        panel.addView(optimizationTextView);

        TextView fileTitle = new TextView(ctx);
        fileTitle.setText("📁 已捕获文件");
        fileTitle.setTextColor(Color.parseColor("#E94560"));
        fileTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        fileTitle.setTypeface(null, Typeface.BOLD);
        fileTitle.setPadding(0, 8, 0, 4);
        panel.addView(fileTitle);

        fileListTextView = new TextView(ctx);
        fileListTextView.setText("等待捕获...");
        fileListTextView.setTextColor(Color.parseColor("#A7A7A7"));
        fileListTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        fileListTextView.setMaxLines(6);
        panel.addView(fileListTextView);

        Button btnClose = new Button(ctx);
        btnClose.setText("✕ 收起面板");
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setTextColor(Color.parseColor("#E94560"));
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePanel(ctx);
            }
        });
        panel.addView(btnClose);

        scrollView.addView(panel);
        return scrollView;
    }

    private static Button createPanelButton(Context ctx, String text, String bgColor) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setBackgroundColor(Color.parseColor(bgColor));
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setPadding(8, 4, 8, 4);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private static void showJsInjectDialog(Context ctx) {
        try {
            Activity activity = null;
            if (currentActivityRef != null) {
                activity = currentActivityRef.get();
            }
            if (activity == null || activity.isFinishing()) {
                Toast.makeText(ctx, "无法打开注入对话框", Toast.LENGTH_SHORT).show();
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("💉 JS注入");

            LinearLayout dialogLayout = new LinearLayout(activity);
            dialogLayout.setOrientation(LinearLayout.VERTICAL);
            dialogLayout.setPadding(32, 16, 32, 16);

            TextView hint = new TextView(activity);
            hint.setText("输入要注入的JavaScript代码:");
            hint.setTextColor(Color.parseColor("#8888AA"));
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            hint.setPadding(0, 0, 0, 8);
            dialogLayout.addView(hint);

            EditText jsInput = new EditText(activity);
            jsInput.setHint("// 输入JS代码...");
            jsInput.setHintTextColor(Color.parseColor("#555577"));
            jsInput.setTextColor(Color.parseColor("#AADDAA"));
            jsInput.setBackgroundColor(Color.parseColor("#1A1A2E"));
            jsInput.setTypeface(Typeface.MONOSPACE);
            jsInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            jsInput.setPadding(16, 12, 16, 12);
            jsInput.setMinLines(5);
            jsInput.setGravity(Gravity.TOP | Gravity.START);

            SharedPreferences sp = getModulePrefs(ctx);
            if (sp != null) {
                String saved = sp.getString(KEY_JS_SCRIPTS, "");
                if (saved != null && !saved.isEmpty()) {
                    String[] parts = saved.split("#---#");
                    if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                        jsInput.setText(parts[0].trim());
                    }
                }
            }

            dialogLayout.addView(jsInput);
            builder.setView(dialogLayout);

            builder.setPositiveButton("注入", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String js = jsInput.getText().toString().trim();
                    if (js.isEmpty()) return;

                    synchronized (trackedWebViews) {
                        for (WebView wv : trackedWebViews) {
                            try {
                                wv.evaluateJavascript(js, null);
                                ok("手动JS注入: " + js.substring(0, Math.min(js.length(), 80)) + "...");
                            } catch (Exception e) {
                                err("JS注入失败: " + e.getMessage());
                            }
                        }
                    }
                    Toast.makeText(ctx, "JS已注入!", Toast.LENGTH_SHORT).show();
                }
            });

            builder.setNeutralButton("注入到所有", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String js = jsInput.getText().toString().trim();
                    if (js.isEmpty()) return;

                    synchronized (trackedWebViews) {
                        for (WebView wv : trackedWebViews) {
                            try {
                                wv.evaluateJavascript(js, null);
                            } catch (Exception e) {}
                        }
                    }
                    ok("批量JS注入到 " + trackedWebViews.size() + " 个WebView");
                    Toast.makeText(ctx, "已注入所有WebView!", Toast.LENGTH_SHORT).show();
                }
            });

            builder.setNegativeButton("取消", null);
            builder.show();
        } catch (Exception e) {
            warn("JS注入对话框失败: " + e.getMessage());
        }
    }

    private static void togglePanel(Context ctx) {
        try {
            if (isPanelExpanded) {
                if (floatingPanelView != null && floatingPanelView.isShown()) {
                    windowManager.removeView(floatingPanelView);
                }
                isPanelExpanded = false;
            } else {
                if (floatingPanelView == null) return;
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
                windowManager.addView(floatingPanelView, panelParams);
                isPanelExpanded = true;
            }
        } catch (Exception e) {
            dbg("面板切换失败: " + e.getMessage());
        }
    }

    private static void updateStats() {
        if (statsTextView != null && mainHandler != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        statsTextView.setText("捕获: " + capturedCount.get() +
                                " | 解密: " + decryptedCount.get() +
                                " | 失败: " + failedCount.get());
                    } catch (Exception e) {}
                }
            });
        }
    }

    private static void addCapturedFileName(String name) {
        synchronized (capturedFileNames) {
            capturedFileNames.add(name);
            if (capturedFileNames.size() > 20) {
                capturedFileNames.remove(0);
            }
        }
        updateFileList();
    }

    private static void updateFileList() {
        if (fileListTextView == null || mainHandler == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (capturedFileNames) {
                        if (capturedFileNames.isEmpty()) {
                            fileListTextView.setText("等待捕获...");
                        } else {
                            StringBuilder sb = new StringBuilder();
                            int start = Math.max(0, capturedFileNames.size() - 8);
                            for (int i = start; i < capturedFileNames.size(); i++) {
                                sb.append("• ").append(capturedFileNames.get(i)).append("\n");
                            }
                            fileListTextView.setText(sb.toString());
                        }
                    }
                } catch (Exception e) {}
            }
        });
    }

    private static void showFallbackDialog(final Activity activity) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    new AlertDialog.Builder(activity)
                            .setTitle("🔓 WebDecrypt Pro v9.0")
                            .setMessage(
                                    "系统层级拦截已激活!\n\n" +
                                            "功能:\n" +
                                            "• AssetManager拦截 - 资源加载\n" +
                                            "• WebView拦截 - 渲染引擎\n" +
                                            "• Cipher拦截 - 加密解密\n" +
                                            "• Chromium拦截 - 内核层\n" +
                                            "• JS注入 - 自定义脚本\n" +
                                            "• HTML捕捉 - 实时源码\n\n" +
                                            "输出: " + OUTPUT_DIR + "\n" +
                                            "统计: 捕获" + capturedCount.get() + " 解密" + decryptedCount.get()
                            )
                            .setPositiveButton("导出日志", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    flushLog();
                                    Toast.makeText(activity, "日志已保存!", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("关闭", null)
                            .show();
                } catch (Exception e) {}
            }
        });
    }

    private static void scanAndDecrypt(Activity activity) {
        info("开始全量扫描...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
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
                                    synchronized (CAPTURED_FILES) {
                                        if (CAPTURED_FILES.add(outputPath)) {
                                            saveFile(outputPath, data);
                                            ok("扫描捕获: " + name + " (" + data.length + "B)");
                                            count++;
                                        }
                                    }
                                }
                            } catch (Exception e) {}
                        }
                    }
                    zip.close();
                    info("APK扫描完成: " + count + " 个文件");

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
                        synchronized (CAPTURED_FILES) {
                            if (CAPTURED_FILES.add(outputPath)) {
                                saveFile(outputPath, data);
                                ok("Data目录捕获: " + relativePath + " (" + data.length + "B)");
                            }
                        }
                    }
                } catch (Exception e) {}
            }
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("android") ||
                lpparam.packageName.equals("com.webdecrypt.xposed") ||
                lpparam.packageName.equals("de.robv.android.xposed.installer") ||
                lpparam.packageName.startsWith("com.android.") ||
                lpparam.packageName.startsWith("com.google.") ||
                lpparam.packageName.startsWith("com.topjohnwu.")) {
            return;
        }

        info("╔══════════════════════════════════════════════════════════╗");
        info("║  WebDecrypt Pro v9.0 — 目标: " + lpparam.packageName);
        info("╚══════════════════════════════════════════════════════════╝");

        ensureDirs();

        ClassLoader classLoader = lpparam.classLoader;

        hookAssetManager(classLoader);
        hookWebView(classLoader);
        hookWebResourceResponse(classLoader);
        hookCryptoLibraries(classLoader);
        hookChromium(classLoader);
        hookActivity(classLoader);

        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                flushLog();
            }
        }, 30000, 30000);

        info("══════════════════════════════════════════════════════════");
        info("  所有Hook已安装完成! 目标: " + lpparam.packageName);
        info("  输出目录: " + OUTPUT_DIR);
        info("  新功能: JS注入 | HTML捕捉 | 优化建议 | 悬浮窗交互");
        info("══════════════════════════════════════════════════════════");
    }
}
