package flaff.raspberrypi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.trouch.webiopi.client.PiClient;
import com.trouch.webiopi.client.PiHttpClient;
import com.trouch.webiopi.client.devices.digital.Macros;
import com.trouch.webiopi.client.devices.digital.NativeGPIO;
import com.trouch.webiopi.client.devices.digital.GPIO;


public class MainActivity extends ActionBarActivity {

    PiClient client;
    String host = "192.168.3.14";
    NativeGPIO gpio;
    Macros macros;
    int pin = 26;

    boolean rebootConfirm = false;
    boolean connected = true;

    ImageButton rebootButton, lightButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(getResources().getColor(R.color.navBar));
        }

        // -- pi
        client = new PiHttpClient(host, 80);
        gpio = new NativeGPIO(client);
        macros = new Macros(client);
        // -- end pi

        // TODO: change button to triangle, if no connection

        lightButton = (ImageButton) findViewById(R.id.lightButton);
        rebootButton = (ImageButton) findViewById(R.id.rebootButton);

        lightButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleLight();   }
        });

        rebootButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                promptReboot();   }
        });


        firstRun();
    }

    private void toggleLight()
    {
        new Thread(
                new Runnable() {
                    public void run() {
                        int state = -1;

                        try {
                            state = (int)Float.parseFloat(client.sendRequest("GET", "/GPIO/"+ pin +"/value"));
                        } catch (Exception e) {
                            System.out.println("toggleLight() - can't read state, exiting");
                            return;
                        }

                        gpio.setFunction(pin, GPIO.OUT);

                        if(state == 0 || state == 1)
                        {
                            runOnUiThread(
                                    new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {

                                            rebootButton.setImageResource(R.drawable.button_reboot);
                                        }
                                    }
                            );
                        }

                        System.out.println("writing " + state + " to pin " + pin);
                        if(state == 0)
                            gpio.digitalWrite(pin, true);
                        else if(state == 1)
                            gpio.digitalWrite(pin, false);
                        else {
                            updateImageConnected(false);


                        }
                    }
        }).start();
    }

    private void promptReboot()
    {
        if(!rebootConfirm)
        {
            rebootConfirmTimer();
            rebootConfirm = true;
        }
        else
        {
            Toast.makeText(getApplicationContext(),"Rebooting", Toast.LENGTH_LONG).show();
            updateImageConnected(false);


            //toggleLight();
            reboot();
            wentOffline();
            // TODO: change reboot button to triangle until rebooted (no connection yet)
            rebootConfirm = false;
        }
    }

    private void reboot()
    {
        new Thread(
                new Runnable()
                {
                    public void run()
                    {
                        macros.callMacro("reboot");
                    }
                }
        ).start();
    }

    private void wentOffline()
    {
        new Thread()
        {
            public void run()
            {
                runOnUiThread(
                    new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            ((ImageButton)findViewById(R.id.rebootButton)).setImageResource(R.drawable.button_reboot_attention);
                        }
                    }
                );

                int state = -1;

                try { Thread.currentThread().sleep( 3000 );} catch (InterruptedException e) { e.printStackTrace(); }

               while(state == -1)
                {
                    try {
                        state = (int)Float.parseFloat(client.sendRequest("GET", "/GPIO/"+ pin +"/value"));
                    } catch (Exception e) {
                        System.out.println("state " + state + " exception");
                    }
                    try { Thread.currentThread().sleep( 1000 );} catch (InterruptedException e) { e.printStackTrace(); }

                }

                runOnUiThread(
                        new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                WifiManager wifiManager;
                                WifiInfo info;

                                wifiManager = (WifiManager) getSystemService (Context.WIFI_SERVICE);
                                info = wifiManager.getConnectionInfo ();

                                ((TextView)findViewById(R.id.networkText)).setText(info.getSSID().replace("\"", ""));
                                ((ImageButton)findViewById(R.id.rebootButton)).setImageResource(R.drawable.button_reboot);
                                Toast.makeText(getApplicationContext(),"connected",Toast.LENGTH_LONG).show();
                            }
                        }
                );


            }
        }.start();

    }

    private void firstRun()
    {


        new Thread()
        {
            public void run(){
                int state = -1;
                while(state == -1)
                {
                    try {
                        state = (int)Float.parseFloat(client.sendRequest("GET", "/GPIO/"+ pin +"/value"));
                    } catch (Exception e) {
                        System.out.println("state " + state + " exception");
                    }

                    if(state == -1)
                    {
                        runOnUiThread(
                                new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        ((ImageButton)findViewById(R.id.rebootButton)).setImageResource(R.drawable.button_reboot_attention);
                                    }
                                }
                        );
                    }
                    try { Thread.currentThread().sleep( 1000 );} catch (InterruptedException e) { e.printStackTrace(); }

                }

                runOnUiThread(
                        new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                WifiManager wifiManager;
                                WifiInfo info;

                                wifiManager = (WifiManager) getSystemService (Context.WIFI_SERVICE);
                                info = wifiManager.getConnectionInfo ();

                                ((TextView)findViewById(R.id.networkText)).setText(info.getSSID().replace("\"", ""));
                                ((ImageButton)findViewById(R.id.rebootButton)).setImageResource(R.drawable.button_reboot);
                                //Toast.makeText(getApplicationContext(),"connected",Toast.LENGTH_LONG).show();
                            }
                        }
                );


            }
        }.start();

    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void rebootConfirmTimer()
    {
        new Thread(
                new Runnable()
                {
                    public void run()
                    {
                        try {
                            Thread.currentThread().sleep(300);
                            rebootConfirm = false;
                            // TODO: change reboot to normal
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
        ).start();
    }

    private void updateImageConnected(boolean in)
    {
        if(in)
        {
            rebootButton.setImageResource(R.drawable.button_reboot);
            connected = true;
        }
        else
        {
            rebootButton.setImageResource(R.drawable.button_reboot_attention);
            connected = false;
        }
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

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }
    }
}
