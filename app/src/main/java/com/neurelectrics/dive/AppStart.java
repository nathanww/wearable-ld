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
        Log.i("task status",""+taskStatus);
        if (taskStatus == 0) { //consent form has not been filled out
            Intent getConsent = new Intent(this, ConsentActivity.class);
            startActivity(getConsent); //consent is important
        }
        if (taskStatus == 1) { //consent form has not been filled out
            Intent testFitbit = new Intent(this, FitbitTest.class);
            startActivity(testFitbit); //consent is important
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_start);
        checkStatus();
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        checkStatus();
    }
}