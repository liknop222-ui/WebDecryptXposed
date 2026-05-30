package com.webdecrypt.xposed;

import androidx.appcompat.app.AppCompatActivity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.robv.android.xposed.XposedBridge;

/**
 * 模块主界面 — 显示模块状态和说明
 */
public class MainActivity extends androidx.appcompat.app.AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("🔓 WebDecrypt Pro v8.0");
        title.setTextSize(24);
        layout.addView(title);

        TextView desc = new TextView(this);
        desc.setText("\n通用Web本地加密HTML解密模块\n\n" +
                "功能:\n" +
                "• 系统层级拦截 AssetManager 资源加载\n" +
                "• WebView 渲染引擎拦截\n" +
                "• Cipher/Base64 加密解密拦截\n" +
                "• Chromium 内核深度拦截\n" +
                "• 可拖拽悬浮窗实时监控\n\n" +
                "使用方法:\n" +
                "1. 在 LSPosed/Xposed 中激活本模块\n" +
                "2. 勾选目标应用的作用域\n" +
                "3. 重启目标应用\n" +
                "4. 操作应用, 悬浮窗自动出现\n" +
                "5. 解密文件保存到 /sdcard/WebDecrypt/\n\n" +
                "原理:\n" +
                "当App从加密的assets加载HTML时,\n" +
                "App会在内存中解压/解密后交给WebView渲染,\n" +
                "本模块拦截系统函数,直接获取解密后的原始数据。");
        desc.setTextSize(14);
        layout.addView(desc);

        setContentView(layout);
    }
}
