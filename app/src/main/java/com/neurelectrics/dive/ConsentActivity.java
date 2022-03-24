package com.neurelectrics.dive;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ConsentActivity extends AppCompatActivity {
    private boolean checkInternet() { //returns true if internet is connected
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        try {
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress("8.8.8.8", 53), 1500);
            sock.close();
            return true;
        } catch (IOException e) { return false; }
    }


    private void connectionAlert() {
        AlertDialog.Builder builder;
        builder = new AlertDialog.Builder(this);
        builder.setMessage("Unable to reach the Internet. Make sure you're connected and try again.")
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });

        //Creating dialog box
        AlertDialog alert = builder.create();
        //Setting the title manually
        alert.setTitle("No connection");
        alert.show();
    }
    @Override
    public void onBackPressed() {
        // Not calling **super**, disables back button in current screen.
    }
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        System.exit(0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        if (!checkInternet()) {
            connectionAlert();
        }
        Log.i("consent","starting consent");
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