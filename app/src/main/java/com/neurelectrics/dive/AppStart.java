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


        int taskStatus = sharedPref.getInt("taskStatus", 0); //get where we are in the experiment
        taskStatus=0;
        Log.i("task status",""+taskStatus);
        if (taskStatus == 0) { //consent form has not been filled out
            Intent getConsent = new Intent(this, ConsentActivity.class);
            startActivity(getConsent); //consent is important
        }
        if (taskStatus == 1) { //consent form has not been filled out
            Intent testFitbit = new Intent(this, FitbitTest.class);
            startActivity(testFitbit); //consent is important
        }
        if (taskStatus == 2) { //consent form has not been filled out
            Intent sleepQ = new Intent(this, sleepQuestion.class);
            startActivity(sleepQ);
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_start);
    }


public  void onResume() {
        super.onResume();
        checkStatus();
    }
}