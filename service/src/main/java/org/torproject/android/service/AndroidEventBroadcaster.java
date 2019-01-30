package org.torproject.android.service;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import com.msopentech.thali.toronionproxy.BaseEventBroadcaster;
import com.msopentech.thali.toronionproxy.TorSettings;

import static org.torproject.android.service.TorServiceConstants.*;

public final class AndroidEventBroadcaster extends BaseEventBroadcaster {

    private final LocalBroadcastManager mBroadcaster;
    private final Context mContext;

    public AndroidEventBroadcaster(Context context, TorSettings settings) {
        super(settings);
        mContext = context;
        mBroadcaster = LocalBroadcastManager.getInstance(context);;
    }

    @Override
    public void broadcastBandwidth(long upload, long download, long written, long read) {
        Intent intent = new Intent(LOCAL_ACTION_BANDWIDTH);
        intent.putExtra("up", upload).putExtra("down", download).putExtra("written", written)
                .putExtra("read", read).putExtra(EXTRA_STATUS, mStatus.getStatus());
        mBroadcaster.sendBroadcast(intent);
    }

    @Override
    public void broadcastLogMessage(String logMessage) {
        Intent intent = new Intent(LOCAL_ACTION_LOG);
        intent.putExtra(LOCAL_EXTRA_LOG, logMessage);
        intent.putExtra(EXTRA_STATUS, mStatus.getStatus());
        mBroadcaster.sendBroadcast(intent);
    }

    @Override
    public void broadcastStatus() {
        Intent intent = getActionStatusIntent();
        System.out.println("Sending status: " + mStatus.getStatus());
        mBroadcaster.sendBroadcast(intent);
        mContext.sendBroadcast(intent);
    }

    private Intent getActionStatusIntent() {
        return new Intent(ACTION_STATUS).putExtra(EXTRA_STATUS, mStatus.getStatus());
    }

    /*
     * Send Orbot's status in reply to an
     * {@link TorServiceConstants#ACTION_START} {@link Intent}, targeted only to
     * the app that sent the initial request. If the user has disabled auto-
     * starts, the reply {@code ACTION_START Intent} will include the extra
     * {@link TorServiceConstants#STATUS_STARTS_DISABLED}
     */
    public void replyWithStatus(Intent startRequest) {
        String packageName = startRequest.getStringExtra(EXTRA_PACKAGE_NAME);

        Intent reply = new Intent(ACTION_STATUS);
        reply.putExtra(EXTRA_STATUS, mStatus.getStatus());
        reply.putExtra(EXTRA_SOCKS_PROXY, "socks://127.0.0.1:" + SOCKS_PROXY_PORT_DEFAULT);
        reply.putExtra(EXTRA_SOCKS_PROXY_HOST, "127.0.0.1");
        reply.putExtra(EXTRA_SOCKS_PROXY_PORT, SOCKS_PROXY_PORT_DEFAULT);
        reply.putExtra(EXTRA_HTTP_PROXY, "http://127.0.0.1:" + HTTP_PROXY_PORT_DEFAULT);
        reply.putExtra(EXTRA_HTTP_PROXY_HOST, "127.0.0.1");
        reply.putExtra(EXTRA_HTTP_PROXY_PORT, HTTP_PROXY_PORT_DEFAULT);

        if (packageName != null) {
            reply.setPackage(packageName);
            mContext.sendBroadcast(reply);
        } else {
            mBroadcaster.sendBroadcast(reply);
        }
    }
}
