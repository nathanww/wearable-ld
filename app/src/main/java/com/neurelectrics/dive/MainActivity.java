package com.neurelectrics.dive;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

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

public class MainActivity extends AppCompatActivity {
    fitbitServer server;
    MediaPlayer startTraining;
    MediaPlayer training2;
    MediaPlayer lucidMusic;
    MediaPlayer signalcue;
    MediaPlayer noguidance;
    int[] delayTimes={45,45,45,45,45,70,55,65,70,80,65,60,75,75,90,120};
    int STIMULUS_LENGTH=73;  //how long is the cue sound? THis  prevents issues with it overlapping
    int delayItem=0;
    int trainingEpochs=0; //one training epoch is 1 seconds, this is used to control the timing during training
    double lastPacket=System.currentTimeMillis();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //start the Fitbit server
        server = new fitbitServer();
        try {
            server.start();
        } catch(IOException ioe) {
            Log.e("Httpd", ioe.getMessage());
        }
        Log.w("Httpd", "Web server initialized.");

        Button startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startButton.setEnabled(false);
                startTraining= MediaPlayer.create(MainActivity.this,R.raw.training1);
                training2= MediaPlayer.create(MainActivity.this,R.raw.beeps);
                lucidMusic= MediaPlayer.create(MainActivity.this,R.raw.eno1fade);
                lucidMusic.setLooping(false);

                lucidMusic.setVolume(0.8f,0.8f);
                signalcue= MediaPlayer.create(MainActivity.this,R.raw.atsignal);
                signalcue.setLooping(false);
                noguidance= MediaPlayer.create(MainActivity.this,R.raw.cueswoguidance);
                signalcue.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { //if delay item is 4, meaning we just played the instructions for the fourth time, then start the cues without guidance
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        if (delayItem==4) {
                            noguidance.start();
                        }
                    }
                });
                startTraining.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { //when the first part of the instrutionas are complete, star tthe second part
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        training2.start();
                    }
                });

                training2.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { //when the first part of the instrutionas are complete, star tthe second part
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                handleTrainingSounds(); //start the period where we just play cues and sintructions with long pauses in between.
                            }
                        }, 10000, 1000);
                    }
                });
                training2.start();
            }
        });
    }

    void handleTrainingSounds() { //handle the repeating cues during training with different inter stimulus intervals
        if (trainingEpochs == delayTimes[delayItem]+STIMULUS_LENGTH) { //we have to add the length of the stimulus because this track the onset time, not the ffset time

            lucidMusic.start();
            signalcue.start();
            trainingEpochs=1;
            if (delayItem < delayTimes.length-1) {
                delayItem++;
            }
            Log.i("cycle","start");

        }
        else {
            trainingEpochs++;
        }
    }



    //fitbitServer handles getting data from the fitbit which sends it on port 8085
    private class fitbitServer extends NanoHTTPD {

        public fitbitServer() {
            super(8085);
            Log.i("fitbit", "server start");


        }


        public Response serve(String uri, Method method,
                              Map<String, String> header,
                              Map<String, String> parameters,
                              Map<String, String> files) {
            Log.e("fitbitserver", "request");
            if (uri.indexOf("rawdata") > -1) { //recieved a data packet from the Fitbit, set the Fitbit status to good.
                Log.i("data",parameters.toString());
            }
            return newFixedLengthResponse(Response.Status.OK, "normal", "");

        }
    }
}


