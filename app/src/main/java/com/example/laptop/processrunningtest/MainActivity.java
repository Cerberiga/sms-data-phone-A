package com.example.laptop.processrunningtest;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;


public class MainActivity extends ActionBarActivity {

    boolean run = false;
    boolean thread_run = true;
    Runnable r;
    Thread t;
    DatagramSocket ds;
    Process c_code;
    TextView main_tv;
    String interfaceName = "rmnet_sdio0";
    HashMap<String, DNS> dns_cache = new HashMap<String, DNS>();

    private SmsService sr;
    private boolean m_bound = false;


    /* Launches a thread that will open a socket and continually listen to it. When closed in
    * onDestroy, the socket will be closed, resulting in a caught SocketException. When a packet
    * is received, call handlePacket.
    */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Launch the SmsService, which runs in the background and allows for a persistent broadcast receiver
        Intent intent = new Intent(this, SmsService.class);
        bindService(intent, mConnection, Service.BIND_AUTO_CREATE);

        main_tv = (TextView) findViewById(R.id.text_stuff);
        r = new Runnable() {
            public void run()
            {
                try {
                    ds = new DatagramSocket(51691);
                    byte[] buf = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    while(thread_run)
                    {
                        Log.i("SOCKET", "BEFORE REC");
                        ds.receive(packet);
                        handlePacket(packet);

                        Log.i("SOCKET", "AFTER REC");
                    }
                } catch (SocketException e) {
                    if(ds.isClosed())
                    {
                        Log.i("SOCKET", "CLOSED");
                    }
                    else
                    {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        Thread t = new Thread(r);
        t.start();
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            SmsService.SmsBinder binder = (SmsService.SmsBinder) service;
            sr = binder.getService();
            sr.setCallingActivity(MainActivity.this);
            m_bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {
            m_bound = false;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /*
    If the sub-process (C code is running), find the pid of the running process and issue a kill.
     */
    public void stopRunning(View v)
    {
        if(run) {
            Process pid_finder = null;
            try {
                pid_finder = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(pid_finder.getOutputStream());
                DataInputStream in = new DataInputStream(pid_finder.getInputStream());
                os.writeBytes("ps -C hw\n");
                os.writeBytes("exit\n");
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String line;
                int counter = 0;
                String field;
                String value = null;
                while ((line = br.readLine()) != null) {
                    String[] words = line.split("\\s+");
                    if (counter == 0) {
                        if (words.length > 1) {
                            field = words[1];
                        }
                    } else {
                        if (words.length > 1) {
                            value = words[1];
                        }
                    }
                    counter+=1;
                }
                if(value != null)
                {
                    Process terminator = Runtime.getRuntime().exec("su");
                    DataOutputStream os2 = new DataOutputStream(terminator.getOutputStream());
                    os2.writeBytes("kill " + value + "\n");
                    os2.writeBytes("exit\n");
                    terminator.waitFor();
                    run = false;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /*
    If the button is clicked when the sub-process is running, this calls stopRunning. Otherwise,
    it starts a process with root permissions and simply executes the C code.
     */
    public void startRunning(View v) {


        if (run) {
            stopRunning(v);
            main_tv.setText("Inferior process not running");
            Button b = (Button) findViewById(R.id.button);
            b.setText("Start Process");
        } else {
            //Process temp;
            try {
                c_code = Runtime.getRuntime().exec("su");
                //TODO: Add shell code to remove default IP table routes, and add "default dev lo" route

                DataOutputStream os = new DataOutputStream(c_code.getOutputStream());
                os.writeBytes("./data/local/hw " + interfaceName + "\n");
                run = true;
                main_tv.setText("Inferior process running");
                Button b = (Button) findViewById(R.id.button);
                b.setText("Stop Process");
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    /*
    Closes the socket being listened to by the other thread, ends the thread, and then kills the
    sub-process if it is running.
     */
    @Override
    public void onDestroy()
    {
        if (m_bound) {
            unbindService(mConnection);
            m_bound = false;
        }
        if(thread_run)
        {
            thread_run = false;
            ds.close();
        }
        stopRunning(null);
        super.onDestroy();
    }

    /*
    Handles the information received from the C code. The rest of the work will be done here.
     */
    public void handlePacket(DatagramPacket p)
    {
        byte[] temp = p.getData();
        int length = p.getLength();
        byte[] data = new byte[length];
        byte[] name = new byte[length];



        if(length > 41) {
            System.arraycopy(temp, 0, data, 0, length);
            System.arraycopy(temp, 41, name, 0, length - 41);
            Log.i("SOCKET", new String(data) + " Address=" + p.getAddress() + " SocketAddress=" + p.getSocketAddress() + " length=" + length);

            StringBuilder sb = new StringBuilder("");
            for (int i = 0; i < data.length; i++) {
                sb.append(String.format("%02x", data[i]));
                sb.append(" ");
            }
            Log.i("SOCKET", "\nRaw Hex packet: " + sb.toString());

            int s_port = ((((int) data[20]) & 0xFF) * 256) + (((int) data[21])&0xFF);
            int d_port = ((((int) data[22]) & 0xFF) * 256) + (((int) data[23])&0xFF);
            int id = 256 * (((int) data[28])&0xFF) + (((int) data[29])&0xFF);
            Log.i("SOCKET", "DNS ID: " + id);
            String ip = (((int) data[12])&0xFF) + "." + (((int)data[13])&0xFF) + "." + (((int)data[14])&0xFF) + "." + (((int)data[15])&0xFF);
            String d_ip = (((int) data[16])&0xFF) + "." + (((int)data[17])&0xFF) + "." + (((int)data[18])&0xFF) + "." + (((int)data[19])&0xFF);
            Log.i("SOCKET", "IP: " + ip);
            Log.i("SOCKET", "DIP: " + d_ip);
            Log.i("SOCKET", "D_PORT: " + d_port);
            if(d_port != 53)
            {
                return;
            }
            if(d_port == 53)
            {
                Log.i("SOCKET", "SPORT " + s_port);
                Log.i("SOCKET", "DPORT " + d_port);
                String string_name = parseString(name, data[40]);
                if (checkCache(string_name, s_port, d_port, ip, data)) {
                    Log.i("SOCKET", "SENDING...");
                    new SmsTask(main_tv, data).execute();
                }
                //new SmsTask(main_tv, data).execute();
            }
        }
    }

    String parseString(byte[] b, byte first)
    {
        Log.i("ASDFS", new String(b));
        int len = b.length;
        int i = 0;
        int advance_amt = (int) first;
        i += advance_amt;
        while(i < len)
        {
            if(b[i] ==  0)
                break;
            advance_amt = (int) b[i];
            b[i] = '.';
            i += advance_amt + 1;
        }
        byte []temp = new byte[i];
        System.arraycopy(b, 0, temp, 0, i);
        return new String(temp);
    }

    void copyArr(DNS s, byte[] arr)
    {
        s.s_ip_1 = arr[12];
        s.s_ip_2 = arr[13];
        s.s_ip_3 = arr[14];
        s.s_ip_4 = arr[15];

        s.d_ip_1 = arr[16];
        s.d_ip_2 = arr[17];
        s.d_ip_3 = arr[18];
        s.d_ip_4 = arr[19];

        s.s_p_1 = arr[20];
        s.s_p_2 = arr[21];
        s.d_p_1 = arr[22];
        s.d_p_2 = arr[23];

    }

    boolean checkCache(String s, int s_port, int d_port, String ip, byte[] arr)
    {
        long secs = (new Date()).getTime();
        boolean _ret = false;
        if(dns_cache.containsKey(s))
        {
            DNS blah = dns_cache.get(s);
            if(secs - blah.timestamp > 60*1000)
            {
                blah.timestamp = secs;
                blah.s_port = s_port;
                _ret = true;

                Log.i("SOCKET", "UPDATING: " + blah.s_port + " " + blah.d_port + " " + blah.timestamp);
            }
            copyArr(blah, arr);
            blah.s_port = s_port;
            Log.i("SOCKET", "NO CHANGE: " + blah.s_port + " " + blah.d_port + " " + blah.timestamp);
        }
        else
        {
            DNS blah = new DNS();
            blah.timestamp = secs;
            blah.s_port = s_port;
            blah.d_port = d_port;
            blah.ip = ip;
            _ret = true;
            dns_cache.put(s, blah);
            Log.i("SOCKET", "ADDING: " + s_port + " " + d_port + " " + secs);
            copyArr(blah, arr);
        }
        return _ret;
    }
}
