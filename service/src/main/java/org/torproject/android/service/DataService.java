package org.torproject.android.service;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;
import com.msopentech.thali.toronionproxy.EventBroadcaster;
import com.msopentech.thali.toronionproxy.TorConfig;
import com.msopentech.thali.toronionproxy.TorConfigBuilder;
import org.torproject.android.binary.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

final class DataService {

    private final Context mContext;
    private final TorService torService;
    private final EventBroadcaster eventBroadcaster;
    private final TorConfig torConfig;

    DataService(Context context, TorService torService, TorConfig torConfig, EventBroadcaster eventBroadcaster) {
        this.mContext = context;
        this.torService = torService;
        this.eventBroadcaster = eventBroadcaster;
        this.torConfig = torConfig;
        String packageName = context.getApplicationInfo().packageName;
        COOKIE_CONTENT_URI = Uri.parse("content://" + packageName + ".ui.hiddenservices.providers.cookie/cookie");
        HS_CONTENT_URI = Uri.parse("content://" + packageName + ".ui.hiddenservices.providers/hs");
    }

    private Uri HS_CONTENT_URI;
    private Uri COOKIE_CONTENT_URI;

    private static final class HiddenService implements BaseColumns {
        static final String NAME = "name";
        static final String PORT = "port";
        static final String ONION_PORT = "onion_port";
        static final String DOMAIN = "domain";
        static final String AUTH_COOKIE = "auth_cookie";
        static final String AUTH_COOKIE_VALUE = "auth_cookie_value";
        static final String CREATED_BY_USER = "created_by_user";
        static final String ENABLED = "enabled";
    }

    private static final class ClientCookie implements BaseColumns {
        static final String DOMAIN = "domain";
        static final String AUTH_COOKIE_VALUE = "auth_cookie_value";
        static final String ENABLED = "enabled";
    }

    private static final String[] hsProjection = new String[]{
            HiddenService._ID,
            HiddenService.NAME,
            HiddenService.DOMAIN,
            HiddenService.PORT,
            HiddenService.AUTH_COOKIE,
            HiddenService.AUTH_COOKIE_VALUE,
            HiddenService.ONION_PORT,
            HiddenService.ENABLED};

    private static final String[] cookieProjection = new String[]{
            ClientCookie._ID,
            ClientCookie.DOMAIN,
            ClientCookie.AUTH_COOKIE_VALUE,
            ClientCookie.ENABLED};

    void updateHiddenServices() {
        ContentResolver cr = mContext.getContentResolver();
        Cursor hsCursor = cr.query(HS_CONTENT_URI, hsProjection, null, null, null);
        if (hsCursor == null) {
            return;
        }
        try {
            while (hsCursor.moveToNext()) {
                String domain = hsCursor.getString(hsCursor.getColumnIndex(HiddenService.DOMAIN));
                Integer localPort = hsCursor.getInt(hsCursor.getColumnIndex(HiddenService.PORT));
                Integer authCookie = hsCursor.getInt(hsCursor.getColumnIndex(HiddenService.AUTH_COOKIE));
                String authCookieValue = hsCursor.getString(hsCursor.getColumnIndex(HiddenService.AUTH_COOKIE_VALUE));

                // Update only new domains or restored from backup with auth cookie
                if ((domain == null || domain.length() < 1) || (authCookie == 1 && (authCookieValue == null || authCookieValue.length() < 1))) {
                    String hsDirPath = new File(torConfig.getHiddenServiceDir(), "hs" + localPort).getCanonicalPath();
                    File file = new File(hsDirPath, "hostname");

                    if (file.exists()) {
                        ContentValues fields = new ContentValues();
                        try {
                            String onionHostname = Utils.readString(new FileInputStream(file)).trim();
                            if (authCookie == 1) {
                                String[] aux = onionHostname.split(" ");
                                onionHostname = aux[0];
                                fields.put(HiddenService.AUTH_COOKIE_VALUE, aux[1]);
                            }
                            fields.put(HiddenService.DOMAIN, onionHostname);
                            cr.update(HS_CONTENT_URI, fields, "port=" + localPort, null);
                        } catch (FileNotFoundException e) {
                            eventBroadcaster.broadcastException("unable to read onion hostname file", e);
                            torService.notify(mContext.getString(R.string.unable_to_read_hidden_service_name),
                                    torService.getNotifyId(), R.drawable.ic_stat_notifyerr);
                        }
                    } else {
                        torService.notify(mContext.getString(R.string.unable_to_read_hidden_service_name),
                                torService.getNotifyId(), R.drawable.ic_stat_notifyerr);
                    }
                }
            }
        } catch (NumberFormatException e) {
            Log.e(OrbotConstants.TAG, "error parsing hsport", e);
        } catch (Exception e) {
            Log.e(OrbotConstants.TAG, "error starting share server", e);
        }
        hsCursor.close();
    }

    TorConfigBuilder updateConfigBuilder(TorConfigBuilder builder) {
        ContentResolver cr = mContext.getContentResolver();

        /* ---- Hidden Services ---- */
        Cursor hsCursor = cr.query(HS_CONTENT_URI, hsProjection,
                HiddenService.ENABLED + "=1", null, null);
        if (hsCursor != null) {
            try {
                while (hsCursor.moveToNext()) {
                    Integer localPort = hsCursor.getInt(hsCursor.getColumnIndex(HiddenService.PORT));
                    Integer onionPort = hsCursor.getInt(hsCursor.getColumnIndex(HiddenService.ONION_PORT));
                    Integer authCookie = hsCursor.getInt(hsCursor.getColumnIndex(HiddenService.AUTH_COOKIE));
                    String hsDirPath = new File(torConfig.getHiddenServiceDir(), "hs" + localPort).getCanonicalPath();

                    eventBroadcaster.broadcastDebug("Adding hidden service on port: " + localPort);
                    builder.line("HiddenServiceDir " + hsDirPath);
                    builder.line(String.format("HiddenServicePort %d 127.0.0.1:%d", onionPort, localPort));

                    if (authCookie == 1) {
                        String name = hsCursor.getString(hsCursor.getColumnIndex(HiddenService.NAME));
                        builder.line("HiddenServiceAuthorizeClient stealth " + name);
                    }
                }
            } catch (NumberFormatException e) {
                Log.e(OrbotConstants.TAG, "error parsing hsport", e);
            } catch (Exception e) {
                Log.e(OrbotConstants.TAG, "error starting share server", e);
            }
            hsCursor.close();
        }

        /* ---- Client Cookies ---- */
        Cursor cookieCursor = cr.query(COOKIE_CONTENT_URI, cookieProjection, ClientCookie.ENABLED + "=1", null, null);
        if (cookieCursor != null) {
            try {
                while (cookieCursor.moveToNext()) {
                    String domain = cookieCursor.getString(cookieCursor.getColumnIndex(ClientCookie.DOMAIN));
                    String cookie = cookieCursor.getString(cookieCursor.getColumnIndex(ClientCookie.AUTH_COOKIE_VALUE));
                    builder.line(String.format("HidServAuth %s %s", domain, cookie));
                }
            } catch (Exception e) {
                Log.e(OrbotConstants.TAG, "error starting share server", e);
            }
            cookieCursor.close();
        }
        return builder;
    }
}
