package com.neurelectrics.dive;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaPlayer;
import android.os.Bundle;
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

    void escalateSound(MediaPlayer inputSound) {
        soundTimer=new Timer();
        soundTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                inputSound.start(); //start the period where we just play cues and sintructions with long pauses in between.
            }
        }, 10000, 1000);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sound_calibration);

        //set up the sound files
        training1= MediaPlayer.create(SoundCalibration.this,R.raw.soundcheck1);
        training2= MediaPlayer.create(SoundCalibration.this,R.raw.soundcheck2);
        sound1= MediaPlayer.create(SoundCalibration.this,R.raw.twobeeps);
        sound2= MediaPlayer.create(SoundCalibration.this,R.raw.threebeeps);


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

                                        }
                                    }
        );


        training1.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { //when the first training is done, start playing the sound and enable the "I heard a sound" button
            @Override
            public void onCompletion(MediaPlayer mp) {
                soundHeard.setVisibility(View.VISIBLE);

            }
        });
    }
}