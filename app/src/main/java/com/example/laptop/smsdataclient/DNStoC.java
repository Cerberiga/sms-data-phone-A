package com.example.laptop.smsdataclient;

import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;

/**
 * Created by laptop on 5/28/2015.
 */
public class DNStoC implements Runnable{
    //private static DNStoC ourInstance = new DNStoC();

    /*public static DNStoC getInstance() {
        return ourInstance;
    }*/

    int s_port;
    int d_port;
    int id;
    byte[] raw;
    MainActivity ma;
    DatagramSocket ds;

    public DNStoC(MainActivity ma, byte[] raw, DatagramSocket ds, int id) {
        this.ma = ma;
        /*this.s_port = s_port;
        this.d_port = d_port;*/
        this.ds = ds;
        this.raw = raw;
        this.id = id;
    }

    @Override
    public void run() {
        try {
            byte[] temp = new byte[raw.length - 13];
            System.arraycopy(raw, 12+1, temp, 0, raw.length - 13);
            String dest = ma.parseString(temp, raw[12]);

            byte[] total = new byte[raw.length + 12];

            HashMap<Integer, DNS> hm = ma.dns_cache;
            //HashMap<String, DNS> hm = ma.dns_cache;
            //if(hm.containsKey(dest))
            if(hm.containsKey(id))
            {
                //DNS blah = hm.get(dest);
                DNS blah = hm.get(id);

                // Record TTL
                blah.first_recv = System.currentTimeMillis();

                // Update entry on UI to say "DNS completed" with timestamp
                String remove = ma.list.get(blah.pos);
                //aa.remove(remove);
                String add = "Completed request to : " + dest + " RTT: " + (blah.first_recv - blah.first_sent);
                ma.addRTT(blah.first_recv - blah.first_sent, dest);
                //aa.insert(add, blah.pos);
                Message msg = new Message();
                Bundle b = new Bundle();
                b.putInt("type", 0);
                b.putString("remove", remove);
                b.putString("add", add);
                b.putInt("index", blah.pos);
                msg.setData(b);
                ma.h.sendMessage(msg);
                Log.i("SOCKET", "Completed: " + blah.s_port + " " + blah.d_port + " " + blah.timestamp);

                // Since we have compelted the request, set the sent time to 0
                blah.first_sent = 0;

                total[0] = blah.d_ip_4;
                total[1] = blah.d_ip_3;
                total[2] = blah.d_ip_2;
                total[3] = blah.d_ip_1;

                total[4] = blah.s_ip_4;
                total[5] = blah.s_ip_3;
                total[6] = blah.s_ip_2;
                total[7] = blah.s_ip_1;

                total[8] = blah.d_p_2;
                total[9] = blah.d_p_1;

                total[10] = blah.s_p_2;
                total[11] = blah.s_p_1;

                System.arraycopy(raw,0,total,12,raw.length);

                //s_port = hm.get(dest).s_port;
                //d_port = hm.get(dest).d_port;

                Log.i("FORWARDING: ", dest);
                Log.i("FORWARDING: ", "" + s_port);
                Log.i("FORWARDING: ", "" + d_port);
                Log.i("FORWARDING: ", new String(raw));
                Log.i("FORWARDING: ", "ACTUALLY FORWARDING: ");

                StringBuilder sb = new StringBuilder("");
                for (int k = 0; k < total.length; k++) {
                    sb.append(String.format("%02x", total[k]));
                    sb.append(" ");
                }
                Log.i("RECV", "Raw Hex packet forwarding: " + sb.toString());

                //DatagramPacket p = new DatagramPacket(raw, raw.length);
                DatagramPacket p = new DatagramPacket(total, total.length);
                //p.setPort(s_port);
                p.setPort(34567);
                p.setAddress(InetAddress.getByName("127.0.0.1"));//InetAddress.getByName(hm.get(dest).ip));
                //p.setData(raw);
                p.setData(total);

                //DatagramSocket ds = new DatagramSocket(51692);//d_port);
                ds.send(p);
                //ds.close();
            }
            else
            {
                Log.i("FORWARDING: ", "NO MATCH");
            }
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
