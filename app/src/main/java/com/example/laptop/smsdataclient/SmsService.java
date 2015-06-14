package com.example.laptop.smsdataclient;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.provider.Telephony;
import android.util.Log;

import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * Created by laptop on 4/22/2015.
 */
public class SmsService extends Service{
    private MainActivity main_act;
    private SmsReceiver br = new SmsReceiver();
    private SmsBinder sm_br = new SmsBinder();
    private IntentFilter in_f = new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);


    @Override
    public void onCreate()
    {
        try {
            br.ds = new DatagramSocket(51692);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        this.registerReceiver(br, in_f);
        super.onCreate();
        Log.i("SMSSERV", "STARTED");
    }

    /*
    This is the IBinder that is returned to the MainActivity's ServiceConnection when it is
    successfully bound.
     */
    public IBinder onBind(Intent intent)
    {
        return sm_br;
    }

    public void setCallingActivity(MainActivity act) {
        main_act = act;
        br.setCallingActivity(main_act);
    }

    /*
    When getService is called from MainActivity, it returns a SmsService object, allowing the
    MainActivity to interact with the SmsService... finally.
     */
    public class SmsBinder extends Binder {
        SmsService getService() {
            return SmsService.this;
        }
    }

    @Override
    public void onDestroy()
    {
        br.ds.close();
        this.unregisterReceiver(br);
        super.onDestroy();
    }
}
