package xyz.untan.mastodontest;

import android.content.Intent;
import android.net.Uri;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;


class MastodonApi {
    static void registerAppToInstance(
            RequestQueue queue, String host,
            Response.Listener<JSONObject> listener, Response.ErrorListener errListener) {
        String url = "https://" + host + "/api/v1/apps"
                + "?client_name=mstdnp"
                + "&redirect_uris=mstdnp%3A%2F%2Fauthorize"
                + "&scopes=write";

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, url, null, listener, errListener);
        queue.add(request);
    }

    static Intent getAuthorizeIntent(String host, String clientId) {
        String uri= "https://" + host + "/oauth/authorize"
                + "?client_id=" + clientId
                + "&response_type=code"
                + "&redirect_uri=mstdnp%3A%2F%2Fauthorize"
                + "&scope=write";
        return new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
    }
    /*
    static void authorizeApp(
            RequestQueue queue, String host, String clientId, String clientSecret,
            Response.Listener<JSONObject> listener, Response.ErrorListener errListener) {
        String url = "https://" + host + "/oauth/authorize"
                + "?client_id=" + clientId
                + "&response_type=code"
                + "&redirect_uri=mstdnp%3A%2F%2Fmstdnp";

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, url, null, listener, errListener);
        queue.add(request);
    }
    */

    static void getToken(
            RequestQueue queue, String host, String clientId, String clientSecret, String code,
            Response.Listener<JSONObject> listener, Response.ErrorListener errListener) {
        String url = "https://" + host + "/oauth/token"
                + "?client_id=" + clientId
                + "&client_secret=" + clientSecret
                + "&grant_type=authorization_code"
                + "&code=" + code
                + "&redirect_uri=mstdnp%3A%2F%2Fauthorize"
                + "&scope=write";

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, url, null, listener, errListener);
        queue.add(request);
    }

    static void toot(
            RequestQueue queue, String host, final String token,
            Response.Listener<JSONObject> listener, Response.ErrorListener errListener,
            final String status) {
        String encodedStatus = null;
        try {
            encodedStatus = URLEncoder.encode(status, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String url = "https://" + host + "/api/v1/statuses?status=" + encodedStatus;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, url, null, listener, errListener) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = super.getHeaders();
                // copy map
                headers = new HashMap<>(headers);
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        queue.add(request); // send request
    }
}
