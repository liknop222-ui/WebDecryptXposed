package com.webdecrypt.xposed;

import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class AntiDetectionHook {

    private static final String TAG = "WebDecrypt-Anti";

    private static final Set<String> XPOSED_CLASSES = new HashSet<>(Arrays.asList(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XposedHelpers",
            "de.robv.android.xposed.IXposedHookLoadPackage",
            "de.robv.android.xposed.IXposedHookInitZygote",
            "de.robv.android.xposed.IXposedHookCmdInit",
            "de.robv.android.xposed.XC_MethodHook",
            "de.robv.android.xposed.XC_MethodReplacement",
            "de.robv.android.xposed.callbacks.XC_LoadPackage",
            "de.robv.android.xposed.callbacks.XC_InitPackageResources",
            "de.robv.android.xposed.services.BaseService",
            "de.robv.android.xposed.IXposedService",
            "de.robv.android.xposed.XSharedPreferences"
    ));

    private static final Set<String> FRIDA_KEYWORDS = new HashSet<>(Arrays.asList(
            "frida", "gadget", "linjector", "frida-agent",
            "re.frida.server", "frida-server"
    ));

    private static final Set<String> MAGISK_KEYWORDS = new HashSet<>(Arrays.asList(
            "com.topjohnwu.magisk", "magisk", "supersu",
            "com.koushikdutta.superuser", "eu.chainfire.supersu",
            "com.noshufou.android.su", "com.thirdparty.superuser"
    ));

    private static final Set<String> ROOT_INDICATORS = new HashSet<>(Arrays.asList(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/magisk/.core",
            "/sbin/.magisk"
    ));

    private static final Set<String> DANGEROUS_PACKAGES = new HashSet<>(Arrays.asList(
            "de.robv.android.xposed.installer",
            "org.lsposed.manager",
            "com.topjohnwu.magisk",
            "com.tsng.hidemyapplist",
            "io.github.vvb2060.magisk",
            "org.meowcat.edxposed.manager",
            "org.exposed.flutter",
            "com.android.vending.billing.InAppBillingService.COIN"
    ));

    private static final int[] FRIDA_PORTS = {
            27042, 27043, 27044, 27045, 27046, 27047, 27048, 27049, 27050
    };

    private static volatile boolean antiDetectionEnabled = true;

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!antiDetectionEnabled) {
            XposedBridge.log("[AntiDetect] 反检测已禁用");
            return;
        }

        XposedBridge.log("[AntiDetect] ═══════════════════════════════════════");
        XposedBridge.log("[AntiDetect] 反检测引擎启动 — 目标: " + lpparam.packageName);
        XposedBridge.log("[AntiDetect] ═══════════════════════════════════════");

        hookProcMaps(lpparam.classLoader);
        hookProcStatus(lpparam.classLoader);
        hookFileAccess(lpparam.classLoader);
        hookClassLoader(lpparam.classLoader);
        hookPackageManager(lpparam.classLoader);
        hookSystemProperties(lpparam.classLoader);
        hookRuntimeExec(lpparam.classLoader);
        hookNetPortCheck(lpparam.classLoader);
        hookDebugCheck(lpparam.classLoader);
        hookBuildFingerprint(lpparam.classLoader);
        hookDlIteratePhdr(lpparam.classLoader);
        hookThreadStackWalk(lpparam.classLoader);

        XposedBridge.log("[AntiDetect] ✅ 所有反检测Hook已安装");
    }

    private static void hookProcMaps(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.io.FileInputStream",
                    cl,
                    "<init>",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String path = (String) param.args[0];
                            if (path != null && path.contains("/proc/") && path.contains("/maps")) {
                                Object fis = param.getResult();
                                if (fis != null) {
                                    param.setResult(new FilteredFileInputStream(path, fis));
                                }
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "java.io.FileReader",
                    cl,
                    "<init>",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String path = (String) param.args[0];
                            if (path != null && path.contains("/proc/") && path.contains("/maps")) {
                                Object fr = param.getResult();
                                if (fr != null) {
                                    param.setResult(new FilteredFileReader(path, fr));
                                }
                            }
                        }
                    }
            );

            XposedBridge.log("[AntiDetect] ✅ /proc/maps 过滤Hook完成");
        } catch (Exception e) {
            XposedBridge.log("[AntiDetect] ⚠️ /proc/maps Hook失败: " + e.getMessage());
        }
    }

    private static void hookProcStatus(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.io.FileInputStream",
                    cl,
                    "<init>",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String path = (String) param.args[0];
                            if (path != null && path.contains("/proc/") && path.endsWith("/status")) {
                                Object fis = param.getResult();
                                if (fis != null) {
                                    param.setResult(new FilteredStatusStream(path, fis));
                                }
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.os.Process",
                    cl,
                    "myPid",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        }
                    }
            );

            XposedBridge.log("[AntiDetect] ✅ /proc/status TracerPid过滤Hook完成");
        } catch (Exception e) {
            XposedBridge.log("[AntiDetect] ⚠️ /proc/status Hook失败: " + e.getMessage());
        }
    }

    private static void hookFileAccess(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.io.File",
                    cl,
                    "exists",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String path = ((java.io.File) param.thisObject).getAbsolutePath();

                            for (String indicator : ROOT_INDICATORS) {
                                if (path.equals(indicator) || path.startsWith(indicator)) {
                                    param.setResult(false);
                                    return;
                                }
                            }

                            if (path.contains("frida") || path.contains("xposed") ||
                                    path.contains("magisk") || path.contains("supersu") ||
                                    path.contains("lsposed") || path.contains("edxposed") ||
                                    path.contains("/data/local/tmp/frida") ||
                                    path.contains("re.frida.server")) {
                                param.setResult(false);
                                return;
                            }

                            if (path.equals("/system/app/Superuser.apk") ||
                                    path.equals("/system/xbin/daemonsu") ||
                                    path.equals("/system/bin/.ext/.su") ||
                                    path.equals("/system/usr/we-need-root")) {
                                param.setResult(false);
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "java.io.File",
                    cl,
                    "canRead",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String path = ((java.io.File) param.thisObject).getAbsolutePath();
                            if (isSensitivePath(path)) {
                                param.setResult(false);
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "java.io.File",
                    cl,
                    "listFiles",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String path = ((java.io.File) param.thisObject).getAbsolutePath();
                            if (path.equals("/data/local/tmp") || path.equals("/sbin") ||
                                    path.equals("/system/xbin") || path.equals("/system/bin")) {
                                java.io.File[] original = (java.io.File[]) param.getResult();
                                if (original != null) {
                                    List<java.io.File> filtered = new ArrayList<>();
                                    for (java.io.File f : original) {
                                        String name = f.getName().toLowerCase();
                                        if (!name.contains("frida") && !name.contains("xposed") &&
                                                !name.contains("magisk") && !name.contains("su") &&
                                                !name.contains("supersu") && !name.contains("busybox")) {
                                            filtered.add(f);
                                        }
                                    }
                                    param.setResult(filtered.toArray(new java.io.File[0]));
                                }
                            }
                        }
                    }
            );

            XposedBridge.log("[AntiDetect] ✅ 文件访问过滤Hook完成");
        } catch (Exception e) {
            XposedBridge.log("[AntiDetect] ⚠️ 文件访问Hook失败: " + e.getMessage());
        }
    }

    private static boolean isSensitivePath(String path) {
        if (path == null) return false;
        String lp = path.toLowerCase();
        return lp.contains("frida") || lp.contains("xposed") || lp.contains("magisk") ||
                lp.contains("supersu") || lp.contains("/su") || lp.contains("lsposed") ||
                lp.contains("edxposed") || lp.contains("busybox");
    }

    private static void hookClassLoader(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.lang.ClassLoader",
                    cl,
                    "loadClass",
                    String.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String className = (String) param.args[0];
                            if (className != null && isBlockedClass(className)) {
                                param.setThrowable(new ClassNotFoundException(className));
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    Class.class,
                    "forName",
                    String.class,
                    boolean.class,
                    ClassLoader.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String className = (String) param.args[0];
                            if (className != null && isBlockedClass(className)) {
                                param.setThrowable(new ClassNotFoundException(className));
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    Class.class,
                    "forName",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String className = (String) param.args[0];
                            if (className != null && isBlockedClass(className)) {
                                param.setThrowable(new ClassNotFoundException(className));
                            }
                        }
                    }
            );

            XposedBridge.log("[AntiDetect] ✅ ClassLoader过滤Hook完成");
        } catch (Exception e) {
            XposedBridge.log("[AntiDetect] ⚠️ ClassLoader Hook失败: " + e.getMessage());
        }
    }

    private static boolean isBlockedClass(String className) {
        if (className.startsWith("de.robv.android.xposed.")) return true;
        if (className.startsWith("com.topjohnwu.magisk.")) return true;
        if (className.contains("XposedBridge")) return true;
        if (className.contains("XposedHelpers")) return true;
        if (className.contains("EdXposed")) return true;
        if (className.startsWith("com.swift.sandhook.")) return true;
        if (className.startsWith("com.elderdrivers.riru.")) return true;
        if (className.equals("com.android.tools.r8.GeneratedOutlineSupport")) return false;
        return false;
    }

    private static void hookPackageManager(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    cl,
                    "getPackageInfo",
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String packageName = (String) param.args[0];
                            if (DANGEROUS_PACKAGES.contains(packageName)) {
                                param.setThrowable(new android.content.pm.PackageManager.NameNotFoundException(packageName));
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    cl,
                    "getApplicationInfo",
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String packageName = (String) param.args[0];
                            if (DANGEROUS_PACKAGES.contains(packageName)) {
                                param.setThrowable(new android.content.pm.PackageManager.NameNotFoundException(packageName));
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    cl,
                    "getInstalledPackages",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            @SuppressWarnings("unchecked")
                            List<Object> packages = (List<Object>) param.getResult();
                            if (packages != null && !packages.isEmpty()) {
                                List<Object> filtered = new ArrayList<>();
                                for (Object pkg : packages) {
                                    try {
                                        String pkgName = (String) XposedHelpers.getObjectField(pkg, "packageName");
                                        if (!DANGEROUS_PACKAGES.contains(pkgName)) {
                                            filtered.add(pkg);
                                        }
                                    } catch (Exception e) {
                                        filtered.add(pkg);
                                    }
                                }
                                param.setResult(filtered);
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    cl,
                    "getInstalledApplications",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            @SuppressWarnings("unchecked")
                            List<Object> apps = (List<Object>) param.getResult();
                            if (apps != null && !apps.isEmpty()) {
                                List<Object> filtered = new ArrayList<>();
                                for (Object app : apps) {
                                    try {
                                        String pkgName = (String) XposedHelpers.getObjectField(app, "packageName");
                                        if (!DANGEROUS_PACKAGES.contains(pkgName)) {
                                            filtered.add(app);
                                        }
                                    } catch (Exception e) {
                                        filtered.add(app);
                                    }
                                }
                                param.setResult(filtered);
                            }
                        }
                    }
            );

            XposedBridge.log("[AntiDetect] ✅ PackageManager过滤Hook完成");
        } catch (Exception e) {
            XposedBridge.log("[AntiDetect] ⚠️ PackageManager Hook失败: " + e.getMessage());
        }
    }

    private static void hookSystemProperties(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.os.SystemProperties",
                    cl,
                    "get",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            String result = (String) param.getResult();

                            if ("ro.build.tags".equals(key)) {
                                param.setResult("release-keys");
                            } else if ("ro.build.type".equals(key)) {
                                String current = (String) param.getResult();
                                if ("userdebug".equals(current) || "eng".equals(current)) {
                                    param.setResult("user");
                                }
                            } else if ("ro.debuggable".equals(key)) {
                                param.setResult("0");
                            } else if ("ro.secure".equals(key)) {
                                param.setResult("1");
                            } else if ("ro.build.selinux".equals(key)) {
                                param.setResult("1");
                            } else if ("init.svc.adbd".equals(key)) {
                            } else if (key != null && key.contains("magisk")) {
                                param.setResult("");
                            } else if (key != null && key.contains("xposed")) {
                                param.setResult("");
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.os.SystemProperties",
                    cl,
                    "get",
                    String.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            if ("ro.build.tags".equals(key)) {
                                param.setResult("release-keys");
                            } else if ("ro.debuggable".equals(key)) {
                                param.setResult("0");
                            } else if ("ro.secure".equals(key)) {
                                param.setResult("1");
                            }
                        }
                    }
            );

            XposedBridge.log("[AntiDetect] ✅ SystemProperties伪装Hook完成");
        } catch (Exception e) {
            XposedBridge.log("[AntiDetect] ⚠️ SystemProperties Hook失败: " + e.getMessage());
        }
    }

    private static void hookRuntimeExec(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.lang.Runtime",
                    cl,
                    "exec",
                    String[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String[] cmds = (String[]) param.args[0];
                            if (cmds != null && cmds.length > 0) {
                                String cmd = cmds[0].toLowerCase();
                                if (cmd.contains("su") || cmd.contains("which su") ||
                                        cmd.contains("busybox") || cmd.contains("frida") ||
                                        cmd.contains("magisk") || cmd.contains("supersu") ||
                                        cmd.contains("/system/xbin/su") ||
                                        cmd.contains("pm list packages") ||
                                        cmd.contains("getprop ro.build.tags")) {
                                    param.setResult(new ProcessBuilder().start());
                                }
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "java.lang.Runtime",
                    cl,
                    "exec",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String cmd = ((String) param.args[0]).toLowerCase();
                            if (cmd.contains("su") || cmd.contains("which su") ||
                                    cmd.contains("busybox") || cmd.contains("frida") ||
                                    cmd.contains("magisk") || cmd.contains("supersu") ||
                                    cmd.contains("pm list packages") ||
                                    cmd.contains("getprop")) {
                                param.setResult(new ProcessBuilder().start());
                            }
                        }
                    }
            );

            XposedBridge.log("[AntiDetect] ✅ Runtime.exec过滤Hook完成");
        } catch (Exception e) {
            XposedBridge.log("[AntiDetect] ⚠️ Runtime.exec Hook失败: " + e.getMessage());
        }
    }

    private static void hookNetPortCheck(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookConstructor(
                    "java.net.Socket",
                    cl,
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            int port = (int) param.args[1];
                            for (int fp : FRIDA_PORTS) {
                                if (port == fp) {
                                    param.setThrowable(new java.net.ConnectException("Connection refused"));
                                    return;
                                }
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "java.net.InetSocketAddress",
                    cl,
                    "createUnresolved",
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String host = (String) param.args[0];
                            int port = (int) param.args[1];
                            if (host != null && (host.equals("127.0.0.1") || host.equals("localhost"))) {
                                for (int fp : FRIDA_PORTS) {
                                    if (port == fp) {
                                        param.setThrowable(new java.net.ConnectException("Connection refused"));
                                        return;
                                    }
                                }
                            }
                        }
                    }
            );

            XposedBridge.log("[AntiDetect] ✅ 网络端口检测过滤Hook完成");
        } catch (Exception e) {
            XposedBridge.log("[AntiDetect] ⚠️ 网络端口Hook失败: " + e.getMessage());
        }
    }

    private static void hookDebugCheck(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.os.Debug",
                    cl,
                    "isDebuggerConnected",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(false);
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.os.Debug",
                    cl,
                    "waitingForDebugger",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(false);
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.app.ActivityManager",
                    cl,
                    "isUserAMonkey",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(false);
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.os.Process",
                    cl,
                    "myUid",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        }
                    }
            );

            XposedBridge.log("[AntiDetect] ✅ 调试检测过滤Hook完成");
        } catch (Exception e) {
            XposedBridge.log("[AntiDetect] ⚠️ 调试检测Hook失败: " + e.getMessage());
        }
    }

    private static void hookBuildFingerprint(ClassLoader cl) {
        try {
            XposedHelpers.setStaticObjectField(Build.class, "TAGS", "release-keys");

            String fingerprint = Build.FINGERPRINT;
            if (fingerprint != null && (fingerprint.contains("test-keys") ||
                    fingerprint.contains("dev-keys") || fingerprint.contains("userdebug"))) {
                String patched = fingerprint.replace("test-keys", "release-keys")
                        .replace("dev-keys", "release-keys")
                        .replace("userdebug", "user");
                XposedHelpers.setStaticObjectField(Build.class, "FINGERPRINT", patched);
            }

            if ("userdebug".equals(Build.TYPE)) {
                XposedHelpers.setStaticObjectField(Build.class, "TYPE", "user");
            }

            XposedBridge.log("[AntiDetect] ✅ Build指纹伪装完成");
        } catch (Exception e) {
            XposedBridge.log("[AntiDetect] ⚠️ Build指纹伪装失败: " + e.getMessage());
        }
    }

    private static void hookDlIteratePhdr(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.io.BufferedReader",
                    cl,
                    "readLine",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String line = (String) param.getResult();
                            if (line != null) {
                                String ll = line.toLowerCase();
                                if (ll.contains("frida") || ll.contains("xposed") ||
                                        ll.contains("magisk") || ll.contains("lsposed") ||
                                        ll.contains("edxposed") || ll.contains("riru") ||
                                        ll.contains("sandhook")) {
                                    param.setResult(skipToNextNonSensitiveLine(param));
                                }
                            }
                        }
                    }
            );

            XposedBridge.log("[AntiDetect] ✅ dl_iterate_phdr过滤Hook完成");
        } catch (Exception e) {
            XposedBridge.log("[AntiDetect] ⚠️ dl_iterate_phdr Hook失败: " + e.getMessage());
        }
    }

    private static void hookThreadStackWalk(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.lang.Thread",
                    cl,
                    "getStackTrace",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            StackTraceElement[] original = (StackTraceElement[]) param.getResult();
                            if (original == null) return;

                            List<StackTraceElement> filtered = new ArrayList<>();
                            for (StackTraceElement element : original) {
                                String cn = element.getClassName();
                                if (!cn.startsWith("de.robv.android.xposed.") &&
                                        !cn.startsWith("com.webdecrypt.xposed.") &&
                                        !cn.contains("XposedBridge") &&
                                        !cn.contains("EdXposed") &&
                                        !cn.contains("YAHFA") &&
                                        !cn.contains("SandHook")) {
                                    filtered.add(element);
                                }
                            }
                            param.setResult(filtered.toArray(new StackTraceElement[0]));
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "java.lang.Throwable",
                    cl,
                    "getStackTrace",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            StackTraceElement[] original = (StackTraceElement[]) param.getResult();
                            if (original == null) return;

                            List<StackTraceElement> filtered = new ArrayList<>();
                            for (StackTraceElement element : original) {
                                String cn = element.getClassName();
                                if (!cn.startsWith("de.robv.android.xposed.") &&
                                        !cn.startsWith("com.webdecrypt.xposed.") &&
                                        !cn.contains("XposedBridge") &&
                                        !cn.contains("EdXposed")) {
                                    filtered.add(element);
                                }
                            }
                            param.setResult(filtered.toArray(new StackTraceElement[0]));
                        }
                    }
            );

            XposedBridge.log("[AntiDetect] ✅ 线程栈过滤Hook完成");
        } catch (Exception e) {
            XposedBridge.log("[AntiDetect] ⚠️ 线程栈Hook失败: " + e.getMessage());
        }
    }

    private static String skipToNextNonSensitiveLine(MethodHookParam param) {
        return "";
    }

    public static void setEnabled(boolean enabled) {
        antiDetectionEnabled = enabled;
    }

    private static class FilteredFileInputStream extends java.io.FileInputStream {
        private final String path;

        public FilteredFileInputStream(String path, Object original) throws Exception {
            super(path);
            this.path = path;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int result = super.read(b);
            if (result > 0 && path.contains("/maps")) {
                filterBuffer(b, result);
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int result = super.read(b, off, len);
            if (result > 0 && path.contains("/maps")) {
                filterBuffer(b, off + result);
            }
            return result;
        }

        private void filterBuffer(byte[] b, int len) {
            String content = new String(b, 0, len);
            String[] lines = content.split("\n");
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                String ll = line.toLowerCase();
                if (!ll.contains("frida") && !ll.contains("xposed") &&
                        !ll.contains("magisk") && !ll.contains("lsposed") &&
                        !ll.contains("edxposed") && !ll.contains("riru") &&
                        !ll.contains("sandhook") && !ll.contains("gadget")) {
                    sb.append(line).append("\n");
                }
            }
            byte[] filtered = sb.toString().getBytes();
            System.arraycopy(filtered, 0, b, 0, Math.min(filtered.length, b.length));
        }
    }

    private static class FilteredFileReader extends java.io.FileReader {
        private final String path;

        public FilteredFileReader(String path, Object original) throws Exception {
            super(path);
            this.path = path;
        }
    }

    private static class FilteredStatusStream extends java.io.FileInputStream {
        private final String path;

        public FilteredStatusStream(String path, Object original) throws Exception {
            super(path);
            this.path = path;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int result = super.read(b);
            if (result > 0 && path.endsWith("/status")) {
                String content = new String(b, 0, result);
                if (content.contains("TracerPid:")) {
                    content = content.replaceAll("TracerPid:\\s*\\d+", "TracerPid:\t0");
                    byte[] patched = content.getBytes();
                    System.arraycopy(patched, 0, b, 0, Math.min(patched.length, b.length));
                }
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int result = super.read(b, off, len);
            if (result > 0 && path.endsWith("/status")) {
                String content = new String(b, off, result);
                if (content.contains("TracerPid:")) {
                    content = content.replaceAll("TracerPid:\\s*\\d+", "TracerPid:\t0");
                    byte[] patched = content.getBytes();
                    System.arraycopy(patched, 0, b, off, Math.min(patched.length, b.length - off));
                }
            }
            return result;
        }
    }
}
