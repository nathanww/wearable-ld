package com.neurelectrics.dive;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class sleepQuestion extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep_question);
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        Log.i("sleep equestinnaire","starting questionnaire");
        String pageTarget="https://docs.google.com/forms/d/e/1FAIpQLScoOcp3gFlGw6U8ais96aK71CEUjLdsno1Mbp2TbtbMSKa_jQ/viewform";
        setContentView(R.layout.activity_consent);
        WebView wv = (WebView) findViewById(R.id.consentView);
        wv.loadUrl(pageTarget);
        WebSettings webSettings = wv.getSettings();
        webSettings.setJavaScriptEnabled(true);
        Context con=getApplicationContext();
        wv.setWebViewClient(new WebViewClient() {

            public void onPageFinished(WebView view, String url) {
                if (url.indexOf("formResponse") > -1) {
                    Log.i("consenturl",url);
                    Log.i("consent","consent complete");
                    editor.putInt("taskStatus",1);
                    editor.commit();
                    finish();
                }
            }
        });

    }
}