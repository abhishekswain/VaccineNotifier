package com.abhishek.vaccinenotifier.activities;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.abhishek.vaccinenotifier.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class NotificationActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notification);

        WebView mWebView = (WebView) findViewById(R.id.notificationWeb);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);


        BufferedReader reader = null;
        try {
            File file = new File(getFilesDir().getAbsolutePath().toString() + File.separator + "vaccinereport" + File.separator + "index.html");

            reader = new BufferedReader(new FileReader(file.getAbsoluteFile()));

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        StringBuilder stringBuilder = new StringBuilder();
        String line = null;
        String ls = System.getProperty("line.separator");
        while (true) {
            try {
                if (!((line = reader.readLine()) != null)) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            stringBuilder.append(line);
            //stringBuilder.append(ls);
        }
// delete the last new line separator
        //stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String content = stringBuilder.toString();

        webSettings.setDefaultTextEncodingName("utf-8");
        mWebView.loadDataWithBaseURL(null, content, "text/html", "utf-8", null);
    }
}