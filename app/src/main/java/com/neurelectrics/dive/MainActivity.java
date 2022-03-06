package com.neurelectrics.dive;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.Task;

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

import static android.view.View.GONE;
import static java.nio.charset.StandardCharsets.UTF_8;

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

    boolean firstTraining=true;
    float sleepVolume=0;

    float oldx,oldy,oldz=0; //variables to detect sudden motion
    float MOTION_THRESH=5f; //how much motion is considered an arousal
    float ONSET_THRESH=0.95f; //how high does the rem probability have to be to trigger cueing?
    float cueVolume=0.0f;
    float CUE_VOLUME_INC=0.00075f; //how much does the cue volume increase ach second?
    int BUFFER_SIZE=60; //HOW MANY TO AVERAGE?
    boolean conFixArm=false; //whether the app will try to restart itself on exit, set to true if we need to restart the Fitbit app to fix a connection issue
    boolean DEBUG_MODE=false;

    boolean cueRunning=false;

    int ONSET_TIME=14400; //minimum time the app must be running before it will cue
    int BACKOFF_TIME=600;
    int elapsedTime=0;
    int lastArousal=(0-BACKOFF_TIME);
    ArrayList<Float> probBuffer=new ArrayList<Float>(); //buffer for averaging REM probabilities



    int[] delayTimes={0,45,45,45,45,70,55,65,70,80,65,60,75,75,90,120};
    int STIMULUS_LENGTH=20;  //how long is the cue sound? THis  prevents issues with it overlapping
    int delayItem=0;
    int trainingEpochs=0; //one training epoch is 1 seconds, this is used to control the timing during training
    double lastPacket=System.currentTimeMillis();


    void maximizeVolume() {
        AudioManager am =
                (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        am.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                0);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //keep the cpu on
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "Dive::DataAcquistion");
        wakeLock.acquire();
        wakeupHandler(); //start a loop to keep the device active
        //set up the lucid music
        lucidMusic= MediaPlayer.create(MainActivity.this,R.raw.twobeeps);
        lucidMusic.setVolume(1.0f,1.0f);
        //start the Fitbit server
        server = new fitbitServer();
        try {
            server.start();
        } catch(IOException ioe) {
            Log.e("Httpd", ioe.getMessage());
        }
        Log.w("Httpd", "Web server initialized.");
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        //get the training status--have we done the training before?
        firstTraining=sharedPref.getBoolean("initialTrainingComplete",false);

        Button stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.exit(0);
            }
        }
            );
        Button startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startButton.setVisibility(View.GONE);
                TextView instr=(TextView)  findViewById(R.id.appRunningHeader);
                instr.setVisibility(View.VISIBLE);
                stopButton.setVisibility(View.VISIBLE);
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
            elapsedTime=ONSET_TIME+50;
            BACKOFF_TIME=10;
            ONSET_THRESH=0;
            MOTION_THRESH=800F;

        }

        //start the Fitbit monitoring
        Timer fitbitMonitor=new Timer();
        fitbitMonitor.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView connectionWarning=(TextView) findViewById(R.id.connectionWarning);
                        if (System.currentTimeMillis() > lastPacket+10000) {
                            connectionWarning.setVisibility(View.VISIBLE);
                        }
                        else {
                            connectionWarning.setVisibility(View.GONE);
                        }
                    }
                });

            }
        }, 10000, 1000);



    }
    private void fixConnection() {
        conFixArm=true; //enable app to self-restart
               Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.fitbit.FitbitMobile");
        if (launchIntent != null) {
            startActivity(launchIntent);//null pointer check in case package name was not found
        }

    }

    void wakeupHandler() { //turn the screen on (if turned off) during recording period to improve acquistion reliability. Also checks the connection status and tries to reset thje connection if ti appears broken
        final Handler wakeuptimer = new Handler();
        Runnable runnableCode = new Runnable() {
            @Override
            public void run() {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                Log.i("Dive","wakeup");
                PowerManager.WakeLock powerOn = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "Dive:poweron");
                powerOn.acquire();
                powerOn.release();
                wakeuptimer.postDelayed(this, 60000);

            }
        };
        runnableCode.run();
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
                Log.i("cuedata","point1");


                    //String test=stageData.split("\"Probability( is3=1 )\":")[0];
                    //Log.d("debug",test);

                    if (cueRunning && (Math.abs(motionX-oldx) > MOTION_THRESH ||Math.abs(motionY-oldy) > MOTION_THRESH || Math.abs(motionZ-oldz) > MOTION_THRESH)) {
                        isArousal=true;
                        lastArousal=elapsedTime;

                        float arousalSum=sharedPref.getFloat("arousalSum2",0);
                        int arousalN=sharedPref.getInt("arousalN2",0);
                        if (arousalN < 4) {
                            arousalN++;
                            arousalSum = arousalSum + cueVolume;
                            editor.putFloat("arousalSum2", arousalSum);
                            editor.putInt("arousalN2", arousalN);
                            editor.commit();
                        }
                        cueVolume=0;
                    }
                    oldx=motionX;
                    oldy=motionY;
                    oldz=motionZ;


                Log.i("cuedata",elapsedTime+","+lastArousal+","+(elapsedTime-lastArousal)+","+BACKOFF_TIME);
                    //if (avgProb >= ONSET_THRESH && elapsedTime >= ONSET_TIME && elapsedTime-lastArousal >= BACKOFF_TIME) { //conditions are good for cueing
                if (avgProb >= ONSET_THRESH && elapsedTime >= ONSET_TIME && elapsedTime-lastArousal >= BACKOFF_TIME) { //cue starts if we have exceeded the threshold and keeps running until an arousal interrupts it
                    Log.i("cuedata","point2");

                        if (!cueRunning) {
                            cueRunning=true;
                            lucidMusic= MediaPlayer.create(MainActivity.this,R.raw.twobeeps);
                            lucidMusic.setVolume(cueVolume,cueVolume);
                            lucidMusic.setLooping(true);
                            lucidMusic.start();
                        }

                    }
                    else if (elapsedTime-lastArousal < BACKOFF_TIME){ //arousal, stop the cues as needed

                        if (cueRunning) {
                            lucidMusic.stop();
                            cueRunning=false;
                        }
                    }
                if (cueRunning) { //if the cueing is running, start incrementing the volume
                    cueVolume = cueVolume + CUE_VOLUME_INC;
                    //check to see if we've recorded enough arousals to set a volume cap. If we have, make sure the volume doesn't exceed the cap
                    float arousalSum = sharedPref.getFloat("arousalSum2", 0);
                    int arousalN = sharedPref.getInt("arousalN2", 0);
                    if (arousalN >= 4) {
                        float meanThresh = (arousalSum / arousalN) * 0.75f;
                        if (cueVolume > meanThresh) {
                            cueVolume = meanThresh;
                            Log.i("volume", "capped at " + meanThresh);
                        }
                    }
                    maximizeVolume(); //override any volume adjustments
                    lucidMusic.setVolume(cueVolume, cueVolume);

                }
            }
                return("time:"+System.currentTimeMillis()+",hr:"+hr+",xa:"+motionX+",ya:"+motionY+",za:"+motionZ+",xg"+gyrox+",yg"+gyroy+",zg"+gyroz+",is3:"+s3Prob+",is3avg:"+avgProb+",cueing:"+cueRunning+",vol:"+cueVolume+",elapsed:"+(elapsedTime));

            } //no stage info available
            else {
                Log.e("cuing","No stage");
                return "";
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        public Response serve(String uri, Method method,
                              Map<String, String> header,
                              Map<String, String> parameters,
                              Map<String, String> files) {
            //Log.e("fitbitserver", "request");
            lastPacket=System.currentTimeMillis();
            if (uri.indexOf("rawdata") > -1) { //recieved a data packet from the Fitbit, set the Fitbit status to good.
                Log.i("data",parameters.toString());
                String result=handleStaging(parameters.toString());
                Log.i("cuedata",result);
                String temp=parameters.toString()+","+result+"\n";
                printWriter.print(temp);
                printWriter.flush();
                //how send telemetry
                try {
                    String urlString = "https://biostream-1024.appspot.com/sendps?"+"user=" + sharedPref.getString("userID","DEFAULT") + "&data=" + URLEncoder.encode(result, StandardCharsets.UTF_8.toString());
                    URL url = new URL(urlString);
                    url.openStream();
                } catch (Exception e) {
                    Log.e("telemetry", "error");
                    e.printStackTrace();
                }

            }
            return newFixedLengthResponse(Response.Status.OK, "normal", "");

        }
    }
}


