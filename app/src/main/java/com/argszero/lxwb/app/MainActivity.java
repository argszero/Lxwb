package com.argszero.lxwb.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.Set;


public class MainActivity extends Activity {
    private WebView webView;
    private WebChromeClient webChromeClient =new WebChromeClient() {
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
        openLog();
        File dir = this.getExternalFilesDir(null);
        System.out.println("dir:"+dir.getAbsolutePath());
        if (dir == null) {
            dir = this.getFilesDir();
        }
        Storage.get().init(dir.toString());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        new PollingService.PollingThread(this).start();
        PollingUtils.startPollingService(this, 600, PollingService.class, PollingService.ACTION);


        this.webView = ((WebView) findViewById(R.id.webView));
        this.webView.getSettings().setJavaScriptEnabled(true);
        this.webView.addJavascriptInterface(new MyJavaScriptInterface(this), "backend");
        this.webView.setWebChromeClient(webChromeClient);
        this.webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        this.webView.requestFocus();
        this.webView.setWebViewClient(new MyWebViewClient(null));
        this.webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        try {
            String baseUrl = Storage.get().getBaseUrl();
//            FileUtils.write(new File(dir,"thumbnail/index.html"), IOUtils.toString(getResources().getAssets().open("index.html")));
//            Storage.get().write2file(IOUtils.toString(getResources().getAssets().open("index.html"),"index.html");
            this.webView.loadDataWithBaseURL(baseUrl, IOUtils.toString(getResources().getAssets().open("index.html")), "text/html", "utf-8", null);
//            String url = baseUrl + "111.html";
//            String url = baseUrl + "index.html";
//            System.out.println("aaaaaaaa:url:"+url);
//            this.webView.loadUrl(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
//        this.webView.loadUrl("file:///android_asset/index.html");
        SharedPreferences sharedPreferences = getSharedPreferences("lxwb", Activity.MODE_PRIVATE);
        String account = sharedPreferences.getString("account", "");
        if("".equals(account)){
            Intent localIntent = new Intent();
            localIntent.setClass(getApplicationContext(), SettingActivity.class);
            startActivity(localIntent);
        }

    }

    private void openLog() {
        java.util.logging.Logger.getLogger("org.apache.http.wire").setLevel(java.util.logging.Level.FINEST);
        java.util.logging.Logger.getLogger("org.apache.http.headers").setLevel(java.util.logging.Level.FINEST);
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
        System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
        System.setProperty("org.apache.commons.logging.simplelog.log.httpclient.wire", "debug");
//        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "debug");
//        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.headers", "debug");
    }


    private class MyWebViewClient extends WebViewClient {
        public MyWebViewClient(Object o) {
        }
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static class MyJavaScriptInterface {

        private MainActivity mainActivity;

        public MyJavaScriptInterface(MainActivity mainActivity) {

            this.mainActivity = mainActivity;
        }


        @JavascriptInterface
        public long getCurrentWeiboId() {
            final SharedPreferences sharedPreferences = mainActivity.getSharedPreferences("weibo", Activity.MODE_PRIVATE);
            long publicCurrentItemId = sharedPreferences.getLong("CurrentWeiboId", 0);
            return publicCurrentItemId;
        }

        @JavascriptInterface
        public void setCurrentWeiboId(long weiboId) {
            final SharedPreferences sharedPreferences = mainActivity.getSharedPreferences("weibo", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putLong("CurrentWeiboId", weiboId);
            editor.commit();
        }

        @JavascriptInterface
        public String listWeiboBeforeInclude(long weiboId, int limit) {
            return Storage.get().listWeiboBeforeInclude(weiboId, limit).toString();
        }

        @JavascriptInterface
        public String listWeiboAfter(long weiboId, int limit) {
            return Storage.get().listWeiboAfter(weiboId, limit).toString();
        }

        @JavascriptInterface
        public String listWeiboBefore(long weiboId, int limit) {
            return Storage.get().listWeiboBefore(weiboId, limit).toString();
        }

        @JavascriptInterface
        public int countWeibo() {
            int count = Storage.get().countWeibo();
            Log.i("count", "" + count);
            return count;
        }

        @JavascriptInterface
        public void openImage(String type,String img,String imgs) {
            Intent localIntent = new Intent();
            localIntent.putExtra("img", img);
            localIntent.putExtra("type", type);
            localIntent.putExtra("imgs", imgs);
            localIntent.setClass(mainActivity.getApplicationContext(), ImageActivity.class);
            mainActivity.startActivity(localIntent);
        }
        @JavascriptInterface
        public void saveRePost(long weiboId,String postContent) {
            Storage.get().saveRePost(weiboId, postContent);
        }
        @JavascriptInterface
        public void showSetting() {
            Intent localIntent = new Intent();
            localIntent.setClass(mainActivity.getApplicationContext(), SettingActivity.class);
            mainActivity.startActivity(localIntent);
        }


    }
}
