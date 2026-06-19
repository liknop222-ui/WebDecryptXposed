package com.webdecrypt.xposed;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Assets 解密 / Dump 引擎
 *
 * 职责：
 * 1. 扩展目标文件类型识别（覆盖常见加密资源 / 前端打包产物 / 数据库 / 证书等）
 * 2. 加密特征识别（熵值 / 非文本头部 / 已知魔数）
 * 3. 自动尝试多种解密方案（XOR / Base64 变体 / AES / DES / RC4），多方案纠错
 * 4. 输出每一步的新手友好说明，供上层日志展示
 */
public final class AssetDecrypter {

    /** 单条解密尝试结果 */
    public static class DecryptResult {
        public final String method;     // 解密方法名
        public final byte[] data;       // 解密后数据，null 表示失败
        public final String note;       // 新手友好说明

        public DecryptResult(String method, byte[] data, String note) {
            this.method = method;
            this.data = data;
            this.note = note;
        }

        public boolean success() {
            return data != null && data.length > 0;
        }
    }

    /** 一次 dump 的完整结果 */
    public static class DumpResult {
        public final String source;             // 来源描述
        public final byte[] rawData;            // 原始数据
        public final List<DecryptResult> attempts = new ArrayList<>();
        public final List<String> logs = new ArrayList<>();

        public DumpResult(String source, byte[] rawData) {
            this.source = source;
            this.rawData = rawData;
        }

        /** 取最可信的解密产物（优先可读文本，其次任意成功） */
        public DecryptResult best() {
            DecryptResult fallback = null;
            for (DecryptResult r : attempts) {
                if (r.success()) {
                    if (isReadableText(r.data)) return r;
                    if (fallback == null) fallback = r;
                }
            }
            return fallback;
        }
    }

    private AssetDecrypter() {}

    // ════════════════════════════════════════════════════════════════
    // 文件类型识别
    // ════════════════════════════════════════════════════════════════

    /** 扩展的目标扩展名：覆盖加密资源、前端打包产物、数据库、证书、字体、媒体清单等 */
    public static final String[] TARGET_EXTENSIONS = {
            // 加密 / 自定义资源
            ".vm", ".enc", ".dat", ".bin", ".pak", ".bundle", ".asar",
            ".cipher", ".crypt", ".locked", ".secret", ".raw", ".blob",
            // 前端 / Web
            ".html", ".htm", ".js", ".mjs", ".css", ".json", ".xml", ".vue",
            ".tpl", ".ejs", ".jsx", ".ts", ".map", ".wasm",
            // 数据 / 配置
            ".db", ".sqlite", ".sqlite3", ".properties", ".cfg", ".conf", ".ini", ".yaml", ".yml",
            // 证书 / 密钥
            ".pem", ".key", ".crt", ".p12", ".keystore",
            // 字体 / 媒体清单
            ".ttf", ".otf", ".m3u8", ".m3u", ".smil"
    };

    /** 关键文件名片段（无扩展名时兜底匹配） */
    public static final String[] TARGET_NAME_KEYWORDS = {
            "index", "main", "app", "view", "config", "secret", "encrypt",
            "decrypt", "license", "vip", "member", "pay", "api", "route",
            "manifest", "bundle", "chunk", "vendor", "runtime", "polyfill"
    };

    public static boolean isTargetFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        for (String ext : TARGET_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        for (String kw : TARGET_NAME_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    // ════════════════════════════════════════════════════════════════
    // 加密特征识别
    // ════════════════════════════════════════════════════════════════

    /** 判断是否为可读文本（用于挑选最佳解密产物） */
    public static boolean isReadableText(byte[] data) {
        if (data == null || data.length < 4) return false;
        int printable = 0;
        int checkLen = Math.min(data.length, 1024);
        for (int i = 0; i < checkLen; i++) {
            int b = data[i] & 0xff;
            if (b == 0x09 || b == 0x0a || b == 0x0d) { printable++; continue; }
            if (b >= 0x20 && b < 0x7f) { printable++; continue; }
            // UTF-8 多字节中文等
            if (b >= 0x80) { printable++; continue; }
        }
        return printable * 100 / checkLen >= 90;
    }

    /** 判断是否疑似 HTML / JS / JSON 等文本内容 */
    public static boolean looksLikeWebContent(byte[] data) {
        if (data == null || data.length < 5) return false;
        try {
            String head = new String(data, 0, Math.min(data.length, 512), "UTF-8").toLowerCase();
            return head.contains("<html") || head.contains("<!doctype") || head.contains("<head") ||
                    head.contains("<body") || head.contains("<script") || head.contains("<div") ||
                    head.contains("<template") || head.contains("function(") || head.contains("var ") ||
                    head.contains("const ") || head.contains("import ") || head.contains("export ") ||
                    head.contains("\"$schema") || head.contains("{") && head.contains("}");
        } catch (Exception e) {
            return false;
        }
    }

    /** 计算香农熵，判断数据是否高熵（疑似加密 / 压缩） */
    public static double shannonEntropy(byte[] data) {
        if (data == null || data.length == 0) return 0;
        int[] freq = new int[256];
        int len = Math.min(data.length, 65536);
        for (int i = 0; i < len; i++) freq[data[i] & 0xff]++;
        double entropy = 0;
        for (int f : freq) {
            if (f == 0) continue;
            double p = (double) f / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    /** 是否疑似加密数据（高熵 + 非文本头部） */
    public static boolean looksEncrypted(byte[] data) {
        if (data == null || data.length < 16) return false;
        if (isReadableText(data)) return false;
        return shannonEntropy(data) > 7.0;
    }

    // ════════════════════════════════════════════════════════════════
    // 主入口：对一段原始数据尝试全部解密方案
    // ════════════════════════════════════════════════════════════════

    /**
     * 对原始数据执行多方案解密尝试。
     * @param source 来源描述（用于日志）
     * @param rawData 原始数据
     * @param capturedKeys 从 Cipher hook 捕获到的密钥列表（algorithm -> key bytes）
     */
    public static DumpResult tryDecrypt(String source, byte[] rawData, Map<String, List<byte[]>> capturedKeys) {
        DumpResult result = new DumpResult(source, rawData);

        if (rawData == null || rawData.length == 0) {
            result.logs.add("⚠️ 数据为空，跳过解密");
            return result;
        }

        result.logs.add("📦 原始大小: " + rawData.length + " 字节");
        result.logs.add("📊 熵值: " + String.format("%.2f", shannonEntropy(rawData)));
        result.logs.add(looksEncrypted(rawData) ? "🔒 疑似加密数据，开始多方案解密" : "📄 数据可能为明文或压缩数据");

        // 方案1：明文直接可用
        if (looksLikeWebContent(rawData)) {
            result.attempts.add(new DecryptResult("明文", rawData, "数据已是可读的 Web 内容，无需解密"));
            result.logs.add("✅ 方案1 明文: 数据可直接识别为 Web 内容");
        }

        // 方案2：Base64 变体
        tryBase64Variants(rawData, result);

        // 方案3：XOR 暴力（单字节 1~255）
        tryXorSingleByte(rawData, result);

        // 方案4：XOR 常见多字节 key
        tryXorCommonKeys(rawData, result);

        // 方案5：使用捕获到的密钥尝试 AES / DES
        tryCapturedKeyCiphers(rawData, capturedKeys, result);

        // 方案6：RC4 常见弱口令
        tryRc4CommonKeys(rawData, result);

        DecryptResult best = result.best();
        if (best != null) {
            result.logs.add("🎉 最佳解密方案: " + best.method + " (" + best.data.length + " 字节)");
        } else if (looksLikeWebContent(rawData)) {
            result.logs.add("ℹ️ 未找到额外解密方案，但原始数据已是 Web 内容");
        } else {
            result.logs.add("❌ 所有自动解密方案均未产生可读结果，已保存原始数据供人工分析");
        }

        return result;
    }

    // ════════════════════════════════════════════════════════════════
    // 各解密方案实现
    // ════════════════════════════════════════════════════════════════

    private static void tryBase64Variants(byte[] data, DumpResult result) {
        if (!isMostlyBase64(data)) return;
        int[][] flags = {
                {android.util.Base64.DEFAULT},
                {android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP},
                {android.util.Base64.NO_WRAP},
                {android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP}
        };
        String[] names = {"Base64(标准)", "Base64(URL安全)", "Base64(无换行)", "Base64(URL+无填充)"};
        for (int i = 0; i < flags.length; i++) {
            try {
                byte[] decoded = android.util.Base64.decode(data, flags[i][0]);
                if (decoded != null && decoded.length > 0 && (looksLikeWebContent(decoded) || isReadableText(decoded))) {
                    result.attempts.add(new DecryptResult(names[i], decoded, "Base64 解码后得到可读内容"));
                    result.logs.add("✅ 方案2 " + names[i] + ": 解码成功 (" + decoded.length + " 字节)");
                    return;
                }
            } catch (Exception ignore) {}
        }
        result.logs.add("ℹ️ 方案2 Base64: 数据像 Base64 但解码后不可读");
    }

    private static boolean isMostlyBase64(byte[] data) {
        if (data == null || data.length < 8) return false;
        int valid = 0;
        int len = Math.min(data.length, 2048);
        for (int i = 0; i < len; i++) {
            int b = data[i] & 0xff;
            if ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9') ||
                    b == '+' || b == '/' || b == '-' || b == '_' || b == '=' || b == '\n' || b == '\r') {
                valid++;
            }
        }
        return valid * 100 / len >= 90;
    }

    private static void tryXorSingleByte(byte[] data, DumpResult result) {
        int bestKey = -1;
        byte[] bestData = null;
        for (int key = 1; key < 256; key++) {
            byte[] xored = xorBytes(data, new byte[]{(byte) key});
            if (looksLikeWebContent(xored)) {
                bestKey = key;
                bestData = xored;
                break;
            }
        }
        if (bestData != null) {
            result.attempts.add(new DecryptResult("XOR(单字节 0x" + Integer.toHexString(bestKey) + ")",
                    bestData, "单字节异或解密成功，密钥 0x" + Integer.toHexString(bestKey)));
            result.logs.add("✅ 方案3 XOR单字节: 命中密钥 0x" + Integer.toHexString(bestKey) + " (" + bestData.length + " 字节)");
        } else {
            result.logs.add("ℹ️ 方案3 XOR单字节: 1~255 未命中可读内容");
        }
    }

    private static void tryXorCommonKeys(byte[] data, DumpResult result) {
        String[] commonKeys = {
                "webdecrypt", "1234567890", "abcdefgh", "password", "secret",
                "0123456789abcdef", "webview", "encrypt", "default", "test"
        };
        for (String k : commonKeys) {
            byte[] keyBytes = k.getBytes(Charset.forName("UTF-8"));
            byte[] xored = xorBytes(data, keyBytes);
            if (looksLikeWebContent(xored)) {
                result.attempts.add(new DecryptResult("XOR(密钥\"" + k + "\")", xored, "多字节异或解密成功"));
                result.logs.add("✅ 方案4 XOR多字节: 命中密钥 \"" + k + "\" (" + xored.length + " 字节)");
                return;
            }
        }
        result.logs.add("ℹ️ 方案4 XOR多字节: 常见口令未命中");
    }

    private static void tryCapturedKeyCiphers(byte[] data, Map<String, List<byte[]>> capturedKeys, DumpResult result) {
        if (capturedKeys == null || capturedKeys.isEmpty()) {
            result.logs.add("ℹ️ 方案5 对称密钥: 暂未捕获到密钥，跳过 AES/DES 尝试");
            return;
        }
        // 尝试 AES（密钥长度 16/24/32）与 DES（密钥长度 8）
        tryCipherWithKeys(data, capturedKeys, "AES", new String[]{"AES/ECB/PKCS5Padding", "AES/CBC/PKCS5Padding", "AES"}, result);
        tryCipherWithKeys(data, capturedKeys, "DES", new String[]{"DES/ECB/PKCS5Padding", "DES/CBC/PKCS5Padding", "DES"}, result);
    }

    private static void tryCipherWithKeys(byte[] data, Map<String, List<byte[]>> capturedKeys,
                                          String algo, String[] transforms, DumpResult result) {
        List<byte[]> keys = capturedKeys.get(algo);
        if (keys == null || keys.isEmpty()) return;
        for (byte[] key : keys) {
            // 校验密钥长度
            if (algo.equals("AES") && key.length != 16 && key.length != 24 && key.length != 32) continue;
            if (algo.equals("DES") && key.length != 8) continue;
            for (String transform : transforms) {
                try {
                    Cipher cipher = Cipher.getInstance(transform);
                    SecretKeySpec keySpec = new SecretKeySpec(key, algo);
                    if (transform.contains("CBC")) {
                        byte[] iv = new byte[algo.equals("AES") ? 16 : 8];
                        System.arraycopy(key, 0, iv, 0, Math.min(key.length, iv.length));
                        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
                    } else {
                        cipher.init(Cipher.DECRYPT_MODE, keySpec);
                    }
                    byte[] decrypted = cipher.doFinal(data);
                    if (decrypted != null && decrypted.length > 0 && (looksLikeWebContent(decrypted) || isReadableText(decrypted))) {
                        result.attempts.add(new DecryptResult(algo + "(" + transform + ")",
                                decrypted, "使用捕获的 " + algo + " 密钥解密成功"));
                        result.logs.add("✅ 方案5 " + algo + "(" + transform + "): 解密成功 (" + decrypted.length + " 字节)");
                        return;
                    }
                } catch (Exception ignore) {}
            }
        }
        result.logs.add("ℹ️ 方案5 " + algo + ": 捕获密钥未能解出可读内容");
    }

    private static void tryRc4CommonKeys(byte[] data, DumpResult result) {
        String[] commonKeys = {"123456", "webdecrypt", "secret", "default", "admin", "key"};
        for (String k : commonKeys) {
            try {
                byte[] decrypted = rc4(data, k.getBytes(Charset.forName("UTF-8")));
                if (decrypted != null && looksLikeWebContent(decrypted)) {
                    result.attempts.add(new DecryptResult("RC4(密钥\"" + k + "\")", decrypted, "RC4 解密成功"));
                    result.logs.add("✅ 方案6 RC4: 命中密钥 \"" + k + "\" (" + decrypted.length + " 字节)");
                    return;
                }
            } catch (Exception ignore) {}
        }
        result.logs.add("ℹ️ 方案6 RC4: 常见口令未命中");
    }

    /** RC4 实现 */
    public static byte[] rc4(byte[] data, byte[] key) {
        if (data == null || key == null || key.length == 0) return null;
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) s[i] = i;
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + s[i] + (key[i % key.length] & 0xff)) & 0xff;
            int tmp = s[i]; s[i] = s[j]; s[j] = tmp;
        }
        byte[] out = new byte[data.length];
        int i = 0, jj = 0;
        for (int n = 0; n < data.length; n++) {
            i = (i + 1) & 0xff;
            jj = (jj + s[i]) & 0xff;
            int tmp = s[i]; s[i] = s[jj]; s[jj] = tmp;
            int k = s[(s[i] + s[jj]) & 0xff];
            out[n] = (byte) (data[n] ^ k);
        }
        return out;
    }

    /** 异或工具 */
    public static byte[] xorBytes(byte[] data, byte[] key) {
        if (data == null || key == null || key.length == 0) return null;
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) out[i] = (byte) (data[i] ^ key[i % key.length]);
        return out;
    }

    /** 生成数据指纹（CRC32），用于去重 */
    public static String fingerprint(byte[] data) {
        if (data == null) return "0";
        CRC32 crc = new CRC32();
        int len = Math.min(data.length, 8192);
        crc.update(data, 0, len);
        return Long.toHexString(crc.getValue()) + "_" + data.length;
    }

    /** 生成头部 hex 摘要，便于日志展示 */
    public static String headHex(byte[] data) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, 16); i++) {
            sb.append(String.format("%02x", data[i] & 0xff));
        }
        return sb.toString();
    }
}
