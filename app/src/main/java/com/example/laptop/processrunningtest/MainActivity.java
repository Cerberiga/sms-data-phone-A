package com.example.laptop.processrunningtest;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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


public class MainActivity extends ActionBarActivity {

    boolean run = false;
    boolean thread_run = true;
    Runnable r;
    Thread t;
    DatagramSocket ds;
    Process c_code;
    TextView main_tv;
    String interfaceName = "eth0";


    /* Launches a thread that will open a socket and continually listen to it. When closed in
    * onDestroy, the socket will be closed, resulting in a caught SocketException. When a packet
    * is received, call handlePacket.
    */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
        System.arraycopy(temp, 0, data, 0, length);
        Log.i("SOCKET", new String(data) + " Address=" + p.getAddress() + " SocketAddress=" + p.getSocketAddress() + " length=" + length);

        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < data.length; i++) {
            sb.append(String.format("%02x", data[i]));
            sb.append(" ");
        }
        Log.i("SOCKET", "\nRaw Hex packet: " + sb.toString());
    }

}
