package xyz.untan.mstdnp;

import android.Manifest;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.appcompat.app.AlertDialog;


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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && this.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            this.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);

//            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
//                Log.i(TAG, "permission " + (isGranted ? "GRANTED" : "DENIED"));
//            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // start MetadataService
        Intent intent = new Intent(this, MetadataService.class);
        startForegroundService(intent);
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
