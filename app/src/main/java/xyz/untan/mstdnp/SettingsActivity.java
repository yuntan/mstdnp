package xyz.untan.mstdnp;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;


public class SettingsActivity extends FragmentActivity {
    private static final String TAG = SettingsActivity.class.getSimpleName();
    static final String KEY_ENABLED = "enabled";
    static final String KEY_SCOPE = "scope";
    private AppStatus _appStatus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialize member
        _appStatus = new AppStatus();

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        PreferenceManager.setDefaultValues(this, R.xml.pref_main, false);

        if (!_appStatus.permissionCheckCompleted) {
            DialogFragment dialog = new OpenSettingDialogFragment();
            dialog.show(getSupportFragmentManager(), TAG);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // start MetadataService
        Intent intent = new Intent(this, MetadataService.class);
        startService(intent);
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_main);
        }
    }


    public static class OpenSettingDialogFragment extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.title_open_setting)
                    .setMessage(R.string.message_open_setting)
                    .setPositiveButton(R.string.action_open_setting, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                            startActivity(intent);
                        }
                    })
                    .create();
        }
    }
}
