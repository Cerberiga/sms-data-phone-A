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
    private TextView tv;
    private byte data[];
    private int seqNum;
    private final int bytesPerSms = 115;
    private SmsManager smsManager = SmsManager.getDefault();
    private String phone_no;

    public SmsTask(TextView view, byte data[], int seqNum, String phone_no) {
        tv = view;
        this.data = data;
        this.seqNum = seqNum;
        this.phone_no = phone_no;
    }

    @Override
    protected String doInBackground(String... urls) {
        String str = "Sent";
        try {
            String msg;

            int id = 256 * (((int) data[28])&0xFF) + (((int) data[29])&0xFF);
            Log.i("SENDING", "DNS ID: " + id);

            int count = (data.length / bytesPerSms) + 1;
            for (int i = 0; i < count; i++) {
                int offset = i * bytesPerSms;
                int len = Math.min(data.length - offset, bytesPerSms);
                byte[] sub = new byte[3 + len];
                sub[0] = (byte) seqNum;
                sub[1] = (byte) (i + 1);
                sub[2] = (byte) count;
                System.arraycopy(data, offset, sub, 3, len);
                msg = Base64.encodeToString(sub, Base64.NO_WRAP);
                Log.i("sms", "Sending message (length=" + msg.length() + " :" + msg);
                //smsManager.sendTextMessage(phone_no, null, msg, null, null);
                smsManager.sendTextMessage(phone_no, null, msg, null, null);
            }

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

