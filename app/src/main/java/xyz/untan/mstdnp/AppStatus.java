package xyz.untan.mstdnp;

import com.os.operando.garum.annotations.DefaultBoolean;
import com.os.operando.garum.annotations.Pref;
import com.os.operando.garum.annotations.PrefKey;
import com.os.operando.garum.models.PrefModel;


@Pref(name = "app_status")
class AppStatus extends PrefModel{
    @PrefKey
    String instanceHost;

    @PrefKey
    String clientId;

    @PrefKey
    String clientSecret;

    @PrefKey
    String accessToken;
}
