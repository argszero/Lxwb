package com.argszero.lxwb.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;


public class ImageActivity extends Activity {

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);
        String type = getIntent().getStringExtra("type");
        String img = getIntent().getStringExtra("img");
        String imgs = getIntent().getStringExtra("imgs");
        WebView webView = (WebView) findViewById(R.id.webView);
        WebSettings ws = webView.getSettings();
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        ws.setDisplayZoomControls(false);
        String url = Storage.get().getBaseUrl() + "/" + img;
        StringBuilder sb = new StringBuilder();
        for (String pic : imgs.split(",")) {
            sb.append("<img width=\"100%\"  src=\"" + type + "/" + pic + "\" /><br/>\n");
        }

        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head lang=\"en\">\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                "    <title></title>\n" +
                "    <style type=\"text/css\">\n" +
                "     html  body {background-color:black;}\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body width=\"100%\">\n" +
                sb.toString() +
                "</body>\n" +
                "</html>";
        System.out.println(html);
        String baseUrl = Storage.get().getBaseUrl();
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null);
    }

}
