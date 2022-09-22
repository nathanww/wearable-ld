package com.neurelectrics.dive;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.BatteryManager;
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
import org.w3c.dom.Text;

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

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    fitbitServer server;
    accServer accserver;
    MediaPlayer startTraining;
    MediaPlayer training2;
    MediaPlayer lucidMusic;
    MediaPlayer signalcue;
    MediaPlayer noguidance;
    Timer trainingTimer;
    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;
    SensorManager sensorManager;
    Context mainContext;
    double ax,ay,az=-1;   // accelerometer values
    double oldax,olday,oldaz=-1;   // accelerometer values
    boolean firstTraining=true;
    float sleepVolume=0;
    boolean enableSleepCueing=true;
    float oldx,oldy,oldz=0; //variables to detect sudden motion
    float MOTION_THRESH=5f; //how much motion is considered an arousal
    float ONSET_THRESH=0.5f; //how high does the rem probability have to be to trigger cueing?
    float cueVolume=0.0f;
    float CUE_VOLUME_INC=0.00075f; //how much does the cue volume increase ach second?
    int BUFFER_SIZE=360; //HOW MANY TO AVERAGE?
    int MOTION_BUFFER_SIZE=800; //how many seconds to look at when counting motion
    boolean conFixArm=false; //whether the app will try to restart itself on exit, set to true if we need to restart the Fitbit app to fix a connection issue
    boolean DEBUG_MODE=false;
    boolean fitbitMode;
    boolean shamNight=true;
    boolean cueRunning=false;
    int ONSET_TIME=14400; //minimum time the app must be running before it will cue
    int MOTION_ONSET_TIME=18000;
    int BACKOFF_TIME=600;
    float MOTION_PERCENT=0.1f; //percentile for epochs with no motion. 0.05 means that 90% of samples in the baseline have FEWER epochs with no motion
    int elapsedTime=0;
    int lastArousal=(0-BACKOFF_TIME);
    boolean acc_mode_escalate=true; //does the volume escalate in accelerometer mode? This is randomly assinged

    ArrayList<Float> probBuffer=new ArrayList<Float>(); //buffer for averaging REM probabilities
    ArrayList<Integer> motionBuffer=new ArrayList<Integer>(); //buffer for averaging periods with no detectable motion
    ArrayList<Integer> baselineBuffer=new ArrayList<Integer>(); //motion baseline




    int[] delayTimes={0,45,45,45,45,70,55,65,70,80,65,60,75,75,90,120};
    int STIMULUS_LENGTH=73;  //how long is the cue sound? THis  prevents issues with it overlapping
    int delayItem=0;
    int trainingEpochs=0; //one training epoch is 1 seconds, this is used to control the timing during training

    int currentNight;
    double lastPacket=System.currentTimeMillis();

    @Override
    public void onBackPressed() {
        // Not calling **super**, disables back button in current screen.
    }

    public boolean isPluggedIn() {
        Intent intent = this.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }

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
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "Dive::DataAcquistion");
            wakeLock.acquire();
        }
        catch (Exception e) {// wakelock can cause an exception on some samsung devices??
            Log.i("datacollector","Couldn't acquire wakelock");
        }
        wakeupHandler(); //start a loop to keep the device active

        //start monitoring the accelerometer
        sensorManager=(SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) { //if the acceleromter exists
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        }
        mainContext=this;
        //set up the lucid music
        lucidMusic= MediaPlayer.create(MainActivity.this,R.raw.trainingsignalshort);
        lucidMusic.setVolume(1.0f,1.0f);

        //get ether this is a Fitbit user or not
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        fitbitMode=sharedPref.getBoolean("fitbitMode",true);
        fitbitMode=false;
        if (fitbitMode) {
            //if user is in fitbit mode, start the Fitbit server
            server = new fitbitServer();
            try {
                server.start();
            } catch (IOException ioe) {
                Log.e("Httpd", ioe.getMessage());
            }
            Log.w("Httpd", "Web server initialized.");
        }
        else { //otherwise, start the acceleromter server
            acc_mode_escalate=sharedPref.getBoolean("acc_mode_escalate",false);
            accserver=new accServer();
            accserver.start();
            //display the messages about phone position
            TextView pib=(TextView) findViewById(R.id.phoneInBed);
            pib.setVisibility(View.VISIBLE);
        }



        //get the training status--have we done the training before?
        firstTraining=sharedPref.getBoolean("firstTraining",true);

        Button stopButton = (Button) findViewById(R.id.reportButton);
        Button abortButton = (Button) findViewById(R.id.abortButton);
        abortButton.setOnClickListener(new View.OnClickListener() {
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
                if (isPluggedIn()) {
                    if (System.currentTimeMillis() > lastPacket+3000 && fitbitMode) { //display an error if we don't have a fitbit connection and we should
                        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                        alertDialog.setTitle("Cannot start");
                        alertDialog.setMessage("The Fitbit is not connected. Make sure the Dream app is running on the Fitbit. If the Dream app is running, try exiting it, syncing the Fitbit, and restarting the Dream app.");
                        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        alertDialog.show();
                    }
                    else { //Fitbit is connected normally
                        startButton.setVisibility(View.GONE);
                        abortButton.setVisibility(View.VISIBLE);
                        TextView instr = (TextView) findViewById(R.id.appRunningHeader);
                        instr.setVisibility(View.VISIBLE);
                        TextView startInstructions = (TextView) findViewById(R.id.startInstructions);
                        startInstructions.setVisibility(GONE);
                        TextView header = (TextView) findViewById(R.id.header);
                        header.setVisibility(GONE);
                        if (firstTraining) {
                            startTraining = MediaPlayer.create(MainActivity.this, R.raw.training1);
                            training2 = MediaPlayer.create(MainActivity.this, R.raw.training2);
                        } else {
                            startTraining = MediaPlayer.create(MainActivity.this, R.raw.experimental);
                            training2 = MediaPlayer.create(MainActivity.this, R.raw.blank);

                        }


                        lucidMusic.setLooping(false);

                        lucidMusic.setVolume(0.8f, 0.8f);
                        signalcue = MediaPlayer.create(MainActivity.this, R.raw.atsignal);
                        signalcue.setLooping(false);
                        noguidance = MediaPlayer.create(MainActivity.this, R.raw.cueswoguidance);
                        signalcue.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { //if delay item is 4, meaning we just played the instructions for the fourth time, then start the cues without guidance
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                if ((firstTraining && delayItem == 4) || (!firstTraining && delayItem == 1)) {
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
                                trainingTimer = new Timer();
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
                }
                else {//phone is not connected to power, so show an alert
                    AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                    alertDialog.setTitle("Phone not plugged in");
                    alertDialog.setMessage("The phone must be plugged in to its charger to start");
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();

                }
            }
        });
        if (DEBUG_MODE) {
            elapsedTime=MOTION_ONSET_TIME+50;
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
                        if (System.currentTimeMillis() > lastPacket+3000 && sharedPref.getInt("taskStatus",0) < 5 && fitbitMode) { //show the message if we have a connection problem and also we're not in sleep mode (if the connection dropped during sleep there's no point in bothering the user about it)
                            connectionWarning.setVisibility(View.VISIBLE);
                        }
                        else {
                            connectionWarning.setVisibility(View.GONE);
                        }
                    }
                });

            }
        }, 3000, 1000);


    //jump back into sleep mode if the task status is 5
        int taskStatus = sharedPref.getInt("taskStatus", 0); //get where we are in the experiment
        if (taskStatus == 5) {
            switchToSleepMode();
            ONSET_TIME=600;
        }

        //if this is the first night, or this is an active participant, then turn the sham mode off
        if ((sharedPref.getBoolean("pType",true)) || sharedPref.getInt("currentNight",0) > 6) {
            shamNight=false;
        }

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
                try {
                    PowerManager.WakeLock powerOn = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "Dive:poweron");
                    powerOn.acquire();
                    powerOn.release();
                }
                catch( Exception e) {//wakelock causes exception on some samsung devices?
                    Log.i("datacollector","Couldn't acquire wakelock");
                }
                wakeuptimer.postDelayed(this, 30000);

            }
        };
        runnableCode.run();
    }
    void switchToSleepMode() {
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        runOnUiThread (new Thread(new Runnable() {
            public void run() {
                Button abortButton = (Button) findViewById(R.id.abortButton);
                abortButton.setVisibility(GONE);
                Button startButton = (Button) findViewById(R.id.startButton);
                startButton.setVisibility(GONE);
                TextView header=(TextView) findViewById(R.id.header);
                header.setVisibility(GONE);
                TextView instr=(TextView) findViewById(R.id.startInstructions);
                instr.setVisibility(GONE);
                Button stopButton = (Button) findViewById(R.id.reportButton);
                stopButton.setVisibility(View.VISIBLE);
                stopButton.setOnClickListener(new View.OnClickListener() {
                                                   @Override
                                                   public void onClick(View v) {
                                                    editor.putInt("taskStatus",6);
                                                    editor.putLong("startedTime",System.currentTimeMillis());
                                                    editor.commit();
                                                    finish();
                                                   }
                                               }
                );
                TextView runningHeader=(TextView)findViewById(R.id.appRunningHeader);
                runningHeader.setVisibility(GONE);
                TextView wakeHeader=(TextView)findViewById(R.id.wakeHeader);
                wakeHeader.setVisibility(View.VISIBLE);

                editor.putInt("taskStatus",5);
                editor.commit();

            }
        }));


        editor.putBoolean("firstTraining",false);
        editor.commit();
        enableSleepCueing=true;


    }
    void handleTrainingSounds() { //handle the repeating cues during training with different inter stimulus intervals
        if (trainingEpochs == delayTimes[delayItem]+STIMULUS_LENGTH) { //we have to add the length of the stimulus because this track the onset time, not the ffset time

            lucidMusic.start();
            if (firstTraining) { //play the instructions after first four stimuli on first night, but only after the first stimulus on subsequent nights
                if (delayItem < 4) {
                    signalcue.start();
                }
            }
                else { //not the first training
                    if (delayItem == 0) {
                        signalcue.start();
                    }
                }


            trainingEpochs=1;
            if (delayItem < delayTimes.length-1) {
                delayItem++;
            }
            else { //we have reached the end of the training sequence!
                trainingTimer.cancel();
                trainingTimer.purge();
                //switch from training mode to sleep mode
                switchToSleepMode();
            }
            Log.i("cycle","start");

        }
        else {
            trainingEpochs++;
        }
    }

    //acceleromter functions
    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType()==Sensor.TYPE_ACCELEROMETER){
            ax=event.values[0];
            ay=event.values[1];
            az=event.values[2];
        }
    }


    private class accServer { //fitbitless server that uses the phone's accelerometer instead
        SharedPreferences sharedPref;
        SharedPreferences.Editor editor;
        Handler packetHandler;
        Runnable run;
        float soundVolume;
        boolean everCued=false;
        public accServer() {
            sharedPref= getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
            editor=sharedPref.edit();
            packetHandler=new Handler();
            Log.i("accserver","accserver initialized");
            soundVolume=sharedPref.getFloat("wakeSoundThresh",0.01f);
        }
        private int sum(ArrayList<Integer> data) {
            int sum = 0;
            for (int i=0; i< data.size(); i++) {

                sum += data.get(i);
            }
            return sum;
        }

        private float compare(ArrayList<Integer> data,int compvalue) {
            float sum = 0;
            for (int i=0; i< data.size(); i++) {
                if (data.get(i) > compvalue) { //we use "greater than" rather than greater than or equal so if the acceleormeter gets stuck people will still get cued
                    sum++;
                }
            }
            return sum/data.size();
        }

        public void start() { //when accserver is started, poll the phone's sensors every second and act base don that
            packetHandler.postDelayed( run = new Runnable() {
                public void run() {
                    //reset the accelerometer
                    if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) { //if the acceleromter exists
                        sensorManager.unregisterListener(MainActivity.this);
                        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
                        sensorManager.registerListener(MainActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
                    }
                    elapsedTime++;
                    double result=Math.abs(ax-oldax)+Math.abs(ay-olday)+Math.abs(az-oldaz);
                    oldax=ax;
                    olday=ay;
                    oldaz=az;
                    Log.i("motionresult",""+result);
                    if (result < 0.01) { //this counts as "no motion"
                        motionBuffer.add(1);
                    }
                    else {
                        motionBuffer.add(0);
                    }
                    if (motionBuffer.size() > MOTION_BUFFER_SIZE) {
                        motionBuffer.remove(0);
                    }
                    int count=sum(motionBuffer);
                    if (elapsedTime % 10 == 0) { //every 10 seconds, append the count to the baseline
                        baselineBuffer.add(count);
                    }
                    float comparison=compare(baselineBuffer,count);
                    Log.i("motioncount",""+count);
                    if (elapsedTime >= MOTION_ONSET_TIME) { //we are in the window where cueing can start
                        //cueing can start if we exceed the threhsold, or if we ever exceedd the threshold and are running in no-offset mode
                        if ((comparison <= MOTION_PERCENT || (everCued && sharedPref.getBoolean("acc_mode_offset",true)==false)) && enableSleepCueing) { //cue starts if we have exceeded the threshold and keeps running until an arousal interrupts it
                            Log.i("cuedata", "startcue-motion");
                            everCued=true;
                            maximizeVolume();
                            if (!cueRunning && !shamNight) {
                                cueRunning = true;
                                lucidMusic = MediaPlayer.create(MainActivity.this, R.raw.combinedsignal);
                                lucidMusic.setVolume(soundVolume, soundVolume);
                                lucidMusic.setLooping(true);

                                lucidMusic.start();

                            }
                            if (acc_mode_escalate) { //if the volume is set to escalate, then do that
                                soundVolume = soundVolume + CUE_VOLUME_INC;
                            }
                            if (soundVolume > sharedPref.getFloat("highestVol", 0.0f)) {
                                editor.putFloat("highestVol", soundVolume);
                            }
                            editor.putInt("totalCues", sharedPref.getInt("totalCues", 0) + 1);
                            editor.apply();


                        } else { //REM end?
                            soundVolume = sharedPref.getFloat("wakeSoundThresh", 0.01f);
                            if (cueRunning && !shamNight) {
                                lucidMusic.stop();
                                cueRunning = false;
                            }
                        }
                    }


                    String status="A,"+System.currentTimeMillis()+","+cueRunning+","+soundVolume+","+(elapsedTime)+","+String.format("%.5f", ax)+","+String.format("%.5f", ay)+","+String.format("%.5f", az)+","+count+","+comparison;
                    String current=sharedPref.getString("sleepdata","");
                    editor.putString("sleepdata",current+"##"+status);
                    editor.apply();
                    packetHandler.postDelayed(run, 1000);
                }
            }, 10);

        }

    }


    //fitbitServer handles getting data from the fitbit which sends it on port 8085
    private class fitbitServer extends NanoHTTPD {
        SharedPreferences sharedPref;
        SharedPreferences.Editor editor;
        public fitbitServer() {
            super(8085);
            Log.i("fitbit", "server start");
            sharedPref= getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
            editor=sharedPref.edit();
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
            float s3Prob=-1;
            float motionX=-1;
            float motionY=-1;
            float motionZ=-1;
            float hr=-1;
            float gyrox=-1;
            float gyroy=-1;
            float gyroz=-1;
            float avgProb=-1;
            //logic for controlling cueing
            if (stageData.indexOf("\"Probability( is3=1 )\":") > -1) {
                s3Prob = Float.parseFloat(stageData.split(":")[12].split(",")[0]);
                motionX = Float.parseFloat(stageData.split(":")[2].split(",")[0]);
                motionY = Float.parseFloat(stageData.split(":")[3].split(",")[0]);
                motionZ = Float.parseFloat(stageData.split(":")[4].split(",")[0]);
                hr = Float.parseFloat(stageData.split(":")[1].split(",")[0]);
                gyrox = Float.parseFloat(stageData.split(":")[5].split(",")[0]);
                gyroy = Float.parseFloat(stageData.split(":")[6].split(",")[0]);
                gyroz = Float.parseFloat(stageData.split(":")[7].split(",")[0]);
                if (gyrox < -90) { //this can only happen if we are on a device with no gyro, this activates the more relaxed REM criteria
                    ONSET_THRESH=0.15f;
                }
                probBuffer.add(s3Prob);
                if (probBuffer.size() > BUFFER_SIZE) {
                    probBuffer.remove(0);
                }
                avgProb = average(probBuffer);


                boolean isArousal = false;
                if (elapsedTime >= ONSET_TIME) {
                    Log.i("cuedata", "point1");


                    //String test=stageData.split("\"Probability( is3=1 )\":")[0];
                    //Log.d("debug",test);

                    if (!shamNight && cueRunning && (Math.abs(motionX - oldx) > MOTION_THRESH || Math.abs(motionY - oldy) > MOTION_THRESH || Math.abs(motionZ - oldz) > MOTION_THRESH)) {
                        isArousal = true;
                        lastArousal = elapsedTime;

                        float arousalSum = sharedPref.getFloat("arousalSum2", 0);
                        int arousalN = sharedPref.getInt("arousalN2", 0);
                        if (arousalN < 2) {
                            arousalN++;
                            arousalSum = arousalSum + cueVolume;
                            editor.putFloat("arousalSum2", arousalSum);
                            editor.putInt("arousalN2", arousalN);
                            editor.commit();
                        }
                        cueVolume = 0;
                    }
                    oldx = motionX;
                    oldy = motionY;
                    oldz = motionZ;



                    Log.i("cuedata", elapsedTime + "," + lastArousal + "," + (elapsedTime - lastArousal) + "," + BACKOFF_TIME);
                    //if (avgProb >= ONSET_THRESH && elapsedTime >= ONSET_TIME && elapsedTime-lastArousal >= BACKOFF_TIME) { //conditions are good for cueing
                    if (avgProb >= ONSET_THRESH && elapsedTime >= ONSET_TIME && elapsedTime - lastArousal >= BACKOFF_TIME && enableSleepCueing) { //cue starts if we have exceeded the threshold and keeps running until an arousal interrupts it
                        Log.i("cuedata", "point2");

                        if (!cueRunning && !shamNight) {
                            cueRunning = true;
                            lucidMusic = MediaPlayer.create(MainActivity.this, R.raw.combinedsignal);
                            lucidMusic.setVolume(cueVolume, cueVolume);
                            lucidMusic.setLooping(true);

                            lucidMusic.start();

                        }

                    } else if (elapsedTime - lastArousal < BACKOFF_TIME || !enableSleepCueing) { //arousal, stop the cues as needed

                        if (cueRunning && !shamNight) {
                            lucidMusic.stop();
                            cueRunning = false;
                        }
                    }
                    if (cueRunning) { //if the cueing is running, start incrementing the
                        editor.putFloat("highestVol", cueVolume);
                        editor.putInt("totalCues",sharedPref.getInt("totalCues",0)+1);
                        editor.apply();
                        cueVolume = cueVolume + CUE_VOLUME_INC;
                        //check to see if we've recorded enough arousals to set a volume cap. If we have, make sure the volume doesn't exceed the cap
                        float arousalSum = sharedPref.getFloat("arousalSum2", 0);
                        int arousalN = sharedPref.getInt("arousalN2", 0);

                        if (arousalN >= 2) {
                            float meanThresh = (arousalSum / arousalN);
                            if (cueVolume > meanThresh) {
                                cueVolume = meanThresh;
                                Log.i("volume", "capped at " + meanThresh);
                            }
                        }
                        maximizeVolume(); //override any volume adjustments
                        lucidMusic.setVolume(cueVolume, cueVolume);

                    }
                }
            }
                return(System.currentTimeMillis()+","+hr+","+String.format("%.2f", motionX)+","+String.format("%.2f", motionY)+","+String.format("%.2f", motionZ)+","+String.format("%.3f", gyrox)+","+String.format("%.3f", gyroy)+","+String.format("%.3f", gyroz)+","+String.format("%.2f", s3Prob)+","+String.format("%.2f", avgProb)+","+cueRunning+","+cueVolume+","+(elapsedTime)+","+String.format("%.2f", ax)+","+String.format("%.2f", ay)+","+String.format("%.2f", az));


        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        public Response serve(String uri, Method method,
                              Map<String, String> header,
                              Map<String, String> parameters,
                              Map<String, String> files) {
            //Log.e("fitbitserver", "request");

            //reset the accelerometer
            if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) { //if the acceleromter exists
                sensorManager.unregisterListener(MainActivity.this);
                sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
                sensorManager.registerListener(MainActivity.this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
            }
            lastPacket=System.currentTimeMillis();
            if (uri.indexOf("rawdata") > -1) { //recieved a data packet from the Fitbit, set the Fitbit status to good.
                Log.i("data",parameters.toString());
                String result=handleStaging(parameters.toString());

                String current=sharedPref.getString("sleepdata","");
                editor.putString("sleepdata",current+"##"+result);
                editor.apply();


            }
            return newFixedLengthResponse(Response.Status.OK, "normal", "");

        }
    }
}


