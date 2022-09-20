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
import java.util.Random;
import java.util.function.Consumer;

public class sleepQuestion extends AppCompatActivity {

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
        setContentView(R.layout.activity_sleep_question);
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        if (!checkInternet()) {
            connectionAlert();
        }
        Log.i("sleep questinnaire","starting questionnaire");
        //get the participant ID if it exists, if not generate a new one
        int pid=sharedPref.getInt("pid",-1);
        if (pid == -1) {  //no existing ID
            Random rand = new Random();
            pid=rand.nextInt(2147483647);
            editor.putInt("pid",pid);
            editor.putBoolean("algo",false);
            //set the participant type (control or real)
            editor.putBoolean("pType",rand.nextBoolean());
            //set the escalation type for accelerometer mode (escalate or don't)
            editor.putBoolean("acc_mode_escalate",rand.nextBoolean());
            editor.commit();
        }
        String pageTarget="https://northwestern.az1.qualtrics.com/jfe/form/SV_6R1LomQ0Zmj6Ts2?participantID="+pid;
        WebView wv = (WebView) findViewById(R.id.sqView);
        wv.loadUrl(pageTarget);
        WebSettings webSettings = wv.getSettings();
        webSettings.setJavaScriptEnabled(true);
        Context con=getApplicationContext();
        wv.setWebViewClient(new WebViewClient() {

            public void onPageFinished(WebView view, String url) {
                if (url.indexOf("github") > -1) {
                    Log.i("squrl",url);
                    Log.i("sq","complete");

                    editor.putInt("taskStatus",3);
                    editor.commit();
                    finish();
                }
            }
        });

    }
}