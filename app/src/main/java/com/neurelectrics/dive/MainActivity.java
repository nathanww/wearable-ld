package com.neurelectrics.dive;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
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
    Timer trainingTimer;
    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;
    float oldx,oldy,oldz=0; //variables to detect sudden motion
    float MOTION_THRESH=3f; //how much motion is considered an arousal
    float ONSET_THRESH=0.99f; //how high does the rem probability have to be to trigger cueing?
    float cueVolume=0.0f;
    float CUE_VOLUME_INC=0.01f; //how much does the cue volume increase ach second?
    int BUFFER_SIZE=60; //HOW MANY TO AVERAGE?
    boolean DEBUG_MODE=false;

    boolean cueRunning=false;
    int lastArousal=0;
    int ONSET_TIME=14400; //minimum time the app must be running before it will cue
    int BACKOFF_TIME=600;
    int elapsedTime=0;

    ArrayList<Float> probBuffer=new ArrayList<Float>(); //buffer for averaging REM probabilities



    int[] delayTimes={0,45,45,45,45,70,55,65,70,80,65,60,75,75,90,120};
    int STIMULUS_LENGTH=73;  //how long is the cue sound? THis  prevents issues with it overlapping
    int delayItem=0;
    int trainingEpochs=0; //one training epoch is 1 seconds, this is used to control the timing during training
    double lastPacket=System.currentTimeMillis();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //set up the lucid music
        lucidMusic= MediaPlayer.create(MainActivity.this,R.raw.eno1fade);
        lucidMusic.setVolume(1.0f,1.0f);
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
                training2= MediaPlayer.create(MainActivity.this,R.raw.training2);

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
                        trainingTimer=new Timer();
                        trainingTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                handleTrainingSounds(); //start the period where we just play cues and sintructions with long pauses in between.
                            }
                        }, 10000, 1000);
                    }
                });
                startTraining.start();
            }
        });
        if (DEBUG_MODE) {
            ONSET_TIME=0;
            BACKOFF_TIME=10;
            ONSET_THRESH=0;

        }

        //start the Fitbit monitoring
        Timer fitbitMonitor=new Timer();
        fitbitMonitor.schedule(new TimerTask() {
            @Override
            public void run() {
                if (System.currentTimeMillis() > lastPacket+10000) {

                }
            }
        }, 0, 10000);

        sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        editor=sharedPref.edit();

    }

    void handleTrainingSounds() { //handle the repeating cues during training with different inter stimulus intervals
        if (trainingEpochs == delayTimes[delayItem]+STIMULUS_LENGTH) { //we have to add the length of the stimulus because this track the onset time, not the ffset time

            lucidMusic.start();
            if (delayItem < 4) {
                signalcue.start();
            }
            trainingEpochs=1;
            if (delayItem < delayTimes.length-1) {
                delayItem++;
            }
            else {
                trainingTimer.cancel();
                trainingTimer.purge();
            }
            Log.i("cycle","start");

        }
        else {
            trainingEpochs++;
        }
    }



    //fitbitServer handles getting data from the fitbit which sends it on port 8085
    private class fitbitServer extends NanoHTTPD {
        FileWriter fileWriter;
        PrintWriter printWriter;

        public fitbitServer() {
            super(8085);
            Log.i("fitbit", "server start");
            try {
                fileWriter = new FileWriter(getApplicationContext().getExternalFilesDir(null) + "/fitbitdata.txt", true);
                Log.i("filedir",getApplicationContext().getExternalFilesDir(null)+"");
                printWriter = new PrintWriter(fileWriter);
            }
            catch (Exception e) {
                Log.e("Server","Error opening file");
            }

        }

        /*dummy stage data
        {data={"hr":77,"accx":-3.365152359008789,"accy":3.8511905670166016,"accz":8.557137489318848,"gyrox":1.1430635452270508,"gyroy":0.11505126953125,"gyroz":-0.23862648010253906,"seconds":286,"aqTime":1638816782462,"b":96}STAGE{"Probability( is3=0 )":0.9999999999999639,"Probability( is3=1 )":3.6029740452123885e-14,"Most Likely is3":0}, NanoHttpd.QUERY_STRING=data=%7B%22hr%22%3A77%2C%22accx%22%3A-3.365152359008789%2C%22accy%22%3A3.8511905670166016%2C%22accz%22%3A8.557137489318848%2C%22gyrox%22%3A1.1430635452270508%2C%22gyroy%22%3A0.11505126953125%2C%22gyroz%22%3A-0.23862648010253906%2C%22seconds%22%3A286%2C%22aqTime%22%3A1638816782462%2C%22b%22%3A96%7DSTAGE%7B%22Probability(%20is3%3D0%20)%22%3A0.9999999999999639%2C%22Probability(%20is3%3D1%20)%22%3A3.6029740452123885e-14%2C%22Most%20Likely%20is3%22%3A0%7D}*/
        private float average(ArrayList<Float> data) {
            float sum = 0;
            for (int i=0; i< data.size(); i++) {
                sum += data.get(i);
            }
            return sum / data.size();
        }
        String handleStaging(String stageData) {
            elapsedTime++;
            //logic for controlling cueing
            if (stageData.indexOf("\"Probability( is3=1 )\":") > -1) {
                float s3Prob=Float.parseFloat(stageData.split(":")[12].split(",")[0]);
                float motionX=Float.parseFloat(stageData.split(":")[2].split(",")[0]);
                float motionY=Float.parseFloat(stageData.split(":")[3].split(",")[0]);
                float motionZ=Float.parseFloat(stageData.split(":")[4].split(",")[0]);
                float hr=Float.parseFloat(stageData.split(":")[1].split(",")[0]);
                float gyrox=Float.parseFloat(stageData.split(":")[5].split(",")[0]);
                float gyroy=Float.parseFloat(stageData.split(":")[6].split(",")[0]);
                float gyroz=Float.parseFloat(stageData.split(":")[7].split(",")[0]);
                probBuffer.add(s3Prob);
                if (probBuffer.size() > BUFFER_SIZE) {
                    probBuffer.remove(0);
                }
                float avgProb=average(probBuffer);


                boolean isArousal=false;
            if (elapsedTime >= ONSET_TIME) {


                    //String test=stageData.split("\"Probability( is3=1 )\":")[0];
                    //Log.d("debug",test);

                    if (cueRunning && (Math.abs(motionX-oldx) > MOTION_THRESH ||Math.abs(motionY-oldy) > MOTION_THRESH || Math.abs(motionZ-oldz) > MOTION_THRESH)) {
                        isArousal=true;
                        lastArousal=elapsedTime;

                        float arousalSum=sharedPref.getFloat("arousalSum",0);
                        int arousalN=sharedPref.getInt("arousalN",0);
                        if (arousalN < 4) {
                            arousalN++;
                            arousalSum = arousalSum + cueVolume;
                            editor.putFloat("arousalSum", arousalSum);
                            editor.putInt("arousalN", arousalN);
                            editor.commit();
                        }
                        cueVolume=0;
                    }
                    oldx=motionX;
                    oldy=motionY;
                    oldz=motionZ;


                    //cueing logic control
                    if (avgProb >= ONSET_THRESH && elapsedTime >= ONSET_TIME && elapsedTime-lastArousal >= BACKOFF_TIME) { //conditions are good for cueing
                        if (!cueRunning) {
                            cueRunning=true;
                            lucidMusic= MediaPlayer.create(MainActivity.this,R.raw.eno1fade);
                            lucidMusic.setVolume(cueVolume,cueVolume);
                            lucidMusic.setLooping(true);
                            lucidMusic.start();
                        }
                        cueVolume=cueVolume+CUE_VOLUME_INC;
                        //check to see if we've recorded enough arousals to set a volume cap. If we have, make sure the volume doesn't exceed the cap
                        float arousalSum=sharedPref.getFloat("arousalSum",0);
                        int arousalN=sharedPref.getInt("arousalN",0);
                        if (arousalN >= 4) {
                            float meanThresh=(arousalSum/arousalN)*0.75f;
                            if (cueVolume > meanThresh) {
                                cueVolume=meanThresh;
                                Log.i("volume", "capped at "+meanThresh);
                            }
                        }
                        lucidMusic.setVolume(cueVolume,cueVolume);
                    }
                    else {

                        if (cueRunning) {
                            lucidMusic.stop();
                            cueRunning=false;


                        }
                    }
            }
                return(System.currentTimeMillis()+","+hr+","+motionX+","+motionY+","+motionZ+","+gyrox+","+gyroy+","+gyroz+","+s3Prob+","+avgProb+","+cueRunning+","+cueVolume+","+(elapsedTime-lastArousal));

            } //no stage info available
            else {
                Log.e("cuing","No stage");
                return "";
            }
        }

        public Response serve(String uri, Method method,
                              Map<String, String> header,
                              Map<String, String> parameters,
                              Map<String, String> files) {
            //Log.e("fitbitserver", "request");
            if (uri.indexOf("rawdata") > -1) { //recieved a data packet from the Fitbit, set the Fitbit status to good.
                Log.i("data",parameters.toString());
                String result=handleStaging(parameters.toString());
                Log.i("cuedata",result);
                String temp=parameters.toString()+","+result+"\n";
                printWriter.print(temp);
                printWriter.flush();

            }
            return newFixedLengthResponse(Response.Status.OK, "normal", "");

        }
    }
}


