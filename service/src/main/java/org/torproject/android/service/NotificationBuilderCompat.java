package org.torproject.android.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;

import java.lang.reflect.Constructor;

/*
 * This is a compatibility wrapper-class around the native
 * android.app.Notification.Builder class. This class is needed
 * because we are currently targeting Android API level 26 and
 * supporting API level 16 as the minimum level, but we're using
 * the Android Support Library 23.4.0. This puts us in a situation
 * where Android API 26 requires "channels", but the support library
 * doesn't know what a channel is.
 *
 * This is a temporary hack until we upgrade to a newer support library
 * (mozilla-central uses 26.1.0, at the time of this writing).
 */

public class NotificationBuilderCompat {
    private static final String LOGTAG = "NotificationBuilderCompat";
    private static final Class notificationBuilderClass = Notification.Builder.class;

    /* Credit: http://www.javadocexamples.com/java/lang/Class/getDeclaredConstructor(...%20parameterTypes).html */
    // Constructor signature before Android O
    private static final Class[] REPLICATE_CONSTRUCTOR_PARAMS_PRE_O = new Class[]{Context.class};
    // Constructor signature Android O and newer
    private static final Class[] REPLICATE_CONSTRUCTOR_PARAMS_O_PLUS = new Class[]{Context.class, String.class};

    public static final String DEFAULT_CHANNEL_ID = "torbrowser_channel_0";

    private Notification.Builder mBuilder;

    public NotificationBuilderCompat(Context context, String channelId) {
        Constructor constructor;

        // If we think we're running on a device with Oreo or newer, then
        // try constructing a Notification.Builder with a channel Id.
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                constructor = notificationBuilderClass.getConstructor(REPLICATE_CONSTRUCTOR_PARAMS_O_PLUS);
                mBuilder = (Notification.Builder) constructor.newInstance(context, channelId);
                return;
            } catch (Exception e) {
            }
        }
        try {
            // Fall back on the constructor without a channel ID
            constructor = notificationBuilderClass.getConstructor(REPLICATE_CONSTRUCTOR_PARAMS_PRE_O);
            mBuilder = (Notification.Builder) constructor.newInstance(context);
        } catch (Exception e) {
            mBuilder = new Notification.Builder(context);
        }
    }

    public NotificationBuilderCompat(Context context) {
        this(context, DEFAULT_CHANNEL_ID);
    }

    public NotificationBuilderCompat setContentText(CharSequence title) {
        mBuilder = mBuilder.setContentText(title);
        return this;
    }

    public NotificationBuilderCompat setContentTitle(CharSequence title) {
        mBuilder = mBuilder.setContentTitle(title);
        return this;
    }

    public NotificationBuilderCompat setSmallIcon(int icon, int level) {
        mBuilder = mBuilder.setSmallIcon(icon, level);
        return this;
    }

    public NotificationBuilderCompat setSmallIcon(int icon) {
        mBuilder = mBuilder.setSmallIcon(icon);
        return this;
    }

    public NotificationBuilderCompat setLargeIcon(Bitmap b) {
        mBuilder = mBuilder.setLargeIcon(b);
        return this;
    }

    public NotificationBuilderCompat setContentIntent(PendingIntent intent) {
        mBuilder = mBuilder.setContentIntent(intent);
        return this;
    }

    public NotificationBuilderCompat setCategory(String category) {
        // This was added in API level 21
        if (Build.VERSION.SDK_INT >= 21) {
            mBuilder = mBuilder.setCategory(category);
        }
        return this;
    }

    public NotificationBuilderCompat addAction(int icon, CharSequence title, PendingIntent intent) {
        mBuilder = mBuilder.addAction(icon, title, intent);
        return this;
    }

    public NotificationBuilderCompat setOngoing(boolean ongoing) {
        mBuilder = mBuilder.setOngoing(ongoing);
        return this;
    }

    public NotificationBuilderCompat setTicker(CharSequence tickerText) {
        mBuilder = mBuilder.setTicker(tickerText);
        return this;
    }

    public NotificationBuilderCompat setPriority(int prio) {
        mBuilder = mBuilder.setPriority(prio);
        return this;
    }

    public NotificationBuilderCompat setDeleteIntent(PendingIntent intent) {
        mBuilder = mBuilder.setDeleteIntent(intent);
        return this;
    }

    public NotificationBuilderCompat setAutoCancel(boolean autoCancel) {
        mBuilder = mBuilder.setAutoCancel(autoCancel);
        return this;
    }

    public NotificationBuilderCompat setDefaults(int defaults) {
        mBuilder = mBuilder.setDefaults(defaults);
        return this;
    }

    public NotificationBuilderCompat setStyle(Notification.Style style) {
        mBuilder = mBuilder.setStyle(style);
        return this;
    }

    public NotificationBuilderCompat setWhen(long when) {
        mBuilder = mBuilder.setWhen(when);
        return this;
    }

    public NotificationBuilderCompat setProgress(int max, int progress, boolean indeterminate) {
        mBuilder = mBuilder.setProgress(max, progress, indeterminate);
        return this;
    }

    public NotificationBuilderCompat setLights(int argb, int onMs, int offMs) {
        mBuilder = mBuilder.setLights(argb, onMs, offMs);
        return this;
    }

    public Notification build() {
        return mBuilder.build();
    }
}
