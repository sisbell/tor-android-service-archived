package org.torproject.android.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import com.msopentech.thali.toronionproxy.TorSettings;
import org.torproject.android.service.util.Prefs;
import org.torproject.android.service.vpn.OrbotVpnManager;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.torproject.android.service.TorServiceConstants.HTTP_PROXY_PORT_DEFAULT;

public class AndroidTorSettings implements TorSettings {
    private final SharedPreferences prefs;

    private final Context context;

    private static String PREF_OR = "pref_or";
    private static String PREF_OR_PORT = "pref_or_port";
    private static String PREF_OR_NICKNAME = "pref_or_nickname";
    private static String PREF_REACHABLE_ADDRESSES = "pref_reachable_addresses";
    private static String PREF_REACHABLE_ADDRESSES_PORTS = "pref_reachable_addresses_ports";
    private static String PREF_SOCKS = "pref_socks";
    private static String PREF_ISOLATE_DEST = "pref_isolate_dest";
    private static String PREF_CONNECTION_PADDING = "pref_connection_padding";
    private static String PREF_REDUCED_CONNECTION_PADDING = "pref_reduced_connection_padding";


    public AndroidTorSettings(Context context, SharedPreferences prefs) {
        this.prefs = prefs;
        this.context = context;
    }

    @Override
    public boolean disableNetwork() {
        return true;
    }

    @Override
    public String dnsPort() {
        return prefs.getString("pref_dnsport", String.valueOf(TorServiceConstants.TOR_DNS_PORT_DEFAULT));
    }

    @Override
    public String getCustomTorrc() {
        return prefs.getString("pref_custom_torrc", "");
    }

    @Override
    public String getEntryNodes() {
        return prefs.getString("pref_entrance_nodes", "");
    }

    @Override
    public String getExcludeNodes() {
        return prefs.getString("pref_exclude_nodes", "");
    }

    @Override
    public String getExitNodes() {
        return prefs.getString("pref_exit_nodes", "");
    }

    @Override
    public int getHttpTunnelPort() {
        return HTTP_PROXY_PORT_DEFAULT;
    }

    @Override
    public List<String> getListOfSupportedBridges() {
        try {
            return Arrays.asList(new String(Prefs.getBridgesList().getBytes("ISO-8859-1")).split(","));
        } catch (UnsupportedEncodingException e) {
            return new ArrayList<>();
        }
    }

    @Override
    public String getProxyHost() {
        return prefs.getString("pref_proxy_host", null);
    }

    @Override
    public String getProxyPassword() {
        return prefs.getString("pref_proxy_password", null);
    }

    @Override
    public String getProxyPort() {
        return prefs.getString("pref_proxy_port", null);
    }

    @Override
    public String getProxySocks5Host() {
        return OrbotVpnManager.sSocksProxyLocalhost;
    }

    @Override
    public String getProxySocks5ServerPort() {
        return String.valueOf(OrbotVpnManager.sSocksProxyServerPort);
    }

    @Override
    public String getProxyType() {
        return prefs.getString("pref_proxy_type", null);
    }

    @Override
    public String getProxyUser() {
        return prefs.getString("pref_proxy_username", null);
    }

    @Override
    public String getReachableAddressPorts() {
        return prefs.getString(PREF_REACHABLE_ADDRESSES_PORTS, "*:80,*:443");
    }

    @Override
    public String getRelayNickname() {
        return prefs.getString(PREF_OR_NICKNAME, "Orbot");
    }

    @Override
    public int getRelayPort() {
        return Integer.parseInt(prefs.getString(PREF_OR_PORT, "9001"));
    }

    @Override
    public String getSocksPort() {
        return prefs.getString(PREF_SOCKS, String.valueOf(TorServiceConstants.SOCKS_PROXY_PORT_DEFAULT));
    }

    @Override
    public String getVirtualAddressNetwork() {
        return "10.192.0.0/10";
    }

    @Override
    public boolean hasBridges() {
        return Prefs.bridgesEnabled();
    }

    @Override
    public boolean hasConnectionPadding() {
        return prefs.getBoolean(PREF_CONNECTION_PADDING, false);
    }

    @Override
    public boolean hasCookieAuthentication() {
        return true;
    }

    @Override
    public boolean hasDebugLogs() {
        return Prefs.useDebugLogging();
    }

    @Override
    public boolean hasIsolationAddressFlagForTunnel() {
        return prefs.getBoolean(PREF_ISOLATE_DEST, false);
    }

    @Override
    public boolean hasOpenProxyOnAllInterfaces() {
        return Prefs.openProxyOnAllInterfaces();
    }

    @Override
    public boolean hasReachableAddress() {
        return prefs.getBoolean(PREF_REACHABLE_ADDRESSES, false);
    }

    @Override
    public boolean hasReducedConnectionPadding() {
        return prefs.getBoolean(PREF_REDUCED_CONNECTION_PADDING, true);
    }

    @Override
    public boolean hasSafeSocks() {
        return false;
    }

    @Override
    public boolean hasStrictNodes() {
        return prefs.getBoolean("pref_strict_nodes", false);
    }

    @Override
    public boolean hasTestSocks() {
        return false;
    }

    @Override
    public boolean isAutomapHostsOnResolve() {
        return true;
    }

    @Override
    public boolean isRelay() {
        return prefs.getBoolean(PREF_OR, false);
    }

    @Override
    public boolean runAsDaemon() {
        return true;
    }

    @Override
    public String transPort() {
        return prefs.getString("pref_transport", String.valueOf(TorServiceConstants.TOR_TRANSPROXY_PORT_DEFAULT));
    }

    @Override
    public boolean useSocks5() {
        return Prefs.useVpn() && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
    }
}
