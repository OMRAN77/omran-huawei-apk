package com.omran.aibuilder.twa;

// v-native-webview: بعد ثلاث محاولات مع مسار TWA على جهاز المالك (هواوي)
// والانهيار مستمر بلا تقرير — شاشة WebView أصلية بسيطة بلا أي منطق
// androidbrowserhelper عند الإقلاع: تفتح الموقع مباشرة على أي جهاز.

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class OmranWebActivity extends Activity {

    private static final String HOME_URL = "https://omran-ai-builder.vercel.app/?store=play";
    private static final String HOME_HOST = "omran-ai-builder.vercel.app";
    private static final int FILE_PICK = 71;

    private static final int PERM_REQ = 72;

    private WebView web;
    private ValueCallback<Uri[]> pendingFilePick;
    private PermissionRequest pendingWebPermission;

    private static String[] neededAndroidPerms(PermissionRequest request) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) out.add(android.Manifest.permission.CAMERA);
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) out.add(android.Manifest.permission.RECORD_AUDIO);
        }
        return out.toArray(new String[0]);
    }

    private boolean hasPerm(String p) {
        return android.os.Build.VERSION.SDK_INT < 23
            || checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void handleWebPermission(PermissionRequest request) {
        try {
            String[] need = neededAndroidPerms(request);
            if (need.length == 0) { request.grant(request.getResources()); return; }
            boolean all = true;
            for (String p : need) if (!hasPerm(p)) { all = false; break; }
            if (all) { request.grant(request.getResources()); return; }
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                if (pendingWebPermission != null) { try { pendingWebPermission.deny(); } catch (Throwable ignored) { } }
                pendingWebPermission = request;
                requestPermissions(need, PERM_REQ);
            } else {
                request.grant(request.getResources());
            }
        } catch (Throwable e) {
            try { request.deny(); } catch (Throwable ignored) { }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != PERM_REQ) { super.onRequestPermissionsResult(code, perms, results); return; }
        PermissionRequest req = pendingWebPermission;
        pendingWebPermission = null;
        if (req == null) return;
        boolean any = false;
        for (int r : results) if (r == android.content.pm.PackageManager.PERMISSION_GRANTED) { any = true; break; }
        try {
            if (any) req.grant(req.getResources()); else req.deny();
        } catch (Throwable ignored) { }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        // v-ua-tag: نلحق رقم إصدار التطبيق بالـ user-agent حتى تُظهر تقارير
        // الأخطاء من الموقع أي نسخة أندرويد مثبّتة بالضبط
        try {
            int vc = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            s.setUserAgentString(s.getUserAgentString() + " OmranApp/" + vc);
        } catch (Throwable ignored) { }
        CookieManager.getInstance().setAcceptCookie(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                // v-app-settings: الموقع يطلب فتح إعدادات التطبيق (لمنح إذن
                // الكاميرا يدويًا بعد رفضه) عبر omran-app://settings
                if (scheme.equals("omran-app")) {
                    try {
                        startActivity(new Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())));
                    } catch (Throwable ignored) { }
                    return true;
                }
                if (!scheme.equals("http") && !scheme.equals("https")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Throwable ignored) { }
                    return true;
                }
                // نفس الموقع داخل التطبيق؛ روابط خارجية للمتصفح
                if (HOME_HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Throwable ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // v-cam-mic: الويب لا يُمنح كاميرا/مايك إلا إذا كان التطبيق
                // نفسه حاصلًا على صلاحية أندرويد المقابلة — نطلبها من
                // المستخدم عند أول حاجة ثم نمنح الصفحة.
                runOnUiThread(() -> handleWebPermission(request));
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (pendingFilePick != null) { pendingFilePick.onReceiveValue(null); }
                pendingFilePick = cb;
                try {
                    startActivityForResult(p.createIntent(), FILE_PICK);
                } catch (Throwable e) {
                    pendingFilePick = null;
                    return false;
                }
                return true;
            }
        });

        String url = HOME_URL;
        Uri data = getIntent() != null ? getIntent().getData() : null;
        if (data != null && HOME_HOST.equals(data.getHost())) url = data.toString();
        web.loadUrl(url);
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == FILE_PICK && pendingFilePick != null) {
            pendingFilePick.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            pendingFilePick = null;
            return;
        }
        super.onActivityResult(code, result, data);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        try { if (web != null) web.destroy(); } catch (Throwable ignored) { }
        super.onDestroy();
    }
}
