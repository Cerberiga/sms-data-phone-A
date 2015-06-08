package com.example.laptop.processrunningtest;

import android.os.AsyncTask;
import android.telephony.SmsManager;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Dennis on 5/22/2015.
 */
public class SmsTask extends AsyncTask<String, Void, String> {

    TextView tv;
    byte data[];

    public SmsTask(TextView view, byte data[]) {
        tv = view;
        this.data = data;
    }

    @Override
    protected String doInBackground(String... urls) {
        String str = "Sent";
        try {
            //String phoneNo = "5556";
            String phoneNo = "8186051992";
            //String phoneNo = "15304004608";
            String msg;

            msg = Base64.encodeToString(data, Base64.DEFAULT);

            //char[] packet = new char[1000];
            //Arrays.fill(packet, 'a');
            //packet[999] = '\0';
            //msg = new String(packet);
            //msg = "HelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHello";
            //msg = "HelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHello";
            Log.i("sms", "Sending message (length=" + msg.length() + " :" + msg);
            int id = 256 * (((int) data[28])&0xFF) + (((int) data[29])&0xFF);
            Log.i("SENDING", "DNS ID: " + id);
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNo, null, msg, null, null);
            ArrayList<String> msgList = smsManager.divideMessage(msg);
            //smsManager.sendMultipartTextMessage(phoneNo, null, msgList, null, null);
            Log.i("sms", "Message sent");
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("sms", "Exception");
        }

        return str;
    }

    @Override
    protected void onPostExecute(String s) {
        tv.setText(s);
    }
}

