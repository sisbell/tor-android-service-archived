/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.torproject.android.service.vpn;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.VpnService;
import android.net.VpnService.Builder;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;
import com.runjva.sourceforge.jsocks.protocol.ProxyServer;
import com.runjva.sourceforge.jsocks.server.ServerAuthenticatorNone;
import org.torproject.android.service.R;
import org.torproject.android.service.TorServiceConstants;
import org.torproject.android.service.util.TorServiceUtils;

import java.io.*;
import java.net.InetAddress;
import java.util.List;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class OrbotVpnManager implements Handler.Callback {
    private static final String TAG = "OrbotVpnService";
    private final static int VPN_MTU = 1500;
    private final static boolean mIsLollipop = Build.VERSION.SDK_INT >= Build.VERSION_CODES
            .LOLLIPOP;
    //this is the actual DNS server we talk to over UDP or TCP (now using Tor's DNS port)
    private final static String DEFAULT_ACTUAL_DNS_HOST = "127.0.0.1";
    private final static int DEFAULT_ACTUAL_DNS_PORT = TorServiceConstants.TOR_DNS_PORT_DEFAULT;
    public static int sSocksProxyServerPort = -1;
    public static String sSocksProxyLocalhost = null;
    File filePdnsd;
    private Thread mThreadVPN;
    private String mSessionName = "OrbotVPN";
    private ParcelFileDescriptor mInterface;
    private int mTorSocks = TorServiceConstants.SOCKS_PROXY_PORT_DEFAULT;
    private ProxyServer mSocksProxyServer;
    private boolean isRestart = false;
    private VpnService mService;

    public OrbotVpnManager(VpnService service) {
        mService = service;
        File fileBinHome = mService.getDir(TorServiceConstants.DIRECTORY_TOR_BINARY, Application
                .MODE_PRIVATE);
        filePdnsd = new File(fileBinHome, TorServiceConstants.PDNSD_ASSET_KEY);
        Tun2Socks.init();
    }

    public static void makePdnsdConf(Context context, String dns, int port, File fileDir) throws
            IOException {
        String conf = String.format(context.getString(R.string.pdnsd_conf), dns, port, fileDir
                .getCanonicalPath());

        File f = new File(fileDir, "pdnsd.conf");

        if (f.exists()) {
            f.delete();
        }

        FileOutputStream fos = new FileOutputStream(f, false);
        PrintStream ps = new PrintStream(fos);
        ps.print(conf);
        ps.close();

        File cache = new File(fileDir, "pdnsd.cache");

        if (!cache.exists()) {
            try {
                cache.createNewFile();
            } catch (Exception e) {

            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void doLollipopAppRouting(Builder builder) throws NameNotFoundException {
        List<TorifiedApp> apps = TorifiedApp.getApps(mService, TorServiceUtils
                .getSharedPrefs(mService.getApplicationContext()));
        boolean perAppEnabled = false;
        for (TorifiedApp app : apps) {
            if (app.isTorified() && (!app.getPackageName().equals(mService.getPackageName()))) {
                builder.addAllowedApplication(app.getPackageName());
                perAppEnabled = true;
            }
        }
        if (!perAppEnabled) {
            builder.addDisallowedApplication(mService.getPackageName());
        }
    }

    void handleIntent(Builder builder, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        switch (intent.getAction()) {
            case "start":
                // Stop the previous session by interrupting the thread.
                if (mThreadVPN == null || (!mThreadVPN.isAlive())) {
                    Log.d(TAG, "starting OrbotVPNService service!");
                    mTorSocks = intent.getIntExtra("torSocks", TorServiceConstants
                            .SOCKS_PROXY_PORT_DEFAULT);
                    if (!mIsLollipop) {
                        startSocksBypass();
                    }
                    setupTun2Socks(builder);
                }
                break;
            case "stop":
                Log.d(TAG, "stop OrbotVPNService service!");
                stopVPN();
                break;
            case "refresh":
                Log.d(TAG, "refresh OrbotVPNService service!");
                if (!mIsLollipop)
                    startSocksBypass();
                if (!isRestart)
                    setupTun2Socks(builder);
                break;
        }
    }

    @Override
    public boolean handleMessage(Message message) {
        if (message != null) {
            Toast.makeText(mService, message.what, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    public void onRevoke() {
        Log.w(TAG, "VPNService REVOKED!");
        if (!isRestart) {
            SharedPreferences prefs = TorServiceUtils.getSharedPrefs(mService
                    .getApplicationContext());
            prefs.edit().putBoolean("pref_vpn", false).commit();
            stopVPN();
        }
        isRestart = false;
    }

    private synchronized void setupTun2Socks(final Builder builder) {
        if (mInterface != null) {//stop tun2socks now to give it time to clean up
            isRestart = true;
            Tun2Socks.Stop();
        }

        mThreadVPN = new Thread() {

            public void run() {
                try {
                    if (isRestart) {
                        Log.d(TAG, "is a restart... let's wait for a few seconds");
                        Thread.sleep(3000);
                    }
                    //start PDNSD daemon pointing to actual DNS
                    startDNS(DEFAULT_ACTUAL_DNS_HOST, DEFAULT_ACTUAL_DNS_PORT);

                    final String vpnName = "OrbotVPN";
                    final String localhost = "127.0.0.1";
                    final String virtualGateway = "192.168.200.1";
                    final String virtualIP = "192.168.200.2";
                    final String virtualNetMask = "255.255.255.0";
                    final String dummyDNS = "1.1.1.1"; //this is intercepted by the tun2socks
                    // library, but we must put in a valid DNS to start
                    final String defaultRoute = "0.0.0.0";
                    final String localSocks = localhost + ':' + mTorSocks;
                    final String localDNS = virtualGateway + ':' + "8091";
                    final boolean localDnsTransparentProxy = true;

                    builder.setMtu(VPN_MTU).addAddress(virtualGateway, 32).setSession(vpnName)
                            .addDnsServer(dummyDNS).addRoute(dummyDNS, 32).addRoute(defaultRoute,
                            0);

                    if (mIsLollipop)
                        doLollipopAppRouting(builder);

                    // Create a new interface using the builder and save the parameters.
                    ParcelFileDescriptor newInterface = builder.setSession(mSessionName).establish();

                    if (mInterface != null) {
                        Log.d(TAG, "Stopping existing VPN interface");
                        mInterface.close();
                        mInterface = null;
                    }

                    mInterface = newInterface;

                    Tun2Socks.Start(mInterface, VPN_MTU, virtualIP, virtualNetMask, localSocks,
                            localDNS, localDnsTransparentProxy);

                    isRestart = false;
                } catch (Exception e) {
                    Log.d(TAG, "tun2Socks has stopped", e);
                }
            }

        };
        mThreadVPN.start();
    }

    private void startDNS(String dns, int port) throws IOException {

        makePdnsdConf(mService, dns, port, filePdnsd.getParentFile());

        String[] cmdString = {filePdnsd.getCanonicalPath(), "-c", filePdnsd.getParent() + "/pdnsd" +
                ".conf"};
        ProcessBuilder pb = new ProcessBuilder(cmdString);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try {
            proc.waitFor();
        } catch (Exception e) {
        }

        Log.i(TAG, "PDNSD: " + proc.exitValue());

        if (proc.exitValue() != 0) {
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                Log.d(TAG, "pdnsd: " + line);
            }
        }
    }

    private void startSocksBypass() {
        new Thread() {

            public void run() {
                if (sSocksProxyServerPort == -1) {
                    try {
                        sSocksProxyLocalhost = "127.0.0.1";
                        sSocksProxyServerPort = (int) ((Math.random() * 1000) + 10000);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to access localhost", e);
                        throw new RuntimeException("Unable to access localhost: " + e);
                    }
                }

                if (mSocksProxyServer != null) {
                    stopSocksBypass();
                }

                try {
                    mSocksProxyServer = new ProxyServer(new ServerAuthenticatorNone(null, null));
                    ProxyServer.setVpnService(mService);
                    mSocksProxyServer.start(sSocksProxyServerPort, 5, InetAddress.getLocalHost());
                } catch (Exception e) {
                    Log.e(TAG, "error getting host", e);
                }
            }
        }.start();

    }

    private synchronized void stopSocksBypass() {
        if (mSocksProxyServer != null) {
            mSocksProxyServer.stop();
            mSocksProxyServer = null;
        }
    }

    private void stopVPN() {
        if (mIsLollipop)
            stopSocksBypass();

        if (mInterface != null) {
            try {
                Log.d(TAG, "closing interface, destroying VPN interface");

                mInterface.close();
                mInterface = null;
            } catch (Exception e) {
                Log.d(TAG, "error stopping tun2socks", e);
            } catch (Error e) {
                Log.d(TAG, "error stopping tun2socks", e);
            }
        }

        Tun2Socks.Stop();

        try {
            TorServiceUtils.killProcess(filePdnsd);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mThreadVPN = null;
    }
}
