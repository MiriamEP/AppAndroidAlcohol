package com.example.mirian.app_v4;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Mirian on 01/05/2017.
 */
//Pantalla de bienvenida
public class splash_screen extends Activity {

    //Duración de la pantalla splash
    private final int TIEMPO_SPLASH = 3000; //3 segundos

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash); //layout de la imagen (xml)

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Intent mainIntent = new Intent().setClass(splash_screen.this, MainActivity.class);
                startActivity(mainIntent);//tras los 3 segundos pasamos la actividad al MainActivity
                finish();
            }
        };

        Timer timer = new Timer();
        timer.schedule(task, TIEMPO_SPLASH);
    }
}
