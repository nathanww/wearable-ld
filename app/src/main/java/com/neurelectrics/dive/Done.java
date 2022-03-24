package com.neurelectrics.dive;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class Done extends AppCompatActivity {
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
        setContentView(R.layout.activity_done);
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        Button newSession = (Button) findViewById(R.id.startNewSession);
        newSession.setOnClickListener(new View.OnClickListener() {
                                          @Override
                                          public void onClick(View v) {
                                              editor.putInt("taskStatus",4);
                                              int currentNight=sharedPref.getInt("currentNight",0);
                                              editor.putInt("currentNight",currentNight+1);
                                              editor.commit();
                                              finish();
                                          }
                                      }
        );
    }
}