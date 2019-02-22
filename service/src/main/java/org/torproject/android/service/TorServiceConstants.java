/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals
.com/guardian */
/* See LICENSE for licensing information */

package org.torproject.android.service;

import android.content.Intent;

public interface TorServiceConstants {

    String TOR_APP_USERNAME = "org.torproject.android";
    String BROWSER_APP_USERNAME = "org.torproject.torbrowser";

    String DIRECTORY_TOR_BINARY = "bin";
    String SHELL_CMD_PS = "toolbox ps";
    int TOR_TRANSPROXY_PORT_DEFAULT = 9140;

    int TOR_DNS_PORT_DEFAULT = 5400;
    int HTTP_PROXY_PORT_DEFAULT = 8218;
    int SOCKS_PROXY_PORT_DEFAULT = 9150;

    //control port
    String LOG_NOTICE_HEADER = "NOTICE";
    String LOG_NOTICE_BOOTSTRAPPED = "Bootstrapped";

    /**
     * A request to Orbot to transparently start Tor services
     */
    String ACTION_START = "org.torproject.android.intent.action.START";
    /**
     * {@link Intent} send by Orbot with {@code ON/OFF/STARTING/STOPPING} status
     */
    String ACTION_STATUS = "org.torproject.android.intent.action.STATUS";
    /**
     * {@code String} that contains a status constant: {@link #STATUS_ON},
     * {@link #STATUS_OFF}, {@link #STATUS_STARTING}, or
     * {@link #STATUS_STOPPING}
     */
    String EXTRA_STATUS = "org.torproject.android.intent.extra.STATUS";
    /**
     * A {@link String} {@code packageName} for Orbot to direct its status reply
     * to, used in {@link #ACTION_START} {@link Intent}s sent to Orbot
     */
    String EXTRA_PACKAGE_NAME = "org.torproject.android.intent.extra.PACKAGE_NAME";
    /**
     * The SOCKS proxyWithAuthentication settings in URL form.
     */
    String EXTRA_SOCKS_PROXY = "org.torproject.android.intent.extra.SOCKS_PROXY";
    String EXTRA_SOCKS_PROXY_HOST = "org.torproject.android.intent.extra.SOCKS_PROXY_HOST";
    String EXTRA_SOCKS_PROXY_PORT = "org.torproject.android.intent.extra.SOCKS_PROXY_PORT";
    /**
     * The HTTP proxyWithAuthentication settings in URL form.
     */
    String EXTRA_HTTP_PROXY = "org.torproject.android.intent.extra.HTTP_PROXY";
    String EXTRA_HTTP_PROXY_HOST = "org.torproject.android.intent.extra.HTTP_PROXY_HOST";
    String EXTRA_HTTP_PROXY_PORT = "org.torproject.android.intent.extra.HTTP_PROXY_PORT";

    String LOCAL_ACTION_LOG = "log";
    String LOCAL_ACTION_BANDWIDTH = "bandwidth";
    String LOCAL_EXTRA_LOG = "log";

    /**
     * All tor-related services and daemons are stopped
     */
    String STATUS_OFF = "OFF";
    /**
     * All tor-related services and daemons have completed starting
     */
    String STATUS_ON = "ON";
    String STATUS_STARTING = "STARTING";
    String STATUS_STOPPING = "STOPPING";

    /**
     * The user has disabled the ability for background starts triggered by
     * apps. Fallback to the old {@link Intent} action that brings up Orbot:
     * {@link org.torproject.android.OrbotMainActivity#INTENT_ACTION_REQUEST_START_TOR}
     */
    String STATUS_STARTS_DISABLED = "STARTS_DISABLED";

    // actions for internal command Intents
    String CMD_SIGNAL_HUP = "signal_hup";
    String CMD_STATUS = "status";
    String CMD_FLUSH = "flush";
    String CMD_NEWNYM = "newnym";
    String CMD_VPN = "vpn";
    String CMD_VPN_CLEAR = "vpnclear";
    String CMD_UPDATE_TRANS_PROXY = "update";
    String CMD_SET_EXIT = "setexit";

    //  String BINARY_TOR_VERSION = "0.3.1.8-openssl1.0.2k";
    String PREF_BINARY_TOR_VERSION_INSTALLED = "BINARY_TOR_VERSION_INSTALLED";

    //obfsproxy 
    String OBFSCLIENT_ASSET_KEY = "obfs4proxy";

    //DNS daemon for TCP DNS over TOr
    String PDNSD_ASSET_KEY = "pdnsd";

    //EXIT COUNTRY CODES
    String[] COUNTRY_CODES = {"DE", "AT", "SE", "CH", "IS", "CA", "US", "ES", "FR", "BG", "PL", "AU", "BR", "CZ", "DK", "FI", "GB", "HU", "NL", "JP", "RO", "RU", "SG", "SK"};


    String HIDDEN_SERVICES_DIR = "hidden_services";


}
