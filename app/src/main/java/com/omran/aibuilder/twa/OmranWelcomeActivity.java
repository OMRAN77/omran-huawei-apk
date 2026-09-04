package com.omran.aibuilder.twa;

// v-native-welcome (AppGallery rule 4.1): شاشة ترحيب أصلية (native) تُعرض عند
// أول تشغيل — تُظهر هوية التطبيق ومميزاته بواجهة أندرويد حقيقية (لا WebView)
// لتثبت أن التطبيق ليس مجرد غلاف لموقع. بعد أول مرة يدخل المستخدم مباشرةً.
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class OmranWelcomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences prefs = getSharedPreferences("omran_prefs", MODE_PRIVATE);
        // بعد أول تشغيل: ادخل التطبيق مباشرةً بلا شاشة الترحيب.
        if (prefs.getBoolean("onboarded", false)) {
            startWeb();
            return;
        }

        final boolean ar = java.util.Locale.getDefault().getLanguage().startsWith("ar");

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0a0a12"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(44), dp(24), dp(28));
        scroll.addView(root);

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.omran.aibuilder.R.mipmap.ic_launcher);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(84), dp(84));
        logo.setLayoutParams(logoLp);
        root.addView(logo);

        TextView title = new TextView(this);
        title.setText(ar ? "عمران AI" : "Omran AI");
        title.setTextColor(Color.parseColor("#d4af37"));
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(14), 0, dp(4));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText(ar ? "منصّة ذكاء اصطناعي متكاملة — صور وفيديو ومحادثة وأدوات"
                       : "All-in-one AI platform — images, video, chat & tools");
        sub.setTextColor(Color.parseColor("#98a0b3"));
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(22));
        root.addView(sub);

        String[][] feats = ar ? new String[][]{
            {"💬", "محادثة ذكية", "دردشة مع أقوى نماذج الذكاء الاصطناعي"},
            {"🎨", "توليد الصور", "صور احترافية من وصف نصّي"},
            {"🎬", "صانع الفيديو", "فيديوهات بالذكاء الاصطناعي"},
            {"👗", "أزياء وديكور وإعلانات", "استوديوهات تصميم بالذكاء الاصطناعي"},
            {"📈", "سوق الأسهم العالمي", "أسعار حيّة ومتابعة لحظية"},
            {"📺", "تلفزيون مباشر", "قنوات عالمية مباشرة"},
            {"🕋", "القبلة والمواقيت", "اتجاه القبلة ومواقيت الصلاة مع تنبيهات"},
            {"📄", "مساعد المستندات والسيرة", "تحليل وإنشاء المستندات والسير الذاتية"},
        } : new String[][]{
            {"💬", "Smart Chat", "Chat with the most powerful AI models"},
            {"🎨", "Image Generation", "Professional images from a text prompt"},
            {"🎬", "Video Maker", "AI-generated videos"},
            {"👗", "Fashion, Decor & Ads", "AI design studios"},
            {"📈", "Global Stock Market", "Live prices, real-time"},
            {"📺", "Live TV", "Live international channels"},
            {"🕋", "Qibla & Prayer Times", "Qibla direction & prayer times with alerts"},
            {"📄", "Docs & CV Assistant", "Analyze and create documents & resumes"},
        };
        for (String[] f : feats) root.addView(featureCard(f[0], f[1], f[2], ar));

        Button start = new Button(this);
        start.setText(ar ? "ابدأ الآن" : "Get Started");
        start.setAllCaps(false);
        start.setTextColor(Color.parseColor("#0a0a12"));
        start.setTextSize(17);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#d4af37"));
        btnBg.setCornerRadius(dp(14));
        start.setBackground(btnBg);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        btnLp.topMargin = dp(22);
        start.setLayoutParams(btnLp);
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putBoolean("onboarded", true).apply();
                startWeb();
            }
        });
        root.addView(start);

        setContentView(scroll);
    }

    private LinearLayout featureCard(String emoji, String heading, String desc, boolean ar) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#16161f"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#2a2a3d"));
        card.setBackground(bg);

        TextView icon = new TextView(this);
        icon.setText(emoji);
        icon.setTextSize(25);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (ar) iconLp.leftMargin = dp(14); else iconLp.rightMargin = dp(14);
        icon.setLayoutParams(iconLp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView h = new TextView(this);
        h.setText(heading);
        h.setTextColor(Color.parseColor("#f1f1f4"));
        h.setTextSize(15.5f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        if (ar) h.setGravity(Gravity.END);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(Color.parseColor("#98a0b3"));
        d.setTextSize(12.5f);
        if (ar) d.setGravity(Gravity.END);

        texts.addView(h);
        texts.addView(d);

        // RTL: النص على اليمين والأيقونة على أقصى اليمين
        if (ar) { card.addView(texts); card.addView(icon); }
        else    { card.addView(icon);  card.addView(texts); }
        return card;
    }

    private void startWeb() {
        startActivity(new Intent(this, OmranWebActivity.class));
        finish();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
