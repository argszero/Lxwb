package com.argszero.lxwb.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shaoaq on 7/13/15.
 */
public class Storage {
    private final static Pattern URL = Pattern.compile("(http|ftp|https):\\/\\/([\\w\\-_]+(?:(?:\\.[\\w\\-_]+)+))([\\w\\-\\.,@?^=%&amp;:/~\\+#]*[\\w\\-\\@?^=%&amp;/~\\+#])?");
    private final static Pattern CLIPURL = Pattern.compile("clipurl = \"([^\"]*)\"");
    private final static Pattern SINA_MP4 = Pattern.compile("http://us.sinaimg.cn/(.*.mp4).*");
    private final static Pattern MIAOPAI_MP4 = Pattern.compile("http://gslb.miaopai.com/stream/(.*.mp4).*");
    private final static String initSql = "" +
            "CREATE TABLE IF NOT EXISTS weibo(" +
            "   id integer primary key," +
            "   created_at varchar(100)," +
            "   text varchar(1000)," +
            "   user_id integer," +
            "   user_name varchar(100)," +
            "   pics varchar(2000)," +
            "   videos varchar(2000)," +
            "   re_id integer," +
            "   re_created_at varchar(100)," +
            "   re_text varchar(1000)," +
            "   re_user_id integer," +
            "   re_user_name varchar(100)," +
            "   re_pics varchar(2000)," +
            "   re_videos varchar(2000)" +
            ");" +
            "" +
            "CREATE TABLE IF NOT EXISTS repost(" +
            "   id integer primary key autoincrement," +
            "   weibo_id integer," +
            "   content varchar(1000)" +
            ");" +
            "";

    private String dir;
    private static Storage instance = new Storage();

    public static Storage get() {
        return instance;
    }

    public synchronized void init(String dir) {
        if (this.dir == null) {
            this.dir = dir;
            SQLiteDatabase db = openDb();
            for (String sql : initSql.split(";")) {
                db.execSQL(sql);
            }
            db.close();
        }
    }

    public long getLastId() {
        SQLiteDatabase db = openDb();
        try {
            Cursor cursor = db.rawQuery("select max(id) " +
                    "   from weibo " +
                    "", new String[]{});
            if (cursor.getCount() != 0) {
                cursor.moveToNext();
                long lastId = cursor.getLong(0);
                return lastId;
            }
            cursor.close();
        } finally {
            db.close();
        }
        return 0;
    }

    public void saveStatus(JSONObject status) throws JSONException, IOException {
        SQLiteDatabase db = openDb();
        try {
            ContentValues values = new ContentValues();
            set(status, values, "");
            set(status.optJSONObject("retweeted_status"), values, "re_");
            Log.i("new weibo", values.getAsLong("id") + "");
            db.insert("weibo", null, values);
        } finally {
            db.close();
        }
    }

    public String getBaseUrl() {
        try {
            File baseDir = new File(dir);
            return baseDir.toURI().toURL().toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return "";
        }
    }

    private void set(JSONObject status, ContentValues values, String prefix) throws JSONException, IOException {
        if (status == null) return;
        values.put(prefix + "id", status.getLong("id"));
        values.put(prefix + "created_at", status.getString("created_at"));
        String text = status.optString("text");
        values.put(prefix + "text", text);
        JSONObject user = status.optJSONObject("user"); //抱歉，你暂时没有这条微博的查看权限哦,这种情况下看不到user
        if (user != null) {
            values.put(prefix + "user_id", user.getLong("id"));
            values.put(prefix + "user_name", user.getString("name"));
        }
        JSONArray pic_urls = status.optJSONArray("pic_urls");
        List<String> pics = new ArrayList<String>();
        for (int i = 0; pic_urls != null && i < pic_urls.length(); i++) {
            Matcher matcher = Pattern.compile("/([^/]*)$").matcher(pic_urls.getJSONObject(i).getString("thumbnail_pic"));
            if (matcher.find()) {
                String pic = matcher.group(1);
                pics.add(pic);
                save(pic, "thumbnail");
//                save(pic, "bmiddle");
                save(pic, "large");
            }
        }
        values.put(prefix + "pics", StringUtils.join(pics, ","));

        List<String> videos = new ArrayList<String>();
        if (text != null) {
            Matcher matcher = URL.matcher(text);
            while (matcher.find()) {
                String url = matcher.group();
                Log.e("find url", url);
                String html = null;
                for (int i=0;i<10;i++) {
                    try {
                        html = IOUtils.toString(URI.create("http://www4.flvcd.com/parse.php?format=&kw=" + url));
                        break;
                    } catch (UnknownHostException e) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
                Matcher clipurlMatcher = CLIPURL.matcher(html);
                while (clipurlMatcher.find()) {
                    String downloadUrl = clipurlMatcher.group(1);
                    String fileName = null;
                    Matcher sinaMp4Matcher = SINA_MP4.matcher(downloadUrl);
                    if (sinaMp4Matcher.find()) {
                        fileName = "sina_" + sinaMp4Matcher.group(1);
                    }
                    if (fileName == null) {
                        Matcher miaopaiMp4Matcher = MIAOPAI_MP4.matcher(downloadUrl);
                        if (miaopaiMp4Matcher.find()) {
                            fileName = "miaopai_" + miaopaiMp4Matcher.group(1);
                        }
                    }
                    if (fileName != null) {
                        Log.e("find mp4", fileName);
                        videos.add(fileName);
                        File videoDir = new File(dir, "video");
                        Log.e("video dir", videoDir.getAbsolutePath());
                        videoDir.mkdirs();
                        File videoFile = new File(videoDir, fileName);
                        if (videoFile.exists()) {
                            videoFile.setLastModified(System.currentTimeMillis());
                        } else {
                            FileUtils.copyURLToFile(new URL(downloadUrl), videoFile);
                            Log.e("video file", videoFile.getAbsolutePath());
                        }
                    } else {
                        Log.e("notmp4", "url:" + url);
                    }
                }
            }
        }
        values.put(prefix + "videos", StringUtils.join(videos, ","));
    }

    private void save(String pic, String imageType) throws IOException {
        File imgTypeDir = new File(dir, imageType);
        imgTypeDir.mkdirs();
        if (new File(imgTypeDir, pic).exists()) {
            new File(imgTypeDir, pic).setLastModified(System.currentTimeMillis());
        } else {
            FileUtils.copyURLToFile(new URL("http://ww4.sinaimg.cn/" + imageType + "/" + pic), new File(imgTypeDir, pic));
        }
    }

    public JSONArray listWeiboBeforeInclude(long weibo_id, int limit) {
        if (weibo_id == 0) {
            weibo_id = getLastId();
        }
        SQLiteDatabase db = openDb();
        try {
            JSONArray result = new JSONArray();
            Cursor cursor = db.rawQuery("select * " +
                            "   from weibo " +
                            "   where id <= ?" +
                            "   order by id desc " +
                            "   limit ? " +
                            "",
                    new String[]{weibo_id + "", limit + ""});
            while (cursor.moveToNext()) {
                try {
                    JSONObject object = getWeibo(cursor);
                    result.put(object);
                } catch (JSONException e) {
                    Log.e("abc", "list error", e);
                }
            }
            return result;
        } finally {
            db.close();
        }
    }

    private JSONObject getWeibo(Cursor cursor) throws JSONException {
        int id = cursor.getColumnIndex("id");
        int created_at = cursor.getColumnIndex("created_at");
        int text = cursor.getColumnIndex("text");
        int user_id = cursor.getColumnIndex("user_id");
        int user_name = cursor.getColumnIndex("user_name");
        int pics = cursor.getColumnIndex("pics");
        int videos = cursor.getColumnIndex("videos");

        int re_id = cursor.getColumnIndex("re_id");
        int re_created_at = cursor.getColumnIndex("re_created_at");
        int re_text = cursor.getColumnIndex("re_text");
        int re_user_id = cursor.getColumnIndex("re_user_id");
        int re_user_name = cursor.getColumnIndex("re_user_name");
        int re_pics = cursor.getColumnIndex("re_pics");
        int re_videos = cursor.getColumnIndex("re_videos");

        JSONObject object = new JSONObject();
        object.put("id", cursor.getLong(id));
        if (!cursor.isNull(created_at)) {
            object.put("created_at", cursor.getString(created_at));
        }
        if (!cursor.isNull(text)) {
            object.put("text", cursor.getString(text));
        }
        if (!cursor.isNull(user_id)) {
            object.put("user_id", cursor.getLong(user_id));
        }
        if (!cursor.isNull(user_name)) {
            object.put("user_name", cursor.getString(user_name));
        }
        if (!cursor.isNull(pics)) {
            object.put("pics", cursor.getString(pics));
        }
        if (!cursor.isNull(videos)) {
            object.put("videos", cursor.getString(videos));
        }


        try {
            if (!cursor.isNull(re_id)) {
                object.put("re_id", cursor.getLong(re_id));
            }
            if (!cursor.isNull(re_created_at)) {
                object.put("re_created_at", cursor.getString(re_created_at));
            }
            if (!cursor.isNull(re_text)) {
                object.put("re_text", cursor.getString(re_text));
            }
            if (!cursor.isNull(re_user_id)) {
                object.put("re_user_id", cursor.getLong(re_user_id));
            }
            if (!cursor.isNull(re_user_name)) {
                object.put("re_user_name", cursor.getString(re_user_name));
            }
            if (!cursor.isNull(re_pics)) {
                object.put("re_pics", cursor.getString(re_pics));
            }
            if (!cursor.isNull(re_videos)) {
                object.put("re_videos", cursor.getString(re_videos));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return object;
    }

    public JSONArray listWeiboAfter(long weibo_id, int limit) {
        SQLiteDatabase db = openDb();
        try {
            JSONArray result = new JSONArray();
            Cursor cursor = db.rawQuery("select * " +
                            "   from weibo " +
                            "   where id > ?" +
                            "   order by id asc " +
                            "   limit ? " +
                            "   ",
                    new String[]{weibo_id + "", limit + ""});
            List<JSONObject> list = new ArrayList<JSONObject>();
            while (cursor.moveToNext()) {
                try {
                    JSONObject object = getWeibo(cursor);
                    list.add(object);
                } catch (JSONException e) {
                    Log.e("abc", "list error", e);
                }
            }
            Collections.reverse(list);
            for (JSONObject object : list) {
                result.put(object);
            }
            return result;
        } finally {
            db.close();
        }
    }

    public JSONArray listWeiboBefore(long weibo_id, int limit) {
        SQLiteDatabase db = openDb();
        try {
            JSONArray result = new JSONArray();
            Cursor cursor = db.rawQuery("select * " +
                            "   from weibo " +
                            "   where id < ?" +
                            "   order by id desc " +
                            "   limit ? " +
                            "   ",
                    new String[]{weibo_id + "", limit + ""});
            while (cursor.moveToNext()) {
                try {
                    JSONObject object = getWeibo(cursor);
                    result.put(object);
                } catch (JSONException e) {
                    Log.e("abc", "list error", e);
                }
            }
            return result;
        } finally {
            db.close();
        }
    }

    public int countWeibo() {
        SQLiteDatabase db = openDb();
        try {
            Cursor cursor = db.rawQuery("select count(1) " +
                            "   from weibo " +
                            "   ",
                    new String[]{});
            cursor.moveToNext();
            return cursor.getInt(0);
        } finally {
            db.close();
        }
    }

    private SQLiteDatabase openDb() {
        return SQLiteDatabase.openOrCreateDatabase(dir + "/db.db", null);
    }

    public String getImage(String img) {
        return dir + "/" + img;
    }

    public void saveRePost(long weibo_id, String postContent) {
        System.out.println("save repost");
        SQLiteDatabase db = openDb();
        try {
            ContentValues values = new ContentValues();
            values.put("weibo_id", weibo_id);
            values.put("content", postContent);
            long id = db.insert("repost", null, values);
            System.out.println("save repost:id:" + id);
        } finally {
            db.close();
        }
        Log.e("abc", "saveandlist:" + listRePost().length());
    }

    public JSONArray listRePost() {
        SQLiteDatabase db = openDb();
        try {
            JSONArray result = new JSONArray();
            Cursor cursor = db.rawQuery("select * " +
                            "   from  repost" +
                            "   ",
                    new String[]{});
            while (cursor.moveToNext()) {
                try {
                    JSONObject object = new JSONObject();
                    int id = cursor.getColumnIndex("id");
                    int weibo_id = cursor.getColumnIndex("weibo_id");
                    int content = cursor.getColumnIndex("content");
                    object.put("id", cursor.getLong(id));
                    object.put("weibo_id", cursor.getLong(weibo_id));
                    object.put("content", cursor.getString(content));
                    result.put(object);
                } catch (JSONException e) {
                    Log.e("abc", "list error", e);
                }
            }
            return result;
        } finally {
            db.close();
        }
    }

    public int removeRePost(long id) {
        SQLiteDatabase db = openDb();
        try {
            int result = db.delete("repost", "id = ?", new String[]{id + ""});
            return result;
        } finally {
            db.close();
        }
    }

    public void removeHisPics(int expired) {
        removeHisMedias(expired, "large");
        removeHisMedias(expired, "thumbnail");
    }

    public void removeHisVideos(int expired) {
        removeHisMedias(expired, "video");
    }

    private void removeHisMedias(int expired, String mediaType) {
        File imgTypeDir = new File(dir, mediaType);
        File[] pics = imgTypeDir.listFiles();
        if (pics != null) {
            for (File pic : pics) {
                if (System.currentTimeMillis() - pic.lastModified() > expired) {
                    pic.delete();
                }
            }
        }
    }


    public void removeHisWeibo(int max) {
        int count = countWeibo();
        int expired = count-max;
        if (expired > 0) {
            removeWeibo(max);
        }
    }


    private void removeWeibo(int keep) {
        SQLiteDatabase db = openDb();
        try {
            Cursor cursor = db.rawQuery("" +
                    "select min(id) " +
                    "from (" +
                    "   select id" +
                    "   from weibo" +
                    "   order by id desc" +
                    "   limit ?" +
                    ")", new String[]{keep + ""});
            cursor.moveToNext();
            long minId = cursor.getLong(0);
            db.execSQL("" +
                            "   delete " +
                            "   from weibo " +
                            "   where id < ? " +
                            "   ",
                    new String[]{minId + ""});
        } finally {
            db.close();
        }
    }
}
