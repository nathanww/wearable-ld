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


import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class DreamReport extends AppCompatActivity {
    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;
    long startedTime=0;

    void postSleepData(String data, String userID) {
        try {
            RequestQueue requestQueue = Volley.newRequestQueue(this);
            String URL = "https://biostream-1024.appspot.com/lucid";


            StringRequest stringRequest = new StringRequest(Request.Method.POST, URL, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Log.i("VOLLEY", response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("VOLLEY", error.toString());
                }
            }) {
                @Override
                protected Map<String,String> getParams(){
                    Map<String,String> params = new HashMap<String, String>();
                    params.put("userID",userID);
                    params.put("data",data);
                    Log.i("sleepdata",data);
                    return params;
                }

                @Override
                protected Response<String> parseNetworkResponse(NetworkResponse response) {
                    String responseString = "";
                    if (response != null) {
                        responseString = String.valueOf(response.statusCode);
                        // can get more details such as response.headers
                    }
                    return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                }
            };

            requestQueue.add(stringRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
                            //only wipe the sleep data if we know this is the last session for the night. That way we know all the data will be written
                            editor.putString("sleepdata","");
                            editor.commit();
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
        int pid=sharedPref.getInt("pid",0);
        String sleepdata=sharedPref.getString("sleepdata","");
        Log.i("sleepdata",sleepdata.substring(0,100));
        int night=sharedPref.getInt("currentNight",0);
        if (!checkInternet()) {
            connectionAlert();
        }
        else { //if the internet is working, send the sleep data
            postSleepData(sleepdata,pid+"-night"+night);
        }
        Log.i("Dream report","Starting dream report");

        String data1="";
        String data2="";
        String data3="";

            if (sleepdata.length() > 5000) {
                data1=sleepdata.substring(0,5000);
                data2=sleepdata.substring(5000,5000*2);
                if (sleepdata.length() > 5000*2) {
                    data3=sleepdata.substring(5000*2);
                }
            }
            else {
                data1=sleepdata;
            }




        //send the sleep data to qualtrics
        Log.i("dreamreport","Loading qualtrics page,"+sleepdata.length());
        //String pageTarget="https://northwestern.az1.qualtrics.com/jfe/form/SV_6FCssjBFQNC95j0?pid="+pid+"&wakeThresh="+sharedPref.getFloat("wakeSoundThresh",-1)+"&participantType="+sharedPref.getBoolean("pType",false)+"&night="+sharedPref.getInt("currentNight",-1)+"&arousal="+sharedPref.getFloat("arousalSum2",-1)+":"+sharedPref.getInt("arousalN2",-1)+"&reportDelay="+(System.currentTimeMillis()-startedTime)/1000+"&sleepdata1="+data1+"&sleepdata2="+data2+"&sleepdata3="+data3;
        String pageTarget="https://northwestern.az1.qualtrics.com/jfe/form/SV_6FCssjBFQNC95j0?pid="+pid+"&wakeThresh="+sharedPref.getFloat("wakeSoundThresh",-1)+"&participantType="+sharedPref.getBoolean("pType",false)+"&night="+sharedPref.getInt("currentNight",-1)+"&arousalSum="+sharedPref.getFloat("arousalSum2",-1)+"&arousalN="+sharedPref.getInt("arousalN2",-1)+"&reportDelay="+(System.currentTimeMillis()-startedTime)/1000+"&highestVol="+sharedPref.getFloat("highestVol",-1);

        Log.i("pagetarget",pageTarget);
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