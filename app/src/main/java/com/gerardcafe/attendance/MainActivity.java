
package com.gerardcafe.attendance;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private String pendingContent;
    private String pendingMimeType;
    private String pendingTitle;
    private static final int CREATE_FILE = 2001;
    private static final int OPEN_FILE = 2002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void saveFile(String filename, String content,
                                 String mimeType) {
                runOnUiThread(() -> {
                    pendingContent = content;
                    pendingMimeType = mimeType;
                    pendingTitle = filename;

                    Intent intent =
                        new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(mimeType);
                    intent.putExtra(Intent.EXTRA_TITLE, filename);
                    startActivityForResult(intent, CREATE_FILE);
                });
            }

            @JavascriptInterface
            public void openBackup() {
                runOnUiThread(() -> {
                    Intent intent =
                        new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"application/json", "text/json",
                                     "application/octet-stream"});
                    startActivityForResult(intent, OPEN_FILE);
                });
            }
        }, "Android");

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent result) {
        super.onActivityResult(requestCode, resultCode, result);

        if (resultCode != RESULT_OK || result == null) {
            pendingContent = null;
            return;
        }

        Uri uri = result.getData();
        if (uri == null) return;

        if (requestCode == CREATE_FILE && pendingContent != null) {
            try (OutputStream out =
                    getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("تعذر فتح الملف");
                out.write(pendingContent.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "تم حفظ الملف بنجاح",
                    Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر حفظ الملف",
                    Toast.LENGTH_LONG).show();
            } finally {
                pendingContent = null;
                pendingMimeType = null;
                pendingTitle = null;
            }
        } else if (requestCode == OPEN_FILE) {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                if (in == null) throw new Exception("تعذر فتح الملف");
                byte[] bytes = new byte[8192];
                int count;
                while ((count = in.read(bytes)) != -1) {
                    buffer.write(bytes, 0, count);
                }
                String content = new String(
                    buffer.toByteArray(), StandardCharsets.UTF_8);
                String escaped = org.json.JSONObject.quote(content);
                webView.evaluateJavascript(
                    "window.importBackupFromAndroid(" + escaped + ");",
                    null);
            } catch (Exception e) {
                Toast.makeText(this, "تعذر قراءة ملف النسخة",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
