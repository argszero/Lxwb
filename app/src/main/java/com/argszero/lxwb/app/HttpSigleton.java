package com.argszero.lxwb.app;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * Created by shaoaq on 8/3/15.
 */
public class HttpSigleton {
    private static DefaultHttpClient instance = null;
    protected HttpSigleton() {

    }
    public static DefaultHttpClient getInstance() {
        if(instance == null) {
            instance =new DefaultHttpClient();
        }
        return instance;
    }
}
