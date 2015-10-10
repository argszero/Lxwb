package com.argszero.lxwb.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Created by shaoaq on 8/18/15.
 */
public class SettingActivity extends Activity {
    private WebView webView;
    private WebChromeClient webChromeClient = new WebChromeClient() {
        private View myView = null;
        private CustomViewCallback myCallback = null;

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (myCallback != null) {
                myCallback.onCustomViewHidden();
                myCallback = null;
                return;
            }

            ViewGroup parent = (ViewGroup) webView.getParent();
            parent.removeView(webView);
            parent.addView(view);
            myView = view;
            myCallback = callback;
        }


        @Override
        public void onHideCustomView() {
            if (myView != null) {
                if (myCallback != null) {
                    myCallback.onCustomViewHidden();
                    myCallback = null;
                }

                ViewGroup parent = (ViewGroup) myView.getParent();
                parent.removeView(myView);
                parent.addView(webView);
                myView = null;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        this.webView = ((WebView) findViewById(R.id.webView));
        this.webView.getSettings().setJavaScriptEnabled(true);
        this.webView.addJavascriptInterface(new MyJavaScriptInterface(this), "backend");
        this.webView.setWebChromeClient(webChromeClient);
        this.webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        this.webView.requestFocus();
        this.webView.setWebViewClient(new MyWebViewClient(null));
        this.webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        this.webView.loadUrl("file:///android_asset/setting.html");
    }

    public static class MyJavaScriptInterface {

        private SettingActivity settingActivity;
        private SharedPreferences sharedPreferences;

        public MyJavaScriptInterface(SettingActivity settingActivity) {
            this.settingActivity = settingActivity;
            sharedPreferences = settingActivity.getSharedPreferences("lxwb", Activity.MODE_PRIVATE);
        }

        @JavascriptInterface
        public String get(String key, String defValue) {
            return sharedPreferences.getString(key, defValue);
        }

        @JavascriptInterface
        public void set(String key, String value) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(key, value);
            editor.commit();
        }
    }

    private class MyWebViewClient extends WebViewClient {
        public MyWebViewClient(Object o) {
        }
    }


}
