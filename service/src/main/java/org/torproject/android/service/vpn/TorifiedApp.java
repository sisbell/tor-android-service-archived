package org.torproject.android.service.vpn;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import org.torproject.android.service.OrbotConstants;

import java.util.*;

public final class TorifiedApp implements Comparable {

    private boolean enabled;
    private int uid;
    private String username;
    private String procname;
    private String name;
    private String packageName;
    private boolean torified;
    private boolean usesInternet;

    public static List<TorifiedApp> getApps(Context context, SharedPreferences prefs) {
        String[] torifiedApps = prefs.getString(OrbotConstants.PREFS_KEY_TORIFIED, "").split("\\|");
        Arrays.sort(torifiedApps);

        PackageManager pMgr = context.getPackageManager();
        List<ApplicationInfo> lAppInfo = pMgr.getInstalledApplications(0);
        Iterator<ApplicationInfo> itAppInfo = lAppInfo.iterator();
        ArrayList<TorifiedApp> apps = new ArrayList<>();

        while (itAppInfo.hasNext()) {
            ApplicationInfo appInfo = itAppInfo.next();
            TorifiedApp app = new TorifiedApp();

            try {
                PackageInfo pInfo = pMgr.getPackageInfo(appInfo.packageName, PackageManager
                        .GET_PERMISSIONS);
                if (pInfo != null && pInfo.requestedPermissions != null) {
                    for (String permInfo : pInfo.requestedPermissions) {
                        if (permInfo.equals("android.permission.INTERNET")) {
                            app.setUsesInternet(true);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1) {
                //System app
                app.setUsesInternet(true);
            }

            if (!app.usesInternet())
                continue;

            apps.add(app);
            app.setEnabled(appInfo.enabled);
            app.setUid(appInfo.uid);
            app.setUsername(pMgr.getNameForUid(app.getUid()));
            app.setProcname(appInfo.processName);
            app.setPackageName(appInfo.packageName);

            try {
                app.setName(pMgr.getApplicationLabel(appInfo).toString());
            } catch (Exception e) {
                app.setName(appInfo.packageName);
            }
            // check if this application is allowed
            app.setTorified(Arrays.binarySearch(torifiedApps, app.getUsername()) >= 0);
        }

        Collections.sort(apps);
        return apps;
    }

    @Override
    public int compareTo(Object another) {
        return this.toString().compareToIgnoreCase(another.toString());
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    /**
     * @return the procname
     */
    public String getProcname() {
        return procname;
    }

    /**
     * @param procname the procname to set
     */
    public void setProcname(String procname) {
        this.procname = procname;
    }

    /**
     * @return the uid
     */
    public int getUid() {
        return uid;
    }

    /**
     * @param uid the uid to set
     */
    public void setUid(int uid) {
        this.uid = uid;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled the enabled to set
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return the torified
     */
    public boolean isTorified() {
        return torified;
    }

    /**
     * @param torified the torified to set
     */
    public void setTorified(boolean torified) {
        this.torified = torified;
    }

    public void setUsesInternet(boolean usesInternet) {
        this.usesInternet = usesInternet;
    }

    @Override
    public String toString() {
        return getName();
    }

    public boolean usesInternet() {
        return usesInternet;
    }
}
