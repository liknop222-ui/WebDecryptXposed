package com.webdecrypt.xposed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BuiltInScripts {

    public static final String CAT_DEBUG = "🔍 调试";
    public static final String CAT_MODIFY = "✏️ 修改";
    public static final String CAT_DOWNLOAD = "📥 下载";
    public static final String CAT_NAV = "🧭 导航";
    public static final String CAT_UNLOCK = "🔓 解锁";
    public static final String CAT_TOOL = "🔧 工具";

    public static class ScriptItem {
        public final String name;
        public final String category;
        public final String description;
        public final String code;

        public ScriptItem(String name, String category, String description, String code) {
            this.name = name;
            this.category = category;
            this.description = description;
            this.code = code;
        }
    }

    public static List<ScriptItem> getAllScripts() {
        List<ScriptItem> scripts = new ArrayList<>();
        addDebugScripts(scripts);
        addModifyScripts(scripts);
        addDownloadScripts(scripts);
        addNavScripts(scripts);
        addUnlockScripts(scripts);
        addToolScripts(scripts);
        return scripts;
    }

    public static Map<String, List<ScriptItem>> getScriptsByCategory() {
        Map<String, List<ScriptItem>> map = new LinkedHashMap<>();
        for (ScriptItem item : getAllScripts()) {
            if (!map.containsKey(item.category)) {
                map.put(item.category, new ArrayList<>());
            }
            map.get(item.category).add(item);
        }
        return map;
    }

    private static void addDebugScripts(List<ScriptItem> s) {
        s.add(new ScriptItem("DOM查看器", CAT_DEBUG, "查看页面DOM结构和节点统计",
            "(function(){var n=document.querySelectorAll('*'),d=0;function g(e,c){if(c>d)d=c;for(var i=0;i<e.children.length;i++)g(e.children[i],c+1)}g(document.documentElement,0);var t=document.title,u=location.href,s=document.querySelectorAll('script').length,c=document.querySelectorAll('link[rel=stylesheet]').length,i=document.querySelectorAll('img').length,a=document.querySelectorAll('a').length;window.__wd_android.log('📄 '+t+'\\n🔗 '+u+'\\n📊 节点:'+n.length+' 深度:'+d+'\\n📜 脚本:'+s+' 样式:'+c+' 🖼 图片:'+i+' 🔗 链接:'+a);})();"));

        s.add(new ScriptItem("事件监听器", CAT_DEBUG, "检测所有绑定的事件监听器",
            "(function(){var es=['click','dblclick','mousedown','mouseup','mouseover','mouseout','mousemove','keydown','keyup','keypress','submit','change','focus','blur','scroll','resize','touchstart','touchend','touchmove','input','load','error'];var r={};es.forEach(function(e){var h=window._eventListeners||[];var c=document.querySelectorAll('[on'+e+']');if(c.length>0)r[e]=c.length});var s=Object.keys(r).map(function(k){return k+':'+r[k]}).join(', ');window.__wd_android.log('🎯 事件监听: '+s);})();"));

        s.add(new ScriptItem("Cookie查看器", CAT_DEBUG, "查看当前页面所有Cookie",
            "(function(){var c=document.cookie;if(!c){window.__wd_android.log('🍪 无Cookie');return}var pairs=c.split(';');var r='🍪 Cookie ('+pairs.length+'个):\\n';pairs.forEach(function(p){var kv=p.trim().split('=');r+=kv[0]+' = '+(kv[1]||'')+'\\n'});window.__wd_android.log(r);})();"));

        s.add(new ScriptItem("LocalStorage查看", CAT_DEBUG, "查看LocalStorage所有键值",
            "(function(){var r='💾 LocalStorage ('+localStorage.length+'项):\\n';for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);var v=localStorage.getItem(k);if(v&&v.length>100)v=v.substring(0,100)+'...';r+=k+' = '+v+'\\n'}window.__wd_android.log(r);})();"));

        s.add(new ScriptItem("SessionStorage查看", CAT_DEBUG, "查看SessionStorage所有键值",
            "(function(){var r='📦 SessionStorage ('+sessionStorage.length+'项):\\n';for(var i=0;i<sessionStorage.length;i++){var k=sessionStorage.key(i);var v=sessionStorage.getItem(k);if(v&&v.length>100)v=v.substring(0,100)+'...';r+=k+' = '+v+'\\n'}window.__wd_android.log(r);})();"));

        s.add(new ScriptItem("网络请求监控", CAT_DEBUG, "Hook XMLHttpRequest和Fetch监控所有网络请求",
            "(function(){var _xo=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u){window.__wd_android.log('📡 XHR: '+m+' '+u);return _xo.apply(this,arguments)};var _xs=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.send=function(b){if(b)window.__wd_android.log('📤 XHR Body: '+(b.toString().substring(0,200)));return _xs.apply(this,arguments)};if(window.fetch){var _f=window.fetch;window.fetch=function(u,o){window.__wd_android.log('📡 Fetch: '+(typeof u==='string'?u:u.url));return _f.apply(this,arguments)}}window.__wd_android.log('✅ 网络监控已启动');})();"));

        s.add(new ScriptItem("Console日志捕获", CAT_DEBUG, "捕获所有console.log/warn/error输出",
            "(function(){var _l=console.log,_w=console.warn,_e=console.error;console.log=function(){_l.apply(console,arguments);window.__wd_android.log('[LOG] '+Array.from(arguments).join(' '))};console.warn=function(){_w.apply(console,arguments);window.__wd_android.log('[WARN] '+Array.from(arguments).join(' '))};console.error=function(){_e.apply(console,arguments);window.__wd_android.log('[ERR] '+Array.from(arguments).join(' '))};window.__wd_android.log('✅ Console捕获已启动');})();"));

        s.add(new ScriptItem("性能分析", CAT_DEBUG, "分析页面加载性能和资源耗时",
            "(function(){var p=performance.timing;var r='⚡ 性能分析:\\n';r+='DNS: '+(p.domainLookupEnd-p.domainLookupStart)+'ms\\n';r+='TCP: '+(p.connectEnd-p.connectStart)+'ms\\n';r+='请求: '+(p.responseStart-p.requestStart)+'ms\\n';r+='响应: '+(p.responseEnd-p.responseStart)+'ms\\n';r+='DOM解析: '+(p.domComplete-p.domLoading)+'ms\\n';r+='总加载: '+(p.loadEventEnd-p.navigationStart)+'ms\\n';var res=performance.getEntriesByType('resource');var slow=res.filter(function(e){return e.duration>500});r+='慢资源(>500ms): '+slow.length+'\\n';slow.forEach(function(e){r+='  ⚠️ '+e.name.substring(0,80)+' '+Math.round(e.duration)+'ms\\n'});window.__wd_android.log(r);})();"));
    }

    private static void addModifyScripts(List<ScriptItem> s) {
        s.add(new ScriptItem("编辑模式", CAT_MODIFY, "开启页面直接编辑模式，可修改任意文字",
            "document.body.contentEditable=!document.body.contentEditable;document.designMode=document.body.contentEditable?'on':'off';window.__wd_android.log(document.body.contentEditable?'✅ 编辑模式已开启':'❌ 编辑模式已关闭');"));

        s.add(new ScriptItem("显示隐藏元素", CAT_MODIFY, "显示所有display:none和visibility:hidden的元素",
            "(function(){var h=document.querySelectorAll('[style*=\"display:none\"],[style*=\"display: none\"],[hidden]');var v=document.querySelectorAll('[style*=\"visibility:hidden\"],[style*=\"visibility: hidden\"]');h.forEach(function(e){e.style.display='block';e.style.border='2px dashed red'});v.forEach(function(e){e.style.visibility='visible';e.style.border='2px dashed orange'});window.__wd_android.log('✅ 显示隐藏元素: display:none='+h.length+' visibility:hidden='+v.length);})();"));

        s.add(new ScriptItem("移除遮罩层", CAT_MODIFY, "移除全屏遮罩、弹窗遮罩等",
            "(function(){var r=0;document.querySelectorAll('*').forEach(function(e){var s=getComputedStyle(e);if(s.position==='fixed'&&(s.backgroundColor==='rgba(0, 0, 0, 0.5)'||s.backgroundColor==='rgba(0, 0, 0, 0.6)'||s.backgroundColor==='rgba(0, 0, 0, 0.7)'||s.backgroundColor==='rgba(0, 0, 0, 0.8)')){e.remove();r++}if(s.position==='fixed'&&e.style.zIndex>999){e.style.display='none';r++}});window.__wd_android.log('✅ 移除遮罩: '+r+'个');})();"));

        s.add(new ScriptItem("禁用右键限制", CAT_MODIFY, "移除右键菜单限制和文字选择限制",
            "(function(){document.oncontextmenu=null;document.onselectstart=null;document.ondragstart=null;document.oncopy=null;document.oncut=null;document.onpaste=null;document.querySelectorAll('*').forEach(function(e){e.style.userSelect='auto';e.style.webkitUserSelect='auto';e.style.MozUserSelect='auto'});window.__wd_android.log('✅ 右键和选择限制已移除');})();"));

        s.add(new ScriptItem("修改User-Agent", CAT_MODIFY, "切换User-Agent为桌面Chrome",
            "(function(){Object.defineProperty(navigator,'userAgent',{get:function(){return'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}});Object.defineProperty(navigator,'platform',{get:function(){return'Win32'}});window.__wd_android.log('✅ UA已切换为桌面Chrome');})();"));

        s.add(new ScriptItem("注入jQuery", CAT_MODIFY, "动态注入jQuery库以便调试",
            "(function(){if(window.jQuery){window.__wd_android.log('jQuery已存在: v'+jQuery.fn.jquery);return}var s=document.createElement('script');s.src='https://cdn.jsdelivr.net/npm/jquery@3.7.1/dist/jquery.min.js';s.onload=function(){window.__wd_android.log('✅ jQuery '+jQuery.fn.jquery+' 已注入')};document.head.appendChild(s);})();"));

        s.add(new ScriptItem("修改Viewport", CAT_MODIFY, "修改viewport为桌面宽度",
            "(function(){var vp=document.querySelector('meta[name=viewport]');if(vp){vp.content='width=1280, initial-scale=1'}else{vp=document.createElement('meta');vp.name='viewport';vp.content='width=1280, initial-scale=1';document.head.appendChild(vp)}window.__wd_android.log('✅ Viewport已修改为1280px');})();"));

        s.add(new ScriptItem("强制暗黑模式", CAT_MODIFY, "为页面添加暗黑模式滤镜",
            "(function(){if(document.getElementById('__wd_dark')){document.getElementById('__wd_dark').remove();window.__wd_android.log('❌ 暗黑模式已关闭');return}var s=document.createElement('style');s.id='__wd_dark';s.textContent='html{filter:invert(0.9) hue-rotate(180deg)!important}img,video,canvas{filter:invert(1) hue-rotate(180deg)!important}';document.head.appendChild(s);window.__wd_android.log('✅ 暗黑模式已开启');})();"));
    }

    private static void addDownloadScripts(List<ScriptItem> s) {
        s.add(new ScriptItem("提取所有图片", CAT_DOWNLOAD, "提取页面中所有图片URL",
            "(function(){var imgs=document.querySelectorAll('img');var bgs=document.querySelectorAll('[style*=\"background-image\"]');var r='🖼 图片 ('+imgs.length+'张):\\n';var seen=new Set();imgs.forEach(function(i){var s=i.src||i.dataset.src||i.dataset.original;if(s&&!seen.has(s)){seen.add(s);r+=s+'\\n'}});bgs.forEach(function(e){var bg=getComputedStyle(e).backgroundImage;if(bg&&bg!=='none'){var m=bg.match(/url\\([\"']?(.+?)[\"']?\\)/);if(m&&!seen.has(m[1])){seen.add(m[1]);r+=m[1]+'\\n'}}});window.__wd_android.log(r);})();"));

        s.add(new ScriptItem("提取所有链接", CAT_DOWNLOAD, "提取页面中所有超链接",
            "(function(){var links=document.querySelectorAll('a[href]');var r='🔗 链接 ('+links.length+'个):\\n';var seen=new Set();links.forEach(function(a){var h=a.href;if(h&&!h.startsWith('javascript:')&&!seen.has(h)){seen.add(h);r+=h+'\\n'}});window.__wd_android.log(r);})();"));

        s.add(new ScriptItem("提取视频源", CAT_DOWNLOAD, "提取页面中所有视频源地址",
            "(function(){var r='🎬 视频源:\\n';document.querySelectorAll('video').forEach(function(v){if(v.src)r+='video.src: '+v.src+'\\n';v.querySelectorAll('source').forEach(function(s){r+='source: '+s.src+' ('+s.type+')\\n'})});document.querySelectorAll('iframe').forEach(function(f){r+='iframe: '+f.src+'\\n'});if(r==='🎬 视频源:\\n')r+='未找到视频';window.__wd_android.log(r);})();"));

        s.add(new ScriptItem("提取音频源", CAT_DOWNLOAD, "提取页面中所有音频源地址",
            "(function(){var r='🎵 音频源:\\n';document.querySelectorAll('audio').forEach(function(a){if(a.src)r+='audio.src: '+a.src+'\\n';a.querySelectorAll('source').forEach(function(s){r+='source: '+s.src+' ('+s.type+')\\n'})});if(r==='🎵 音频源:\\n')r+='未找到音频';window.__wd_android.log(r);})();"));

        s.add(new ScriptItem("提取CSS样式", CAT_DOWNLOAD, "提取页面所有CSS样式表内容",
            "(function(){var r='🎨 CSS样式:\\n';document.querySelectorAll('style').forEach(function(s,i){r+='--- 内联样式 #'+(i+1)+' ---\\n';r+=s.textContent.substring(0,500)+'\\n'});document.querySelectorAll('link[rel=stylesheet]').forEach(function(l,i){r+='外部样式 #'+(i+1)+': '+l.href+'\\n'});window.__wd_android.log(r);})();"));

        s.add(new ScriptItem("提取JS代码", CAT_DOWNLOAD, "提取页面所有内联JavaScript代码",
            "(function(){var r='📜 JS代码:\\n';document.querySelectorAll('script').forEach(function(s,i){if(s.src){r+='外部 #'+(i+1)+': '+s.src+'\\n'}else if(s.textContent.trim()){r+='--- 内联 #'+(i+1)+' ('+s.textContent.length+'字符) ---\\n';r+=s.textContent.substring(0,300)+'\\n'}});window.__wd_android.log(r);})();"));
    }

    private static void addNavScripts(List<ScriptItem> s) {
        s.add(new ScriptItem("页面元素高亮", CAT_NAV, "鼠标悬停时高亮显示元素信息",
            "(function(){if(window.__wd_hl){document.removeEventListener('mouseover',window.__wd_hl);document.removeEventListener('mouseout',window.__wd_hlo);document.getElementById('__wd_hl_box')&&document.getElementById('__wd_hl_box').remove();window.__wd_hl=null;window.__wd_android.log('❌ 高亮已关闭');return}var box=document.createElement('div');box.id='__wd_hl_box';box.style.cssText='position:fixed;z-index:999999;pointer-events:none;border:2px solid red;background:rgba(255,0,0,0.1);transition:all 0.05s';document.body.appendChild(box);window.__wd_hl=function(e){var t=e.target;var r=t.getBoundingClientRect();box.style.left=r.left+'px';box.style.top=r.top+'px';box.style.width=r.width+'px';box.style.height=r.height+'px';window.__wd_android.log('<'+t.tagName.toLowerCase()+'>'+(t.id?'#'+t.id:'')+(t.className?'.'+t.className.split(' ').join('.'):'')+' '+Math.round(r.width)+'x'+Math.round(r.height))};window.__wd_hlo=function(){box.style.left='-9999px'};document.addEventListener('mouseover',window.__wd_hl);document.addEventListener('mouseout',window.__wd_hlo);window.__wd_android.log('✅ 元素高亮已开启');})();"));

        s.add(new ScriptItem("表单自动填充", CAT_NAV, "自动填充所有表单输入框",
            "(function(){var c=0;document.querySelectorAll('input').forEach(function(i){var t=i.type;if(t==='text'||t==='search'||t==='email')i.value='test_'+Math.random().toString(36).substring(7);else if(t==='password')i.value='Test@123456';else if(t==='number')i.value='100';else if(t==='tel')i.value='13800138000';else if(t==='url')i.value='https://example.com';else if(t==='checkbox'||t==='radio')i.checked=true;c++});document.querySelectorAll('textarea').forEach(function(t){t.value='测试内容 '+new Date().toLocaleString();c++});document.querySelectorAll('select').forEach(function(s){if(s.options.length>1)s.selectedIndex=1;c++});window.__wd_android.log('✅ 填充了'+c+'个表单元素');})();"));

        s.add(new ScriptItem("滚动到页面底部", CAT_NAV, "自动滚动到页面最底部",
            "(function(){var h=document.documentElement.scrollHeight;var y=0;var t=setInterval(function(){y+=200;window.scrollTo(0,y);if(y>=h){clearInterval(t);window.__wd_android.log('✅ 已滚动到底部')}},50);})();"));

        s.add(new ScriptItem("查找文本", CAT_NAV, "在页面中搜索指定文本并高亮",
            "(function(){var q=prompt('搜索文本:','');if(!q)return;document.querySelectorAll('.__wd_hl').forEach(function(m){m.classList.remove('__wd_hl');m.style.background='';m.style.color=''});var w=new RegExp('('+q.replace(/[.*+?^${}()|[\\]\\\\]/g,'\\\\$&')+')','gi');var n=0;function h(e){if(e.nodeType===3&&w.test(e.textContent)){var sp=document.createElement('span');sp.innerHTML=e.textContent.replace(w,'<span class=\"__wd_hl\" style=\"background:yellow;color:black\">$1</span>');e.parentNode.replaceChild(sp,e);n++}};function walk(e){h(e);for(var i=0;i<e.childNodes.length;i++)walk(e.childNodes[i])}walk(document.body);window.__wd_android.log('🔍 找到 '+n+' 处匹配');})();"));

        s.add(new ScriptItem("页面截图标记", CAT_NAV, "标记页面中所有可点击元素",
            "(function(){var c=0;document.querySelectorAll('a,button,[onclick],input[type=submit],input[type=button]').forEach(function(e){e.style.outline='2px solid lime';e.style.outlineOffset='2px';c++});window.__wd_android.log('✅ 标记了'+c+'个可点击元素');})();"));
    }

    private static void addUnlockScripts(List<ScriptItem> s) {
        s.add(new ScriptItem("VIP内容解锁", CAT_UNLOCK, "尝试移除VIP/付费内容遮罩和限制",
            "(function(){var r=0;document.querySelectorAll('[class*=vip],[class*=VIP],[class*=pay],[class*=lock],[class*=premium],[class*=member],[class*=subscribe]').forEach(function(e){e.style.display='block';e.style.visibility='visible';e.style.filter='none';e.style.opacity='1';e.style.height='auto';e.style.overflow='visible';e.style.position='static';e.className='';r++});document.querySelectorAll('[style*=\"filter: blur\"],[style*=\"filter:blur\"]').forEach(function(e){e.style.filter='none';r++});document.querySelectorAll('[style*=\"-webkit-text-security\"]').forEach(function(e){e.style.webkitTextSecurity='none';r++});window.__wd_android.log('✅ 解锁尝试: '+r+'个元素');})();"));

        s.add(new ScriptItem("阅读模式", CAT_UNLOCK, "提取正文内容，进入纯净阅读模式",
            "(function(){var a=document.querySelector('article')||document.querySelector('[role=main]')||document.querySelector('.content')||document.querySelector('#content')||document.querySelector('main');var t=a?a.innerText:document.body.innerText;var w=window.open('','_blank');w.document.write('<html><head><meta name=viewport content=\"width=device-width,initial-scale=1\"><style>body{font-family:system-ui;max-width:800px;margin:0 auto;padding:20px;line-height:1.8;font-size:16px;color:#333}h1{color:#1a1a2e}</style></head><body><h1>'+document.title+'</h1>'+t.replace(/\\n/g,'<br>')+'</body></html>');w.document.close();window.__wd_android.log('✅ 阅读模式已开启');})();"));

        s.add(new ScriptItem("禁用弹窗", CAT_UNLOCK, "阻止所有alert/confirm/prompt弹窗",
            "(function(){window.__wd_alert=window.alert;window.__wd_confirm=window.confirm;window.__wd_prompt=window.prompt;window.alert=function(m){window.__wd_android.log('🚫 Alert: '+m);return undefined};window.confirm=function(m){window.__wd_android.log('🚫 Confirm: '+m);return true};window.prompt=function(m,d){window.__wd_android.log('🚫 Prompt: '+m);return d||''};window.__wd_android.log('✅ 弹窗已禁用');})();"));

        s.add(new ScriptItem("恢复弹窗", CAT_UNLOCK, "恢复alert/confirm/prompt弹窗",
            "(function(){if(window.__wd_alert)window.alert=window.__wd_alert;if(window.__wd_confirm)window.confirm=window.__wd_confirm;if(window.__wd_prompt)window.prompt=window.__wd_prompt;window.__wd_android.log('✅ 弹窗已恢复');})();"));

        s.add(new ScriptItem("禁用跳转", CAT_UNLOCK, "阻止页面自动跳转和重定向",
            "(function(){window.__wd_assign=location.assign;window.__wd_replace=location.replace;location.assign=function(u){window.__wd_android.log('🚫 阻止跳转: '+u)};location.replace=function(u){window.__wd_android.log('🚫 阻止替换: '+u)};Object.defineProperty(window,'onbeforeunload',{set:function(){},get:function(){return null}});window.__wd_android.log('✅ 跳转已禁用');})();"));

        s.add(new ScriptItem("反反调试", CAT_UNLOCK, "绕过反调试检测(debugger语句)",
            "(function(){var _f=Function.prototype.constructor;Function.prototype.constructor=function(){if(arguments&&arguments[0]==='debugger')return function(){};return _f.apply(this,arguments)};var _st=window.setTimeout;window.setTimeout=function(fn,t){if(typeof fn==='string'&&fn.indexOf('debugger')!==-1)return 0;return _st.apply(this,arguments)};var _si=window.setInterval;window.setInterval=function(fn,t){if(typeof fn==='string'&&fn.indexOf('debugger')!==-1)return 0;return _si.apply(this,arguments)};window.__wd_android.log('✅ 反反调试已启用');})();"));
    }

    private static void addToolScripts(List<ScriptItem> s) {
        s.add(new ScriptItem("页面信息", CAT_TOOL, "获取页面完整信息(标题/URL/编码/大小)",
            "(function(){var r='📄 页面信息:\\n';r+='标题: '+document.title+'\\n';r+='URL: '+location.href+'\\n';r+='协议: '+location.protocol+'\\n';r+='域名: '+location.hostname+'\\n';r+='编码: '+document.characterSet+'\\n';r+='MIME: '+document.contentType+'\\n';r+='模式: '+document.compatMode+'\\n';r+='大小: '+(document.documentElement.outerHTML.length/1024).toFixed(1)+' KB\\n';r+='节点: '+document.querySelectorAll('*').length+'\\n';r+='表单: '+document.querySelectorAll('form').length+'\\n';r+='iframe: '+document.querySelectorAll('iframe').length+'\\n';window.__wd_android.log(r);})();"));

        s.add(new ScriptItem("颜色拾取器", CAT_TOOL, "点击元素获取其颜色值",
            "(function(){window.__wd_cp=function(e){e.preventDefault();e.stopPropagation();var t=e.target;var s=getComputedStyle(t);var bg=s.backgroundColor;var fg=s.color;window.__wd_android.log('🎨 颜色: bg='+bg+' fg='+fg+' tag=<'+t.tagName+'>');document.removeEventListener('click',window.__wd_cp,true)};document.addEventListener('click',window.__wd_cp,true);window.__wd_android.log('✅ 点击元素拾取颜色');})();"));

        s.add(new ScriptItem("清除所有Cookie", CAT_TOOL, "清除当前域名下所有Cookie",
            "(function(){var c=document.cookie.split(';');c.forEach(function(p){var k=p.split('=')[0].trim();document.cookie=k+'=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/';document.cookie=k+'=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/;domain='+location.hostname});window.__wd_android.log('✅ 清除了'+c.length+'个Cookie');})();"));

        s.add(new ScriptItem("清除存储", CAT_TOOL, "清除LocalStorage和SessionStorage",
            "(function(){var ls=localStorage.length;var ss=sessionStorage.length;localStorage.clear();sessionStorage.clear();window.__wd_android.log('✅ 清除 LocalStorage:'+ls+' SessionStorage:'+ss);})();"));

        s.add(new ScriptItem("强制重载", CAT_TOOL, "强制刷新页面(忽略缓存)",
            "location.reload(true);window.__wd_android.log('✅ 强制重载');"));

        s.add(new ScriptItem("查看源码", CAT_TOOL, "在弹窗中查看当前页面完整HTML源码",
            "(function(){var h=document.documentElement.outerHTML;window.__wd_android.captureHtml(h);window.__wd_android.log('✅ 源码已捕捉 ('+h.length+'字符)');})();"));

        s.add(new ScriptItem("API接口探测", CAT_TOOL, "探测页面中可能存在的API接口",
            "(function(){var r='🔌 API探测:\\n';var urls=new Set();document.querySelectorAll('script').forEach(function(s){var m=s.textContent.match(/['\"]\\/(api|v[0-9])\\/[^'\"]+['\"]/g);if(m)m.forEach(function(u){urls.add(u.replace(/[\"']/g,''))})});document.querySelectorAll('[data-url],[data-api],[data-src]').forEach(function(e){if(e.dataset.url)urls.add(e.dataset.url);if(e.dataset.api)urls.add(e.dataset.api)});if(urls.size===0)r+='未发现明显API';else urls.forEach(function(u){r+=u+'\\n'});window.__wd_android.log(r);})();"));

        s.add(new ScriptItem("WebSocket监控", CAT_TOOL, "监控WebSocket连接和消息",
            "(function(){var _ws=window.WebSocket;window.WebSocket=function(u,p){var ws=p?new _ws(u,p):new _ws(u);window.__wd_android.log('🔌 WS连接: '+u);ws.addEventListener('message',function(e){window.__wd_android.log('📥 WS消息: '+(e.data.toString().substring(0,200)))});ws.addEventListener('open',function(){window.__wd_android.log('✅ WS已连接: '+u)});return ws};window.WebSocket.prototype=_ws.prototype;window.__wd_android.log('✅ WebSocket监控已启动');})();"));

        s.add(new ScriptItem("时间加速", CAT_TOOL, "加速定时器(用于跳过等待/倒计时)",
            "(function(){var _st=window.setTimeout;var _si=window.setInterval;window.setTimeout=function(fn,t){if(t>1000)t=100;return _st.call(window,fn,t)};window.setInterval=function(fn,t){if(t>1000)t=100;return _si.call(window,fn,t)};window.__wd_android.log('✅ 时间加速已启用(>1s→100ms)');})();"));

        s.add(new ScriptItem("编码解码工具", CAT_TOOL, "对选中文本进行Base64/URL编解码",
            "(function(){var s=window.getSelection().toString();if(!s){s=prompt('输入要处理的文本:','');if(!s)return}var r='🔧 编码解码:\\n';r+='原文: '+s+'\\n';try{r+='Base64编码: '+btoa(unescape(encodeURIComponent(s)))+'\\n'}catch(e){r+='Base64编码: 失败\\n'}try{r+='Base64解码: '+decodeURIComponent(escape(atob(s)))+'\\n'}catch(e){r+='Base64解码: 非Base64\\n'}r+='URL编码: '+encodeURIComponent(s)+'\\n';try{r+='URL解码: '+decodeURIComponent(s)+'\\n'}catch(e){r+='URL解码: 非URL编码\\n'}window.__wd_android.log(r);})();"));
    }
}
