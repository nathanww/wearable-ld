package com.neurelectrics.dive;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;

import java.util.Timer;
import java.util.TimerTask;

public class SoundCalibration extends AppCompatActivity {
    MediaPlayer training1;
    MediaPlayer training2;
    MediaPlayer sound1;
    MediaPlayer sound2;
    Timer soundTimer;
    float soundVolume=0.0f;
    boolean runSound=true;
    void escalateSound(MediaPlayer inputSound) {
        soundTimer=new Timer();
        soundTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                inputSound.start(); //start the period where we just play cues and sintructions with long pauses in between.
            }
        }, 10000, 1000);
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
        setContentView(R.layout.activity_sound_calibration);

        //set up the sound files
        training1= MediaPlayer.create(SoundCalibration.this,R.raw.soundcheck1);
        sound1= MediaPlayer.create(SoundCalibration.this,R.raw.twobeeps);


        Button calStart = (Button) findViewById(R.id.calStart);
        calStart.setOnClickListener(new View.OnClickListener() {
                                          @Override
                                          public void onClick(View v) {
                                              calStart.setEnabled(false);
                                            training1.start();
                                          }
                                      }
        );

        Button soundHeard = (Button) findViewById(R.id.heardSoundButton);
        soundHeard.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            runSound=false;
                                            sound1.stop();
                                            SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
                                            SharedPreferences.Editor editor = sharedPref.edit();
                                            editor.putFloat("wakeSoundThresh",soundVolume);
                                            editor.putInt("taskStatus",4);
                                            editor.commit();
                                            finish();
                                        }
                                    }
        );


        training1.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { //when the first training is done, start playing the sound and enable the "I heard a sound" button
            @Override
            public void onCompletion(MediaPlayer mp) {
                soundHeard.setVisibility(View.VISIBLE);
                maximizeVolume();
                final Handler soundloop = new Handler();
                soundloop.postDelayed(new Runnable() { //start playing the training sound at 10-s intervals
                    public void run() {
                        if (runSound) {
                            sound1.setVolume(soundVolume, soundVolume);
                            sound1.start();
                            soundVolume = soundVolume + 0.005f;
                            soundloop.postDelayed(this, 10000);
                        }
                    }
                }, 10000);
            }
        });
    }
}