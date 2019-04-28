/* Copyright (c) 2009-2011, Nathan Freitas, Orbot / The Guardian Project -
https://guardianproject.info/apps/orbot */
/* See LICENSE for licensing information */

package org.torproject.android.service;

import android.annotation.TargetApi;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import com.msopentech.thali.android.toronionproxy.AndroidOnionProxyManager;
import com.msopentech.thali.toronionproxy.TorConfig;
import com.msopentech.thali.toronionproxy.TorConfigBuilder;
import com.msopentech.thali.toronionproxy.TorInstaller;
import org.torproject.android.service.util.NotificationBuilderCompat;
import org.torproject.android.service.util.Prefs;
import org.torproject.android.service.util.TorServiceUtils;
import org.torproject.android.service.vpn.TorVpnService;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TorService extends Service implements TorServiceConstants, OrbotConstants {

    public final static String TOR_VERSION = "0.3.5.8-rc-openssl1.0.2p";
    private static final int NOTIFY_ID = 1;
    private static final int ERROR_NOTIFY_ID = 3;
    private final static String NOTIFICATION_CHANNEL_ID = "orbot_channel_1";
    private boolean mConnectivity = true;
    private AndroidOnionProxyManager onionProxyManager;
    private ActionBroadcastReceiver mActionBroadcastReceiver;
    private AndroidEventBroadcaster mEventBroadcaster;
    private DataService mDataService;
    private TorEventHandler mEventHandler;
    private ExecutorService mExecutor = Executors.newFixedThreadPool(3);
    private SharedPreferences mPrefs;
    private int mPortSOCKS;
    private NotificationManager mNotificationManager;
    private boolean mNotificationShowing = false;
    private NotificationBuilderCompat mNotifyBuilder;

    private final BroadcastReceiver mNetworkStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mEventBroadcaster.getStatus().isOff()) {
                return;
            }
            final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context
                    .CONNECTIVITY_SERVICE);
            if (cm == null) {
                return;
            }

            final NetworkInfo netInfo = cm.getActiveNetworkInfo();
            mConnectivity = netInfo != null && netInfo.isConnected();
            if (mPrefs.getBoolean(OrbotConstants.PREF_DISABLE_NETWORK, true)
                    && !mEventBroadcaster.getStatus().isOff()) {
                setTorNetworkEnabledAsync(mConnectivity);
                if (!mConnectivity) {
                    mEventBroadcaster.broadcastNotice(context.getString(R.string
                            .no_network_connectivity_putting_tor_to_sleep_));
                    TorService.this.notify(getString(R.string.no_internet_connection_tor)
                            , NOTIFY_ID, R.drawable.ic_stat_tor_off);
                } else {
                    mEventBroadcaster.broadcastNotice(context.getString(R.string
                            .network_connectivity_is_good_waking_tor_up_));
                    TorService.this.notify(getString(R.string.status_activated),
                            NOTIFY_ID, R.drawable.ic_stat_tor);
                }
            }
        }
    };

    public void clearNotifications() {
        if (mNotificationManager != null)
            mNotificationManager.cancelAll();
        mNotificationShowing = false;
    }

    @TargetApi(14)
    private void clearVpnProxy() {
        mEventBroadcaster.broadcastDebug("clearing VPN Proxy");
        Prefs.putUseVpn(false);

        Intent intentVpn = new Intent(this, TorVpnService.class);
        intentVpn.setAction("stop");
        startService(intentVpn);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel(String appName, String appDescription) {
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel mChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, appName,
                importance);
        mChannel.setDescription(appDescription);
        mChannel.enableLights(false);
        mChannel.enableVibration(false);
        mChannel.setShowBadge(false);
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        mNotificationManager.createNotificationChannel(mChannel);
    }

    public int getNotifyId() {
        return NOTIFY_ID;
    }

    public boolean hasConnectivity() {
        return mConnectivity;
    }

    private void newIdentityAsync() {
        //it is possible to not have a connection yet, and someone might try to newnym
        new Thread() {
            public void run() {
                if (!onionProxyManager.isRunning()) {
                    return;
                }
                if (hasConnectivity() && Prefs.expandedNotifications()) {
                    TorService.this.notify(getString(R.string.newnym), getNotifyId(), R.drawable
                            .ic_stat_tor);
                }
                onionProxyManager.setNewIdentity();
            }
        }.start();
    }

    public void notify(String notifyMsg, int notifyType, int icon) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(getPackageName());
        if(intent == null) {
            Log.d(TAG, "Unable to notify: no intent found");
            return;
        }
        intent.setAction(TorServiceConstants.TOR_APP_USERNAME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

        if (mNotifyBuilder == null) {
            mNotifyBuilder = new NotificationBuilderCompat(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name)).setContentIntent(pendIntent).setCategory("service")
                    .setSmallIcon(R.drawable.ic_stat_tor);

            Intent intentRefresh = new Intent(CMD_NEWNYM);
            PendingIntent pendingIntentNewNym = PendingIntent.getBroadcast(getApplicationContext
                            (), 0,
                    intentRefresh, PendingIntent.FLAG_UPDATE_CURRENT);
            mNotifyBuilder.addAction(R.drawable.ic_refresh_white_24dp, getString(R.string
                            .menu_new_identity),
                    pendingIntentNewNym);
            mNotifyBuilder.setOngoing(Prefs.persistNotifications());
        }

        mNotifyBuilder.setContentText(notifyMsg).setSmallIcon(icon).setTicker(notifyType !=
                NOTIFY_ID ? notifyMsg : null);

        if (!Prefs.persistNotifications())
            mNotifyBuilder.setPriority(Notification.PRIORITY_LOW);

        Notification mNotification = mNotifyBuilder.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFY_ID, mNotification);
        } else if (Prefs.persistNotifications() && (!mNotificationShowing)) {
            startForeground(NOTIFY_ID, mNotification);
            mEventBroadcaster.broadcastNotice("Set background service to FOREGROUND");
        } else {
            mNotificationManager.notify(NOTIFY_ID, mNotification);
        }
        mNotificationShowing = true;
    }

    private void notifyIfConnectedToTorNetwork() {
        try {
            if (onionProxyManager.isRunning()) {
                mPortSOCKS = onionProxyManager.getIPv4LocalHostSocksPort();
                mEventBroadcaster.broadcastLogMessage(getString(R.string
                        .found_existing_tor_process));
                mEventBroadcaster.getStatus().on();
                TorService.this.notify(getString(R.string.status_activated),
                        NOTIFY_ID, R.drawable.ic_stat_tor);
            }
        } catch (Exception e) {
            Log.e(OrbotConstants.TAG, "error onBind", e);
            mEventBroadcaster.broadcastNotice("error finding exiting process: "
                    + e.toString());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e(TAG, "onBind");
        if (intent != null && intent.getAction() != null) {
            Log.e(TAG, intent.getAction());
        }
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            createNotificationChannel(getString(R.string.app_name), getString(R.string
                    .app_description));
        }

        mPrefs = TorServiceUtils.getSharedPrefs(getApplicationContext());
        AndroidTorSettings androidTorSettings = new AndroidTorSettings(this, mPrefs);
        mEventBroadcaster = new AndroidEventBroadcaster(getApplicationContext(), androidTorSettings);
        mEventHandler = new TorEventHandler(this, mEventBroadcaster);

        File configDir = getDir("torservice", Context.MODE_PRIVATE);
        File nativeDir = new File(getApplicationInfo().nativeLibraryDir);
        TorConfig torConfig = createConfig(nativeDir, configDir);
        TorInstaller torInstaller = new CustomTorInstaller(getApplicationContext(), configDir, torConfig.getTorrcFile());

        onionProxyManager =
                new AndroidOnionProxyManager(getApplicationContext(), torConfig,
                        torInstaller, androidTorSettings, mEventBroadcaster, mEventHandler);
        mDataService = new DataService(getApplicationContext(), this, onionProxyManager.getContext().getConfig(),
                mEventBroadcaster);

        registerReceiver(mNetworkStateReceiver, new IntentFilter(ConnectivityManager
                .CONNECTIVITY_ACTION));
        mActionBroadcastReceiver = new ActionBroadcastReceiver();
        registerReceiver(mActionBroadcastReceiver, new IntentFilter(CMD_NEWNYM));

        new Thread(new Runnable() {
            public void run() {
                if (setupTor()) {
                    notifyIfConnectedToTorNetwork();
                }
            }
        }).start();

        Log.i("TorService", "onCreate end");
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(mNetworkStateReceiver);
            unregisterReceiver(mActionBroadcastReceiver);
        } catch (IllegalArgumentException iae) {

        }
        stopTorAsync();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mEventBroadcaster.broadcastNotice("Low Memory Warning!");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        notify(getString(R.string.status_starting_up), NOTIFY_ID, R.drawable.ic_stat_tor);
        if (intent != null)
            mExecutor.execute(new IncomingIntentRouter(intent));
        else
            Log.d(OrbotConstants.TAG, "Got null onStartCommand() intent");
        return Service.START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(OrbotConstants.TAG, "task removed");
        stopTorAsync();
    }

    private void setTorNetworkEnabledAsync(final boolean isEnabled) {
        new Thread() {
            public void run() {
                onionProxyManager.disableNetwork(isEnabled);
            }
        }.start();
    }

    private boolean setupTor() {
        try {
            onionProxyManager.setup();
            return true;
        } catch (Exception e) {
            Log.e(OrbotConstants.TAG, "Error installing Tor binaries", e);
            mEventBroadcaster.broadcastNotice("There was an error installing Tor binaries");
            return false;
        }
    }

    private synchronized void startTor() {
        if (mEventBroadcaster.getStatus().isStopping()) {
            mEventBroadcaster.broadcastLogMessage("Ignoring start request, currently stopping");
        } else if (mEventBroadcaster.getStatus().isOn() && onionProxyManager.getTorPid() != -1) {
            mEventBroadcaster.broadcastLogMessage("Ignoring start request, already started.");
            setTorNetworkEnabledAsync(true);
        } else {
            try {
                updateTorrcConfig();
                mEventBroadcaster.broadcastNotice("checking binary version: " + TOR_VERSION);
                mEventBroadcaster.getStatus().starting();
                notify(getString(R.string.status_starting_up), NOTIFY_ID,
                        R.drawable.ic_stat_tor);
                mEventBroadcaster.broadcastNotice(getString(R.string.status_starting_up));
                onionProxyManager.start();
                mEventBroadcaster.broadcastLogMessage(getString(R.string.tor_process_starting)
                        + ' ' + getString(R.string.tor_process_complete));
                mDataService.updateHiddenServices();
            } catch (Exception e) {
                mEventBroadcaster.broadcastException("Unable to start Tor: " + e.toString(), e);
                notify(getString(R.string.unable_to_start_tor) + ": " + e.getMessage(),
                        ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr);
                mEventBroadcaster.getStatus().off();
            }
        }
    }

    private void startVPNService(Integer portSocks) {
        Intent intentVpn = new Intent(this, TorVpnService.class)
                .setAction("start").putExtra("torSocks", portSocks);
        startService(intentVpn);
    }

    private void stopTorAndClearNotifications() {
        Log.i("TorService", "stopTorAsync");
        mEventBroadcaster.getStatus().stopping();
        mEventBroadcaster.broadcastLogMessage(getString(R.string.status_shutting_down));
        try {
            onionProxyManager.stop();
            stopForeground(true);
            mEventBroadcaster.broadcastLogMessage(getString(R.string.status_disabled));
        } catch (Exception e) {
            mEventBroadcaster.broadcastNotice("An error occured stopping Tor: " + e.getMessage());
            mEventBroadcaster.broadcastLogMessage(getString(R.string.something_bad_happened));
        }
        mEventHandler.getNodes().clear();
        clearNotifications();
        mEventBroadcaster.getStatus().off();
    }

    private void stopTorAsync() {
        new Thread(new Runnable() {
            public void run() {
                stopTorAndClearNotifications();
            }
        }).start();
    }

    /**
     * Updates the torrc file based on the current user preferences
     */
    private boolean updateTorrcConfig() {
        try {
            mEventBroadcaster.broadcastNotice(getString(R.string
                    .updating_settings_in_tor_service));
            TorConfigBuilder builder = onionProxyManager.getContext()
                    .newConfigBuilder().updateTorConfig();

            //Check bridges to see if we need this
            File nativeDir = new File(getApplicationInfo().nativeLibraryDir);
            File pluggableTransport = new File(nativeDir, "libObfs4proxy.so");
            if(!pluggableTransport.canExecute()) pluggableTransport.setExecutable(true);

            builder.configurePluggableTransportsFromSettings(pluggableTransport);
            mDataService.updateConfigBuilder(builder);
            onionProxyManager.getTorInstaller().updateTorConfigCustom
                    (builder.asString());
            mEventBroadcaster.broadcastNotice("updating torrc custom configuration...");
            mEventBroadcaster.broadcastDebug("torrc.custom=" + builder.asString());
            mEventBroadcaster.broadcastNotice("success.");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(OrbotConstants.TAG, e.getMessage());
            mEventBroadcaster.broadcastNotice("Error configuring tor: " + e.toString());
            return false;
        }

        return true;
    }

    private class ActionBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            if (intent != null || CMD_NEWNYM.equals(intent.getAction())) {
                newIdentityAsync();
            }
        }
    }

    private class IncomingIntentRouter implements Runnable {
        private final Intent mIntent;

        IncomingIntentRouter(Intent intent) {
            mIntent = intent;
        }

        public void run() {
            //todo: TEST IF THIS IS CALLED DURING TOR INSTALLATION
            String action = mIntent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_START:
                        mEventBroadcaster.replyWithStatus(mIntent);
                        startTor();
                        break;
                    case ACTION_STATUS:
                        mEventBroadcaster.replyWithStatus(mIntent);
                        break;
                    case CMD_SIGNAL_HUP:
                        onionProxyManager.reloadTorConfig();
                        break;
                    case CMD_NEWNYM:
                        newIdentityAsync();
                        break;
                    case CMD_VPN:
                        startVPNService(mPortSOCKS);
                        break;
                    case CMD_VPN_CLEAR:
                        clearVpnProxy();
                        break;
                    case CMD_SET_EXIT:
                        String newExits = mIntent.getStringExtra("exit");
                        SharedPreferences prefs = TorServiceUtils.getSharedPrefs
                                (getApplicationContext());
                        if (TextUtils.isEmpty(newExits)) {
                            prefs.edit().remove("pref_exit_nodes").apply();
                        } else {
                            prefs.edit().putString("pref_exit_nodes", newExits).apply();
                        }
                        onionProxyManager.setExitNode(newExits);
                        break;
                    default:
                        Log.w(OrbotConstants.TAG, "unhandled TorService Intent: " + action);
                        break;
                }
            }
        }
    }

    private static TorConfig createConfig(File nativeDir, File configDir) {
        TorConfig.Builder builder = new TorConfig.Builder(nativeDir, configDir);
       // builder.dataDir(configDir);
        File exe = new File(nativeDir, "libTor.so");
        exe.setExecutable(true);
        builder.torExecutable(exe);
        return builder.build();
    }
}