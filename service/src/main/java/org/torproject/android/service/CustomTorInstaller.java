package org.torproject.android.service;

import android.content.Context;
import android.util.Log;
import com.msopentech.thali.toronionproxy.TorInstaller;
import org.torproject.android.service.util.Prefs;

import java.io.*;
import java.util.concurrent.TimeoutException;

public class CustomTorInstaller extends TorInstaller {

    private final Context context;
    private final File torrcFile;
    private final File configDir;

    public CustomTorInstaller(Context context, File configDir, File torrcFile) {
        this.context = context;
        this.torrcFile = torrcFile;
        this.configDir = configDir;
    }

    @Override
    public void setup() throws IOException {
        copy(context.getAssets().open("common/geoip"), new File(configDir, "geoip"));
        copy(context.getAssets().open("common/geoip6"), new File(configDir, "geoip6"));
        copy(context.getAssets().open("common/torrc"), new File(configDir, "torrc"));
    }

    @Override
    public void updateTorConfigCustom(String content) throws IOException, TimeoutException {
        updateTorConfigCustom(torrcFile, content);
    }

    /**
     * Opens bridges list as <code>InputStream</code>. First checks for user defined bridges from the user pref file.
     * If it finds user defined bridges, then the stream will contain only these bridges. Otherwise, it returns
     * a set of predefined bridges.
     */
    @Override
    public InputStream openBridgesStream() throws IOException {
        /*
            BridgesList is an overloaded field, which can cause some confusion.. The list can be:
            1) a filter like obfs4 or meek OR 2) it can be a custom bridge
            For (1), we just pass back all bridges, the filter will occur elsewhere in the library.
            For (2) we return the bridge list as a raw stream
            If length is greater than 5, then we know this is a custom bridge
         */
        String userDefinedBridgeList = Prefs.getBridgesList();
        byte bridgeType = (byte) (userDefinedBridgeList.length() > 5 ? 1 : 0);
        ByteArrayInputStream bridgeTypeStream = new ByteArrayInputStream(new byte[]{bridgeType});
        InputStream bridgeStream = (bridgeType == 1) ? new ByteArrayInputStream((userDefinedBridgeList + "\r\n").getBytes())
                : context.getResources().getAssets().open("common/bridges.txt");
        return new SequenceInputStream(bridgeTypeStream, bridgeStream);
    }

    private static void copy(InputStream is, File target) throws IOException {
        FileOutputStream os = new FileOutputStream(target);
        byte[] buffer = new byte[8192];
        int length = is.read(buffer);
        while (length > 0) {
            os.write(buffer, 0, length);
            length = is.read(buffer);
        }
        os.close();
    }

    private static boolean updateTorConfigCustom(File fileTorRcCustom, String content) throws IOException {
        if (fileTorRcCustom.exists()) {
            fileTorRcCustom.delete();
            Log.d("torResources", "deleting existing torrc.custom");
        } else {
            fileTorRcCustom.createNewFile();
        }

        FileOutputStream fos = new FileOutputStream(fileTorRcCustom, false);
        PrintStream ps = new PrintStream(fos);
        ps.print(content);
        ps.close();
        return true;
    }
}
