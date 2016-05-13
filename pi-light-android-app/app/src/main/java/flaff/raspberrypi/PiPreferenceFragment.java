package flaff.raspberrypi;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Created by Flaff on 13.05.2016.
 */
public class PiPreferenceFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
}
