package com.example.laptop.processrunningtest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;
import android.util.Base64;

import java.net.DatagramSocket;

/**
 * Created by laptop on 4/15/2015.
 */
public class SmsReceiver extends BroadcastReceiver{
    SmsManager smsm = SmsManager.getDefault();
    MainActivity main_act;
    DatagramSocket ds;

    public void onReceive(Context c, Intent i)
    {
        Log.i("SMSR", "Received");
        Bundle bundle = i.getExtras();
        if(bundle != null)
        {
            /*
            This is apparently not the right way to receive test messages. You're supposed to
            create a loop and iterate through the data array. Perhaps that's for multi-message
            text messages? Whatever, this works for one text message for now.
             */
            Object[] data = (Object[]) bundle.get("pdus");
            /*
            SmsMessage msg = SmsMessage.createFromPdu((byte[]) data[0]);
            Log.i("SMSR", msg.getDisplayMessageBody());
            main_act.changeText(msg.getDisplayMessageBody());
            */
            StringBuilder str = new StringBuilder("");
            SmsMessage[] msgs = new SmsMessage[data.length];
            for (int j=0; j<msgs.length; j++) {
                msgs[j] = SmsMessage.createFromPdu((byte[])data[j]);
                str.append("SMS from ").append(msgs[j].getOriginatingAddress());
                str.append(" [").append(j).append("]:");
                str.append(msgs[j].getMessageBody());
                Log.i("RECV", msgs[j].getMessageBody());

                byte[] raw = Base64.decode(msgs[j].getMessageBody(), Base64.DEFAULT);

                StringBuilder sb = new StringBuilder("");
                for (int k = 0; k < raw.length; k++) {
                    sb.append(String.format("%02x", raw[k]));
                    sb.append(" ");
                }
                Log.i("RECV", "Raw Hex packet received: " + sb.toString());


                if(raw.length > 28) {
                    int source_port = 256 * (((int) raw[20])&0xFF) + (((int) raw[21])&0xFF);
                    int dest_port = 256 * (((int) raw[22])&0xFF) + (((int) raw[23])&0xFF);
                    int id = 256 * (((int) raw[0])&0xFF) + (((int) raw[1])&0xFF);
                    Log.i("RECV", "DNS ID: " + id);
                    Log.i("RECV", "SOURCE PORT: " + source_port);
                    Log.i("RECV", "DEST PORT: " + dest_port);
                    Log.i("RECV", new String(raw));

                    Thread t = new Thread(new DNStoC(main_act, raw, this.ds));
                    t.start();
                    /*String temp = "";
                    for (int k = 28; k < raw.length; k++) {
                        temp += String.format("0x%02X", raw[k]) + " ";
                        if ((k + 1) % 8 == 0) {
                            Log.i("RECV", temp);
                            temp = "";
                        }
                    }
                    Log.i("RECV", temp);*/
                }
                str.append("\n");
            }
            Log.i("sms", str.toString());
            //---display the new SMS message---
            //Toast.makeText(main_act, str.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    public void setCallingActivity(MainActivity act) {
        main_act = act;
    }
}
