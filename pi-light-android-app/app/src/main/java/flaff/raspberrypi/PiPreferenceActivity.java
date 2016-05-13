package flaff.raspberrypi;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;

import java.util.List;

/**
 * Created by Flaff on 12.05.2016.
 */
public class PiPreferenceActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new PiPreferenceFragment()).commit();
    }
}
