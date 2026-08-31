/*
 * Copyright 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.omran.aibuilder.twa;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Application extends android.app.Application {

  // v-crash-report: «مستمر في التوقف» على أجهزة المالك بلا أي أثر — نرسل
  // خلاصة الانهيار لنقطة أخطاء الواجهة نفسها فتظهر في «فحص النظام».
  private static final String REPORT_URL =
      "https://omran-ai-builder.vercel.app/api/system?action=client-errors";

  @Override
  public void onCreate() {
      super.onCreate();
      final Thread.UncaughtExceptionHandler previous =
          Thread.getDefaultUncaughtExceptionHandler();
      Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
          try { reportCrash(throwable); } catch (Throwable ignored) { }
          if (previous != null) previous.uncaughtException(thread, throwable);
      });
  }

  private void reportCrash(Throwable t) {
      final StringWriter sw = new StringWriter();
      t.printStackTrace(new PrintWriter(sw));
      final String stack = sw.toString();
      final String message = "ANDROID CRASH v" + versionLabel() + ": " + t;
      Thread sender = new Thread(() -> {
          try {
              HttpURLConnection c = (HttpURLConnection) new URL(REPORT_URL).openConnection();
              c.setRequestMethod("POST");
              c.setRequestProperty("Content-Type", "application/json");
              c.setConnectTimeout(4000);
              c.setReadTimeout(4000);
              c.setDoOutput(true);
              String body = "{\"message\":" + jsonStr(message)
                  + ",\"source\":\"android-twa\""
                  + ",\"stack\":" + jsonStr(stack.substring(0, Math.min(stack.length(), 1400)))
                  + ",\"ua\":" + jsonStr(android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                      + " / Android " + android.os.Build.VERSION.RELEASE) + "}";
              try (OutputStream os = c.getOutputStream()) {
                  os.write(body.getBytes(StandardCharsets.UTF_8));
              }
              c.getResponseCode();
              c.disconnect();
          } catch (Throwable ignored) { }
      });
      sender.start();
      try { sender.join(4000); } catch (InterruptedException ignored) { }
  }

  private String versionLabel() {
      try {
          return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
      } catch (Throwable e) { return "?"; }
  }

  private static String jsonStr(String s) {
      StringBuilder b = new StringBuilder("\"");
      for (char ch : s.toCharArray()) {
          switch (ch) {
              case '"': b.append("\\\""); break;
              case '\\': b.append("\\\\"); break;
              case '\n': b.append("\\n"); break;
              case '\r': break;
              case '\t': b.append("\\t"); break;
              default:
                  if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
                  else b.append(ch);
          }
      }
      return b.append('"').toString();
  }
}
