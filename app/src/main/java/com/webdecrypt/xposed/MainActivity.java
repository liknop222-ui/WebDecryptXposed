package com.webdecrypt.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "webdecrypt_prefs";
    private static final String KEY_JS_SCRIPTS = "js_scripts";
    private static final String KEY_AUTO_CAPTURE = "auto_capture";
    private static final String KEY_AUTO_INJECT = "auto_inject";
    private static final String KEY_CAPTURE_HTML = "capture_html";
    private static final String KEY_TARGET_KEYWORDS = "target_keywords";
    private static final String KEY_ANTI_DETECTION = "anti_detection";
    private static final String OUTPUT_DIR = "/sdcard/WebDecrypt/";

    private SharedPreferences prefs;
    private LinearLayout rootLayout;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);

        ScrollView scrollView = new ScrollView(this);
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(40, 40, 40, 40);
        rootLayout.setBackgroundColor(Color.parseColor("#0F0F1A"));

        buildHeader();
        buildLspStatusSection();
        buildAntiDetectionSection();
        buildQuickActions();
        buildCaptureSettings();
        buildJsInjectionSection();
        buildBuiltInScriptsSection();
        buildHtmlCaptureSection();
        buildWebDebugSection();
        buildOptimizationSection();
        buildCapturedFilesSection();
        buildAboutSection();

        scrollView.addView(rootLayout);
        setContentView(scrollView);
    }

    private void buildHeader() {
        TextView title = new TextView(this);
        title.setText("🔓 WebDecrypt Pro v9.0");
        title.setTextColor(Color.parseColor("#E94560"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 8);
        rootLayout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("LSP增强版 — 反检测 + X5兼容 + JS注入 + HTML捕捉 + 内置脚本 + 网页调试");
        subtitle.setTextColor(Color.parseColor("#8888AA"));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setPadding(0, 0, 0, 24);
        rootLayout.addView(subtitle);
    }

    private void buildLspStatusSection() {
        LinearLayout section = createSection("⚡ LSP激活状态");
        boolean isModuleActive = false;
        try { isModuleActive = isModuleActive(); } catch (Exception e) {}

        statusText = new TextView(this);
        if (isModuleActive) {
            statusText.setText("✅ 模块已激活 — LSPosed/Xposed框架已成功加载本模块");
            statusText.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            statusText.setText("❌ 模块未激活 — 需要在LSPosed中启用本模块");
            statusText.setTextColor(Color.parseColor("#F44336"));
        }
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusText.setPadding(0, 8, 0, 8);
        section.addView(statusText);

        if (!isModuleActive) {
            LinearLayout guideLayout = new LinearLayout(this);
            guideLayout.setOrientation(LinearLayout.VERTICAL);
            guideLayout.setBackgroundColor(Color.parseColor("#1A1A2E"));
            guideLayout.setPadding(20, 20, 20, 20);

            String[] steps = {
                "激活步骤:",
                "1. 确保已安装 LSPosed (需要Magisk + Zygisk)",
                "2. 安装本模块APK",
                "3. 打开 LSPosed Manager",
                "4. 找到 \"WebDecrypt Pro\" 并启用模块",
                "5. 设置作用域 → 勾选目标App",
                "6. 强制停止目标App后重新打开",
                "7. 悬浮窗自动出现 → 展开即可交互"
            };
            for (String step : steps) {
                TextView stepText = new TextView(this);
                stepText.setText(step);
                stepText.setTextColor(step.startsWith("激活") ? Color.parseColor("#E94560") : Color.parseColor("#CCCCEE"));
                stepText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                stepText.setPadding(0, 4, 0, 4);
                guideLayout.addView(stepText);
            }
            section.addView(guideLayout);

            Button btnOpenLsposed = createButton("打开 LSPosed Manager", "#0F3460");
            btnOpenLsposed.setOnClickListener(v -> {
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");
                    if (intent != null) startActivity(intent);
                    else showAlertDialog("未找到LSPosed Manager", "请确保已安装LSPosed Manager");
                } catch (Exception e) { showAlertDialog("无法打开", "请手动打开LSPosed Manager"); }
            });
            section.addView(btnOpenLsposed);
        } else {
            TextView activeInfo = new TextView(this);
            activeInfo.setText("模块已就绪。打开目标App后，悬浮窗将自动注入。\n展开悬浮窗可使用: 监控控制、JS注入、HTML捕捉、内置脚本、网页调试、优化建议");
            activeInfo.setTextColor(Color.parseColor("#AADDAA"));
            activeInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            activeInfo.setPadding(0, 8, 0, 0);
            section.addView(activeInfo);
        }
        rootLayout.addView(section);
    }

    private boolean isModuleActive() { return false; }

    private void buildAntiDetectionSection() {
        LinearLayout section = createSection("🛡️ 反检测引擎");

        TextView desc = new TextView(this);
        desc.setText("在目标App启动前注入反检测逻辑，伪装正常环境，隐藏框架特征。\n\n" +
            "覆盖检测层:\n" +
            "• 进程/端口 — 过滤/proc/maps、TracerPid、Frida端口\n" +
            "• 内存/符号 — 过滤dl_iterate_phdr、BufferedReader输出\n" +
            "• 文件/描述符 — 隐藏su/frida/magisk等敏感文件\n" +
            "• Java层扫描 — 过滤ClassLoader.loadClass异常类\n" +
            "• 行为/时间 — 绕过反调试、debugger语句\n" +
            "• 设备指纹 — 伪装Build属性、ro.build.tags\n" +
            "• 线程栈 — 过滤StackTrace中的Xposed特征\n" +
            "• PackageManager — 隐藏Magisk/Xposed等应用");
        desc.setTextColor(Color.parseColor("#CCCCEE"));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        desc.setPadding(0, 0, 0, 12);
        section.addView(desc);

        Switch switchAnti = new Switch(this);
        switchAnti.setText("  启用反检测引擎");
        switchAnti.setTextColor(Color.WHITE);
        switchAnti.setChecked(prefs.getBoolean(KEY_ANTI_DETECTION, true));
        switchAnti.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean(KEY_ANTI_DETECTION, isChecked).apply());
        section.addView(switchAnti);

        rootLayout.addView(section);
    }

    private void buildQuickActions() {
        LinearLayout section = createSection("🎯 快捷操作");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);

        Button btnOpenDir = createButton("📁 输出目录", "#0F3460");
        btnOpenDir.setOnClickListener(v -> openOutputDirectory());
        row.addView(btnOpenDir, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnClearData = createButton("🗑 清空数据", "#5C1A1A");
        btnClearData.setOnClickListener(v -> showClearDataDialog());
        row.addView(btnClearData, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        section.addView(row);

        Button btnViewLog = createButton("📋 查看运行日志", "#16213E");
        btnViewLog.setOnClickListener(v -> showLogFile());
        section.addView(btnViewLog);
        rootLayout.addView(section);
    }

    private void buildCaptureSettings() {
        LinearLayout section = createSection("⚙️ 拦截设置");

        Switch switchAutoCapture = new Switch(this);
        switchAutoCapture.setText("  自动捕获");
        switchAutoCapture.setTextColor(Color.WHITE);
        switchAutoCapture.setChecked(prefs.getBoolean(KEY_AUTO_CAPTURE, true));
        switchAutoCapture.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(KEY_AUTO_CAPTURE, c).apply());
        section.addView(switchAutoCapture);

        Switch switchAutoInject = new Switch(this);
        switchAutoInject.setText("  自动注入JS");
        switchAutoInject.setTextColor(Color.WHITE);
        switchAutoInject.setChecked(prefs.getBoolean(KEY_AUTO_INJECT, false));
        switchAutoInject.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(KEY_AUTO_INJECT, c).apply());
        section.addView(switchAutoInject);

        Switch switchCaptureHtml = new Switch(this);
        switchCaptureHtml.setText("  实时HTML捕捉");
        switchCaptureHtml.setTextColor(Color.WHITE);
        switchCaptureHtml.setChecked(prefs.getBoolean(KEY_CAPTURE_HTML, true));
        switchCaptureHtml.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(KEY_CAPTURE_HTML, c).apply());
        section.addView(switchCaptureHtml);

        TextView kwLabel = new TextView(this);
        kwLabel.setText("目标Activity关键词 (逗号分隔):");
        kwLabel.setTextColor(Color.parseColor("#8888AA"));
        kwLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        kwLabel.setPadding(0, 12, 0, 4);
        section.addView(kwLabel);

        EditText kwInput = new EditText(this);
        kwInput.setText(prefs.getString(KEY_TARGET_KEYWORDS, "Web,Main,Browser,Home,Content"));
        kwInput.setTextColor(Color.WHITE);
        kwInput.setHint("Web,Main,Browser...");
        kwInput.setHintTextColor(Color.parseColor("#555577"));
        kwInput.setBackgroundColor(Color.parseColor("#1A1A2E"));
        kwInput.setPadding(16, 12, 16, 12);
        kwInput.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) prefs.edit().putString(KEY_TARGET_KEYWORDS, kwInput.getText().toString()).apply(); });
        section.addView(kwInput);

        rootLayout.addView(section);
    }

    private void buildJsInjectionSection() {
        LinearLayout section = createSection("💉 JS注入引擎");

        TextView desc = new TextView(this);
        desc.setText("自定义JavaScript代码，在目标App的WebView中自动注入执行。\n支持多段脚本，每段用 #---# 分隔。\n兼容系统WebView和腾讯X5内核。");
        desc.setTextColor(Color.parseColor("#8888AA"));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        desc.setPadding(0, 0, 0, 12);
        section.addView(desc);

        String savedScripts = prefs.getString(KEY_JS_SCRIPTS,
            "// 示例: 捕获所有按钮点击\n" +
            "document.addEventListener('click', function(e) {\n" +
            "  console.log('[WD] 点击:', e.target.tagName);\n" +
            "});\n" +
            "\n#---#\n\n" +
            "// 示例: Hook XMLHttpRequest\n" +
            "var _origOpen = XMLHttpRequest.prototype.open;\n" +
            "XMLHttpRequest.prototype.open = function(method, url) {\n" +
            "  console.log('[WD] XHR:', method, url);\n" +
            "  return _origOpen.apply(this, arguments);\n" +
            "};");

        EditText scriptInput = new EditText(this);
        scriptInput.setText(savedScripts);
        scriptInput.setTextColor(Color.parseColor("#AADDAA"));
        scriptInput.setBackgroundColor(Color.parseColor("#1A1A2E"));
        scriptInput.setTypeface(Typeface.MONOSPACE);
        scriptInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        scriptInput.setPadding(16, 12, 16, 12);
        scriptInput.setMinLines(8);
        scriptInput.setGravity(Gravity.TOP | Gravity.START);
        section.addView(scriptInput);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 8, 0, 0);

        Button btnSave = createButton("💾 保存脚本", "#0F3460");
        btnSave.setOnClickListener(v -> { prefs.edit().putString(KEY_JS_SCRIPTS, scriptInput.getText().toString()).apply(); showAlertDialog("已保存", "JS脚本已保存，下次目标App启动时生效"); });
        btnRow.addView(btnSave, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnClear = createButton("🗑 清空", "#5C1A1A");
        btnClear.setOnClickListener(v -> { scriptInput.setText(""); prefs.edit().remove(KEY_JS_SCRIPTS).apply(); });
        btnRow.addView(btnClear, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        section.addView(btnRow);

        TextView tip = new TextView(this);
        tip.setText("💡 提示: 注入的JS可通过 console.log('[WD]...') 输出到日志\n使用 window.__wd_capture(html) 可手动捕捉HTML\n使用 window.__wd_log(msg) 可输出到模块日志");
        tip.setTextColor(Color.parseColor("#666688"));
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tip.setPadding(0, 8, 0, 0);
        section.addView(tip);

        rootLayout.addView(section);
    }

    private void buildBuiltInScriptsSection() {
        LinearLayout section = createSection("📜 内置脚本库 (30+)");

        TextView desc = new TextView(this);
        desc.setText("30+内置脚本，按分类组织，在悬浮窗中一键执行:\n\n" +
            "🔍 调试类 (8个):\n" +
            "  DOM查看器 | 事件监听器 | Cookie查看器\n" +
            "  LocalStorage查看 | SessionStorage查看\n" +
            "  网络请求监控 | Console日志捕获 | 性能分析\n\n" +
            "✏️ 修改类 (8个):\n" +
            "  编辑模式 | 显示隐藏元素 | 移除遮罩层\n" +
            "  禁用右键限制 | 修改User-Agent | 注入jQuery\n" +
            "  修改Viewport | 强制暗黑模式\n\n" +
            "📥 下载类 (6个):\n" +
            "  提取所有图片 | 提取所有链接 | 提取视频源\n" +
            "  提取音频源 | 提取CSS样式 | 提取JS代码\n\n" +
            "🧭 导航类 (5个):\n" +
            "  页面元素高亮 | 表单自动填充 | 滚动到底部\n" +
            "  查找文本 | 页面截图标记\n\n" +
            "🔓 解锁类 (6个):\n" +
            "  VIP内容解锁 | 阅读模式 | 禁用弹窗\n" +
            "  恢复弹窗 | 禁用跳转 | 反反调试\n\n" +
            "🔧 工具类 (10个):\n" +
            "  页面信息 | 颜色拾取器 | 清除所有Cookie\n" +
            "  清除存储 | 强制重载 | 查看源码\n" +
            "  API接口探测 | WebSocket监控 | 时间加速 | 编码解码工具");
        desc.setTextColor(Color.parseColor("#CCCCEE"));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        section.addView(desc);

        rootLayout.addView(section);
    }

    private void buildHtmlCaptureSection() {
        LinearLayout section = createSection("🌐 HTML实时捕捉");

        TextView desc = new TextView(this);
        desc.setText("实时捕捉目标App中WebView当前渲染的完整HTML源码。\n\n" +
            "捕捉方式:\n" +
            "• 自动捕捉: WebView每次加载时自动保存HTML\n" +
            "• 手动捕捉: 通过悬浮窗「🌐 捕捉HTML」按钮触发\n" +
            "• JS触发: 在注入脚本中调用 window.__wd_capture(html)\n" +
            "• 源码按钮: 悬浮窗「📋 源码」一键获取\n\n" +
            "兼容系统WebView和腾讯X5内核。\n" +
            "捕捉的HTML保存到: /sdcard/WebDecrypt/captured/");
        desc.setTextColor(Color.parseColor("#CCCCEE"));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        section.addView(desc);

        Button btnViewCaptured = createButton("📂 查看已捕捉的HTML", "#0F3460");
        btnViewCaptured.setOnClickListener(v -> openCapturedDirectory());
        section.addView(btnViewCaptured);

        rootLayout.addView(section);
    }

    private void buildWebDebugSection() {
        LinearLayout section = createSection("🔧 网页调试工具箱");

        TextView desc = new TextView(this);
        desc.setText("悬浮窗中点击「🔧 网页调试」打开多选工具箱，可同时启用多个调试功能:\n\n" +
            "• 🌐 捕捉当前HTML — 获取完整页面源码\n" +
            "• 📊 页面性能分析 — DNS/TCP/加载耗时\n" +
            "• 🔍 DOM结构查看 — 节点/深度/脚本统计\n" +
            "• 📡 网络请求监控 — Hook XHR/Fetch\n" +
            "• 📜 Console日志捕获 — 拦截log/warn/error\n" +
            "• 🔧 反反调试 — 绕过debugger和反调试\n" +
            "• ✏️ 开启编辑模式 — 直接修改页面内容\n" +
            "• 🔓 VIP内容解锁 — 移除付费遮罩\n" +
            "• 📖 阅读模式 — 纯净阅读体验\n" +
            "• 🚫 禁用弹窗 — 阻止alert/confirm/prompt\n" +
            "• ⚡ 时间加速 — 跳过等待/倒计时\n" +
            "• 🎨 颜色拾取器 — 点击获取元素颜色");
        desc.setTextColor(Color.parseColor("#CCCCEE"));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        section.addView(desc);

        rootLayout.addView(section);
    }

    private void buildOptimizationSection() {
        LinearLayout section = createSection("🧠 交互优化建议");

        TextView desc = new TextView(this);
        desc.setText("悬浮窗展开后，点击「🧠 优化分析」自动分析当前WebView内容:\n\n" +
            "• 📊 页面结构分析 — DOM层级深度、节点数量\n" +
            "• 🔒 安全检测 — 不安全的HTTP请求、明文密码字段\n" +
            "• ⚡ 性能建议 — 大图片资源、未压缩资源、阻塞JS\n" +
            "• 🔗 API端点发现 — 检测XHR/Fetch请求的API地址\n" +
            "• 📱 适配检测 — viewport设置、移动端兼容性\n" +
            "• 🎯 事件监听分析 — 绑定的事件类型和数量\n\n" +
            "分析结果实时显示在悬浮窗面板中，并提供可操作的优化建议。");
        desc.setTextColor(Color.parseColor("#CCCCEE"));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        section.addView(desc);

        rootLayout.addView(section);
    }

    private void buildCapturedFilesSection() {
        LinearLayout section = createSection("📁 已捕获文件");

        File outputDir = new File(OUTPUT_DIR);
        if (outputDir.exists()) {
            StringBuilder sb = new StringBuilder();
            String[] subDirs = {"assets", "webview", "intercepted", "response", "decrypted", "decoded", "scanned", "chromium", "captured"};
            for (String sub : subDirs) {
                File dir = new File(OUTPUT_DIR + sub);
                if (dir.exists()) {
                    File[] files = dir.listFiles();
                    int count = (files != null) ? files.length : 0;
                    if (count > 0) sb.append(sub).append(": ").append(count).append(" 个文件\n");
                }
            }
            if (sb.length() == 0) sb.append("暂无捕获文件，打开目标App后自动开始捕获");
            TextView filesText = new TextView(this);
            filesText.setText(sb.toString());
            filesText.setTextColor(Color.parseColor("#AADDAA"));
            filesText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            filesText.setPadding(0, 4, 0, 0);
            section.addView(filesText);
        } else {
            TextView emptyText = new TextView(this);
            emptyText.setText("暂无捕获文件，打开目标App后自动开始捕获");
            emptyText.setTextColor(Color.parseColor("#8888AA"));
            emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            section.addView(emptyText);
        }
        rootLayout.addView(section);
    }

    private void buildAboutSection() {
        LinearLayout section = createSection("ℹ️ 关于");

        TextView about = new TextView(this);
        about.setText("WebDecrypt Pro v9.0 — LSP增强版\n\n" +
            "核心原理:\n" +
            "当App从加密的assets加载HTML时，App会在内存中解压/解密后\n" +
            "交给WebView渲染。本模块拦截系统函数，直接获取解密后的\n" +
            "原始数据。\n\n" +
            "拦截层级 (7层):\n" +
            "• 资源层: AssetManager.open()\n" +
            "• 渲染层: WebView.loadUrl/loadData (系统+X5)\n" +
            "• 网络层: shouldInterceptRequest\n" +
            "• 响应层: WebResourceResponse\n" +
            "• 加密层: Cipher.doFinal\n" +
            "• 编码层: Base64.decode\n" +
            "• 内核层: AwContents/ContentViewCore\n\n" +
            "v9.0新增:\n" +
            "• 🛡️ 反检测引擎 (12个Hook点)\n" +
            "• 📱 腾讯X5内核兼容\n" +
            "• 📜 30+内置脚本库\n" +
            "• 🔧 网页调试工具箱\n" +
            "• 💉 JS注入引擎 (系统+X5)\n" +
            "• 🌐 HTML实时捕捉\n" +
            "• 🧠 交互优化建议\n" +
            "• ⚡ LSP激活检测与引导");
        about.setTextColor(Color.parseColor("#8888AA"));
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        section.addView(about);
        rootLayout.addView(section);
    }

    private LinearLayout createSection(String title) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackgroundColor(Color.parseColor("#12122A"));
        section.setPadding(24, 20, 24, 20);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        section.setLayoutParams(params);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.parseColor("#E94560"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, 12);
        section.addView(titleView);

        return section;
    }

    private Button createButton(String text, String bgColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(Color.parseColor(bgColor));
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setPadding(16, 8, 16, 8);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 8, 4);
        btn.setLayoutParams(params);
        return btn;
    }

    private void openOutputDirectory() {
        try {
            File dir = new File(OUTPUT_DIR);
            if (!dir.exists()) dir.mkdirs();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(dir), "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "选择文件管理器"));
        } catch (Exception e) { showAlertDialog("无法打开", "输出目录: " + OUTPUT_DIR); }
    }

    private void openCapturedDirectory() {
        try {
            File dir = new File(OUTPUT_DIR + "captured/");
            if (!dir.exists()) dir.mkdirs();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(dir), "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "选择文件管理器"));
        } catch (Exception e) { showAlertDialog("无法打开", "捕捉目录: " + OUTPUT_DIR + "captured/"); }
    }

    private void showClearDataDialog() {
        new AlertDialog.Builder(this)
            .setTitle("清空捕获数据")
            .setMessage("确定要清空 /sdcard/WebDecrypt/ 下的所有捕获文件吗?")
            .setPositiveButton("确定", (d, w) -> { deleteRecursive(new File(OUTPUT_DIR)); new File(OUTPUT_DIR).mkdirs(); recreate(); })
            .setNegativeButton("取消", null)
            .show();
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteRecursive(child); }
        file.delete();
    }

    private void showLogFile() {
        File logFile = new File(OUTPUT_DIR + "log.txt");
        if (!logFile.exists()) { showAlertDialog("日志文件不存在", "请先打开目标App运行一段时间后再查看"); return; }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            StringBuilder sb = new StringBuilder();
            String line; int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 200) { sb.append(line).append("\n"); lineCount++; }
            reader.close();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("📋 运行日志 (最近200行)");
            TextView logView = new TextView(this);
            logView.setText(sb.toString());
            logView.setTextColor(Color.parseColor("#AADDAA"));
            logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            logView.setTypeface(Typeface.MONOSPACE);
            logView.setPadding(24, 16, 24, 16);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(logView);
            builder.setView(scroll);
            builder.setPositiveButton("关闭", null);
            builder.show();
        } catch (Exception e) { showAlertDialog("读取失败", e.getMessage()); }
    }

    private void showAlertDialog(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("确定", null).show();
    }
}
