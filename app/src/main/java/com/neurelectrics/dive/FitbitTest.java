package com.neurelectrics.dive;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import fi.iki.elonen.NanoHTTPD;

public class FitbitTest extends AppCompatActivity {
fitbitTestServer server;
boolean fitbitData=false;

void confirmFitbit() {
    Log.i("server","got data");
    runOnUiThread(new Runnable() {
        @Override
        public void run() {
            LinearLayout instructions=(LinearLayout) findViewById(R.id.instructions);
            TextView connected=(TextView) findViewById(R.id.fitbitConnected);
            TextView connected2=(TextView) findViewById(R.id.fitbitInstructions);
            TextView connectionIssue=(TextView) findViewById(R.id.connectionIssue);
            connected.setVisibility(View.VISIBLE);
            connected2.setVisibility(View.VISIBLE);
            //instructions.setVisibility(View.GONE);
            connectionIssue.setVisibility(View.GONE);

            SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt("taskStatus",2);
            editor.commit();
        }

    });
    Timer returnToMain=new Timer();
    returnToMain.schedule(new TimerTask() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    server.stop();
                    finish();
                }

            });

        }
    }, 5000, 10000);
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
        setContentView(R.layout.activity_fitbit_test);

        Button install = (Button) findViewById(R.id.fitbitInstall);
        install.setOnClickListener(new View.OnClickListener() {
                                       @Override
                                       public void onClick(View v) {
                                        //fitbit app URL: https://gallery.fitbit.com/details/80917cd9-4e00-4842-b59f-41aa23209254
                                           Intent i = new Intent(Intent.ACTION_VIEW);
                                           i.setData(Uri.parse("https://gallery.fitbit.com/details/80917cd9-4e00-4842-b59f-41aa23209254"));
                                           startActivity(i);
                                       }
                                   }
        );

        Button connect = (Button) findViewById(R.id.connectButton);
        Timer fitbitMonitor=new Timer();
        server = new fitbitTestServer();
        connect.setOnClickListener(new View.OnClickListener() {
                                       @Override
                                       public void onClick(View v) {

                                           try {
                                               server.start();
                                           } catch (IOException ioe) {
                                               Log.e("Httpd", ioe.getMessage());
                                           }
                                           fitbitMonitor.schedule(new TimerTask() {
                                               @Override
                                               public void run() {
                                                   runOnUiThread(new Runnable() {
                                                       @Override
                                                       public void run() {
                                                           TextView connectionIssue=(TextView) findViewById(R.id.connectionIssue);
                                                           if (!fitbitData) {
                                                               Log.i("connection","issue");
                                                               connectionIssue.setVisibility(View.VISIBLE);
                                                           }
                                                           else {
                                                               connectionIssue.setVisibility(View.GONE);
                                                           }
                                                       }

                                                   });

                                               }
                                           }, 10000, 1000);
                                       }
                                   }
        );



    }



private class fitbitTestServer extends NanoHTTPD {


    public fitbitTestServer() {
        super(8085);

    }

        public Response serve(String uri, Method method,
                Map<String, String> header,
                Map<String, String> parameters,
                Map<String, String> files) {
            fitbitData=true;
            confirmFitbit();
            return newFixedLengthResponse(Response.Status.OK, "normal", "");

        }

    }


}
