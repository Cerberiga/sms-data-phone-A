package com.example.laptop.smsdataclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsMessage;
import android.util.Log;
import android.util.Base64;

import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by laptop on 4/15/2015.
 */

/* BroadcastReceiver to receive SMS messages from Phone B. These messages are interpreted as being
packets that are destined for other applications on this phone. Create an instance of DnsToC in
order to forward the packet to the original requester.
 */
public class SmsReceiver extends BroadcastReceiver{
    MainActivity main_act;
    DatagramSocket ds;
    ArrayList<MessageBuffer> mbufList = new ArrayList<>();

    public void onReceive(Context context, Intent intent)
    {
        Log.i("SMSR", "Received");
        Bundle bundle = intent.getExtras();
        if(bundle != null)
        {
            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus.length < 1)
                return;

            StringBuilder sb = new StringBuilder("");
            String phone_b_no;
            for (int j = 0; j < pdus.length; j++) {
                SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdus[j]);
                phone_b_no = sms.getOriginatingAddress();
                //String new_phone = "+1" + main_act.phone_no;
                boolean is_equal = PhoneNumberUtils.compare(phone_b_no, main_act.phone_no);//new_phone.equals(phone_b_no);
                Log.i("NUMBER", is_equal +"");
                if(!is_equal)
                {
                    return;
                }
                sb.append(sms.getMessageBody());
            }
            String body = sb.toString();
            Log.i("RECV", body);

            byte[] raw;

            try {
                raw = Base64.decode(body, Base64.NO_WRAP);
            }
            catch(IllegalArgumentException iae)
            {
                Log.i("EXCEPTION", "Illegal Argument Exception\n");
                return;
            }

            sb = new StringBuilder("");
            for (int k = 0; k < raw.length; k++) {
                sb.append(String.format("%02x", raw[k]));
                sb.append(" ");
            }
            Log.i("RECV", "Raw Hex packet received: " + sb.toString());

            int seqNum = raw[0];
            int dataNum = raw[1];
            int dataCount = raw[2];
            Log.i("sms", seqNum + " " + dataNum + " " + dataCount);
            // dataNum and dataCount start at 1
            if (seqNum < 0 || dataNum < 1 || dataCount < 1 || dataNum > dataCount)
                return;

            byte[] data = new byte[raw.length - 3];
            System.arraycopy(raw, 3, data, 0, raw.length - 3);

            boolean found = false;
            MessageBuffer mbuf = null;
            int mbufIndex = -1;
            for (int i = 0; i < mbufList.size(); i++) {
                mbuf = mbufList.get(i);
                if (mbuf.seqNum == seqNum) {
                    found = true;
                    mbuf.add(data, dataNum);
                    mbufIndex = i;
                    Log.i("sms", "Found mbuf with seqNum " + seqNum);
                    break;
                }
            }

            if (!found) {
                mbuf = new MessageBuffer(seqNum);
                mbuf.add(data, dataNum);
                mbufList.add(mbuf);
                mbufIndex = mbufList.size() - 1;
                Log.i("sms", "Added mbuf with seqNum " + seqNum);
            }

            if (mbuf.count == dataCount) {
                byte[] mergedData = mbuf.getData();
                sb = new StringBuilder("");
                for (int i = 0; i < mergedData.length; i++) {
                    sb.append(String.format("%02x", mergedData[i]) + " ");
                }
                Log.i("SmsReceiver", "Merged data [" + mergedData.length + "]: " + sb.toString());

                if(mergedData.length > 28) {
                    int source_port = 256 * (((int) mergedData[20])&0xFF) +
                            (((int) mergedData[21])&0xFF);
                    int dest_port = 256 * (((int) mergedData[22])&0xFF) +
                            (((int) mergedData[23])&0xFF);
                    int id = 256 * (((int) mergedData[0])&0xFF) + (((int) mergedData[1])&0xFF);
                    Log.i("RECV", "DNS ID: " + id);
                    Log.i("RECV", "SOURCE PORT: " + source_port);
                    Log.i("RECV", "DEST PORT: " + dest_port);
                    Log.i("RECV", new String(mergedData));

                    synchronized (main_act) {
                        Thread t = new Thread(new DNStoC(main_act, mergedData, this.ds, id));
                        t.start();
                    }
                    Log.i("SmsReceiver", "Started DNStoC thread");
                }

                mbufList.remove(mbufIndex);
                Log.i("SmsReceiver", "Removed mbuf entry");
            }
        }
    }

    public void setCallingActivity(MainActivity act) {
        main_act = act;
    }

    public class MessageBuffer {
        ArrayList<Entry> list;
        int count;
        int length;
        int seqNum;

        class Entry {
            byte[] data;
            int dataNum;

            Entry(byte[] data, int dataNum) {
                this.data = data;
                this.dataNum = dataNum;
            }
        }

        public MessageBuffer(int seqNum) {
            this.seqNum = seqNum;
            list = new ArrayList<>();
            count = 0;
            length = 0;
        }

        public void add(byte[] data, int dataNum) {
            if (data == null || dataNum < 1)
                return;

            // Reject duplicates
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).dataNum == dataNum)
                    return;
            }

            list.add(new Entry(data, dataNum));
            length += data.length;
            count++;
        }

        public byte[] getData() {
            if (list.size() < 1)
                return null;

            Collections.sort(list, new Comparator<Entry>() {
                @Override
                public int compare(Entry first, Entry second) {
                    if (first.dataNum < second.dataNum)
                        return -1;
                    else if (first.dataNum > second.dataNum)
                        return 1;
                    else
                        return 0;
                }
            });

            byte[] mergedData = new byte[length];
            int offset = 0;
            for (int i = 0; i < list.size(); i++){
                byte[] b = list.get(i).data;
                System.arraycopy(b, 0, mergedData, offset, b.length);
                offset += b.length;
            }

            return mergedData;
        }
    }
}
