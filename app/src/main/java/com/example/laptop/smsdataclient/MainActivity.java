package com.example.laptop.smsdataclient;

import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.view.ViewGroup.*;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
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
    HashMap<Integer, DNS> dns_cache = new HashMap<Integer, DNS>();
    ArrayList<String> ifaces = new ArrayList<String>();
    ArrayList<String> tables = new ArrayList<String>();
    ArrayList<String> route_del = new ArrayList<String>();
    ArrayList<String> route_add = new ArrayList<String>();
    private ArrayList<Long> roundTripTimes = new ArrayList<Long>();
    private ArrayList<String> rttname = new ArrayList<String>();
    boolean rchanged = false;
    private SmsService sr;
    private boolean m_bound = false;
    ArrayList<String> list = new ArrayList<String>();
    ListView lv;
    static ArrayAdapter aa;
    Handler h;
    String phone_no;
    Context c = this;
    /* When a new outgoing packet is received by the destination, it is received by a different
    thread from the UI thread. Use this handler to update the UI when a packet is received.
     */
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

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int height = dm.heightPixels;
        lv = (ListView) findViewById(R.id.cache);
        Button b = (Button) findViewById(R.id.exit);
        LayoutParams lp = (LayoutParams) lv.getLayoutParams();
        lp.height = height/2;
        lv.setLayoutParams(lp);

        h = new MyHandler();

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

    /* Creates and binds the SmsService to allow constant reception of Sms Messages in the
    background.
     */
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
            clear_vals();
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
        synchronized(c) {
            if (rchanged) {
                restoreRoutes();
            }
        }
        stopRunning(null);
        super.onDestroy();
    }

    /*
    Handles the information received from the C code. The rest of the work will be done here.
    Parse the packet and if it is a DNS packet that should be forwarded to the internet, forward it
    to Phone B by creating an SmsTask object.
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
                if (checkCache(string_name, s_port, d_port, ip, data, id)) {
                    Log.i("SOCKET", "SENDING...");

                    new SmsTask(main_tv, data, seqNum++, phone_no).execute();
                }
            }
        }
    }

    /*Parse the byte array of the packet and determine the name of the DNS request*/
    String parseString(byte[] b, byte first)
    {
        try {
            Log.i("ASDFS", new String(b));
            int len = b.length;
            int i = 0;
            int advance_amt = (int) first;
            i += advance_amt;
            while (i < len) {
                if (b[i] == 0)
                    break;
                advance_amt = (int) b[i];
                b[i] = '.';
                i += advance_amt + 1;
            }
            byte[] temp = new byte[i];
            System.arraycopy(b, 0, temp, 0, i);
            return new String(temp);
        }
        catch(ArrayIndexOutOfBoundsException e)
        {
            Log.i("SOCKET", "Invalid packet");
            return "";
        }
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

    /* This function mangles or restores the routing table. If the outgoing routes exist, replace
    them with routes to localhost.
     */
    public void toggleRoutes(View v) {
        synchronized(c) {
            if (rchanged) {
                restoreRoutes();
                Button b = (Button) findViewById(R.id.button_routes);
                b.setText("Remove Routes");
            } else {
                removeRoutes(v);
                Button b = (Button) findViewById(R.id.button_routes);
                b.setText("Restore Routes");
            }
        }
    }

    /* Remove default routes. Scan the routing table and identify all default routes and record
    them. If the default route goes out of a valid interface (ex. rmnet_sdio0, wlan0, etc), then
    remove it from the table.
     */
    public void removeRoutes(View v)
    {
        Runnable r = new Runnable()
        {
            public void run()
            {
                synchronized(c) {
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
                        route_add = new ArrayList<String>();
                        route_del = new ArrayList<String>();
                        while ((line = br.readLine()) != null) {
                            String[] words = line.split(" ");
                            //Log.i("ROUTES", line);
                            int size = words.length;
                            boolean dev_added = false;
                            boolean table_added = false;
                            for (int i = 0; i < size; i++) {
                                if (i == 0) {
                                    if (!words[i].equals("default")) {
                                        break;
                                    }
                                }
                                if (words[i].equals("table") && i < size - 1) {
                                    tables.add(words[i + 1]);
                                    table_added = true;
                                } else if (words[i].equals("dev") && i < size - 1) {
                                    dev_added = true;
                                    ifaces.add(words[i + 1]);
                                }

                            }
                            if (dev_added && !table_added) {
                                tables.add(null);
                            }
                        }

                        for (int j = 0; j < tables.size(); j++) {
                            if (ifaces.get(j).equals("lo")) {
                                continue;
                            }
                            interfaceName = ifaces.get(j);
                            Process route_proc = Runtime.getRuntime().exec("su");
                            DataOutputStream os2 = new DataOutputStream(route_proc.getOutputStream());
                            DataInputStream in2 = new DataInputStream(route_proc.getInputStream());
                            String del = "ip route del default dev " + ifaces.get(j);
                            String add = "ip route add default dev lo";
                            if (tables.get(j) != null) {
                                del += " table " + tables.get(j);
                                add += " table " + tables.get(j);
                            }
                            del += "\n";
                            add += "\n";
                            Log.i("ROUTES", "RUIN ROUTE (DEL): " + del);
                            Log.i("ROUTES", "RUIN ROUTE (ADD): " + add);

                            route_del.add(del);
                            route_add.add(add);
                            os2.writeBytes(del);
                            os2.writeBytes(add);
                            os2.writeBytes("exit\n");
                            route_proc.waitFor();
                            rchanged = true;
                            //BufferedReader br2 = new BufferedReader(new InputStreamReader(in));

                            //while ((line = br.readLine()) != null) {
                        }
                        Log.i("ROUTES", tables.toString());
                        Log.i("ROUTES", ifaces.toString());
                        Log.i("ROUTES", route_add.toString());
                        Log.i("ROUTES", route_del.toString());
                        Log.i("ROUTES", "END LOOP");
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                }
            }
        };
        Thread t = new Thread(r);
        if(!rchanged) {
            t.start();
        }
    }

    /* Scan through all previously deleted and added routes. Remove all default routes that go
    to local loopback and replace them with the routes that were previously deleted.
     */
    synchronized void restoreRoutes()
    {
        Runnable r = new Runnable() {
            public void run() {
                synchronized(c)
                {
                    int add_size = route_add.size();
                    int del_size = route_del.size();
                    Process route_proc;
                    try {
                        for (int i = 0; i < add_size; i++) {
                            String[] arr = route_add.get(i).split(" ");
                            arr[2] = "del";
                            StringBuilder builder = new StringBuilder();
                            for (String s : arr) {
                                builder.append(s);
                                builder.append(" ");
                            }
                            builder.append("\n");
                            route_proc = Runtime.getRuntime().exec("su");
                            DataOutputStream os2 = new DataOutputStream(route_proc.getOutputStream());
                            DataInputStream in2 = new DataInputStream(route_proc.getInputStream());
                            Log.i("ROUTES", "RESTORE ROUTE (DEL): " + builder.toString());
                            os2.writeBytes(builder.toString());
                            os2.writeBytes("exit\n");
                            route_proc.waitFor();
                        }
                        for (int j = 0; j < del_size; j++) {
                            String[] arr = route_del.get(j).split(" ");
                            arr[2] = "add";
                            StringBuilder builder = new StringBuilder();
                            for (String s : arr) {
                                builder.append(s);
                                builder.append(" ");
                            }
                            builder.append("\n");
                            route_proc = Runtime.getRuntime().exec("su");
                            DataOutputStream os2 = new DataOutputStream(route_proc.getOutputStream());
                            DataInputStream in2 = new DataInputStream(route_proc.getInputStream());
                            Log.i("ROUTES", "RESTORE ROUTE (ADD): " + builder.toString());
                            os2.writeBytes(builder.toString());
                            os2.writeBytes("exit\n");
                            route_proc.waitFor();
                            rchanged = false;
                        }
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                }
            }
        };
        Thread t = new Thread(r);
        if(rchanged)
        {
            t.start();
        }
    }


    /* When this phone receives a DNS request, check the local cache to see if this DNS request has
    been seen before and if it is being serviced. If a particular DNS ID is currently outstanding,
    ignore any duplicate DNS requests. However, each subsequent request may be out of different
    ports. Thus, update the cache with the port number of the most recent request. If a DNS request
    has not been seen before, forward the packet.
      */
    synchronized boolean checkCache(String s, int s_port, int d_port, String ip, byte[] arr, int id)
    {
        long secs = (new Date()).getTime();
        long ms = System.currentTimeMillis();
        boolean _ret = false;
        //if(dns_cache.containsKey(s))
        if(dns_cache.containsKey(id))
        {
            //DNS blah = dns_cache.get(s);
            DNS blah = dns_cache.get(id);
            // If we have already completed the request, reset the timestamp to right now
            if(blah.first_sent == 0) {
                _ret = true;
                blah.first_sent = ms;
                blah.timestamp = ms;
            }

            copyArr(blah, arr);
            blah.s_port = s_port;

            String remove = list.get(blah.pos);
            //aa.remove(remove);
            String add = "Query: " + s + ", Source Port: " + s_port + ", Timestamp: " + blah.first_sent;
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
            blah.first_sent = ms;
            blah.name = s;
            _ret = true;
            //dns_cache.put(s, blah);
            dns_cache.put(id,blah);
            Log.i("SOCKET", "ADDING: " + s_port + " " + d_port + " " + secs);
            blah.pos = list.size();
            String add = "Query: " + s + ", Source Port: " + s_port + ", Timestamp: " + blah.first_sent;

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

    /* When restarting the process, clear all of the stored values */
    public void clear_vals()
    {
        dns_cache = new HashMap<Integer, DNS>();
        roundTripTimes = new ArrayList<Long>();
        rttname = new ArrayList<String>();
        aa.clear();
    }


   /*
   When a packet is successfully received, add it's round trip time to the array.
    */
    public synchronized void addRTT(long rtt, String s) {
        long avg = 0;
        roundTripTimes.add(rtt);
        rttname.add(s);
        for (int i = 0; i < roundTripTimes.size(); i++) {
            Long trip = roundTripTimes.get(i);
            avg += trip;
            Log.i("RTT instance", i + " :" + rttname.get(i) + " - " + trip);
        }
        avg = avg/roundTripTimes.size();
        Log.i("RTT avg", avg + "");
    }

    /* Set the phone number of phone B*/
    public void setNum(View v)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(c);
        builder.setTitle("Set receiving phone number");
        builder.setMessage("Enter phone number");
        final EditText eText = new EditText(c);
        eText.setInputType(InputType.TYPE_CLASS_TEXT);
        eText.setTextColor(Color.rgb(0, 0, 0));
        builder.setView(eText);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String str = eText.getText().toString();
                if (str != null && str.matches("\\d+(\\.\\d+)?")) {
                    phone_no = str;
                } else {
                    Toast.makeText(c, "Invalid phone number",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }
}
