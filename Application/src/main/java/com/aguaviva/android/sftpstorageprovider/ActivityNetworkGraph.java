package com.aguaviva.android.sftpstorageprovider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

public class ActivityNetworkGraph extends FragmentActivity implements SurfaceHolder.Callback {

    public static final String TAG = "ActivityNetworkGraph";

    public static final String FRAGTAG = "StorageProviderFragment";

    public class Data {
        Data(long start, long delta, float kbps) {
            this.start = start;
            this.delta = delta;
            this.kbps = kbps;
        }
        public long start;
        public long delta;
        public float kbps;
    }
    List<Data> data = new ArrayList<Data>();

    private Paint mPaintRed = new Paint();
    private Paint mPaintGray = new Paint();
    SurfaceView surfaceView;

    public static final String LOG_MESSAGE = "LOG_MESSAGE";
    public static final String DATA = "DATA";
    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(LOG_MESSAGE))
            {
                long time = intent.getLongExtra("TIME",0);
                long delta = intent.getLongExtra("DELTA",0);
                float kbps = intent.getFloatExtra("DATA",0);
                data.add(new Data(time, delta, kbps));
            }
        }
    }

    BroadcastReceiver myBroadcastReceiver = new MyBroadcastReceiver();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_graph);

        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceView.getHolder().addCallback(this);

        //Intent myIntent = getIntent(); // gets the previously created intent
        //String connectionName = myIntent.getStringExtra("connection_name");
        mPaintRed.setColor(Color.RED);
        mPaintRed.setStyle(Paint.Style.STROKE);
        mPaintRed.setStrokeWidth(2);

        mPaintGray.setColor(Color.LTGRAY);
        mPaintGray.setStyle(Paint.Style.STROKE);
        mPaintGray.setStrokeWidth(1);


        registerReceiver(myBroadcastReceiver, new IntentFilter(LOG_MESSAGE));

        Button buttonBack = (Button) findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(myBroadcastReceiver);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        new Thread() {
            public void run() {
                Path pathFrame = new Path();
                Path path = new Path();
                while (true) {
                    try {
                        synchronized (surfaceView) {
                            Canvas canvas = surfaceView.getHolder().lockCanvas();
                            if (canvas != null) {
                                long current_time = System.currentTimeMillis();

                                pathFrame.reset();
                                for (int i = 0; i < 8; i++) {
                                    float y = map(i*64, 0, 512, 0, canvas.getHeight());
                                    pathFrame.moveTo(0, y);
                                    pathFrame.lineTo(canvas.getWidth(), y);
                                }

                                path.reset();
                                for (int i = 0; i < data.size(); i++) {
                                    Data d = data.get(data.size() -1 - i);
                                    float t1 = current_time - d.start;
                                    float t2 = t1 - d.delta;
                                    float kbps = d.kbps;

                                    float x1 = (float)Math.floor(map(t1, 0, 10000, 0, canvas.getWidth()));
                                    float x2 = (float)Math.floor(map(t2, 0, 10000, 0, canvas.getWidth()));
                                    float y1 = (float)Math.floor(map(0, 0, 512, canvas.getHeight(), 0));
                                    float y2 = (float)Math.floor(map(kbps, 0, 512, canvas.getHeight(), 0));

                                    path.moveTo(x1, y1);
                                    path.lineTo(x1, y2);
                                    path.lineTo(x2, y2);
                                    path.lineTo(x2, y1);
                                    if (x1>canvas.getWidth()) {
                                        break;
                                    }
                                }
                                canvas.drawColor(Color.BLACK);
                                canvas.drawPath(pathFrame, mPaintGray);
                                canvas.drawPath(path, mPaintRed);
                                surfaceView.getHolder().unlockCanvasAndPost(canvas);
                            }
                        }
                        sleep(100);//10 fps
                    } catch (InterruptedException ie) {
                    }
                }
            }
        }.start();
    }
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
    float map(float val,float min,float max, float scr_min, float scr_max) {
        float t = (val-min)/(max-min);
        return (scr_min) * (1.0f-t)+ (scr_max * t);
    }
}
