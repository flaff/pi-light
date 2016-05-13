package flaff.raspberrypi;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.TransitionDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.trouch.webiopi.client.PiClient;
import com.trouch.webiopi.client.PiHttpClient;
import com.trouch.webiopi.client.devices.digital.Macros;
import com.trouch.webiopi.client.devices.digital.NativeGPIO;
import com.trouch.webiopi.client.devices.digital.GPIO;


public class MainActivity extends ActionBarActivity {


    /*
     * android
     */
    int currentapiVersion;
    SharedPreferences preferences;


    /*
     * webiopi
     */
    PiClient client;
    NativeGPIO gpio;
    Macros macros;
    boolean paused = false;


    /*
     * settings
     */
    String host;
    String hostAlt;
    int lightPin;
    boolean lightOnStart;


    /*
     * consts
     */
    final int fade_time = 200;


    /**
     * used to make sure reboot button was pressed twice
     */
    boolean rebootConfirm = false;
    boolean connected = false;


    /*
     * ui
     */
    TransitionDrawable background;
    ImageButton rebootButton, lightButton;
    Button settingsButton;
    FrameLayout layout;


    @Override
    protected void onResume() {
        super.onResume();
        getPrefs();
        updatePiStatus();
        updateWifiName();

        paused = false;
        System.out.println("MAIN ACTIVITY RESTORED");
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPrefs();
        updatePiStatus();
        ((TextView)findViewById(R.id.networkText)).setText("paused");
        System.out.println("MAIN ACTIVITY PAUSED");

        paused = true;
    }


    /*
     * do on start
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * fragment
         */
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }


        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        /*
         * lollipop goodies
         */
        currentapiVersion = android.os.Build.VERSION.SDK_INT;

        if (currentapiVersion >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.statusbar_default));
            getWindow().setNavigationBarColor(getResources().getColor(R.color.navbar_default));
        }


        /*
         * pi init
         */
        client = new PiHttpClient(host, 80);
        gpio = new NativeGPIO(client);
        macros = new Macros(client);



        /*
         * ui window init
         */
        Window window = this.getWindow();

        // clear FLAG_TRANSLUCENT_STATUS flag:
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        // add FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS flag to the window
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);



        /*
         * set ui to variables
         */
        lightButton = (ImageButton) findViewById(R.id.lightButton);
        rebootButton = (ImageButton) findViewById(R.id.rebootButton);
        settingsButton = (Button) findViewById(R.id.settingsButton);
        layout = (FrameLayout) findViewById(R.id.container);

        /*
         * TODO: check light state and set background
         */
        //layout.setBackgroundResource(R.drawable.grad_pink); //on
        //layout.setBackgroundResource(R.drawable.grad_pink_dim); //off



        /*
         * button listeners
         */
        lightButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleLight();   }
        });

        rebootButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                piReboot();   }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                goToSettings();
            }
        });

        getPrefs();
        firstRun();
        updateWifiName();
        updatePiStatus();
    }


    private void goToSettings()
    {
        Intent intent = new Intent();
        intent.setClassName(this, "flaff.raspberrypi.PiPreferenceActivity");
        startActivity(intent);

    }


    private void updateWifiName()
    {
        WifiManager wifiManager;
        WifiInfo info;
        String name;

        wifiManager = (WifiManager) getSystemService (Context.WIFI_SERVICE);
        info = wifiManager.getConnectionInfo();

        name = info.getSSID().replace("\"", "");
        ((TextView)findViewById(R.id.networkText)).setText(name);
    }

    private void updatePiStatus()
    {
        int state = -2;

        /*
         * TEMP test, state will be passed as an argument
         */
        try {
            state = (int)Float.parseFloat(client.sendRequest("GET", "/GPIO/"+ lightPin +"/value"));
        } catch (Exception e) {
            // pi offline
            state = -1;
        }

        if(state == -1)  // pi offline
        {
            ((ImageButton)findViewById(R.id.rebootButton)).setImageResource(R.drawable.button_reboot_attention);
            connected = false;
        }
        else // pi online
        {
            ((ImageButton)findViewById(R.id.rebootButton)).setImageResource(R.drawable.button_reboot);
            connected = true;
        }
    }

    private void getPrefs()
    {
        host = preferences.getString("pref_key_pi_ip", "192.168.3.3");
    }


    private void setUI_default()
    {
        /*
         * lollipop statusbar navbar color
         */
        if (currentapiVersion >= Build.VERSION_CODES.LOLLIPOP)
        {
            getWindow().setNavigationBarColor(getResources().getColor(R.color.navbar_default));
            getWindow().setStatusBarColor(getResources().getColor(R.color.statusbar_default));
        }

        /*
         * ui elements
         */
        layout.setBackgroundResource(R.drawable.bg_off_to_on);

        /*
         * animate
         */
        background = (TransitionDrawable) layout.getBackground();
        background.startTransition(fade_time);
    }


    Boolean tempState = false;
    private void toggleLight()
    {
        /*
         * fade bg animation
         */

        // change background transition drawable
        if(tempState)
        {
            layout.setBackgroundResource(R.drawable.bg_off_to_on);
            if (currentapiVersion >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setNavigationBarColor(getResources().getColor(R.color.navbar_default));
                getWindow().setStatusBarColor(getResources().getColor(R.color.statusbar_default));
            }
        }
        else
        {
            layout.setBackgroundResource(R.drawable.bg_on_to_off);
            if (currentapiVersion >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setNavigationBarColor(getResources().getColor(R.color.navbar_dimmed));
                getWindow().setStatusBarColor(getResources().getColor(R.color.statusbar_dimmed));
            }
        }

        background = (TransitionDrawable) layout.getBackground();

        // start transition
        background.startTransition(fade_time);
        tempState = !tempState;


        /*
         * send to pi
         */
        new Thread(
                new Runnable() {
                    public void run() {
                        int state = -1;

                        try {
                            state = (int)Float.parseFloat(client.sendRequest("GET", "/GPIO/"+ lightPin +"/value"));
                        } catch (Exception e) {
                            System.out.println("toggleLight() - can't read state, exiting");
                            return;
                        }

                        gpio.setFunction(lightPin, GPIO.OUT);

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

                        System.out.println("writing " + state + " to pin " + lightPin);
                        if(state == 0)
                            gpio.digitalWrite(lightPin, true);
                        else if(state == 1)
                            gpio.digitalWrite(lightPin, false);
                        else {
                            updateImageConnected(false);


                        }
                    }
        }).start();
    }

    private void piReboot()
    {
        if(!connected)
        {
            Toast.makeText(getApplicationContext(),"Pi is unavailable.", Toast.LENGTH_LONG).show();
            return;
        }

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
                        /*
                         * start python script on pi called 'reboot'
                         */
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
                    if(!paused)
                    {
                        try {
                            state = (int)Float.parseFloat(client.sendRequest("GET", "/GPIO/"+ lightPin +"/value"));
                        } catch (Exception e) {
                            System.out.println("state " + state + " exception, paused: "+paused);
                        }
                        try { Thread.currentThread().sleep( 1000 );} catch (InterruptedException e) { e.printStackTrace(); }
                    }

                }

                System.out.println("here i am");
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
                    if(!paused) {
                        try {
                            state = (int) Float.parseFloat(client.sendRequest("GET", "/GPIO/" + lightPin + "/value"));
                        } catch (Exception e) {
                            System.out.println("state " + state + " exception");
                        }
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
