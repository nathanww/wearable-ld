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
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class DreamReport extends AppCompatActivity {
    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;
    long startedTime=0;
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

    private void askWhereToGo() {
        if (System.currentTimeMillis() - startedTime > (1800*1000)) { //if we've waited more than 30 min to fill out the dream report, it's a delayed report and we shouldn't offer the option to go back to sleep
            editor.putInt("taskStatus",7);
            editor.commit();
            finish();
        }
        else {
            AlertDialog.Builder builder;
            builder = new AlertDialog.Builder(this);
            builder.setMessage("Do you want to go back to sleep or get up for the day?")
                    .setCancelable(false)
                    .setPositiveButton("Go back to sleep", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            editor.putInt("taskStatus", 5);
                            editor.commit();
                            finish();
                        }
                    })
                    .setNegativeButton("Get up for the day", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            editor.putInt("taskStatus", 7);
                            editor.commit();
                            finish();
                        }
                    });

            //Creating dialog box
            AlertDialog alert = builder.create();
            //Setting the title manually
            alert.setTitle("Back to sleep?");
            alert.show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dream_report);
        sharedPref=getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        editor=sharedPref.edit();
        startedTime=System.currentTimeMillis();

        if (!checkInternet()) {
            connectionAlert();
        }
        else { //if the internet is working, send the sleep data to IFTTT/google
            try {
                int pid=sharedPref.getInt("pid",-1);

                String urlString = "https://maker.ifttt.com/trigger/luciddata/with/key/kYjllPFEGolhZUDSPQsFRHxRPr7pZgSixnXCVM8UDIR?value1="+pid+"&value2="+sharedPref.getInt("currentNight",-1)+"&value3="+URLEncoder.encode(sharedPref.getString("sleepdata",""), StandardCharsets.UTF_8.toString());
                URL url = new URL(urlString);
                url.openStream();
                editor.putString("sleepdata","");
            } catch (Exception e) {
                Log.e("telemetry", "error");
                e.printStackTrace();
            }
        }
        Log.i("Dream report","Starting dream report");
        //get the participant ID if it exists, if not generate a new one
        int pid=sharedPref.getInt("pid",-1);

        //send the sleep data to qualtrics
        Log.i("dreamreport","Loading qualtrics page");
        String pageTarget="https://northwestern.az1.qualtrics.com/jfe/form/SV_6FCssjBFQNC95j0?pid="+pid+"&wakeThresh="+sharedPref.getFloat("wakeSoundThresh",-1)+"&participantType="+sharedPref.getBoolean("pType",false)+"&night="+sharedPref.getInt("currentNight",-1)+"&arousal="+sharedPref.getFloat("arousalSum2",-1)+":"+sharedPref.getInt("arousalN2",-1)+"&reportDelay="+(System.currentTimeMillis()-startedTime)/1000;
        WebView wv = (WebView) findViewById(R.id.reportView);
        wv.loadUrl(pageTarget);
        WebSettings webSettings = wv.getSettings();
        webSettings.setJavaScriptEnabled(true);
        Context con=getApplicationContext();
        wv.setWebViewClient(new WebViewClient() {

            public void onPageFinished(WebView view, String url) {
                Log.i("dreamreport","pageload finished");

                if (url.indexOf("github") > -1) {
                    Log.i("squrl",url);
                    Log.i("sq","complete");
                    askWhereToGo();

                }
            }
        });


    }
}