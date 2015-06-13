package com.example.laptop.processrunningtest;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.util.Base64;
import android.view.ViewGroup.*;

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
import java.util.ArrayList;
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
    ArrayList<String> ifaces = new ArrayList<String>();
    ArrayList<String> tables = new ArrayList<String>();
    ArrayList<String> route_del = new ArrayList<String>();
    ArrayList<String> route_add = new ArrayList<String>();
    boolean rchanged = false;
    private SmsService sr;
    private boolean m_bound = false;
    ArrayList<String> list = new ArrayList<String>();
    ListView lv;
    static ArrayAdapter aa;
    Handler h;

    static class MyHandler extends Handler{
        public void handleMessage(Message input)
        {
            Bundle b = input.getData();
            if(b.getInt("type") == 0)
            {
                if(b.getString("remove") != null) {
                    aa.remove(b.getString("remove"));
                    aa.insert(b.getString("add"), b.getInt("index"));
                }
                else
                {
                    aa.add(b.getString("add"));
                }

            }
        }
    }

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

        /*Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
*/
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int height = dm.heightPixels;
        lv = (ListView) findViewById(R.id.cache);
        Button b = (Button) findViewById(R.id.exit);
        LayoutParams lp = (LayoutParams) lv.getLayoutParams();
        lp.height = height/2;
        lv.setLayoutParams(lp);

        h = new MyHandler();


//Log.i("SETHEIGHT", ""+height);
        //Log.i("SETHEIGHT", ""+b.getHeight);

        //ListView lv = (ListView) findViewById(R.id.cache);
        list = new ArrayList<String>();
        list.add("Outgoing DNS requests being serviced");

        aa = new ArrayAdapter(this, R.layout.list_item, list);
        lv.setAdapter(aa);

        main_tv = (TextView) findViewById(R.id.text_stuff);
        r = new Runnable() {
            public void run()
            {
                int seqNum = 0;
                try {
                    ds = new DatagramSocket(51691);
                    byte[] buf = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    while(thread_run)
                    {
                        Log.i("SOCKET", "BEFORE REC");
                        ds.receive(packet);
                        handlePacket(seqNum, packet);

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
        if(rchanged)
        {
            restoreRoutes();
        }
        stopRunning(null);
        super.onDestroy();
    }

    /*
    Handles the information received from the C code. The rest of the work will be done here.
     */
    public void handlePacket(int seqNum, DatagramPacket p)
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
            //BLAH;
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

                    new SmsTask(main_tv, data, seqNum++).execute();
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

    public void exitApp(View v)
    {
        super.onBackPressed();
    }

    public void onBackPressed()
    {

    }

/*public void removeRoutes(View v)
{

    //list.add("NEW ITEM: " + list.size());
    //aa.add("NEW ITEM: " + list.size());

    Runnable r = new Runnable()
    {
        public void run()
        {
            Message msg = new Message();
            Bundle b = new Bundle();
            b.putString("string", "HELLO");
            msg.setData(b);
            h.sendMessage(msg);
        }
    };
    Thread t = new Thread(r);
    t.start();
}

    public void elimItem(View v) {
        String ob = list.get(4);
        aa.remove(ob);
    }
*/
    public void removeRoutes(View v)
    {
        Runnable r = new Runnable()
        {
            public void run()
            {
                Process iface = null;
                try {
                    iface = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(iface.getOutputStream());
                    DataInputStream in = new DataInputStream(iface.getInputStream());
                    os.writeBytes("ip route show table 0\n");
                    os.writeBytes("exit\n");
                    BufferedReader br = new BufferedReader(new InputStreamReader(in));
                    String line;
                    int counter = 0;
                    String field;
                    String value = null;
                    Log.i("ROUTES", "START LOOP");
                    ifaces = new ArrayList<String>();
                    tables = new ArrayList<String>();

                    while ((line = br.readLine()) != null) {
                        String[] words = line.split(" ");
                        //Log.i("ROUTES", line);
                        int size = words.length;
                        boolean dev_added = false;
                        boolean table_added = false;
                        for(int i = 0; i < size; i++)
                        {
                            if(i == 0)
                            {
                                if(!words[i].equals("default"))
                                {
                                    break;
                                }
                            }
                            if(words[i].equals("table") && i < size - 1)
                            {
                                tables.add(words[i+1]);
                                table_added = true;
                            }
                            else if(words[i].equals("dev") && i < size - 1)
                            {
                                dev_added = true;
                                ifaces.add(words[i+1]);
                            }

                        }
                        if(dev_added && !table_added)
                        {
                            tables.add(null);
                        }
                    }

                    for(int j = 0; j < tables.size(); j++)
                    {
                        if(ifaces.get(j).equals("lo"))
                        {
                            continue;
                        }
                        interfaceName = ifaces.get(j);
                        Process route_proc = Runtime.getRuntime().exec("su");
                        DataOutputStream os2 = new DataOutputStream(route_proc.getOutputStream());
                        DataInputStream in2 = new DataInputStream(route_proc.getInputStream());
                        String del = "ip route del default dev " + ifaces.get(j);
                        String add = "ip route add default dev lo";
                        if(tables.get(j) != null)
                        {
                            del += " table " + tables.get(j);
                            add += " table " + tables.get(j);
                        }
                        del += "\n";
                        add += "\n";
                        Log.i("RUIN ROUTE (DEL)", del);
                        Log.i("RUIN ROUTE (ADD)", add);

                        route_del.add(del);
                        route_add.add(add);
                        os2.writeBytes(del);
                        os2.writeBytes(add);
                        os2.writeBytes("exit\n");
                        rchanged = true;
                        //BufferedReader br2 = new BufferedReader(new InputStreamReader(in));

                        //while ((line = br.readLine()) != null) {
                    }

                    Log.i("ROUTES", "END LOOP");
                } catch (IOException e) {
                    e.printStackTrace();
                } /*catch (InterruptedException e) {
                    e.printStackTrace();
                }*/
            }
        };
        Thread t = new Thread(r);
        if(!rchanged) {
            t.start();
        }
    }

    void restoreRoutes()
    {
        Runnable r = new Runnable() {
            public void run() {
                int add_size = route_add.size();
                int del_size = route_del.size();
                Process route_proc;
                try
                {
                    for (int i = 0; i < add_size; i++) {
                        String[] arr = route_add.get(i).split(" ");
                        arr[2] = "del";
                        StringBuilder builder = new StringBuilder();
                        for (String s : arr) {
                            builder.append(s);
                            builder.append(" ");
                        }
                        route_proc = Runtime.getRuntime().exec("su");
                        DataOutputStream os2 = new DataOutputStream(route_proc.getOutputStream());
                        DataInputStream in2 = new DataInputStream(route_proc.getInputStream());
                        Log.i("RESTORE ROUTE (DEL)", builder.toString());
                        os2.writeBytes(builder.toString());
                        os2.writeBytes("exit\n");
                    }
                    for (int j = 0; j < del_size; j++) {
                        String[] arr = route_del.get(j).split(" ");
                        arr[2] = "add";
                        StringBuilder builder = new StringBuilder();
                        for (String s : arr) {
                            builder.append(s);
                            builder.append(" ");
                        }
                        route_proc = Runtime.getRuntime().exec("su");
                        DataOutputStream os2 = new DataOutputStream(route_proc.getOutputStream());
                        DataInputStream in2 = new DataInputStream(route_proc.getInputStream());
                        Log.i("RESTORE ROUTE (ADD)", builder.toString());
                        os2.writeBytes(builder.toString());
                        os2.writeBytes("exit\n");
                    }
                } catch (IOException ioe)
                {
                    ioe.printStackTrace();
                } /*catch (InterruptedException e)
                {
                    e.printStackTrace();
                }*/
            }
        };
        Thread t = new Thread(r);
        t.start();
    }



    boolean checkCache(String s, int s_port, int d_port, String ip, byte[] arr)
    {
        long secs = (new Date()).getTime();
        long ms = System.currentTimeMillis();
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

            String remove = list.get(blah.pos);
            //aa.remove(remove);
            String add = "Query: " + s + ", Source Port: " + s_port + ", Timestamp: " + secs;
            //aa.insert(add, blah.pos);
            Message msg = new Message();
            Bundle b = new Bundle();
            b.putInt("type", 0);
            b.putString("remove", remove);
            b.putString("add", add);
            b.putInt("index", blah.pos);
            msg.setData(b);
            h.sendMessage(msg);
            Log.i("SOCKET", "NO CHANGE: " + blah.s_port + " " + blah.d_port + " " + blah.timestamp);
        }
        else
        {
            DNS blah = new DNS();
            blah.timestamp = secs;
            blah.s_port = s_port;
            blah.d_port = d_port;
            blah.ip = ip;
            blah.first_recv = ms;
            _ret = true;
            dns_cache.put(s, blah);
            Log.i("SOCKET", "ADDING: " + s_port + " " + d_port + " " + secs);
            blah.pos = list.size();
            String add = "Query: " + s + ", Source Port: " + s_port + ", Timestamp: " + secs;

            Message msg = new Message();
            Bundle b = new Bundle();
            b.putInt("type", 0);
            b.putString("remove", null);
            b.putString("add", add);
            msg.setData(b);
            h.sendMessage(msg);

            copyArr(blah, arr);
        }
        return _ret;
    }
}
