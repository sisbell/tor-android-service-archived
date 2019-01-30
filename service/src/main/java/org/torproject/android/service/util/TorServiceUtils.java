/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals
.com/guardian */
/* See LICENSE for licensing information */
package org.torproject.android.service.util;

import android.content.Context;
import android.content.SharedPreferences;
import org.torproject.android.service.OrbotConstants;
import org.torproject.android.service.TorServiceConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import static java.lang.Runtime.getRuntime;

public class TorServiceUtils implements TorServiceConstants {

    public static int findProcessId(String command) throws IOException {
        Process procPs = getRuntime().exec(SHELL_CMD_PS);
        BufferedReader reader = new BufferedReader(new InputStreamReader(procPs.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.contains("PID") && line.contains(command)) {
                String[] lineParts = line.split("\\s+");
                try {
                    return Integer.parseInt(lineParts[1]); //for most devices it is the second
                } catch (NumberFormatException e) {
                    return Integer.parseInt(lineParts[0]); //but for samsungs it is the first
                } finally {
                    try {
                        procPs.destroy();
                    } catch (Exception e) {
                    }
                }
            }
        }
        return -1;
    }

    public static SharedPreferences getSharedPrefs(Context context) {
        return context.getSharedPreferences(OrbotConstants.PREF_TOR_SHARED_PREFS,
                Context.MODE_MULTI_PROCESS);
    }

    public static void killProcess(File fileProcBin) throws Exception {
        killProcess(fileProcBin, "-9"); // this is -KILL
    }

    public static void killProcess(File fileProcBin, String signal) throws Exception {
        int procId = -1;
        int killAttempts = 0;

        while ((procId = TorServiceUtils.findProcessId(fileProcBin.getCanonicalPath())) != -1) {
            killAttempts++;
            String pidString = String.valueOf(procId);
            try {
                getRuntime().exec("busybox killall " + signal + " " + fileProcBin.getName
                        ());
            } catch (IOException ioe) {
            }
            try {
                getRuntime().exec("toolbox kill " + signal + " " + pidString);
            } catch (IOException ioe) {
            }
            try {
                getRuntime().exec("busybox kill " + signal + " " + pidString);
            } catch (IOException ioe) {
            }
            try {
                getRuntime().exec("kill " + signal + " " + pidString);
            } catch (IOException ioe) {
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignored
            }

            if (killAttempts > 4)
                throw new Exception("Cannot kill: " + fileProcBin.getAbsolutePath());
        }
    }
}
