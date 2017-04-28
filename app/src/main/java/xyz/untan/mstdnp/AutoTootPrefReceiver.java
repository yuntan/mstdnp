package xyz.untan.mstdnp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import static xyz.untan.mstdnp.SettingsActivity.KEY_ENABLED;

public class AutoTootPrefReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();

        if (intent.getBooleanExtra("enable", false)) {
            editor.putBoolean(KEY_ENABLED, true);
            editor.apply();
        } else if (intent.getBooleanExtra("disable", false)) {
            editor.putBoolean(KEY_ENABLED, false);
            editor.apply();
        }
    }
}
