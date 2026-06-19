package com.webdecrypt.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "webdecrypt_prefs";
    private static final String KEY_JS_SCRIPTS = "js_scripts";
    private static final String KEY_AUTO_CAPTURE = "auto_capture";
    private static final String KEY_AUTO_INJECT = "auto_inject";
    private static final String KEY_CAPTURE_HTML = "capture_html";
    private static final String KEY_TARGET_KEYWORDS = "target_keywords";
    private static final String KEY_ANTI_DETECTION = "anti_detection";
    private static final String OUTPUT_DIR = "/sdcard/WebDecrypt/";

    // 精美配色
    private static final String C_BG = "#0B0B18";
    private static final String C_CARD = "#15152E";
    private static final String C_CARD_INNER = "#101024";
    private static final String C_ACCENT = "#E94560";
    private static final String C_ACCENT2 = "#7B2FF7";
    private static final String C_TEXT = "#E8E8F0";
    private static final String C_TEXT_DIM = "#8A8AB0";
    private static final String C_OK = "#5BD8A0";
    private static final String C_WARN = "#FFB454";
    private static final String C_ERR = "#FF6B6B";

    private SharedPreferences prefs;
    private LinearLayout rootLayout;
    private TextView statusText;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        handler = new Handler(Looper.getMainLooper());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor(C_BG));
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(18), dp(18), dp(18), dp(28));

        buildHeader();
        buildLspStatusSection();
        buildAntiDetectionSection();
        buildQuickActions();
        buildCaptureSettings();
        buildJsInjectionSection();
        buildDecryptSection();
        buildBuiltInScriptsSection();
        buildHtmlCaptureSection();
        buildWebDebugSection();
        buildOptimizationSection();
        buildCapturedFilesSection();
        buildAboutSection();

        scrollView.addView(rootLayout);
        setContentView(scrollView);
    }

    // ════════════════════════════════════════════════════════════════
    // UI 构建工具（卡片式精美布局）
    // ════════════════════════════════════════════════════════════════

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    /** 创建一张圆角卡片容器 */
    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.parseColor(C_CARD));
        card.setBackground(bg);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(lp);
        return card;
    }

    /** 卡片渐变标题栏 */
    private TextView createCardTitle(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor(C_ACCENT), Color.parseColor(C_ACCENT2)});
        bg.setCornerRadius(dp(12));
        tv.setBackground(bg);
        return tv;
    }

    private TextView createDesc(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(C_TEXT_DIM));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setLineSpacing(dp(2), 1f);
        tv.setPadding(0, dp(10), 0, dp(6));
        return tv;
    }

    private Button createButton(String text, String bgColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setAllCaps(false);
        btn.setPadding(dp(14), dp(8), dp(14), dp(8));
        btn.setMinHeight(0); btn.setMinimumHeight(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(10));
        bg.setColor(Color.parseColor(bgColor));
        btn.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(4), dp(8), dp(4));
        btn.setLayoutParams(params);
        return btn;
    }

    private Switch createSwitch(String text, String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText("  " + text);
        sw.setTextColor(Color.parseColor(C_TEXT));
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(key, c).apply());
        sw.setPadding(0, dp(6), 0, dp(6));
        return sw;
    }

    // ════════════════════════════════════════════════════════════════
    // 各区块
    // ════════════════════════════════════════════════════════════════

    private void buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor(C_ACCENT), Color.parseColor(C_ACCENT2)});
        bg.setCornerRadius(dp(18));
        header.setBackground(bg);
        header.setPadding(dp(22), dp(20), dp(22), dp(20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(16));
        header.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText("🔓 WebDecrypt Pro");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title);

        TextView ver = new TextView(this);
        ver.setText("v9.1 · LSP增强版");
        ver.setTextColor(Color.parseColor("#FFE0E0"));
        ver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        ver.setPadding(0, dp(2), 0, dp(6));
        header.addView(ver);

        TextView subtitle = new TextView(this);
        subtitle.setText("稳定注入 · 悬浮窗保障 · 多方案解密 · 实时日志\n反检测 + X5兼容 + JS注入 + HTML捕捉 + 30+脚本");
        subtitle.setTextColor(Color.parseColor("#FFE8E8"));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        header.addView(subtitle);

        rootLayout.addView(header);
    }

    private void buildLspStatusSection() {
        LinearLayout card = createCard();
        card.addView(createCardTitle("⚡ LSP激活状态"));
        boolean isModuleActive = false;
        try { isModuleActive = isModuleActive(); } catch (Exception e) {}

        statusText = new TextView(this);
        if (isModuleActive) {
            statusText.setText("✅ 模块已激活 — LSPosed/Xposed框架已成功加载本模块");
            statusText.setTextColor(Color.parseColor(C_OK));
        } else {
            statusText.setText("❌ 模块未激活 — 需要在LSPosed中启用本模块");
            statusText.setTextColor(Color.parseColor(C_ERR));
        }
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusText.setPadding(0, dp(10), 0, dp(8));
        card.addView(statusText);

        if (!isModuleActive) {
            LinearLayout guideBox = new LinearLayout(this);
            guideBox.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable gbg = new GradientDrawable();
            gbg.setCornerRadius(dp(12));
            gbg.setColor(Color.parseColor(C_CARD_INNER));
            guideBox.setBackground(gbg);
            guideBox.setPadding(dp(16), dp(14), dp(16), dp(14));

            String[] steps = {
                "激活步骤:",
                "1. 确保已安装 LSPosed (需要Magisk + Zygisk)",
                "2. 安装本模块APK",
                "3. 打开 LSPosed Manager",
                "4. 找到 \"WebDecrypt Pro\" 并启用模块",
                "5. 设置作用域 → 勾选目标App",
                "6. 授予目标App「显示在其他应用上层」权限",
                "7. 强制停止目标App后重新打开",
                "8. 悬浮窗自动出现 → 点击小球展开面板"
            };
            for (String step : steps) {
                TextView stepText = new TextView(this);
                stepText.setText(step);
                stepText.setTextColor(step.startsWith("激活") ? Color.parseColor(C_ACCENT) : Color.parseColor(C_TEXT));
                stepText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                stepText.setPadding(0, dp(3), 0, dp(3));
                guideBox.addView(stepText);
            }
            card.addView(guideBox);

            Button btnOpenLsposed = createButton("打开 LSPosed Manager", "#0F3460");
            btnOpenLsposed.setOnClickListener(v -> {
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");
                    if (intent != null) startActivity(intent);
                    else showAlertDialog("未找到LSPosed Manager", "请确保已安装LSPosed Manager");
                } catch (Exception e) { showAlertDialog("无法打开", "请手动打开LSPosed Manager"); }
            });
            card.addView(btnOpenLsposed);
        } else {
            TextView activeInfo = new TextView(this);
            activeInfo.setText("模块已就绪。打开目标App后，悬浮窗将自动注入。\n\n💡 悬浮窗采用3重注入保障(onResume/获焦/附加窗口)，并带失败重试与权限引导，确保稳定出现。");
            activeInfo.setTextColor(Color.parseColor(C_OK));
            activeInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            activeInfo.setLineSpacing(dp(2), 1f);
            activeInfo.setPadding(0, dp(6), 0, 0);
            card.addView(activeInfo);
        }
        rootLayout.addView(card);
    }

    private boolean isModuleActive() { return false; }

    private void buildAntiDetectionSection() {
        LinearLayout card = createCard();
        card.addView(createCardTitle("🛡️ 反检测引擎"));
        card.addView(createDesc("在目标App启动前注入反检测逻辑，伪装正常环境，隐藏框架特征。\n覆盖进程/端口、内存/符号、文件/描述符、Java层、行为/时间、设备指纹、线程栈、PackageManager 共8大检测层。"));
        card.addView(createSwitch("启用反检测引擎", KEY_ANTI_DETECTION, true));
        rootLayout.addView(card);
    }

    private void buildQuickActions() {
        LinearLayout card = createCard();
        card.addView(createCardTitle("🎯 快捷操作"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));

        Button btnOpenDir = createButton("📁 输出目录", "#0F3460");
        btnOpenDir.setOnClickListener(v -> openOutputDirectory());
        row.addView(btnOpenDir, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnClearData = createButton("🗑 清空数据", "#5C1A1A");
        btnClearData.setOnClickListener(v -> showClearDataDialog());
        row.addView(btnClearData, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);

        Button btnViewLog = createButton("📋 查看运行日志（彩色分级）", "#16213E");
        btnViewLog.setOnClickListener(v -> showLogFile());
        card.addView(btnViewLog);
        rootLayout.addView(card);
    }

    private void buildCaptureSettings() {
        LinearLayout card = createCard();
        card.addView(createCardTitle("⚙️ 拦截设置"));
        card.addView(createSwitch("自动捕获", KEY_AUTO_CAPTURE, true));
        card.addView(createSwitch("自动注入JS", KEY_AUTO_INJECT, false));
        card.addView(createSwitch("实时HTML捕捉", KEY_CAPTURE_HTML, true));

        TextView kwLabel = new TextView(this);
        kwLabel.setText("目标Activity关键词 (逗号分隔，匹配到则注入悬浮窗):");
        kwLabel.setTextColor(Color.parseColor(C_TEXT_DIM));
        kwLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        kwLabel.setPadding(0, dp(10), 0, dp(4));
        card.addView(kwLabel);

        EditText kwInput = new EditText(this);
        kwInput.setText(prefs.getString(KEY_TARGET_KEYWORDS, "Web,Main,Browser,Home,Content"));
        kwInput.setTextColor(Color.parseColor(C_TEXT));
        kwInput.setHint("Web,Main,Browser...");
        kwInput.setHintTextColor(Color.parseColor("#555577"));
        GradientDrawable ebg = new GradientDrawable();
        ebg.setCornerRadius(dp(8));
        ebg.setColor(Color.parseColor(C_CARD_INNER));
        kwInput.setBackground(ebg);
        kwInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        kwInput.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) prefs.edit().putString(KEY_TARGET_KEYWORDS, kwInput.getText().toString()).apply(); });
        card.addView(kwInput);
        rootLayout.addView(card);
    }

    private void buildJsInjectionSection() {
        LinearLayout card = createCard();
        card.addView(createCardTitle("💉 JS注入引擎"));
        card.addView(createDesc("自定义JavaScript代码，在目标App的WebView中自动注入执行。\n支持多段脚本，每段用 #---# 分隔。\n兼容系统WebView和腾讯X5内核。"));

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
        scriptInput.setTextColor(Color.parseColor(C_OK));
        GradientDrawable ebg = new GradientDrawable();
        ebg.setCornerRadius(dp(8));
        ebg.setColor(Color.parseColor(C_CARD_INNER));
        scriptInput.setBackground(ebg);
        scriptInput.setTypeface(Typeface.MONOSPACE);
        scriptInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        scriptInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        scriptInput.setMinLines(8);
        scriptInput.setGravity(Gravity.TOP | Gravity.START);
        card.addView(scriptInput);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(8), 0, 0);

        Button btnSave = createButton("💾 保存脚本", "#0F3460");
        btnSave.setOnClickListener(v -> { prefs.edit().putString(KEY_JS_SCRIPTS, scriptInput.getText().toString()).apply(); showAlertDialog("已保存", "JS脚本已保存，下次目标App启动时生效"); });
        btnRow.addView(btnSave, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnClear = createButton("🗑 清空", "#5C1A1A");
        btnClear.setOnClickListener(v -> { scriptInput.setText(""); prefs.edit().remove(KEY_JS_SCRIPTS).apply(); });
        btnRow.addView(btnClear, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(btnRow);

        card.addView(createDesc("💡 提示: 注入的JS可通过 console.log('[WD]...') 输出到日志\n使用 window.__wd_capture(html) 可手动捕捉HTML\n使用 window.__wd_log(msg) 可输出到模块日志"));
        rootLayout.addView(card);
    }

    private void buildDecryptSection() {
        LinearLayout card = createCard();
        card.addView(createCardTitle("🔓 多方案解密引擎"));
        card.addView(createDesc("对捕获的加密资源自动尝试多种解密方案，多方案纠错，最大化 dump 成功率：\n\n" +
            "• 明文识别 — 直接识别可读 Web 内容\n" +
            "• Base64 变体 — 标准/URL安全/无填充/无换行\n" +
            "• XOR 暴力 — 单字节1~255 + 常见多字节口令\n" +
            "• AES/DES — 复用 Cipher hook 捕获的密钥(ECB/CBC)\n" +
            "• RC4 — 常见弱口令尝试\n" +
            "• 熵值分析 — 香农熵判断加密/压缩特征\n\n" +
            "扩展识别文件类型: .vm/.enc/.dat/.bin/.bundle/.asar/.wasm/.vue/.db/.sqlite/.pem/.key 等\n" +
            "捕获的密钥保存到 /sdcard/WebDecrypt/keys/ 供人工分析"));
        rootLayout.addView(card);
    }

    private void buildBuiltInScriptsSection() {
        LinearLayout card = createCard();
        card.addView(createCardTitle("📜 内置脚本库 (30+)"));
        card.addView(createDesc("30+内置脚本，按分类组织，在悬浮窗中一键执行:\n\n" +
            "🔍 调试类(8): DOM查看器|事件监听器|Cookie|LocalStorage|SessionStorage|网络监控|Console捕获|性能分析\n" +
            "✏️ 修改类(8): 编辑模式|显示隐藏|移除遮罩|禁右键|改UA|注入jQuery|改Viewport|暗黑模式\n" +
            "📥 下载类(6): 图片|链接|视频|音频|CSS|JS\n" +
            "🧭 导航类(5): 元素高亮|表单填充|滚到底部|查找文本|截图标记\n" +
            "🔓 解锁类(6): VIP解锁|阅读模式|禁弹窗|恢复弹窗|禁跳转|反反调试\n" +
            "🔧 工具类(10): 页面信息|颜色拾取|清Cookie|清存储|强重载|源码|API探测|WS监控|时间加速|编解码"));
        rootLayout.addView(card);
    }

    private void buildHtmlCaptureSection() {
        LinearLayout card = createCard();
        card.addView(createCardTitle("🌐 HTML实时捕捉"));
        card.addView(createDesc("实时捕捉目标App中WebView当前渲染的完整HTML源码。\n\n捕捉方式:\n• 自动捕捉: WebView每次加载时自动保存\n• 手动捕捉: 悬浮窗「🌐 捕HTML」按钮\n• JS触发: window.__wd_capture(html)\n• 源码按钮: 悬浮窗「📋 源码」\n\n兼容系统WebView和腾讯X5内核。\n保存到 /sdcard/WebDecrypt/captured/"));

        Button btnViewCaptured = createButton("📂 查看已捕捉的HTML", "#0F3460");
        btnViewCaptured.setOnClickListener(v -> openCapturedDirectory());
        card.addView(btnViewCaptured);
        rootLayout.addView(card);
    }

    private void buildWebDebugSection() {
        LinearLayout card = createCard();
        card.addView(createCardTitle("🔧 网页调试工具箱"));
        card.addView(createDesc("悬浮窗中点击「🔧 调试」打开多选工具箱，可同时启用多个调试功能:\n\n" +
            "• 🌐 捕捉HTML  • 📊 性能分析  • 🔍 DOM查看\n" +
            "• 📡 网络监控  • 📜 Console捕获  • 🔧 反反调试\n" +
            "• ✏️ 编辑模式  • 🔓 VIP解锁  • 📖 阅读模式\n" +
            "• 🚫 禁弹窗  • ⚡ 时间加速  • 🎨 颜色拾取"));
        rootLayout.addView(card);
    }

    private void buildOptimizationSection() {
        LinearLayout card = createCard();
        card.addView(createCardTitle("🧠 交互优化建议"));
        card.addView(createDesc("悬浮窗展开后，点击「🧠 优化」自动分析当前WebView内容:\n\n" +
            "• 📊 页面结构 — DOM层级深度、节点数量\n" +
            "• 🔒 安全检测 — HTTP请求、明文密码字段\n" +
            "• ⚡ 性能建议 — 大图片、阻塞JS\n" +
            "• 🔗 API端点 — XHR/Fetch请求地址\n" +
            "• 📱 适配检测 — viewport、移动端兼容\n" +
            "• 🎯 事件监听 — 绑定的事件类型和数量\n\n" +
            "分析结果实时显示在悬浮窗面板，并提供可操作建议。"));
        rootLayout.addView(card);
    }

    private void buildCapturedFilesSection() {
        LinearLayout card = createCard();
        card.addView(createCardTitle("📁 已捕获文件"));

        File outputDir = new File(OUTPUT_DIR);
        if (outputDir.exists()) {
            StringBuilder sb = new StringBuilder();
            String[] subDirs = {"assets", "webview", "intercepted", "response", "decrypted", "decoded", "scanned", "chromium", "captured", "keys"};
            int total = 0;
            for (String sub : subDirs) {
                File dir = new File(OUTPUT_DIR + sub);
                if (dir.exists()) {
                    File[] files = dir.listFiles();
                    int count = (files != null) ? files.length : 0;
                    if (count > 0) { sb.append(sub).append(": ").append(count).append(" 个\n"); total += count; }
                }
            }
            if (sb.length() == 0) sb.append("暂无捕获文件，打开目标App后自动开始捕获");
            else sb.insert(0, "总计 " + total + " 个文件\n\n");
            TextView filesText = new TextView(this);
            filesText.setText(sb.toString());
            filesText.setTextColor(Color.parseColor(C_OK));
            filesText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            filesText.setTypeface(Typeface.MONOSPACE);
            filesText.setPadding(0, dp(6), 0, 0);
            card.addView(filesText);
        } else {
            card.addView(createDesc("暂无捕获文件，打开目标App后自动开始捕获"));
        }
        rootLayout.addView(card);
    }

    private void buildAboutSection() {
        LinearLayout card = createCard();
        card.addView(createCardTitle("ℹ️ 关于"));
        card.addView(createDesc("WebDecrypt Pro v9.1 — LSP增强版\n\n" +
            "核心原理:\n当App从加密的assets加载HTML时，App会在内存中解压/解密后交给WebView渲染。本模块拦截系统函数，直接获取解密后的原始数据。\n\n" +
            "拦截层级 (7层):\n" +
            "• 资源层: AssetManager.open()\n" +
            "• 渲染层: WebView.loadUrl/loadData (系统+X5)\n" +
            "• 网络层: shouldInterceptRequest\n" +
            "• 响应层: WebResourceResponse\n" +
            "• 加密层: Cipher.doFinal\n" +
            "• 编码层: Base64.decode\n" +
            "• 内核层: AwContents/ContentViewCore\n\n" +
            "v9.1升级:\n" +
            "• 🛡️ 反检测引擎 (8大检测层)\n" +
            "• 📱 腾讯X5内核兼容\n" +
            "• 📜 30+内置脚本库\n" +
            "• 🔧 网页调试工具箱\n" +
            "• 💉 JS注入引擎 (系统+X5)\n" +
            "• 🌐 HTML实时捕捉\n" +
            "• 🧠 交互优化建议\n" +
            "• ⚡ LSP激活检测与引导\n" +
            "• 🪟 悬浮窗3重注入保障+重试+权限引导\n" +
            "• 📋 实时日志面板+彩色分级\n" +
            "• 🔓 多方案解密引擎(XOR/Base64/AES/DES/RC4)"));
        rootLayout.addView(card);
    }

    // ════════════════════════════════════════════════════════════════
    // 操作
    // ════════════════════════════════════════════════════════════════

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

    /** 新手友好的彩色分级日志查看器 */
    private void showLogFile() {
        File logFile = new File(OUTPUT_DIR + "log.txt");
        if (!logFile.exists()) { showAlertDialog("日志文件不存在", "请先打开目标App运行一段时间后再查看。\n\n💡 日志会实时写入 /sdcard/WebDecrypt/log.txt"); return; }
        try {
            // 读取全部行，取最后500行
            List<String> allLines = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            String line;
            while ((line = reader.readLine()) != null) allLines.add(line);
            reader.close();
            int start = Math.max(0, allLines.size() - 500);
            List<String> lines = allLines.subList(start, allLines.size());

            SpannableStringBuilder sb = new SpannableStringBuilder();
            for (String l : lines) {
                int color = colorForLine(l);
                int s = sb.length();
                sb.append(l).append("\n");
                sb.setSpan(new ForegroundColorSpan(color), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (sb.length() == 0) sb.append("日志为空");

            TextView logView = new TextView(this);
            logView.setText(sb);
            logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            logView.setTypeface(Typeface.MONOSPACE);
            logView.setPadding(dp(16), dp(12), dp(16), dp(12));
            ScrollView scroll = new ScrollView(this);
            scroll.addView(logView);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("📋 运行日志 (最近" + lines.size() + "行 · 彩色分级)");
            builder.setView(scroll);
            builder.setPositiveButton("刷新", (d, w) -> showLogFile());
            builder.setNegativeButton("关闭", null);
            builder.show();
            // 滚动到底部
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        } catch (Exception e) { showAlertDialog("读取失败", e.getMessage()); }
    }

    /** 根据日志级别返回颜色 */
    private int colorForLine(String line) {
        if (line == null) return Color.parseColor(C_TEXT);
        if (line.contains("[ERR]") || line.contains("❌")) return Color.parseColor(C_ERR);
        if (line.contains("[WARN]") || line.contains("⚠️")) return Color.parseColor(C_WARN);
        if (line.contains("[OK]") || line.contains("✅")) return Color.parseColor(C_OK);
        if (line.contains("[指南]") || line.contains("💡")) return Color.parseColor("#7BC8F6");
        if (line.contains("[TRC]") || line.contains("→")) return Color.parseColor("#6A6A90");
        if (line.contains("[DBG]") || line.contains("🔍")) return Color.parseColor("#9A8AC0");
        return Color.parseColor(C_TEXT);
    }

    private void showAlertDialog(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("确定", null).show();
    }
}
