package org.torproject.android.service;

import android.text.TextUtils;
import net.freehaven.tor.control.EventHandler;
import org.torproject.android.service.util.Prefs;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 * Created by n8fr8 on 9/25/16.
 */
public final class TorEventHandler implements EventHandler, TorServiceConstants {

    private final AndroidEventBroadcaster mBroadcaster;
    private final TorService mService;
    private final NumberFormat mNumberFormat;

    private long lastRead = -1;
    private long lastWritten = -1;
    private long mTotalTrafficWritten;
    private long mTotalTrafficRead;

    private HashMap<String, Node> hmBuiltNodes = new HashMap<>();

    public TorEventHandler(TorService service, AndroidEventBroadcaster eventBroadcaster) {
        mService = service;
        mBroadcaster = eventBroadcaster;
        mNumberFormat = NumberFormat.getInstance(Locale.getDefault()); //localized numbers!
    }

    @Override
    public void bandwidthUsed(long read, long written) {
        if (read != lastRead || written != lastWritten) {
            StringBuilder sb = new StringBuilder();
            sb.append(formatCount(read));
            sb.append(" \u2193");
            sb.append(" / ");
            sb.append(formatCount(written));
            sb.append(" \u2191");

            int iconId = R.drawable.ic_stat_tor;

            if (read > 0 || written > 0)
                iconId = R.drawable.ic_stat_tor_xfer;

            mService.notify(sb.toString(), mService.getNotifyId(), iconId);

            mTotalTrafficWritten += written;
            mTotalTrafficRead += read;
        }

        lastWritten = written;
        lastRead = read;
        mBroadcaster.broadcastBandwidth(lastWritten, lastRead, mTotalTrafficWritten,
                mTotalTrafficRead);
    }

    public void circuitStatus(String status, String circID, String path) {
        /* once the first circuit is complete, then announce that Orbot is on*/
        if (mBroadcaster.getStatus().isStarting() && TextUtils.equals(status, "BUILT")) {
            mBroadcaster.getStatus().on();
        }

        StringBuilder sb = new StringBuilder().append("Circuit (").append((circID)).append(") ")
                .append(status).append(": ");

        StringTokenizer st = new StringTokenizer(path, ",");
        Node node = null;

        while (st.hasMoreTokens()) {
            String nodePath = st.nextToken();
            node = new Node();

            String[] nodeParts;

            if (nodePath.contains("="))
                nodeParts = nodePath.split("=");
            else
                nodeParts = nodePath.split("~");

            if (nodeParts.length == 1) {
                node.id = nodeParts[0].substring(1);
                node.name = node.id;
            } else if (nodeParts.length == 2) {
                node.id = nodeParts[0].substring(1);
                node.name = nodeParts[1];
            }

            node.status = status;

            sb.append(node.name);

            if (st.hasMoreTokens())
                sb.append(" > ");
        }

        if (Prefs.useDebugLogging())
            mBroadcaster.broadcastDebug(sb.toString());
        else if ("BUILT".equals(status))
            mBroadcaster.broadcastNotice(sb.toString());
        else if ("CLOSED".equals(status))
            mBroadcaster.broadcastNotice(sb.toString());

        if (Prefs.expandedNotifications()) {
            //get IP from last nodename
            if ("BUILT".equals(status)) {
                hmBuiltNodes.put(circID, node);
            }
            if ("CLOSED".equals(status)) {
                hmBuiltNodes.remove(circID);
            }
        }
    }

    private String formatCount(long count) {
        if (mNumberFormat == null) {
            return "";
        }
        // Under 2Mb, returns "xxx.xKb"
        // Over 2Mb, returns "xxx.xxMb"
        if (count < 1e6)
            return mNumberFormat.format(Math.round((float) ((int) (count * 10 / 1024)) / 10))
                    + "kbps";
        else
            return mNumberFormat.format(Math.round((float) ((int) (count * 100 / 1024 / 1024)
            ) / 100)) + "mbps";
    }

    public HashMap<String, Node> getNodes() {
        return hmBuiltNodes;
    }

    @Override
    public void message(String severity, String msg) {
        mBroadcaster.broadcastNotice(severity + ": " + msg);
    }

    @Override
    public void newDescriptors(List<String> orList) {
    }

    @Override
    public void orConnStatus(String status, String orName) {
        StringBuilder sb = new StringBuilder()
                .append("orConnStatus (").append(parseNodeName(orName)).append("): ").append
                        (status);
        mBroadcaster.broadcastDebug(sb.toString());
    }

    private String parseNodeName(String node) {
        if (node.indexOf('=') != -1) {
            return node.substring(node.indexOf("=") + 1);
        } else if (node.indexOf('~') != -1) {
            return node.substring(node.indexOf("~") + 1);
        } else {
            return node;
        }
    }

    @Override
    public void streamStatus(String status, String streamID, String target) {
        StringBuilder sb = new StringBuilder().append("StreamStatus (").append((streamID)).append
                ("): ").append(status);
        mBroadcaster.broadcastNotice(sb.toString());
    }

    @Override
    public void unrecognized(String type, String msg) {
        if(!"STATUS_CLIENT".equals(type)) {
            StringBuilder sb = new StringBuilder().append("Message (").append(type).append("): ")
                    .append(msg);
            mBroadcaster.broadcastNotice(sb.toString());
        }
    }

    public class Node {
        String status;
        String id;
        String name;
    }
}
