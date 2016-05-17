package flaff.raspberrypi;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.TransitionDrawable;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
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
import com.trouch.webiopi.client.devices.digital.GPIO;
import com.trouch.webiopi.client.devices.digital.Macros;
import com.trouch.webiopi.client.devices.digital.NativeGPIO;

import java.io.Console;


public class MainActivity extends ActionBarActivity {


    /*
     * android
     */
    int currentapiVersion;
    SharedPreferences preferences;


    /*
     * webiopi
     */
    PiClient client, clientAlt;
    NativeGPIO gpio, gpioAlt;
    Macros macros, macrosAlt;
    boolean paused = false;


    /*
     * settings
     */
    String host;
    String hostAlt;

    int lightPin;
    boolean lightPinState = false;

    boolean lightOnStart;
    boolean passwordRequired = false;
    String user,password;


    /*
     * consts
     */
    final int fade_time = 200;


    /**
     * used to make sure reboot button was pressed twice
     */
    boolean rebootConfirm = false;
    boolean connected = false;
    boolean connectedAlt = false;


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
        updateWifiName();

        paused = false;
        System.out.println("MAIN ACTIVITY RESUMED");

        if(!connected)
        {
            System.out.println("restored, but not connected, pooling");
            setUI_offline();
            poolIsOnline();
        }
        else
        {
            updatePinState();

            if(lightOnStart)
                lightStartup();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPrefs();
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


        /*
         * get preferences
         */
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        getPrefs();


        /*
         * lollipop goodies
         */
        currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion >= Build.VERSION_CODES.LOLLIPOP)
        {
            getWindow().setNavigationBarColor(getResources().getColor(R.color.navbar_dimmed));
            getWindow().setStatusBarColor(getResources().getColor(R.color.statusbar_dimmed));
        }

        /*
         * raspberry pi init
         */
        client = new PiHttpClient(host, 80);
        clientAlt = new PiHttpClient(hostAlt, 80);

        if(passwordRequired)
        {
            client.setCredentials(user, password);
            clientAlt.setCredentials(user, password);
        }

        gpio = new NativeGPIO(client);
        gpioAlt = new NativeGPIO(clientAlt);

        macros = new Macros(client);
        macrosAlt = new Macros(clientAlt);




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


        updateWifiName();

        updatePinState();

        if(lightOnStart)
            lightStartup();



        /*
         * button listeners
         */
        lightButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!connected) {
                    Toast.makeText(getApplicationContext(), "Pi is unavailable.", Toast.LENGTH_LONG).show();
                    return;
                }
                toggleLight();
            }
        });

        rebootButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                piReboot();
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                goToSettings();
            }
        });

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
        if(name.contains("<"))
            name = this.getResources().getString(R.string.network_name);

        ((TextView)findViewById(R.id.networkText)).setText(name);
    }



    private void getPrefs()
    {
        host = preferences.getString("pref_key_pi_ip", "192.168.3.14");
        hostAlt = preferences.getString("pref_key_pi_ip_alt", "192.168.3.14");

        lightPin = Integer.parseInt(preferences.getString("pref_key_pi_lightpin", "17"));
        lightOnStart = preferences.getBoolean("pref_key_light_on_start", false);

        passwordRequired = preferences.getBoolean("pref_key_auth", false);
        if(passwordRequired)
        {
            user = preferences.getString("pref_key_user", "");
            password = preferences.getString("pref_key_password", "");
        }

    }

    private void updatePinState()
    {
        new Thread(new Runnable(){public void run()
        {
            final Boolean prevLightPinState = lightPinState;

            try {
                lightPinState = gpio.digitalRead(lightPin);
            }catch(Exception e){}

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (lightPinState == prevLightPinState)
                        return;

                    if (!lightPinState)
                        setUI_default();
                    else
                        setUI_dimmed();
                }
            });

        }});
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
         * set ui to default (ease)
         */
        layout.setBackgroundResource(R.drawable.bg_off_to_on);

        /*
         * animate
         */
        background = (TransitionDrawable) layout.getBackground();
        background.startTransition(fade_time);
    }

    private void setUI_dimmed()
    {
        /*
         * lollipop statusbar navbar color
         */
        if (currentapiVersion >= Build.VERSION_CODES.LOLLIPOP)
        {
            getWindow().setNavigationBarColor(getResources().getColor(R.color.navbar_dimmed));
            getWindow().setStatusBarColor(getResources().getColor(R.color.statusbar_dimmed));
        }

        /*
         * set ui to dimmed (ease)
         */
        layout.setBackgroundResource(R.drawable.bg_on_to_off);

        /*
         * animate
         */
        background = (TransitionDrawable) layout.getBackground();
        background.startTransition(fade_time);
    }


    private void lightStartup()
    {
        if(lightPinState)
            toggleLight();
    }


    private void toggleLight()
    {
        /*
         * send to pi
         */
        new Thread(new Runnable(){public void run()
        {
            // change state
            System.out.println(lightPinState);
            try{
                gpio.setFunction(lightPin, GPIO.OUT);
                lightPinState = gpio.digitalRead(lightPin);
                lightPinState = gpio.digitalWrite(lightPin, !lightPinState);
                System.out.println(lightPinState);
            }catch (Exception e)
            {
                runOnUiThread(new Runnable(){@Override public void run() {

                    lightPinState = true;
                    setUI_dimmed();

                    setUI_offline();
                    updateWifiName();

                    connected = false;
                    Toast.makeText(getApplicationContext(),"Pi is unavailable.", Toast.LENGTH_LONG).show();

                    poolIsOnline();

                    return;
                }});
            }

            // update ui
            runOnUiThread(new Runnable(){@Override public void run() {
                if(!lightPinState)
                    setUI_default();
                else
                    setUI_dimmed();
            }});
            }
        }).start();
    }

    private void piReboot()
    {
        /*
         * pi is offline, cant reboot
         */
        if(!connected)
        {
            Toast.makeText(getApplicationContext(),"Pi is unavailable.", Toast.LENGTH_LONG).show();
            return;
        }

        /*
         * tap again to confirm reboot
         */
        if(!rebootConfirm)
        {
            rebootConfirmTimer();
            rebootConfirm = true;
        }
        /*
         * tapped again, rebooting
         */
        else
        {
            Toast.makeText(getApplicationContext(),"Rebooting", Toast.LENGTH_LONG).show();
            updateImageConnected(false);

            reboot();
            setUI_offline();
            poolIsOnline();

            rebootConfirm = false;
        }
    }

    private void reboot()
    {
        new Thread(new Runnable(){public void run()
        {
            /*
             * start python script on pi called 'reboot'
             */
            macros.callMacro("reboot");
        }
        }).start();
    }


    private void setUI_offline()
    {
        rebootButton.setImageResource(R.drawable.button_reboot_attention);
        connected = false;
    }


    private void setUI_online()
    {
        rebootButton.setImageResource(R.drawable.button_reboot);
        connected = true;
    }


    private void poolIsOnline()
    {
        new Thread(new Runnable(){public void run(){
            int state = -1;


            /*
             * pool while can't read state and app not hidden
             */
            while(state == -1 && !paused)
            {
                try {
                    state = (int)Float.parseFloat(client.sendRequest("GET", "/GPIO/"+ lightPin +"/value"));
                } catch (Exception e) {
                    System.out.println("state " + state + " exception, paused: "+paused);
                }
                try { Thread.currentThread().sleep( 333 );} catch (InterruptedException e) { e.printStackTrace(); }
            }

            if(state != -1)
            {
                runOnUiThread(new Runnable(){@Override public void run() {
                    setUI_online();
                    updateWifiName();
                }});

                /*
                 * light startup
                 */
                // change state
                System.out.println(lightPinState);

                try {
                    lightPinState = gpio.digitalRead(lightPin);
                } catch(Exception e){}

                if(lightOnStart && lightPinState)
                    lightPinState = gpio.digitalWrite(lightPin, !lightPinState);

                System.out.println(lightPinState);

                // update ui
                runOnUiThread(new Runnable(){@Override public void run() {
                    if(!lightPinState)
                        setUI_default();
                    else
                        setUI_dimmed();
                }});

            }


        }}).start();
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
