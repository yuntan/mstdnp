package xyz.untan.mstdnp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

/*
* activity for authorization
*/
public class AuthActivity extends AppCompatActivity {
    // views
    private EditText _instanceView;
    // SharedPreference util
    private AppStatus _appStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        _appStatus = new AppStatus();

        _instanceView = (EditText) findViewById(R.id.edit_instance);
        _instanceView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    // FIXME WHY FUCKING KEYBOARD DIDN'T HIDDEN????
                    v.clearFocus();
                    ((InputMethodManager) AuthActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE))
                            .hideSoftInputFromWindow(v.getWindowToken(), 0);

                    attemptAuth();
                    return true;
                }
                return false;
            }
        });
        findViewById(R.id.button_next)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        attemptAuth();
                    }
                });

        // get callback uri from authorize request
        Uri uri = this.getIntent().getData();
        if (uri != null) { // callback
            onIntentCallback(uri);
        } else if (_appStatus.accessToken != null && !_appStatus.accessToken.isEmpty()) {
            // authorization already done
            startActivity(new Intent(this, SettingsActivity.class));
            finish(); // don't show this activity when back button pressed
        }
    }

    private void onIntentCallback(Uri uri) {
        // get authorization code
        String code = uri.getQueryParameter("code");
        if (code == null || code.isEmpty()) {
            showErrorToast();
            return;
        }

        RequestQueue queue = Volley.newRequestQueue(this);
        MastodonApi.getToken(
                queue, _appStatus.instanceHost,
                _appStatus.clientId, _appStatus.clientSecret, code,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        String token = null;
                        try {
                            token = response.getString("access_token");
                        } catch (JSONException e) {
                            e.printStackTrace();
                            showErrorToast();
                            return;
                        }

                        _appStatus.accessToken = token;
                        _appStatus.save();

                        startActivity(new Intent(AuthActivity.this, SettingsActivity.class));
                        finish(); // don't show this activity when back button pressed
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        showErrorToast();
                    }
                });
    }

    private void attemptAuth() {
        final String host = _instanceView.getText().toString();

        // TODO validation

        RequestQueue queue = Volley.newRequestQueue(this);
        MastodonApi.registerAppToInstance(queue, host, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                String clientId = null, clientSecret = null;
                try {
                    clientId = response.getString("client_id");
                    clientSecret = response.getString("client_secret");
                } catch (JSONException e) {
                    e.printStackTrace();
                    showErrorToast();
                    return;
                }

                _appStatus.instanceHost = host;
                _appStatus.clientId = clientId;
                _appStatus.clientSecret = clientSecret;
                _appStatus.save();

                // open browser
                startActivity(MastodonApi.getAuthorizeIntent(host, clientId));
                finish(); // don't show this activity when back button pressed
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                showErrorToast();
            }
        });
    }

    void showErrorToast() {
        // TODO custom style
        Toast.makeText(this, R.string.error_auth_failed, Toast.LENGTH_LONG).show();
    }
}

