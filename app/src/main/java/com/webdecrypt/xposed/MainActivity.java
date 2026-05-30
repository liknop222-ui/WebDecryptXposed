package com.webdecrypt.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "webdecrypt_prefs";
    private static final String KEY_JS_SCRIPTS = "js_scripts";
    private static final String KEY_AUTO_CAPTURE = "auto_capture";
    private static final String KEY_AUTO_INJECT = "auto_inject";
    private static final String KEY_CAPTURE_HTML = "capture_html";
    private static final String KEY_TARGET_KEYWORDS = "target_keywords";
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
        buildQuickActions();
        buildCaptureSettings();
        buildJsInjectionSection();
        buildHtmlCaptureSection();
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
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 8);
        rootLayout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("LSP增强版 — 系统层级拦截 + JS注入 + HTML捕捉 + 交互优化");
        subtitle.setTextColor(Color.parseColor("#8888AA"));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setPadding(0, 0, 0, 24);
        rootLayout.addView(subtitle);
    }

    private void buildLspStatusSection() {
        LinearLayout section = createSection("⚡ LSP激活状态");

        boolean isModuleActive = false;
        try {
            isModuleActive = isModuleActive();
        } catch (Exception e) {}

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
                stepText.setTextColor(step.startsWith("激活") ?
                    Color.parseColor("#E94560") : Color.parseColor("#CCCCEE"));
                stepText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                stepText.setPadding(0, 4, 0, 4);
                guideLayout.addView(stepText);
            }

            section.addView(guideLayout);

            Button btnOpenLsposed = createButton("打开 LSPosed Manager", "#0F3460");
            btnOpenLsposed.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent intent = getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");
                        if (intent != null) {
                            startActivity(intent);
                        } else {
                            showAlertDialog("未找到LSPosed Manager", "请确保已安装LSPosed Manager");
                        }
                    } catch (Exception e) {
                        showAlertDialog("无法打开", "请手动打开LSPosed Manager");
                    }
                }
            });
            section.addView(btnOpenLsposed);
        } else {
            TextView activeInfo = new TextView(this);
            activeInfo.setText("模块已就绪。打开目标App后，悬浮窗将自动注入。\n展开悬浮窗可使用: 监控控制、JS注入、HTML捕捉、优化建议");
            activeInfo.setTextColor(Color.parseColor("#AADDAA"));
            activeInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            activeInfo.setPadding(0, 8, 0, 0);
            section.addView(activeInfo);
        }

        rootLayout.addView(section);
    }

    private boolean isModuleActive() {
        return false;
    }

    private void buildQuickActions() {
        LinearLayout section = createSection("🎯 快捷操作");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);

        Button btnOpenDir = createButton("📁 输出目录", "#0F3460");
        btnOpenDir.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOutputDirectory();
            }
        });
        row.addView(btnOpenDir, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnClearData = createButton("🗑 清空数据", "#5C1A1A");
        btnClearData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showClearDataDialog();
            }
        });
        row.addView(btnClearData, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        section.addView(row);

        Button btnViewLog = createButton("📋 查看运行日志", "#16213E");
        btnViewLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogFile();
            }
        });
        section.addView(btnViewLog);

        rootLayout.addView(section);
    }

    private void buildCaptureSettings() {
        LinearLayout section = createSection("⚙️ 拦截设置");

        Switch switchAutoCapture = new Switch(this);
        switchAutoCapture.setText("  自动捕获");
        switchAutoCapture.setTextColor(Color.WHITE);
        switchAutoCapture.setChecked(prefs.getBoolean(KEY_AUTO_CAPTURE, true));
        switchAutoCapture.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_AUTO_CAPTURE, isChecked).apply();
        });
        section.addView(switchAutoCapture);

        Switch switchAutoInject = new Switch(this);
        switchAutoInject.setText("  自动注入JS");
        switchAutoInject.setTextColor(Color.WHITE);
        switchAutoInject.setChecked(prefs.getBoolean(KEY_AUTO_INJECT, false));
        switchAutoInject.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_AUTO_INJECT, isChecked).apply();
        });
        section.addView(switchAutoInject);

        Switch switchCaptureHtml = new Switch(this);
        switchCaptureHtml.setText("  实时HTML捕捉");
        switchCaptureHtml.setTextColor(Color.WHITE);
        switchCaptureHtml.setChecked(prefs.getBoolean(KEY_CAPTURE_HTML, true));
        switchCaptureHtml.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_CAPTURE_HTML, isChecked).apply();
        });
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
        kwInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                prefs.edit().putString(KEY_TARGET_KEYWORDS, kwInput.getText().toString()).apply();
            }
        });
        section.addView(kwInput);

        rootLayout.addView(section);
    }

    private void buildJsInjectionSection() {
        LinearLayout section = createSection("💉 JS注入引擎");

        TextView desc = new TextView(this);
        desc.setText("自定义JavaScript代码，在目标App的WebView中自动注入执行。\n支持多段脚本，每段用 #---# 分隔。");
        desc.setTextColor(Color.parseColor("#8888AA"));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        desc.setPadding(0, 0, 0, 12);
        section.addView(desc);

        String savedScripts = prefs.getString(KEY_JS_SCRIPTS,
            "// 示例: 捕获所有按钮点击\n" +
            "document.addEventListener('click', function(e) {\n" +
            "  console.log('[WD] 点击:', e.target.tagName, e.target.textContent);\n" +
            "});\n" +
            "\n#---#\n\n" +
            "// 示例: Hook XMLHttpRequest\n" +
            "var _origOpen = XMLHttpRequest.prototype.open;\n" +
            "XMLHttpRequest.prototype.open = function(method, url) {\n" +
            "  console.log('[WD] XHR:', method, url);\n" +
            "  return _origOpen.apply(this, arguments);\n" +
            "};"
        );

        EditText scriptInput = new EditText(this);
        scriptInput.setText(savedScripts);
        scriptInput.setTextColor(Color.parseColor("#AADDAA"));
        scriptInput.setBackgroundColor(Color.parseColor("#1A1A2E"));
        scriptInput.setTypeface(android.graphics.Typeface.MONOSPACE);
        scriptInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        scriptInput.setPadding(16, 12, 16, 12);
        scriptInput.setMinLines(8);
        scriptInput.setGravity(Gravity.TOP | Gravity.START);
        section.addView(scriptInput);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 8, 0, 0);

        Button btnSave = createButton("💾 保存脚本", "#0F3460");
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putString(KEY_JS_SCRIPTS, scriptInput.getText().toString()).apply();
                showAlertDialog("已保存", "JS脚本已保存，下次目标App启动时生效");
            }
        });
        btnRow.addView(btnSave, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnClear = createButton("🗑 清空", "#5C1A1A");
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scriptInput.setText("");
                prefs.edit().remove(KEY_JS_SCRIPTS).apply();
            }
        });
        btnRow.addView(btnClear, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        section.addView(btnRow);

        TextView tip = new TextView(this);
        tip.setText("💡 提示: 注入的JS可通过 console.log('[WD] ...') 输出到日志\n" +
                   "使用 window.__wd_capture(html) 可手动捕捉当前页面HTML");
        tip.setTextColor(Color.parseColor("#666688"));
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tip.setPadding(0, 8, 0, 0);
        section.addView(tip);

        rootLayout.addView(section);
    }

    private void buildHtmlCaptureSection() {
        LinearLayout section = createSection("🌐 HTML实时捕捉");

        TextView desc = new TextView(this);
        desc.setText("实时捕捉目标App中WebView当前渲染的完整HTML源码。\n" +
                    "捕捉方式:\n" +
                    "• 自动捕捉: WebView每次加载时自动保存HTML\n" +
                    "• 手动捕捉: 通过悬浮窗按钮触发\n" +
                    "• JS触发: 在注入脚本中调用 window.__wd_capture(html)\n\n" +
                    "捕捉的HTML保存到: /sdcard/WebDecrypt/captured/");
        desc.setTextColor(Color.parseColor("#CCCCEE"));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        section.addView(desc);

        Button btnViewCaptured = createButton("📂 查看已捕捉的HTML", "#0F3460");
        btnViewCaptured.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCapturedDirectory();
            }
        });
        section.addView(btnViewCaptured);

        rootLayout.addView(section);
    }

    private void buildOptimizationSection() {
        LinearLayout section = createSection("🧠 交互优化建议");

        TextView desc = new TextView(this);
        desc.setText("悬浮窗展开后，自动分析当前WebView内容并提供交互优化建议:\n\n" +
                    "• 📊 页面结构分析 — DOM层级深度、节点数量\n" +
                    "• 🔒 安全检测 — 不安全的HTTP请求、明文密码字段\n" +
                    "• ⚡ 性能建议 — 大图片资源、未压缩资源、阻塞JS\n" +
                    "• 🔗 API端点发现 — 检测XHR/Fetch请求的API地址\n" +
                    "• 📱 适配检测 — viewport设置、移动端兼容性\n" +
                    "• 🎯 事件监听分析 — 绑定的事件类型和数量");
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
                    if (count > 0) {
                        sb.append(sub).append(": ").append(count).append(" 个文件\n");
                    }
                }
            }
            if (sb.length() == 0) {
                sb.append("暂无捕获文件，打开目标App后自动开始捕获");
            }
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
            "拦截层级:\n" +
            "• 资源层: AssetManager.open()\n" +
            "• 渲染层: WebView.loadUrl/loadData\n" +
            "• 网络层: shouldInterceptRequest\n" +
            "• 响应层: WebResourceResponse\n" +
            "• 加密层: Cipher.doFinal\n" +
            "• 编码层: Base64.decode\n" +
            "• 内核层: AwContents/ContentViewCore\n\n" +
            "v9.0新增:\n" +
            "• LSP激活检测与引导\n" +
            "• 自定义JS注入引擎\n" +
            "• 实时HTML捕捉\n" +
            "• 交互优化建议面板\n" +
            "• 悬浮窗交互重构");
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        section.setLayoutParams(params);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.parseColor("#E94560"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
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
        } catch (Exception e) {
            showAlertDialog("无法打开", "输出目录: " + OUTPUT_DIR);
        }
    }

    private void openCapturedDirectory() {
        try {
            File dir = new File(OUTPUT_DIR + "captured/");
            if (!dir.exists()) dir.mkdirs();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(dir), "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "选择文件管理器"));
        } catch (Exception e) {
            showAlertDialog("无法打开", "捕捉目录: " + OUTPUT_DIR + "captured/");
        }
    }

    private void showClearDataDialog() {
        new AlertDialog.Builder(this)
            .setTitle("清空捕获数据")
            .setMessage("确定要清空 /sdcard/WebDecrypt/ 下的所有捕获文件吗?")
            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    deleteRecursive(new File(OUTPUT_DIR));
                    new File(OUTPUT_DIR).mkdirs();
                    recreate();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    private void showLogFile() {
        File logFile = new File(OUTPUT_DIR + "log.txt");
        if (!logFile.exists()) {
            showAlertDialog("日志文件不存在", "请先打开目标App运行一段时间后再查看");
            return;
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            StringBuilder sb = new StringBuilder();
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 200) {
                sb.append(line).append("\n");
                lineCount++;
            }
            reader.close();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("📋 运行日志 (最近200行)");

            TextView logView = new TextView(this);
            logView.setText(sb.toString());
            logView.setTextColor(Color.parseColor("#AADDAA"));
            logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            logView.setTypeface(android.graphics.Typeface.MONOSPACE);
            logView.setPadding(24, 16, 24, 16);

            ScrollView scroll = new ScrollView(this);
            scroll.addView(logView);

            builder.setView(scroll);
            builder.setPositiveButton("关闭", null);
            builder.show();
        } catch (Exception e) {
            showAlertDialog("读取失败", e.getMessage());
        }
    }

    private void showAlertDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show();
    }
}
