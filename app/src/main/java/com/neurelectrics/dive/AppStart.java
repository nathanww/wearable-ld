package com.neurelectrics.dive;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

public class AppStart extends AppCompatActivity {
    void checkStatus() {
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        int taskStatus = sharedPref.getInt("taskStatus", 0); //get where we are in the experiment

        Log.i("taskstatus",""+taskStatus);

        Log.i("task status",""+taskStatus);
        if (taskStatus == 0) { //consent form has not been filled out
            Intent getConsent = new Intent(this, ConsentActivity.class);
            startActivity(getConsent); //consent is important
        }
        if (taskStatus == 1) { //connect fitbit
            Intent testFitbit = new Intent(this, FitbitTest.class);
            startActivity(testFitbit);
        }
        if (taskStatus == 2) { //consent form has not been filled out
            Intent sleepQ = new Intent(this, sleepQuestion.class);
            startActivity(sleepQ);
        }

        if (taskStatus == 3) { //sound claibration
            Intent soundCal = new Intent(this, SoundCalibration.class);
            startActivity(soundCal);
        }
        if (taskStatus == 4 || taskStatus == 5) { //training and sleep
            Intent startSleep = new Intent(this, MainActivity.class);
            startActivity(startSleep);
        }
        if (taskStatus == 6) { //dream report
            Intent dreamReport = new Intent(this, DreamReport.class);
            startActivity(dreamReport);
        }
        if (taskStatus == 7) { //end of the night, if this is night 7 then do the night 7 report, otherwise show the done screen
            if (!sharedPref.getBoolean("night7done",false) && sharedPref.getInt("currentNight",0)==6) {
                Intent night7 = new Intent(this, Night7.class);
                startActivity(night7);
            }
            else {
                Intent done = new Intent(this, Done.class);
                startActivity(done);
            }
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_start);
        checkStatus();

    }


public  void onResume() {
        super.onResume();
        checkStatus();
    }
}