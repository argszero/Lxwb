package com.argszero.lxwb.app;

import android.app.Service;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.util.Log;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Created by shaoaq on 7/3/15.
 */
public class PollingService extends Service {
    public static final String ACTION = "com.argszero.wuyun.app.PollingService";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new PollingThread(this).start();
        return super.onStartCommand(intent, flags, startId);

    }


    public static class PollingThread extends Thread {
        private static AtomicBoolean isRunning = new AtomicBoolean(false);
        private Pattern listPattern = Pattern.compile("href=\"/bugs/wooyun-([^\"#]*)\"");
        private static Pattern pagePattern = Pattern.compile("[\\s\\S]*" +
                "'wybug_title'>.{7}([^<]*)<" +
                "[\\s\\S]*" +
                "'wybug_author'>[^>]*>([^<]*)<" +
                "[\\s\\S]*" +
                "'wybug_open_date'.{8}([^<]*)<" +
                "[\\s\\S]*" +
                "'wybug_level'>[^>]*>\\s*<h3>.{7}\\s*([^<]*)<" +
                "[\\s\\S]*" +
                "");
        private ContextWrapper context;


        public PollingThread(ContextWrapper context) {
            this.context = context;
        }

        @Override
        public void run() {
            if (isRunning.compareAndSet(false, true)) {
                try {
                    if (isWifiConnect()) {
                        sendReposts();
                        clearHis();
                        loadWeibo();
                    }
                } catch (Throwable e) {
                    Log.e("", "error when get new id", e);
                } finally {
                    isRunning.set(false);
                }
            }
        }

        private boolean isWifiConnect() {
            try {
                ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
                NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                return mWifi.isConnected();
            } catch (Throwable e) {
                return false;
            }
        }

        private void clearHis() {
            Storage.get().removeHisPics(5 * 24 * 60 * 60 * 1000);
            Storage.get().removeHisVideos(5 * 24 * 60 * 60 * 1000);
            Storage.get().removeHisWeibo(2000);
        }


        private void loadWeibo() {
            long lastId = Storage.get().getLastId();
            boolean findLast = false;
            List<JSONObject> statusList = new ArrayList<JSONObject>();
            int tryFetchTokenMaxTimes = 3;
            for (int i = 1; i < 3 && !findLast; i++) {
                try {
                    String uri = "https://api.weibo.com/2/statuses/home_timeline.json?count=100&page=" + i + "&access_token=" + WeiboTokenUtils.getToken(context);
                    HttpGet get = new HttpGet(uri);
                    Log.i("fetch uri",uri);
                    DefaultHttpClient client = HttpSigleton.getInstance();
                    HttpResponse response = client.execute(get);
                    if (response.getStatusLine().getStatusCode() == 403) {
                        tryFetchTokenMaxTimes--;
                        if (tryFetchTokenMaxTimes > 0) {
                            WeiboTokenUtils.reFetchToken(context);
                            i = 0;
                            continue;
                        } else {
                            return;
                        }
                    } else {
                        String json = EntityUtils.toString(response.getEntity());
                        JSONObject obj = new JSONObject(json);
                        JSONArray statuses = obj.getJSONArray("statuses");
                        for (int j = 0; j < statuses.length(); j++) {
                            JSONObject status = statuses.getJSONObject(j);
                            long id = status.getLong("id");
                            if (id < lastId) {
                                findLast = true;
                                break;
                            }
                            statusList.add(status);
                        }
                    }

                } catch (Throwable e) {
                    if (e instanceof JSONException) {
                        WeiboTokenUtils.reFetchToken(context);
                    }
                    e.printStackTrace();
                    Log.e("", "error when find new id", e);
                }
            }
            Collections.reverse(statusList);
            for (JSONObject status : statusList) {
                try {
                    Storage.get().saveStatus(status);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("", "error when save new status", e);
                }
            }
        }

        private void sendReposts() {
            System.out.println("send sendReposts");
            try {
                JSONArray rePosts = Storage.get().listRePost();
                Log.e("ready to send reposts", "size:" + rePosts.length());
                for (int i = 0; i < rePosts.length(); i++) {
                    JSONObject rePost = rePosts.getJSONObject(i);
                    long id = rePost.getLong("id");
                    long weibo_id = rePost.getLong("weibo_id");
                    String content = rePost.getString("content");
                    DefaultHttpClient client = HttpSigleton.getInstance();
                    HttpPost post = new HttpPost("https://api.weibo.com/2/statuses/repost.json");
                    List<NameValuePair> params = new ArrayList<NameValuePair>();
                    params.add(new BasicNameValuePair("access_token", WeiboTokenUtils.getToken(context)));
                    params.add(new BasicNameValuePair("id", "" + weibo_id));
                    params.add(new BasicNameValuePair("status", content));
                    params.add(new BasicNameValuePair("is_comment", "0"));
                    post.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
                    HttpResponse response = client.execute(post);
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (statusCode == 200) {
                        Storage.get().removeRePost(id);
                    }
                    EntityUtils.toString(response.getEntity());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (InvalidKeySpecException e) {
                e.printStackTrace();
            }
        }

    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        System.out.println("Service:onDestroy");
    }
}
