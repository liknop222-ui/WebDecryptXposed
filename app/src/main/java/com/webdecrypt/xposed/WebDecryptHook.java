package com.webdecrypt.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
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
    private static final String KEY_ANTI_DETECTION = "anti_detection";

    private static final AtomicInteger capturedCount = new AtomicInteger(0);
    private static final AtomicInteger decryptedCount = new AtomicInteger(0);
    private static final AtomicInteger failedCount = new AtomicInteger(0);
    private static final AtomicBoolean autoCapture = new AtomicBoolean(true);

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
    private static final List<Object> trackedWebViews = new ArrayList<>();
    private static final List<String> trackedWebViewTypes = new ArrayList<>();

    private static final String OPTIMIZATION_JS =
        "(function(){" +
        "var r={};var a=document.querySelectorAll('*');r.totalNodes=a.length;" +
        "var md=0;function g(e,d){if(d>md)md=d;for(var i=0;i<e.children.length;i++)g(e.children[i],d+1)}" +
        "g(document.documentElement,0);r.maxDomDepth=md;" +
        "r.insecureLinks=document.querySelectorAll('a[href^=\"http://\"]').length;" +
        "r.passwordFields=document.querySelectorAll('input[type=\"password\"]').length;" +
        "var bi=0;document.querySelectorAll('img').forEach(function(i){if(i.width>500||i.height>500)bi++});r.largeImages=bi;" +
        "r.scriptCount=document.querySelectorAll('script').length;" +
        "var is=0;document.querySelectorAll('script').forEach(function(s){if(s.textContent&&s.textContent.trim().length>0)is++});r.inlineScripts=is;" +
        "r.hasViewport=document.querySelector('meta[name=\"viewport\"]')!==null;" +
        "r.docSize=document.documentElement.outerHTML.length;" +
        "r.title=document.title||'';r.url=location.href;" +
        "return JSON.stringify(r);})();";

    private static final String HTML_CAPTURE_JS =
        "(function(){return document.documentElement.outerHTML;})();";

    private static final String CAPTURE_BRIDGE_JS =
        "window.__wd_capture=function(h){if(window.__wd_android&&window.__wd_android.captureHtml)window.__wd_android.captureHtml(h)};" +
        "window.__wd_log=function(m){if(window.__wd_android&&window.__wd_android.log)window.__wd_android.log(m)};";

    private static void log(String level, String msg) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        String line = "[" + timestamp + "] [" + level + "] " + msg;
        XposedBridge.log(line);
        Log.d(TAG, line);
        synchronized (LOG_BUFFER) {
            LOG_BUFFER.add(line);
            if (LOG_BUFFER.size() >= 100) flushLog();
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
                for (String line : LOG_BUFFER) sb.append(line).append("\n");
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

    private static void saveFile(String path, byte[] data) { saveFile(path, data, false); }

    private static byte[] readInputStream(InputStream is) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) bos.write(buf, 0, len);
            byte[] data = bos.toByteArray();
            bos.close();
            return data;
        } catch (Exception e) { return null; }
    }

    private static boolean isTargetFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        for (String ext : TARGET_EXTENSIONS) { if (lower.endsWith(ext)) return true; }
        return lower.contains("index") || lower.contains("main") || lower.contains("app") || lower.contains("view");
    }

    private static String generateSafeFilename(String input) {
        if (input == null) input = "unknown";
        String safe = input.replaceAll("[^a-zA-Z0-9._\\-/]", "_").replaceAll("_+", "_");
        return safe.substring(0, Math.min(safe.length(), 100));
    }

    private static boolean isHtmlContent(byte[] data) {
        if (data == null || data.length < 5) return false;
        try {
            String head = new String(data, 0, Math.min(data.length, 500), "UTF-8").toLowerCase();
            return head.contains("<html") || head.contains("<!doctype") || head.contains("<head") ||
                    head.contains("<body") || head.contains("<script") || head.contains("<div") ||
                    head.contains("function(") || head.contains("var ");
        } catch (Exception e) { return false; }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, 32); i++) sb.append(String.format("%02x", bytes[i] & 0xff));
        return sb.toString();
    }

    private static void ensureDirs() {
        String[] dirs = {OUTPUT_DIR, OUTPUT_DIR+"assets/", OUTPUT_DIR+"webview/", OUTPUT_DIR+"intercepted/",
                OUTPUT_DIR+"response/", OUTPUT_DIR+"decrypted/", OUTPUT_DIR+"decoded/",
                OUTPUT_DIR+"scanned/", OUTPUT_DIR+"chromium/", OUTPUT_DIR+"captured/"};
        for (String dir : dirs) new File(dir).mkdirs();
    }

    private static SharedPreferences getModulePrefs(Context ctx) {
        if (modulePrefs == null) {
            try {
                modulePrefs = ctx.createPackageContext("com.webdecrypt.xposed", Context.CONTEXT_IGNORE_SECURITY)
                        .getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
            } catch (Exception e) { warn("无法读取模块配置: " + e.getMessage()); }
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
                    for (String k : kw.split(",")) { String t = k.trim(); if (!t.isEmpty()) keywords.add(t); }
                }
            }
        } catch (Exception e) {}
        if (keywords.isEmpty()) keywords.addAll(Arrays.asList("Web", "Main", "Browser", "Home", "Content"));
        return keywords;
    }

    private static boolean shouldTargetActivity(String name, Context ctx) {
        for (String kw : getTargetKeywords(ctx)) { if (name.contains(kw)) return true; }
        return false;
    }

    private static boolean isAntiDetectionEnabled(Context ctx) {
        try {
            SharedPreferences sp = getModulePrefs(ctx);
            if (sp != null) return sp.getBoolean(KEY_ANTI_DETECTION, true);
        } catch (Exception e) {}
        return true;
    }

    private static void hookAssetManager(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("android.content.res.AssetManager", cl, "open", String.class,
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
                                    if (data != null && data.length > 0 && data.length < 50*1024*1024) {
                                        String op = OUTPUT_DIR + "assets/" + generateSafeFilename(filename);
                                        synchronized (CAPTURED_FILES) { if (CAPTURED_FILES.add(op)) { saveFile(op, data); ok("Asset捕获: " + filename + " (" + data.length + "B)"); addCapturedFileName("assets/" + filename); } }
                                        param.setResult(new ByteArrayInputStream(data));
                                    }
                                }
                            } catch (Exception e) { dbg("Asset读取失败: " + e.getMessage()); }
                        }
                    }
                });
            try { XposedHelpers.findAndHookMethod("android.content.res.AssetManager", cl, "open", String.class, int.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { trace("AssetManager.open(mode): " + p.args[0]); } }); } catch (Exception e) {}
            info("✅ AssetManager Hook 完成");
        } catch (Exception e) { warn("AssetManager Hook 失败: " + e.getMessage()); }
    }

    private static void hookSystemWebView(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("android.webkit.WebView", cl, "loadUrl", String.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) {
                    String u = (String) p.args[0]; trace("WebView.loadUrl: " + u);
                    if (u != null && (u.contains("file://") || u.contains("localhost") || u.contains("127.0.0.1") || u.contains("data:"))) info("🎯 本地加载: " + u);
                }});
            try { XposedHelpers.findAndHookMethod("android.webkit.WebView", cl, "loadUrl", String.class, Map.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { trace("WebView.loadUrl(headers): " + p.args[0]); } }); } catch (Exception e) {}

            XposedHelpers.findAndHookMethod("android.webkit.WebView", cl, "loadData", String.class, String.class, String.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) {
                    String d = (String) p.args[0]; String mt = (String) p.args[1]; trace("WebView.loadData: mt=" + mt + " len=" + (d != null ? d.length() : 0));
                    if (d != null && !d.isEmpty()) { String op = OUTPUT_DIR + "webview/loadData_" + System.currentTimeMillis() + ".html"; saveFile(op, d.getBytes()); ok("loadData捕获: " + d.length() + " chars"); addCapturedFileName("webview/loadData"); }
                    if (p.thisObject instanceof WebView) trackWebView(p.thisObject, "system");
                }});

            XposedHelpers.findAndHookMethod("android.webkit.WebView", cl, "loadDataWithBaseURL", String.class, String.class, String.class, String.class, String.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) {
                    String bu = (String) p.args[0]; String d = (String) p.args[1]; trace("WebView.loadDataWithBaseURL: baseUrl=" + bu);
                    if (d != null && !d.isEmpty()) { String op = OUTPUT_DIR + "webview/loadDataWithBaseURL_" + System.currentTimeMillis() + ".html"; saveFile(op, d.getBytes()); ok("loadDataWithBaseURL捕获: baseUrl=" + bu + " len=" + d.length()); addCapturedFileName("webview/loadDataWithBaseURL"); }
                    if (p.thisObject instanceof WebView) trackWebView(p.thisObject, "system");
                }});

            try { XposedHelpers.findAndHookMethod("android.webkit.WebView", cl, "evaluateJavascript", String.class, android.webkit.ValueCallback.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { String s = (String) p.args[0]; if (s != null && s.length() > 10) trace("evaluateJavascript: " + s.substring(0, Math.min(s.length(), 100))); } }); } catch (Exception e) {}

            info("✅ System WebView Hook 完成");
        } catch (Exception e) { warn("System WebView Hook 失败: " + e.getMessage()); }

        hookWebViewClient(cl);
        hookJSBridge(cl);
    }

    private static void hookX5WebView(ClassLoader cl) {
        String[] x5Classes = {
            "com.tencent.smtt.sdk.WebView",
            "com.tencent.smtt.webview.WebView",
            "com.tencent.tbs.tbsshell.WebView"
        };
        for (String className : x5Classes) {
            try {
                Class<?> x5Class = Class.forName(className, false, cl);
                dbg("发现X5内核类: " + className);

                for (Method m : x5Class.getDeclaredMethods()) {
                    String name = m.getName();
                    Class<?>[] pts = m.getParameterTypes();
                    if ((name.equals("loadUrl") || name.equals("loadData") || name.equals("loadDataWithBaseURL")) && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        try {
                            XposedBridge.hookMethod(m, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("X5.").append(param.method.getName()).append(": ");
                                    for (Object arg : param.args) {
                                        if (arg instanceof String) sb.append(arg).append(" ");
                                    }
                                    trace(sb.toString());

                                    for (Object arg : param.args) {
                                        if (arg instanceof String && ((String) arg).length() > 50) {
                                            String data = (String) arg;
                                            if (data.contains("<") || data.contains("{")) {
                                                String op = OUTPUT_DIR + "webview/x5_" + param.method.getName() + "_" + System.currentTimeMillis() + ".html";
                                                saveFile(op, data.getBytes("UTF-8"));
                                                ok("X5捕获: " + param.method.getName() + " len=" + data.length());
                                                addCapturedFileName("webview/x5_" + param.method.getName());
                                            }
                                        }
                                    }

                                    trackWebView(param.thisObject, "x5");
                                }
                            });
                            info("✅ X5 Hook: " + className + "." + name);
                        } catch (Exception e) { dbg("X5方法Hook失败: " + name); }
                    }
                }

                try {
                    Method evalMethod = x5Class.getMethod("evaluateJavascript", String.class, android.webkit.ValueCallback.class);
                    XposedBridge.hookMethod(evalMethod, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String script = (String) param.args[0];
                            if (script != null && script.length() > 10) trace("X5.evaluateJavascript: " + script.substring(0, Math.min(script.length(), 100)));
                        }
                    });
                } catch (NoSuchMethodException e) {}

                try {
                    Method addJI = x5Class.getMethod("addJavascriptInterface", Object.class, String.class);
                    XposedBridge.hookMethod(addJI, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object obj = param.args[0]; String name = (String) param.args[1];
                            info("X5 JS Bridge: " + name + " → " + obj.getClass().getName());
                        }
                    });
                } catch (NoSuchMethodException e) {}

            } catch (ClassNotFoundException e) {
            } catch (Exception e) { dbg("X5 Hook异常: " + className + " → " + e.getMessage()); }
        }
        info("✅ X5内核扫描完成");
    }

    private static void hookWebViewClient(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("android.webkit.WebViewClient", cl, "shouldInterceptRequest", WebView.class, WebResourceRequest.class,
                new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    if (!autoCapture.get()) return;
                    try {
                        WebResourceRequest req = (WebResourceRequest) p.args[1]; String url = req.getUrl().toString(); trace("shouldInterceptRequest: " + url);
                        WebResourceResponse resp = (WebResourceResponse) p.getResult();
                        if (resp != null && isTargetFile(url)) {
                            InputStream is = resp.getData();
                            if (is != null) {
                                byte[] data = readInputStream(is);
                                if (data != null && data.length > 0) {
                                    String sn = generateSafeFilename(url); String op = OUTPUT_DIR + "intercepted/" + sn;
                                    saveFile(op, data); ok("拦截捕获: " + url + " (" + data.length + "B)"); addCapturedFileName("intercepted/" + sn);
                                    p.setResult(new WebResourceResponse(resp.getMimeType(), resp.getEncoding(), new ByteArrayInputStream(data)));
                                }
                            }
                        }
                    } catch (Exception e) { dbg("shouldIntercept处理失败: " + e.getMessage()); }
                }});
            try { XposedHelpers.findAndHookMethod("android.webkit.WebViewClient", cl, "shouldInterceptRequest", WebView.class, String.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { trace("shouldInterceptRequest(legacy): " + p.args[1]); } }); } catch (Exception e) {}
            info("✅ WebViewClient Hook 完成");
        } catch (Exception e) { warn("WebViewClient Hook 失败: " + e.getMessage()); }
    }

    private static void hookJSBridge(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("android.webkit.WebView", cl, "addJavascriptInterface", Object.class, String.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Object obj = p.args[0]; String name = (String) p.args[1];
                    info("JS Bridge注入: " + name + " → " + obj.getClass().getName());
                    Method[] ms = obj.getClass().getDeclaredMethods();
                    for (int i = 0; i < Math.min(ms.length, 15); i++) dbg("  方法: " + ms[i].getName());
                }});
            info("✅ addJavascriptInterface Hook 完成");
        } catch (Exception e) {}
    }

    private static void hookCryptoLibraries(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("javax.crypto.Cipher", cl, "doFinal", byte[].class,
                new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    byte[] input = (byte[]) p.args[0]; byte[] result = (byte[]) p.getResult();
                    dbg("Cipher.doFinal: input=" + input.length + " output=" + (result != null ? result.length : 0));
                    if (result != null && result.length > 0 && isHtmlContent(result)) {
                        String op = OUTPUT_DIR + "decrypted/cipher_" + System.currentTimeMillis() + ".html";
                        saveFile(op, result); ok("Cipher解密捕获: " + result.length + "B"); decryptedCount.incrementAndGet(); addCapturedFileName("decrypted/cipher");
                    }
                }});
            info("✅ Cipher Hook 完成");
        } catch (Exception e) { warn("Cipher Hook 失败: " + e.getMessage()); }

        try { XposedHelpers.findAndHookConstructor("javax.crypto.spec.SecretKeySpec", cl, byte[].class, String.class,
            new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam p) {
                byte[] key = (byte[]) p.args[0]; String algo = (String) p.args[1];
                info("🔑 密钥: algorithm=" + algo + " keyLen=" + key.length); dbg("  密钥(hex): " + bytesToHex(key));
            }}); } catch (Exception e) {}

        try { XposedHelpers.findAndHookMethod("android.util.Base64", cl, "decode", byte[].class, int.class,
            new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam p) {
                byte[] result = (byte[]) p.getResult();
                if (result != null && result.length > 100 && isHtmlContent(result)) {
                    String op = OUTPUT_DIR + "decoded/base64_" + System.currentTimeMillis() + ".html";
                    saveFile(op, result); ok("Base64解码捕获: " + result.length + "B"); addCapturedFileName("decoded/base64");
                }
            }}); info("✅ Base64 Hook 完成"); } catch (Exception e) {}
    }

    private static void hookWebResourceResponse(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookConstructor("android.webkit.WebResourceResponse", cl, String.class, String.class, InputStream.class,
                new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    if (!autoCapture.get()) return;
                    try {
                        String mt = (String) p.args[0]; InputStream is = (InputStream) p.args[2];
                        if (is != null && mt != null && (mt.contains("html") || mt.contains("javascript") || mt.contains("css") || mt.contains("json"))) {
                            byte[] data = readInputStream(is);
                            if (data != null && data.length > 0 && data.length < 50*1024*1024) {
                                String op = OUTPUT_DIR + "response/response_" + System.currentTimeMillis() + "_" + mt.replace("/", "_") + ".dat";
                                saveFile(op, data); ok("Response捕获: mimeType=" + mt + " (" + data.length + "B)"); addCapturedFileName("response/response");
                                try { p.args[2] = new ByteArrayInputStream(data); } catch (Exception ignore) {}
                            }
                        }
                    } catch (Exception e) {}
                }});
            info("✅ WebResourceResponse Hook 完成");
        } catch (Exception e) { warn("WebResourceResponse Hook 失败: " + e.getMessage()); }
    }

    private static void hookChromium(ClassLoader cl) {
        String[] ccs = {"com.android.webview.chromium.WebViewChromium", "com.android.org.chromium.android_webview.AwContents",
                "com.android.org.chromium.content_public.browser.ContentViewCore", "org.chromium.android_webview.AwContents",
                "org.chromium.content.browser.webcontents.WebContentsImpl"};
        for (String cn : ccs) {
            try {
                Class<?> c = Class.forName(cn, false, cl); dbg("Chromium类: " + cn);
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("loadUrl")) { try { XposedBridge.hookMethod(m, new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { trace("Chromium.loadUrl: " + p.method.getDeclaringClass().getName()); } }); info("✅ Chromium Hook: " + cn + ".loadUrl"); } catch (Exception e) {} }
                }
            } catch (ClassNotFoundException e) {} catch (Exception e) { dbg("Chromium异常: " + cn); }
        }
        info("✅ Chromium 内核扫描完成");
    }

    private static void trackWebView(Object webView, String type) {
        synchronized (trackedWebViews) {
            if (!trackedWebViews.contains(webView)) {
                trackedWebViews.add(webView);
                trackedWebViewTypes.add(type);
                info("追踪WebView(" + type + "): " + webView.hashCode());
                injectBridgeIntoWebView(webView, type);
            }
        }
    }

    private static void injectBridgeIntoWebView(Object webView, String type) {
        try {
            final Object bridge = new Object() {
                @JavascriptInterface public void captureHtml(String html) {
                    if (html != null && !html.isEmpty()) {
                        String op = OUTPUT_DIR + "captured/js_capture_" + System.currentTimeMillis() + ".html";
                        saveFile(op, html.getBytes()); ok("JS捕捉HTML: " + html.length() + " chars"); addCapturedFileName("captured/js_capture");
                    }
                }
                @JavascriptInterface public void log(String msg) { info("[JS] " + msg); }
            };

            if ("system".equals(type) && webView instanceof WebView) {
                WebView wv = (WebView) webView;
                wv.addJavascriptInterface(bridge, "__wd_android");
                new Handler(Looper.getMainLooper()).postDelayed(() -> { try { wv.evaluateJavascript(CAPTURE_BRIDGE_JS, null); } catch (Exception e) {} }, 500);
            } else {
                try {
                    Method addJI = webView.getClass().getMethod("addJavascriptInterface", Object.class, String.class);
                    addJI.invoke(webView, bridge, "__wd_android");
                    Method evalJs = webView.getClass().getMethod("evaluateJavascript", String.class, android.webkit.ValueCallback.class);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> { try { evalJs.invoke(webView, CAPTURE_BRIDGE_JS, null); } catch (Exception e) {} }, 500);
                } catch (Exception e) { warn("X5 Bridge注入失败: " + e.getMessage()); }
            }
            dbg("Bridge注入完成(" + type + "): " + webView.hashCode());
        } catch (Exception e) { warn("Bridge注入失败: " + e.getMessage()); }
    }

    private static void injectCustomJs(Object webView, String type, Context ctx) {
        try {
            SharedPreferences sp = getModulePrefs(ctx);
            if (sp == null) return;
            if (!sp.getBoolean(KEY_AUTO_INJECT, false)) return;
            String scripts = sp.getString(KEY_JS_SCRIPTS, "");
            if (scripts == null || scripts.trim().isEmpty()) return;
            String[] parts = scripts.split("#---#");
            for (String script : parts) {
                String trimmed = script.trim();
                if (!trimmed.isEmpty()) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            if ("system".equals(type) && webView instanceof WebView) {
                                ((WebView) webView).evaluateJavascript(trimmed, null);
                            } else {
                                Method evalJs = webView.getClass().getMethod("evaluateJavascript", String.class, android.webkit.ValueCallback.class);
                                evalJs.invoke(webView, trimmed, null);
                            }
                            info("自定义JS注入(" + type + "): " + trimmed.substring(0, Math.min(trimmed.length(), 80)));
                        } catch (Exception e) { warn("JS注入失败: " + e.getMessage()); }
                    }, 1500);
                }
            }
        } catch (Exception e) { warn("读取自定义JS失败: " + e.getMessage()); }
    }

    private static void captureCurrentHtml(Object webView, String type) {
        try {
            if ("system".equals(type) && webView instanceof WebView) {
                ((WebView) webView).evaluateJavascript(HTML_CAPTURE_JS, (android.webkit.ValueCallback<String>) html -> {
                    if (html != null && !html.isEmpty()) {
                        String u = html; if (u.startsWith("\"") && u.endsWith("\"")) u = u.substring(1, u.length()-1);
                        u = u.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"");
                        String op = OUTPUT_DIR + "captured/live_" + System.currentTimeMillis() + ".html";
                        saveFile(op, u.getBytes()); ok("实时HTML捕捉: " + u.length() + " chars"); addCapturedFileName("captured/live");
                    }
                });
            } else {
                Method evalJs = webView.getClass().getMethod("evaluateJavascript", String.class, android.webkit.ValueCallback.class);
                evalJs.invoke(webView, HTML_CAPTURE_JS, (android.webkit.ValueCallback<String>) html -> {
                    if (html != null && !html.isEmpty()) {
                        String u = html; if (u.startsWith("\"") && u.endsWith("\"")) u = u.substring(1, u.length()-1);
                        u = u.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"");
                        String op = OUTPUT_DIR + "captured/live_" + System.currentTimeMillis() + ".html";
                        saveFile(op, u.getBytes()); ok("X5 HTML捕捉: " + u.length() + " chars"); addCapturedFileName("captured/live");
                    }
                });
            }
        } catch (Exception e) { warn("HTML捕捉失败: " + e.getMessage()); }
    }

    private static void runOptimizationAnalysis(Object webView, String type) {
        try {
            if ("system".equals(type) && webView instanceof WebView) {
                ((WebView) webView).evaluateJavascript(OPTIMIZATION_JS, (android.webkit.ValueCallback<String>) result -> { if (result != null) updateOptimizationView(result); });
            } else {
                Method evalJs = webView.getClass().getMethod("evaluateJavascript", String.class, android.webkit.ValueCallback.class);
                evalJs.invoke(webView, OPTIMIZATION_JS, (android.webkit.ValueCallback<String>) result -> { if (result != null) updateOptimizationView(result); });
            }
        } catch (Exception e) { warn("优化分析失败: " + e.getMessage()); }
    }

    private static void runBuiltInScript(Object webView, String type, String code) {
        try {
            if ("system".equals(type) && webView instanceof WebView) {
                ((WebView) webView).evaluateJavascript(code, null);
            } else {
                Method evalJs = webView.getClass().getMethod("evaluateJavascript", String.class, android.webkit.ValueCallback.class);
                evalJs.invoke(webView, code, null);
            }
            ok("内置脚本执行(" + type + ")");
        } catch (Exception e) { err("脚本执行失败: " + e.getMessage()); }
    }

    private static void updateOptimizationView(String jsonResult) {
        if (optimizationTextView == null || mainHandler == null) return;
        mainHandler.post(() -> {
            try {
                StringBuilder sb = new StringBuilder("📊 页面分析:\n");
                String uq = jsonResult; if (uq.startsWith("\"") && uq.endsWith("\"")) uq = uq.substring(1, uq.length()-1);
                uq = uq.replace("\\\"", "\"").replace("\\n", "\n");
                String[] pairs = uq.replace("{","").replace("}","").replace("\"","").split(",");
                java.util.Map<String,String> map = new java.util.HashMap<>();
                for (String pair : pairs) { String[] kv = pair.split(":",2); if (kv.length==2) map.put(kv[0].trim(), kv[1].trim()); }
                sb.append("📄 ").append(map.getOrDefault("title","")).append("\n🔗 ").append(map.getOrDefault("url","")).append("\n");
                int tn = Integer.parseInt(map.getOrDefault("totalNodes","0")); int md = Integer.parseInt(map.getOrDefault("maxDomDepth","0"));
                sb.append("📊 节点:").append(tn).append(" 深度:").append(md).append("\n");
                int il = Integer.parseInt(map.getOrDefault("insecureLinks","0")); sb.append(il>0?"🔒 ⚠️ HTTP链接:"+il+"\n":"🔒 ✅ 无HTTP链接\n");
                int li = Integer.parseInt(map.getOrDefault("largeImages","0")); if(li>0) sb.append("🖼 ⚠️ 大图:").append(li).append("\n");
                int sc = Integer.parseInt(map.getOrDefault("scriptCount","0")); int is = Integer.parseInt(map.getOrDefault("inlineScripts","0"));
                sb.append("📜 脚本:").append(sc).append("(内联:").append(is).append(")\n");
                boolean hv = Boolean.parseBoolean(map.getOrDefault("hasViewport","false")); sb.append(hv?"📱 ✅ viewport\n":"📱 ⚠️ 缺viewport\n");
                int ds = Integer.parseInt(map.getOrDefault("docSize","0")); sb.append("📏 ").append(ds/1024).append("KB\n💡 建议:\n");
                if(il>0) sb.append("• HTTP→HTTPS\n"); if(li>0) sb.append("• 压缩图片\n"); if(is>3) sb.append("• 提取内联脚本\n"); if(!hv) sb.append("• 加viewport\n"); if(md>15) sb.append("• 简化DOM\n"); if(ds>500*1024) sb.append("• 懒加载\n");
                optimizationTextView.setText(sb.toString());
            } catch (Exception e) { optimizationTextView.setText("分析失败: " + e.getMessage()); }
        });
    }

    private static void hookActivity(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", cl, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    Activity a = (Activity) p.thisObject; String an = a.getClass().getName(); dbg("Activity.onResume: " + an);
                    if (shouldTargetActivity(an, a)) {
                        currentActivityRef = new WeakReference<>(a);
                        if (!isFloatingShown) { info("🎯 目标Activity: " + an);
                            new Handler(Looper.getMainLooper()).postDelayed(() -> { Activity c = currentActivityRef.get(); if (c != null && !c.isFinishing()) { showFloatingWindow(c); scanAndDecrypt(c); } }, 1500);
                        }
                    }
                }
            });
            XposedHelpers.findAndHookMethod("android.app.Activity", cl, "onDestroy", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Activity a = (Activity) p.thisObject; if (currentActivityRef != null && currentActivityRef.get() == a) removeFloatingWindow();
                }
            });
            info("✅ Activity生命周期 Hook 完成");
        } catch (Exception e) { warn("Activity Hook 失败: " + e.getMessage()); }
    }

    private static void removeFloatingWindow() {
        try { if (windowManager != null) { if (floatingBallView != null && floatingBallView.isShown()) windowManager.removeView(floatingBallView); if (floatingPanelView != null && floatingPanelView.isShown()) windowManager.removeView(floatingPanelView); } } catch (Exception e) {}
        floatingBallView = null; floatingPanelView = null; isPanelExpanded = false; isFloatingShown = false;
    }

    private static void showFloatingWindow(Activity activity) {
        try {
            windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            mainHandler = new Handler(Looper.getMainLooper());
            final View ballView = createFloatingBall(activity);
            floatingPanelView = createFloatingPanel(activity);
            final WindowManager.LayoutParams bp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            bp.gravity = Gravity.TOP | Gravity.END; bp.x = 10; bp.y = 200;
            ballView.setOnTouchListener(new View.OnTouchListener() {
                private int lx,ly,ix,iy; private long tt; private float sx,sy;
                public boolean onTouch(View v, MotionEvent e) {
                    switch(e.getAction()) {
                        case MotionEvent.ACTION_DOWN: lx=(int)e.getRawX(); ly=(int)e.getRawY(); ix=bp.x; iy=bp.y; tt=System.currentTimeMillis(); sx=e.getRawX(); sy=e.getRawY(); return true;
                        case MotionEvent.ACTION_MOVE: bp.x=ix-((int)e.getRawX()-lx); bp.y=iy+((int)e.getRawY()-ly); try{windowManager.updateViewLayout(ballView,bp);}catch(Exception x){} return true;
                        case MotionEvent.ACTION_UP: if(System.currentTimeMillis()-tt<200&&Math.abs(e.getRawX()-sx)+Math.abs(e.getRawY()-sy)<20) togglePanel(activity); return true;
                    } return false;
                }
            });
            windowManager.addView(ballView, bp); floatingBallView = ballView; isFloatingShown = true;
            info("✅ 悬浮窗已注入!");
            mainHandler.postDelayed(new Runnable() { public void run() { updateStats(); if(isFloatingShown) mainHandler.postDelayed(this,2000); } }, 2000);
        } catch (Exception e) { warn("悬浮窗失败: " + e.getMessage()); showFallbackDialog(activity); }
    }

    private static View createFloatingBall(Context ctx) {
        TextView ball = new TextView(ctx); ball.setText("🔓"); ball.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        ball.setBackgroundColor(Color.parseColor("#1A1A2E")); ball.setPadding(10,10,10,10);
        LinearLayout w = new LinearLayout(ctx); w.setBackgroundColor(Color.parseColor("#E94560")); w.setPadding(3,3,3,3); w.addView(ball); return w;
    }

    private static View createFloatingPanel(Context ctx) {
        ScrollView sv = new ScrollView(ctx); sv.setBackgroundColor(Color.parseColor("#E01A1A2E"));
        LinearLayout p = new LinearLayout(ctx); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(16,16,16,16); p.setMinimumWidth(440);

        TextView t = new TextView(ctx); t.setText("🔓 WebDecrypt Pro v9.0"); t.setTextColor(Color.parseColor("#E94560")); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15); t.setTypeface(null, Typeface.BOLD); t.setPadding(0,0,0,8); p.addView(t);
        statsTextView = new TextView(ctx); statsTextView.setText("捕获:0|解密:0|失败:0"); statsTextView.setTextColor(Color.WHITE); statsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11); statsTextView.setPadding(0,0,0,8); p.addView(statsTextView);

        LinearLayout r1 = new LinearLayout(ctx); r1.setOrientation(LinearLayout.HORIZONTAL); r1.setPadding(0,0,0,4);
        r1.addView(mkBtn(ctx,"▶ 监控","#0F3460", v->{autoCapture.set(true);toast(ctx,"监控已开启");}), new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        r1.addView(mkBtn(ctx,"⏸ 暂停","#16213E", v->{autoCapture.set(false);toast(ctx,"监控已暂停");}), new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        r1.addView(mkBtn(ctx,"📁 导出","#E94560", v->{flushLog();toast(ctx,"日志已保存");}), new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        p.addView(r1);

        LinearLayout r2 = new LinearLayout(ctx); r2.setOrientation(LinearLayout.HORIZONTAL); r2.setPadding(0,0,0,4);
        r2.addView(mkBtn(ctx,"🌐 捕捉HTML","#0F3460", v->{forEachWv((wv,t2)->captureCurrentHtml(wv,t2));toast(ctx,"HTML捕捉已触发");}), new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        r2.addView(mkBtn(ctx,"🧠 优化分析","#0F3460", v->{forEachWv((wv,t2)->runOptimizationAnalysis(wv,t2));toast(ctx,"优化分析已触发");}), new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        r2.addView(mkBtn(ctx,"💉 注入JS","#0F3460", v->showJsInjectDialog(ctx)), new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        p.addView(r2);

        LinearLayout r3 = new LinearLayout(ctx); r3.setOrientation(LinearLayout.HORIZONTAL); r3.setPadding(0,0,0,4);
        r3.addView(mkBtn(ctx,"📜 内置脚本","#0F3460", v->showBuiltInScriptsDialog(ctx)), new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        r3.addView(mkBtn(ctx,"🔧 网页调试","#0F3460", v->showWebDebugDialog(ctx)), new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        r3.addView(mkBtn(ctx,"📋 源码","#16213E", v->{forEachWv((wv,t2)->{if("system".equals(t2)&&wv instanceof WebView)((WebView)wv).evaluateJavascript(HTML_CAPTURE_JS,(android.webkit.ValueCallback<String>)h->{if(h!=null){String u=h;if(u.startsWith("\"")&&u.endsWith("\""))u=u.substring(1,u.length()-1);u=u.replace("\\n","\n").replace("\\\"","\"");String op=OUTPUT_DIR+"captured/src_"+System.currentTimeMillis()+".html";saveFile(op,u.getBytes());ok("源码捕捉:"+u.length());addCapturedFileName("captured/src");}});});}), new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        p.addView(r3);

        TextView ot = new TextView(ctx); ot.setText("🧠 优化建议"); ot.setTextColor(Color.parseColor("#E94560")); ot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12); ot.setTypeface(null, Typeface.BOLD); ot.setPadding(0,8,0,4); p.addView(ot);
        optimizationTextView = new TextView(ctx); optimizationTextView.setText("点击「🧠 优化分析」获取建议..."); optimizationTextView.setTextColor(Color.parseColor("#AADDAA")); optimizationTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10); optimizationTextView.setMaxLines(12); optimizationTextView.setPadding(8,8,8,8); optimizationTextView.setBackgroundColor(Color.parseColor("#0A0A1A")); p.addView(optimizationTextView);

        TextView ft = new TextView(ctx); ft.setText("📁 已捕获文件"); ft.setTextColor(Color.parseColor("#E94560")); ft.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12); ft.setTypeface(null, Typeface.BOLD); ft.setPadding(0,8,0,4); p.addView(ft);
        fileListTextView = new TextView(ctx); fileListTextView.setText("等待捕获..."); fileListTextView.setTextColor(Color.parseColor("#A7A7A7")); fileListTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9); fileListTextView.setMaxLines(6); p.addView(fileListTextView);

        Button cb = new Button(ctx); cb.setText("✕ 收起"); cb.setBackgroundColor(Color.TRANSPARENT); cb.setTextColor(Color.parseColor("#E94560")); cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11); cb.setOnClickListener(v2->togglePanel(ctx)); p.addView(cb);

        sv.addView(p); return sv;
    }

    private static Button mkBtn(Context ctx, String text, String bg, View.OnClickListener l) {
        Button b = new Button(ctx); b.setText(text); b.setBackgroundColor(Color.parseColor(bg)); b.setTextColor(Color.WHITE); b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11); b.setPadding(8,4,8,4); b.setMinHeight(0); b.setMinimumHeight(0); b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.setMargins(2,0,2,0); b.setLayoutParams(lp); return b;
    }

    private static void toast(Context ctx, String msg) { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show(); }

    interface WvAction { void run(Object wv, String type); }
    private static void forEachWv(WvAction action) {
        synchronized (trackedWebViews) {
            if (trackedWebViews.isEmpty()) return;
            for (int i = 0; i < trackedWebViews.size(); i++) { action.run(trackedWebViews.get(i), trackedWebViewTypes.get(i)); }
        }
    }

    private static void showJsInjectDialog(Context ctx) {
        try {
            Activity a = currentActivityRef != null ? currentActivityRef.get() : null;
            if (a == null || a.isFinishing()) { toast(ctx, "无法打开"); return; }
            AlertDialog.Builder b = new AlertDialog.Builder(a); b.setTitle("💉 JS注入");
            LinearLayout dl = new LinearLayout(a); dl.setOrientation(LinearLayout.VERTICAL); dl.setPadding(32,16,32,16);
            TextView h = new TextView(a); h.setText("输入JavaScript代码:"); h.setTextColor(Color.parseColor("#8888AA")); h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12); h.setPadding(0,0,0,8); dl.addView(h);
            EditText ji = new EditText(a); ji.setHint("// JS代码..."); ji.setHintTextColor(Color.parseColor("#555577")); ji.setTextColor(Color.parseColor("#AADDAA")); ji.setBackgroundColor(Color.parseColor("#1A1A2E")); ji.setTypeface(Typeface.MONOSPACE); ji.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12); ji.setPadding(16,12,16,12); ji.setMinLines(5); ji.setGravity(Gravity.TOP|Gravity.START);
            SharedPreferences sp = getModulePrefs(ctx); if (sp != null) { String sv = sp.getString(KEY_JS_SCRIPTS,""); if (sv != null && !sv.isEmpty()) { String[] pts = sv.split("#---#"); if (pts.length > 0 && !pts[0].trim().isEmpty()) ji.setText(pts[0].trim()); } }
            dl.addView(ji); b.setView(dl);
            b.setPositiveButton("注入", (d,w) -> { String js = ji.getText().toString().trim(); if(js.isEmpty()) return; forEachWv((wv,t2)->runBuiltInScript(wv,t2,js)); toast(ctx,"JS已注入!"); });
            b.setNegativeButton("取消", null); b.show();
        } catch (Exception e) { warn("JS注入对话框失败: " + e.getMessage()); }
    }

    private static void showBuiltInScriptsDialog(Context ctx) {
        try {
            Activity a = currentActivityRef != null ? currentActivityRef.get() : null;
            if (a == null || a.isFinishing()) return;
            AlertDialog.Builder b = new AlertDialog.Builder(a); b.setTitle("📜 内置脚本");
            LinearLayout dl = new LinearLayout(a); dl.setOrientation(LinearLayout.VERTICAL); dl.setPadding(24,16,24,16);

            java.util.Map<String, List<BuiltInScripts.ScriptItem>> cats = BuiltInScripts.getScriptsByCategory();
            for (java.util.Map.Entry<String, List<BuiltInScripts.ScriptItem>> entry : cats.entrySet()) {
                TextView catTitle = new TextView(a); catTitle.setText(entry.getKey()); catTitle.setTextColor(Color.parseColor("#E94560")); catTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14); catTitle.setTypeface(null, Typeface.BOLD); catTitle.setPadding(0,12,0,4); dl.addView(catTitle);
                for (BuiltInScripts.ScriptItem si : entry.getValue()) {
                    Button btn = new Button(a); btn.setText(si.name); btn.setBackgroundColor(Color.parseColor("#0F3460")); btn.setTextColor(Color.WHITE); btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12); btn.setAllCaps(false); btn.setPadding(12,4,12,4); btn.setMinHeight(0); btn.setMinimumHeight(0);
                    String code = si.code;
                    btn.setOnClickListener(v -> { forEachWv((wv,t2)->runBuiltInScript(wv,t2,code)); toast(ctx, si.name + " 已执行"); });
                    dl.addView(btn);
                }
            }

            ScrollView sc = new ScrollView(a); sc.addView(dl); b.setView(sc);
            b.setNegativeButton("关闭", null); b.show();
        } catch (Exception e) { warn("内置脚本对话框失败: " + e.getMessage()); }
    }

    private static void showWebDebugDialog(Context ctx) {
        try {
            Activity a = currentActivityRef != null ? currentActivityRef.get() : null;
            if (a == null || a.isFinishing()) return;
            String[] items = {"🌐 捕捉当前HTML", "📊 页面性能分析", "🔍 DOM结构查看", "📡 网络请求监控", "📜 Console日志捕获", "🔧 反反调试", "✏️ 开启编辑模式", "🔓 VIP内容解锁", "📖 阅读模式", "🚫 禁用弹窗", "⚡ 时间加速", "🎨 颜色拾取器"};
            boolean[] checked = new boolean[items.length];
            new AlertDialog.Builder(a).setTitle("🔧 网页调试工具箱").setMultiChoiceItems(items, checked, null)
                .setPositiveButton("执行", (d,w) -> {
                    List<BuiltInScripts.ScriptItem> all = BuiltInScripts.getAllScripts();
                    java.util.Map<String, BuiltInScripts.ScriptItem> nameMap = new java.util.HashMap<>();
                    for (BuiltInScripts.ScriptItem si : all) nameMap.put(si.name, si);
                    String[] scriptNames = {"当前HTML", "性能分析", "DOM查看器", "网络请求监控", "Console日志捕获", "反反调试", "编辑模式", "VIP内容解锁", "阅读模式", "禁用弹窗", "时间加速", "颜色拾取器"};
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            BuiltInScripts.ScriptItem si = nameMap.get(scriptNames[i]);
                            if (si != null) { String code = si.code; forEachWv((wv,t2)->runBuiltInScript(wv,t2,code)); }
                        }
                    }
                    toast(a, "调试工具已执行");
                }).setNegativeButton("取消", null).show();
        } catch (Exception e) { warn("调试对话框失败: " + e.getMessage()); }
    }

    private static void togglePanel(Context ctx) {
        try {
            if (isPanelExpanded) { if (floatingPanelView != null && floatingPanelView.isShown()) windowManager.removeView(floatingPanelView); isPanelExpanded = false; }
            else { if (floatingPanelView == null) return; WindowManager.LayoutParams pp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_SYSTEM_ALERT, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
                pp.gravity=Gravity.TOP|Gravity.END; pp.x=10; pp.y=260; windowManager.addView(floatingPanelView,pp); isPanelExpanded=true; }
        } catch (Exception e) { dbg("面板切换失败: " + e.getMessage()); }
    }

    private static void updateStats() {
        if (statsTextView != null && mainHandler != null) mainHandler.post(() -> { try { statsTextView.setText("捕获:"+capturedCount.get()+"|解密:"+decryptedCount.get()+"|失败:"+failedCount.get()); } catch (Exception e) {} });
    }

    private static void addCapturedFileName(String name) {
        synchronized (capturedFileNames) { capturedFileNames.add(name); if (capturedFileNames.size() > 20) capturedFileNames.remove(0); }
        if (fileListTextView != null && mainHandler != null) mainHandler.post(() -> { try { synchronized(capturedFileNames) { if(capturedFileNames.isEmpty()){fileListTextView.setText("等待捕获...");}else{StringBuilder sb=new StringBuilder();int st=Math.max(0,capturedFileNames.size()-8);for(int i=st;i<capturedFileNames.size();i++)sb.append("• ").append(capturedFileNames.get(i)).append("\n");fileListTextView.setText(sb.toString());}} } catch (Exception e) {} });
    }

    private static void showFallbackDialog(Activity a) {
        new Handler(Looper.getMainLooper()).post(() -> { try { new AlertDialog.Builder(a).setTitle("🔓 WebDecrypt Pro v9.0").setMessage("拦截已激活!\n功能:AssetManager|WebView|Cipher|Chromium|X5|JS注入|HTML捕捉|反检测\n输出:"+OUTPUT_DIR+"\n统计:捕获"+capturedCount.get()+" 解密"+decryptedCount.get()).setPositiveButton("导出日志",(d,w)->{flushLog();toast(a,"日志已保存");}).setNegativeButton("关闭",null).show(); } catch (Exception e) {} });
    }

    private static void scanAndDecrypt(Activity a) {
        info("开始全量扫描..."); new Thread(() -> { try {
            String apk = a.getApplicationInfo().sourceDir; ZipFile zip = new ZipFile(apk); java.util.Enumeration<? extends ZipEntry> es = zip.entries(); int c = 0;
            while (es.hasMoreElements()) { ZipEntry e = es.nextElement(); String n = e.getName();
                if (n.contains("assets/") && isTargetFile(n)) { try { InputStream is = zip.getInputStream(e); byte[] d = readInputStream(is);
                    if (d != null && d.length > 0) { String op = OUTPUT_DIR+"scanned/"+n.substring(n.indexOf("assets/")+7); synchronized(CAPTURED_FILES){if(CAPTURED_FILES.add(op)){saveFile(op,d);ok("扫描捕获: "+n+" ("+d.length+"B)");c++;}} } } catch (Exception x){} }
            } zip.close(); info("APK扫描完成: "+c+" 个文件"); scanDataDir(a);
        } catch (Exception e) { err("扫描失败: "+e.getMessage()); } }).start();
    }

    private static void scanDataDir(Activity a) {
        try { String dd = a.getApplicationInfo().dataDir; File[] tds = {new File(dd,"files"),new File(dd,"cache"),new File(dd,"app_webview"),new File(dd,"app_flutter")};
            for (File d : tds) { if (d.exists()) scanDirRecursive(d, d.getAbsolutePath()); }
        } catch (Exception e) { dbg("Data目录扫描失败: "+e.getMessage()); }
    }

    private static void scanDirRecursive(File dir, String bp) {
        File[] fs = dir.listFiles(); if (fs == null) return;
        for (File f : fs) { if (f.isDirectory()) scanDirRecursive(f, bp); else if (isTargetFile(f.getName()) && f.length() < 50*1024*1024) { try { InputStream is = new java.io.FileInputStream(f); byte[] d = readInputStream(is);
            if (d != null && isHtmlContent(d)) { String rp = f.getAbsolutePath().substring(bp.length()); String op = OUTPUT_DIR+"scanned/data"+rp; synchronized(CAPTURED_FILES){if(CAPTURED_FILES.add(op)){saveFile(op,d);ok("Data目录捕获: "+rp+" ("+d.length+"B)");}} } } catch (Exception e){} } }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("android") || lpparam.packageName.equals("com.webdecrypt.xposed") ||
                lpparam.packageName.equals("de.robv.android.xposed.installer") ||
                lpparam.packageName.startsWith("com.android.") || lpparam.packageName.startsWith("com.google.") ||
                lpparam.packageName.startsWith("com.topjohnwu.")) return;

        info("╔══════════════════════════════════════════════════════════╗");
        info("║  WebDecrypt Pro v9.0 — 目标: " + lpparam.packageName);
        info("╚══════════════════════════════════════════════════════════╝");

        ensureDirs();

        try { AntiDetectionHook.install(lpparam); } catch (Exception e) { warn("反检测引擎异常: " + e.getMessage()); }

        ClassLoader cl = lpparam.classLoader;
        hookAssetManager(cl);
        hookSystemWebView(cl);
        hookX5WebView(cl);
        hookWebResourceResponse(cl);
        hookCryptoLibraries(cl);
        hookChromium(cl);
        hookActivity(cl);

        new Timer(true).schedule(new TimerTask() { public void run() { flushLog(); } }, 30000, 30000);

        info("══════════════════════════════════════════════════════════");
        info("  所有Hook已安装! 目标: " + lpparam.packageName);
        info("  功能: 拦截|X5|JS注入|HTML捕捉|反检测|内置脚本|网页调试");
        info("══════════════════════════════════════════════════════════");
    }
}
