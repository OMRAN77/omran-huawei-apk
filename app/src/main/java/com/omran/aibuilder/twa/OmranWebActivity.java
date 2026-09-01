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

    private static final String HOME_HOST = "omran-ai-builder.vercel.app";

    // v-store-detect: ملف واحد للمتجرين — نكتشف مصدر التثبيت وقت التشغيل:
    // AppGallery → وضع هواوي (يفعّل إخفاء المحتوى المالي المطلوب لسياساتهم)،
    // وكل ما عداه (Play أو تثبيت يدوي) → وضع Play كما كان.
    private String homeUrl() {
        String store = "play";
        // v-flavors: نكهة gallery تفرض وضع هواوي دائمًا؛ نكهة play تكتشف
        // التثبيت من AppGallery وقت التشغيل (احتياط).
        try { store = getString(com.omran.aibuilder.R.string.omranStore); }
        catch (Throwable ignored) { }
        if (!"huawei".equals(store)) {
            try {
                String inst = getPackageManager().getInstallerPackageName(getPackageName());
                if ("com.huawei.appmarket".equals(inst)) store = "huawei";
            } catch (Throwable ignored) { }
        }
        return "https://" + HOME_HOST + "/?store=" + store;
    }
    private static final int FILE_PICK = 71;

    private static final int PERM_REQ = 72;
    // v-attach-camera: إذن الكاميرا قبل فتح منتقي الإرفاق (حتى يظهر خيار الكاميرا)
    private static final int PERM_CAM_PICK = 73;
    // v-geo: إذن الموقع للويب (مواقيت الصلاة والقبلة)
    private static final int PERM_GEO = 74;

    private WebView web;
    private ValueCallback<Uri[]> pendingFilePick;
    private PermissionRequest pendingWebPermission;
    private WebChromeClient.FileChooserParams pendingChooserParams;
    private Uri cameraOutputUri;
    private String pendingGeoOrigin;
    private android.webkit.GeolocationPermissions.Callback pendingGeoCallback;

    // v-attach-camera: منتقي createIntent() وحده منتقي مستندات بلا كاميرا —
    // في TWA القديم كان كروم يضيف الكاميرا تلقائيًا، وفي WebView علينا نحن:
    // نضيف نية ACTION_IMAGE_CAPTURE تكتب لملف مؤقت عبر الـFileProvider القائم.
    private void launchFileChooser(WebChromeClient.FileChooserParams p) {
        Intent content;
        try { content = p.createIntent(); }
        catch (Throwable e) {
            content = new Intent(Intent.ACTION_GET_CONTENT);
            content.addCategory(Intent.CATEGORY_OPENABLE);
            content.setType("*/*");
        }
        Intent camera = null;
        cameraOutputUri = null;
        // المانيفست يعلن CAMERA، وأندرويد يمنع ACTION_IMAGE_CAPTURE حينها
        // ما لم تكن الصلاحية ممنوحة فعلًا — بدونها نُظهر المنتقي العادي فقط.
        if (hasPerm(android.Manifest.permission.CAMERA)) {
            try {
                java.io.File dir = new java.io.File(getCacheDir(), "camera");
                dir.mkdirs();
                java.io.File photo = new java.io.File(dir, "cap_" + System.currentTimeMillis() + ".jpg");
                photo.createNewFile();
                cameraOutputUri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photo);
                camera = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                camera.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraOutputUri);
                camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                camera.setClipData(android.content.ClipData.newRawUri("camera", cameraOutputUri));
                // v-cam-grant (لقطة «cap_*.jpg | no-decodable»): أعلام المنح على
                // نية داخل EXTRA_INITIAL_INTENTS لا تصل تطبيق الكاميرا — فيكتب
                // في الفراغ ويرجع ملفًا صفريًا. نمنح الصلاحية صراحةً لكل
                // تطبيقات الكاميرا المرشحة.
                try {
                    java.util.List<android.content.pm.ResolveInfo> ris = getPackageManager()
                        .queryIntentActivities(camera, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
                    for (android.content.pm.ResolveInfo ri : ris) {
                        grantUriPermission(ri.activityInfo.packageName, cameraOutputUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    }
                } catch (Throwable ignored) { }
            } catch (Throwable e) { camera = null; cameraOutputUri = null; }
        }
        try {
            Intent toStart;
            if (camera != null && p.isCaptureEnabled()) {
                // <input capture> يريد الكاميرا مباشرة بلا منتقي
                toStart = camera;
            } else {
                toStart = Intent.createChooser(content, null);
                if (camera != null) toStart.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { camera });
            }
            startActivityForResult(toStart, FILE_PICK);
        } catch (Throwable e) {
            if (pendingFilePick != null) { pendingFilePick.onReceiveValue(null); pendingFilePick = null; }
            cameraOutputUri = null;
        }
    }

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
        if (code == PERM_CAM_PICK) {
            // مُنح أو رُفض — نفتح المنتقي على أي حال (الكاميرا تظهر فقط مع الإذن)
            WebChromeClient.FileChooserParams p = pendingChooserParams;
            pendingChooserParams = null;
            if (p != null && pendingFilePick != null) launchFileChooser(p);
            return;
        }
        if (code == PERM_GEO) {
            // v-geo: منح/رفض إذن أندرويد → نمرره لطلب الويب المعلّق
            boolean granted = false;
            for (int r : results) if (r == android.content.pm.PackageManager.PERMISSION_GRANTED) { granted = true; break; }
            if (pendingGeoCallback != null && pendingGeoOrigin != null) {
                pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
            }
            pendingGeoCallback = null; pendingGeoOrigin = null;
            return;
        }
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

    // v-android-share («مافيه مشاركة»): WebView بلا navigator.share ولا تنزيل
    // <a download> — الموقع يرسل الملف base64 لهذا الجسر فتفتح ورقة مشاركة
    // النظام الحقيقية (واتساب/حفظ في الملفات...).
    private class ShareBridge {
        @android.webkit.JavascriptInterface
        public void share(final String b64, final String name, final String mime) {
            new Thread(() -> {
                try {
                    byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                    java.io.File dir = new java.io.File(getCacheDir(), "share");
                    dir.mkdirs();
                    String safe = (name == null || name.trim().isEmpty())
                        ? "omran-file"
                        : name.replaceAll("[/\\\\:*?\"<>|]", "_");
                    java.io.File f = new java.io.File(dir, safe);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                    out.write(bytes);
                    out.close();
                    Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        OmranWebActivity.this, getPackageName() + ".fileprovider", f);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent chooser = Intent.createChooser(send, null);
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(chooser);
                } catch (Throwable ignored) { }
            }).start();
        }
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
        s.setGeolocationEnabled(true); // v-geo: مواقيت الصلاة والقبلة
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

            // v-geo: الموقع يطلب geolocation → نمنحه فقط إذا التطبيق يملك إذن
            // أندرويد، وإلا نطلبه من المستخدم أول مرة.
            @Override
            public void onGeolocationPermissionsShowPrompt(final String origin,
                    final android.webkit.GeolocationPermissions.Callback callback) {
                if (hasPerm(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        || hasPerm(android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    callback.invoke(origin, true, false);
                    return;
                }
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    requestPermissions(new String[] {
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION }, PERM_GEO);
                } else {
                    callback.invoke(origin, true, false);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (pendingFilePick != null) { pendingFilePick.onReceiveValue(null); }
                pendingFilePick = cb;
                // v-attach-camera: أول مرة نطلب إذن الكاميرا حتى يظهر خيارها
                // في المنتقي؛ الرفض لا يمنع المنتقي العادي.
                if (android.os.Build.VERSION.SDK_INT >= 23
                        && !hasPerm(android.Manifest.permission.CAMERA)) {
                    pendingChooserParams = p;
                    try {
                        requestPermissions(new String[] { android.Manifest.permission.CAMERA }, PERM_CAM_PICK);
                        return true;
                    } catch (Throwable e) { pendingChooserParams = null; }
                }
                launchFileChooser(p);
                return pendingFilePick != null;
            }
        });

        web.addJavascriptInterface(new ShareBridge(), "OmranAndroidShare");

        // v-dl-listener: أي تنزيل http(s) يقع داخل الـWebView (روابط PDF
        // وغيرها) ينزل عبر مدير التنزيلات مع إشعار في مجلد Download.
        web.setDownloadListener((dlUrl, ua, contentDisposition, mimetype, contentLength) -> {
            try {
                if (dlUrl != null && (dlUrl.startsWith("http://") || dlUrl.startsWith("https://"))) {
                    android.app.DownloadManager.Request r =
                        new android.app.DownloadManager.Request(Uri.parse(dlUrl));
                    r.setNotificationVisibility(
                        android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    r.setDestinationInExternalPublicDir(
                        android.os.Environment.DIRECTORY_DOWNLOADS,
                        android.webkit.URLUtil.guessFileName(dlUrl, contentDisposition, mimetype));
                    ((android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                }
            } catch (Throwable ignored) { }
        });

        String url = homeUrl();
        Uri data = getIntent() != null ? getIntent().getData() : null;
        if (data != null && HOME_HOST.equals(data.getHost())) url = data.toString();
        web.loadUrl(url);
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == FILE_PICK && pendingFilePick != null) {
            Uri[] out = WebChromeClient.FileChooserParams.parseResult(result, data);
            // v-attach-camera: الكاميرا ترجع RESULT_OK بلا data — الصورة في
            // الملف المؤقت الذي مررناه في EXTRA_OUTPUT.
            // v-cam-grant: لا نسلّم ملفًا صفريًا (كتابة الكاميرا فشلت) —
            // null أفضل من صورة لا تُفك.
            if (out == null && result == Activity.RESULT_OK && cameraOutputUri != null) {
                boolean hasData = false;
                try {
                    android.os.ParcelFileDescriptor pfd =
                        getContentResolver().openFileDescriptor(cameraOutputUri, "r");
                    if (pfd != null) { hasData = pfd.getStatSize() > 0; pfd.close(); }
                } catch (Throwable ignored) { }
                if (hasData) out = new Uri[] { cameraOutputUri };
            }
            pendingFilePick.onReceiveValue(out);
            pendingFilePick = null;
            cameraOutputUri = null;
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
