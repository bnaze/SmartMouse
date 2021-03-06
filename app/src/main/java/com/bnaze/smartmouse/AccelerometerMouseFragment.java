package com.bnaze.smartmouse;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.bnaze.smartmouse.networkutils.Message;
import com.bnaze.smartmouse.networkutils.MessageQueue;
import com.bnaze.smartmouse.networkutils.MessageType;
import com.kircherelectronics.fsensor.observer.SensorSubject;
import com.kircherelectronics.fsensor.sensor.FSensor;
import com.kircherelectronics.fsensor.sensor.acceleration.LowPassLinearAccelerationSensor;

public class AccelerometerMouseFragment extends Fragment{
    private FragmentListener callback;
    private boolean connected;
    private boolean onPaused;

    private float offsetX;
    private float offsetY;
    private int sampledCount;
    private boolean calibrateOn;
    private int movementSample;

    private float vx1 = 0;
    private float vy1 = 0;

    private float ax2 = 0;
    private float ay2 = 0;

    private float px1 = 0;
    private float py1 = 0;

    private float time1 = 0;

    private FSensor fSensor;

    public AccelerometerMouseFragment() {
        // Required empty public constructor
    }

    public static AccelerometerMouseFragment newInstance() {
        AccelerometerMouseFragment fragment = new AccelerometerMouseFragment();
        return fragment;
    }

    public void setAccelerometerMouseListener(FragmentListener callback) {
        this.callback = callback;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onPaused = false;
        calibrateOn = false;
        movementSample = 0;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_accelerometer_mouse, container, false);
        Button rightClick = view.findViewById(R.id.rightclick);
        Button leftClick = view.findViewById(R.id.leftclick);
        rightClick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mouseClick("right");
            }
        });
        leftClick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mouseClick("left");
            }
        });

        return view;
    }

    private SensorSubject.SensorObserver sensorObserver = new SensorSubject.SensorObserver() {
        @Override
        public void onSensorChanged(float[] values) {
            //Determine if this fragment is being used by the user
            //If not, return
            if (isAdded() && isVisible() && getUserVisibleHint()) {
                onPaused = false;
            }
            else{
                onPaused = true;
                return;
            }

            //Remove any anomalies samples
            if(Double.isInfinite(values[3]) || Double.isNaN(values[3])){
                return;
            }

            //Mechnical filtering window. This is our no-movement condition
            //window for x
            float window = (float) 1;

            //window for x
            if(values[0] <= window && values[0] >= -window){
                values[0] = 0;
            }

            //window for y
            if(values[1] <= window && values[1] >= -window){
                values[1] = 0;
            }

            integrate(values);
        }
    };


    public void integrate(float[] values){
        float timeDiff = (float) 0.08;

        float vx2 = vx1 + ((values[0]+offsetX + ax2)/2) * timeDiff;
        float vy2 = vy1 + ((values[1]+offsetX + ay2)/2) * timeDiff;

        float px2 = px1 + ((vx1 + vx2)/2) * timeDiff;
        float py2 = py1 + ((vy1 + vy2)/2) * timeDiff;

        //movementEndCheck(values[0], values[1]);
        if(vx1 == vx2){
            movementSample++;
        }
        else{
            movementSample = 0;
        }

        vx1 = vx2;
        px1 = px2;

        if(movementSample >= 25){
            vx1 = 0;
            px1 = 0;
        }

        if(vy1 == vy2){
            movementSample++;
        }
        else{
            movementSample = 0;
        }

        vy1 = vy2;
        py1 = py2;


        if(movementSample>=25){
            vy1 = 0;
            py1 = 0;
        }

        ax2 = values[0];
        ay2 = values[1];

        //time1 = values[3];

        //Data transfer
        connected = callback.ConnectedValue();
        if(connected){
            double sensitivity = 1;
            //Message value must be in json format {"type" : "type", "value" : {"x": x, "y": y}}
            MessageQueue.getInstance().push(Message.newMessage(MessageType.ACC_MOVE,  "{'x': " + -px2*sensitivity + ", 'y': " + -py2*sensitivity +"}"));
        }
    }

    //Clicking for the accelerometer
    public void mouseClick(String click){
        if(click.equals("left")){
            MessageQueue.getInstance().push(Message.newMessage(MessageType.MOUSE_LEFT_CLICK, null));
        }
        else{
            MessageQueue.getInstance().push(Message.newMessage(MessageType.MOUSE_RIGHT_CLICK, null));
        }
    }

    //Method to determine if the user is using the accelerometer mouse fragment
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        // Make sure that we are currently visible
        if (this.isVisible()) {
            // If we are becoming invisible, then...
            if (!isVisibleToUser) {
                Log.d("Fragments", "Acc Not visible anymore.");
                // TODO stop audio playback
                onPaused = true;
            }
            else{
                Log.d("Fragments", "Acc visible.");
                onPaused = false;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        fSensor = new LowPassLinearAccelerationSensor(getActivity());
        fSensor.register(sensorObserver);
        fSensor.start();
    }

    @Override
    public void onPause() {
        fSensor.unregister(sensorObserver);
        fSensor.stop();
        super.onPause();
    }
}
