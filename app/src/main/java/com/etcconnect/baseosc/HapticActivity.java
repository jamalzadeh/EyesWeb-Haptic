package com.etcconnect.baseosc;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.illposed.osc.OSCListener;
import com.illposed.osc.OSCMessage;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import com.illposed.osc.*;
import java.net.SocketException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.ConnectIQAdbStrategy;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;

public class HapticActivity extends AppCompatActivity {
    // unique identifier representing the ConnectIQ watch app (generated when creating watch app)
    private static String appID = "d0600b4c-6bcf-4f2d-8330-bbb2a6e095b3"; // CIQChecklist UUID
    // The OSC settings the app uses (set in settings class)
    public static String OSCAddress="192.168.137.1";
    public static int outPort=8001;
    public static int inPort=8000;
    Activity myActivity;
    ArrayList<String> messageListIn = new ArrayList<>();
    ArrayAdapter<String> myArrayAdaptor;

    OSCPortIn receiver;
    ListView oscInListView;
    private Button vibrate;
    Vibrator vibrator;
    long[] mVibratePattern;
    int[] mAmplitudes;
    private static IQApp app;
    private static Context appContext;
    private static volatile boolean mSdkReady = false;
    private static ConnectIQ connectIQ;

    private static IQDevice mDevice;
    public HapticActivity() {
        //Create the OSCPort
        try {
            receiver = new OSCPortIn(HapticActivity.inPort);
        } catch (SocketException e) {
            //Todo: Tell user that it failed
            System.out.println("Socket creation failed!");
            return;
        }
        //--------------CIQ------------------------------
        // Device Connect IQ Application ID (manifest file):


        //Hook up the OSC Receiver to listen to messages. Right now
        //      it's just listening to all messages with /*/* format
        //TODO: listen to more OSC messages
        receiver.addListener("/*/*", listener);
        receiver.startListening();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_haptic);
        oscInListView = findViewById(R.id.oscInList);
        app = new IQApp(appID);

        // initialize smartwatch connection

        connectIQ = ConnectIQ.getInstance(this, ConnectIQ.IQConnectType.WIRELESS);
        // Initialize the SDK
        connectIQ.initialize(this, true, mListener);
        myActivity = this;
        myActivity.setTitle("OSC In");
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        myArrayAdaptor = new ArrayAdapter<String>(myActivity, android.R.layout.simple_list_item_1, messageListIn);
        oscInListView.setAdapter(myArrayAdaptor);
    }

    public void addListenerOnButton() {

        vibrate = (Button) findViewById(R.id.button);

        vibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    VibrationEffect vibe = VibrationEffect.createWaveform(mVibratePattern, mAmplitudes, -1);
                    vibrator.vibrate(vibe);
                }else{
                    vibrator.vibrate(mVibratePattern, -1);
                }
            }
        });
    }
    OSCListener listener = new OSCListener() {
        public void acceptMessage(java.util.Date time, OSCMessage message) {
            //Get the OSC message built, added Date/time for convenience
            List tempList = message.getArguments();

            int magnitude = Math.round(Float.valueOf(String.valueOf(tempList.get(tempList.size()-1)))*256);
            int smartwatchmagnitude=Math.round(Float.valueOf(String.valueOf(tempList.get(tempList.size()-1)))*100);
            Log.v("magnitude",String.valueOf(magnitude));
            try {
                writeListToWatch(String.valueOf(smartwatchmagnitude));
            }catch (Exception e)
            {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, magnitude));
            } else {
                //deprecated in API 26
                vibrator.vibrate(100);
            }
            String fullMessage = DateFormat.getDateTimeInstance().format(new Date()) + "\n" + message.getAddress() + ", ";
            for (final Object argument : tempList) {
                fullMessage = fullMessage.concat(String.valueOf(argument));
            }


            //Copy the string over to a final to add to messageListIn
            final String temp = fullMessage;

            //Needs to be added on the UI thread
            myActivity.runOnUiThread(new Runnable() {
                @Override public void run() {
                    messageListIn.add(0, temp);

                    //Keep the list at 100 items
                    if(messageListIn.size() >= 100)
                        messageListIn.remove(messageListIn.size()-1);

                    //Tell the ArrayAdaptor something changed
                    myArrayAdaptor.notifyDataSetChanged();
                }
            });
            //System.out.println("Message received! " + fullMessage);
        }
    };

    // List of known devices
    public void loadDevices() {
        Log.d("Devices","->loadDevices");
        try {
            List<IQDevice> paired = connectIQ.getKnownDevices();

            if (paired != null && paired.size() > 0) {

                for (IQDevice device : paired) {
                    IQDevice.IQDeviceStatus status = connectIQ.getDeviceStatus(device);
                    if (status == IQDevice.IQDeviceStatus.CONNECTED) {
                        // Work with the device
                        mDevice = device;

                        connectIQ.getApplicationInfo(appID,mDevice,mAppInfoListener);
                        connectIQ.registerForDeviceEvents(mDevice,mDeviceEventListener);
                        //----------------Here is where the code terminates---------------
                        // (mDevice,app,mAppEventListener);
                        //----------------------------------------------------------------

                    }
                }
            }
        } catch (InvalidStateException e) {
        } catch (ServiceUnavailableException e) {
            // Garmin Connect Mobile is not installed or needs to be upgraded.
            Log.d("app status","Garmin Connect Mobile is not installed or needs to be upgraded.");
        }
    }

    private ConnectIQ.IQApplicationInfoListener mAppInfoListener = new ConnectIQ.IQApplicationInfoListener(){
        @Override
        public void onApplicationInfoReceived(IQApp iqApp) {
            Log.d("Device connection","Connect IQ application info received.");
        }

        @Override
        public void onApplicationNotInstalled(String s) {
            Log.d("Device connection","Garmin Connect Mobile is not installed on this device.");
        }
    };


    private ConnectIQ.IQDeviceEventListener mDeviceEventListener = new ConnectIQ.IQDeviceEventListener() {
        @Override
        public void onDeviceStatusChanged(IQDevice device, IQDevice.IQDeviceStatus status) {
            Log.d("Device connection","Device connected: " + device);
        }
    };
    private ConnectIQ.ConnectIQListener mListener = new ConnectIQ.ConnectIQListener() {
        @Override
        public void onInitializeError(ConnectIQ.IQSdkErrorStatus errStatus) {
            Log.d("SDK status","Failed to initialize the SDK");
        }

        @Override
        public void onSdkReady() {
            Log.d("SDK status","-> onSdkReady");
            loadDevices();
            mSdkReady = true;
        }

        @Override
        public void onSdkShutDown() {
            Log.d("SDK status","-> onSdkShutDown");
            mSdkReady = false;
        }

    };

    public static void writeListToWatch(String message) throws InvalidStateException, ServiceUnavailableException
    {
        if(mSdkReady)
        {
            System.out.println(mDevice.getFriendlyName()+" status: " + connectIQ.getDeviceStatus(mDevice));

            // todo: support other device types (currently just vivoactive 3)

            if(connectIQ.getDeviceStatus(mDevice) == IQDevice.IQDeviceStatus.CONNECTED )
            {
               /* connectIQ.openApplication(mDevice, app, new ConnectIQ.IQOpenApplicationListener() {
                    @Override
                    public void onOpenApplicationResponse(IQDevice iqDevice, IQApp iqApp, ConnectIQ.IQOpenApplicationStatus iqOpenApplicationStatus) {
                        System.out.println("Open application response: " + iqOpenApplicationStatus + " (app version: " + iqApp.version() + ")");
                    }
                });*/



                System.out.println("Attempting to send list to watch now.");
                connectIQ.sendMessage(mDevice, app, message, new ConnectIQ.IQSendMessageListener() {
                    @Override
                    public void onMessageStatus(IQDevice iqDevice, IQApp iqApp, ConnectIQ.IQMessageStatus iqMessageStatus) {
                        Log.v("message content",message);

                    }
                });
            }
            else {
                Log.v("Watch","not connected");

            }
        }
        else
        {
            System.out.println("Connect IQ SDK not initialized");
        }
    }
}