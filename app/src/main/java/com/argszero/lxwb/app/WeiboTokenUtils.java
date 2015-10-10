package com.argszero.lxwb.app;

import android.app.Activity;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.util.Base64;
import android.widget.Toast;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shaoaq on 7/29/15.
 */
public class WeiboTokenUtils {

    private static String fetchToken(String account, String password) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidKeySpecException {

        DefaultHttpClient client = HttpSigleton.getInstance();
        String appKey = "3815551419";
        String appSec = "846520a385b6c1b026df82ef9739eb1d";
        long now = System.currentTimeMillis();

        HttpGet preLoginGetMethod = new HttpGet("http://login.sina.com.cn/sso/prelogin.php?entry=weibo&callback=sinaSSOController.preloginCallBack&su=&rsakt=mod&client=ssologin.js(v1.4.18)&_=" + now);
        HttpResponse preLoginResponse = client.execute(preLoginGetMethod);
        String preLoginPage = EntityUtils.toString(preLoginResponse.getEntity());
        String pubkey = StringUtils.substringBetween(preLoginPage, "pubkey\":\"", "\"");
        String nonce = StringUtils.substringBetween(preLoginPage, "nonce\":\"", "\"");
        String rsakv = StringUtils.substringBetween(preLoginPage, "rsakv\":\"", "\"");

        String loginUrl = "http://login.sina.com.cn/sso/login.php?client=ssologin.js(v1.4.18)&_=" + now;
        HttpPost loginPost = new HttpPost(loginUrl);
        loginPost.setHeader("Host", "login.sina.com.cn");
        loginPost.setHeader("Origin", "http://d.weibo.com");
        loginPost.setHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.120 Safari/537.36");
        loginPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
        loginPost.setHeader("Accept", "*/*");
        loginPost.setHeader("Cache-Control", "no-cache");
        loginPost.setHeader("Accept-Encoding", "identity");

        String su = new String(Base64.encode(URLEncoder.encode(account, "UTF-8").getBytes(), Base64.DEFAULT)).trim();
        long serverTime = now / 1000;

        String sp = getSp(pubkey, nonce, account, password, serverTime);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("entry", "weibo"));
        params.add(new BasicNameValuePair("gateway", "1"));
        params.add(new BasicNameValuePair("from", ""));
        params.add(new BasicNameValuePair("savestate", "7"));
        params.add(new BasicNameValuePair("useticket", "1"));
        params.add(new BasicNameValuePair("ssosimplelogin", "1"));
        params.add(new BasicNameValuePair("ssosimplelogin", "1"));
        params.add(new BasicNameValuePair("useticket", "1"));
        params.add(new BasicNameValuePair("vsnf", "1"));
        params.add(new BasicNameValuePair("vsnval", ""));
        params.add(new BasicNameValuePair("su", su));
        params.add(new BasicNameValuePair("service", "miniblog"));
        params.add(new BasicNameValuePair("servertime", serverTime + ""));
        params.add(new BasicNameValuePair("nonce", nonce));
        params.add(new BasicNameValuePair("pwencode", "rsa2"));
        params.add(new BasicNameValuePair("rsakv", rsakv));
        params.add(new BasicNameValuePair("sp", sp));
        params.add(new BasicNameValuePair("encoding", "UTF-8"));
        params.add(new BasicNameValuePair("prelt", "115"));
        params.add(new BasicNameValuePair("url", "http://weibo.com/ajaxlogin.php?framelogin=1&callback=parent.sinaSSOController.feedBackUrlCallBack"));
        params.add(new BasicNameValuePair("returntype", "META"));
        loginPost.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
        HttpResponse loginPostResponse = client.execute(loginPost);
        EntityUtils.toString(loginPostResponse.getEntity());

        StringBuilder cookieBuilder = new StringBuilder();
        String uid = "";
        for (Cookie cookie : client.getCookieStore().getCookies()) {
            cookieBuilder.append(cookie.getName() + "=" + cookie.getValue()).append("; ");
            if (cookie.getName().equals("SUS")) {
                String[] splits = cookie.getValue().split("-");//SID-2718025433-1438233512-XD-8bs12-74ddb6b7856a293988a6ff30c5ec24d0
                uid = splits[1];
            }
        }
        System.out.println("Cookie" + cookieBuilder.toString());

        HttpGet authorizeMethod = new HttpGet("https://api.weibo.com/oauth2/authorize?client_id=" + appKey + "&response_type=code&display=mobile&redirect_uri=https://api.weibo.com/oauth2/default.html");
        authorizeMethod.setHeader("Cookie", cookieBuilder.toString());
        HttpContext context = new BasicHttpContext();
        HttpResponse authorizeResponse = client.execute(authorizeMethod, context);
        String authorizeResponseString = EntityUtils.toString(authorizeResponse.getEntity());
        String code = null;
        HttpUriRequest currentReq = (HttpUriRequest) context.getAttribute("http.request");
        if (currentReq.getURI().toString().startsWith("/oauth2/default.html?code=")) {
            code = currentReq.getURI().toString().substring(("/oauth2/default.html?code=").length());
        } else {
            String verifyToken = StringUtils.substringBetween(authorizeResponseString, "name=\"verifyToken\" value=\"", "\"");
            String appkey62 = StringUtils.substringBetween(authorizeResponseString, "name=\"appkey62\" value=\"", "\"");
            HttpPost authorizePostMethod = new HttpPost("https://api.weibo.com/oauth2/authorize");
            authorizePostMethod.setHeader("referer", "https://api.weibo.com/oauth2/authorize?client_id=" + appKey + "&response_type=code&display=mobile&redirect_uri=https://api.weibo.com/oauth2/default.html");
            authorizePostMethod.setHeader("cookie", cookieBuilder.toString());
            params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("entry", "weibo"));
            params.add(new BasicNameValuePair("scope", ""));
            params.add(new BasicNameValuePair("ticket", ""));
            params.add(new BasicNameValuePair("isLoginSina", ""));
            params.add(new BasicNameValuePair("display", "mobile"));
            params.add(new BasicNameValuePair("action", "authorize"));
            params.add(new BasicNameValuePair("withOfficalFlag", "0"));
            params.add(new BasicNameValuePair("withOfficalAccount", ""));
            params.add(new BasicNameValuePair("response_type", "code"));
            params.add(new BasicNameValuePair("regCallback", "https%3A%2F%2Fapi.weibo.com%2F2%2Foauth2%2Fauthorize%3Fclient_id%3D" + appKey + "%26response_type%3Dcode%26display%3Dmobile%26redirect_uri%3Dhttps%253A%252F%252Fapi.weibo.com%252Foauth2%252Fdefault.html%26from%3D%26with_cookie%3D"));
            params.add(new BasicNameValuePair("redirect_uri", "https://api.weibo.com/oauth2/default.html"));
            params.add(new BasicNameValuePair("client_id", appKey));
            params.add(new BasicNameValuePair("appkey62", appkey62));
            params.add(new BasicNameValuePair("state", ""));
            params.add(new BasicNameValuePair("from", ""));
            params.add(new BasicNameValuePair("offcialMobile", "null"));
            params.add(new BasicNameValuePair("visible", "0"));
            params.add(new BasicNameValuePair("version", ""));
            params.add(new BasicNameValuePair("sso_type", ""));
            params.add(new BasicNameValuePair("uid", uid));
            params.add(new BasicNameValuePair("url", ""));
            params.add(new BasicNameValuePair("verifyToken", verifyToken));
            authorizePostMethod.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
            context = new BasicHttpContext();
            HttpResponse authorizePostMethodResponse = client.execute(authorizePostMethod, context);
            EntityUtils.toString(authorizePostMethodResponse.getEntity());
            currentReq = (HttpUriRequest) context.getAttribute("http.request");
            code = currentReq.getURI().toString().substring("/oauth2/default.html?code=".length());
        }

        HttpPost postMethod = new HttpPost("https://api.weibo.com/oauth2/access_token" + "?client_id=" + appKey +
                "&client_secret=" + appSec +
                "&grant_type=authorization_code" +
                "&code=" + code +
                "&redirect_uri=https://api.weibo.com/oauth2/default.html");
        postMethod.setHeader("cookie", cookieBuilder.toString());
        HttpResponse accessTokenResponse = client.execute(postMethod);
        String accessTokenResponseString = EntityUtils.toString(accessTokenResponse.getEntity());
        Pattern pattern = Pattern.compile("ccess_token\":\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(accessTokenResponseString);

        if (matcher.find()) {
            String token = matcher.group(1);
            return token;
        }
        return null;
    }

    private static String getSp(String pubkey, String nonce, String userName, String pwd, long serverTime) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, UnsupportedEncodingException, BadPaddingException, IllegalBlockSizeException {

        String pwdString = serverTime + "\t" + nonce + "\n" + pwd;

        String modeHex = pubkey;
        String exponentHex = "10001";
        KeyFactory factory = KeyFactory.getInstance("RSA");

        BigInteger m = new BigInteger(modeHex, 16); /* public exponent */
        BigInteger e = new BigInteger(exponentHex, 16); /* modulus */
        RSAPublicKeySpec spec = new RSAPublicKeySpec(m, e);

        RSAPublicKey pub = (RSAPublicKey) factory.generatePublic(spec);
//        Cipher enc = Cipher.getInstance("RSA");
        // 在标准JDK中的"RSA" != android里的"RSA"
        // 在标准JDK中的"RSA" == android里的"RSA/ECB/PKCS1Padding"
        Cipher enc = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        enc.init(Cipher.ENCRYPT_MODE, pub);

        byte[] encryptedContentKey = enc.doFinal(pwdString.getBytes("GB2312"));

        return new String(Hex.encodeHex(encryptedContentKey));
    }

    public static String getToken(ContextWrapper context) throws NoSuchPaddingException, NoSuchAlgorithmException, IOException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException, InvalidKeySpecException {
        SharedPreferences sharedPreferences = context.getSharedPreferences("lxwb", Activity.MODE_PRIVATE);
        String token = sharedPreferences.getString("token", "");
        if ("".equals(token)) {
            reFetchToken(context);
        }
        return token;
    }

    public static void reFetchToken(ContextWrapper context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("lxwb", Activity.MODE_PRIVATE);
        String account = sharedPreferences.getString("account", "");
        try {
//            Toast.makeText(context, "用户(" + account + ")开始获取token", Toast.LENGTH_SHORT).show();
            String token = fetchToken(account, sharedPreferences.getString("password", ""));
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("token", token);
            editor.commit();
        } catch (Exception e) {
//            Toast.makeText(context, "用户(" + account + ")获取token失败", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
}
