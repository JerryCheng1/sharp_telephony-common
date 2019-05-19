package com.android.internal.telephony;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.util.Log;
import java.util.Arrays;

public class ConfigResourceUtil {
    public static final String TAG = "ConfigResourceUtil";
    public static String packageName = "com.android.frameworks.telresources";

    private static Resources getResources(Context context) throws NameNotFoundException, IllegalArgumentException {
        if (context == null) {
            Log.e(TAG, "context is null");
            throw new IllegalArgumentException("context==null");
        }
        Resources res = context.getPackageManager().getResourcesForApplication(packageName);
        if (res != null) {
            return res;
        }
        Log.e(TAG, "res is null");
        throw new IllegalArgumentException("res==null");
    }

    public static boolean getBooleanValue(Context context, String resourceName) {
        try {
            Resources res = getResources(context);
            int resId = res.getIdentifier(resourceName, "bool", packageName);
            boolean resValue = res.getBoolean(resId);
            Log.v(TAG, "resourceName = " + resourceName + " resourceId = " + resId + "resourceValue = " + resValue);
            return resValue;
        } catch (NameNotFoundException | NotFoundException | IllegalArgumentException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static int getIntValue(Context context, String resourceName) {
        try {
            Resources res = getResources(context);
            int resId = res.getIdentifier(resourceName, "integer", packageName);
            int resValue = res.getInteger(resId);
            Log.v(TAG, "resourceName = " + resourceName + " resourceId = " + resId + "resourceValue = " + resValue);
            return resValue;
        } catch (NameNotFoundException | NotFoundException | IllegalArgumentException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static String getStringValue(Context context, String resourceName) {
        try {
            Resources res = getResources(context);
            int resId = res.getIdentifier(resourceName, "string", packageName);
            String resValue = res.getString(resId);
            Log.v(TAG, "resourceName = " + resourceName + " resourceId = " + resId + "resourceValue = " + resValue);
            return resValue;
        } catch (NameNotFoundException | NotFoundException | IllegalArgumentException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static int[] getIntArray(Context context, String resourceName) {
        try {
            Resources res = getResources(context);
            int resId = res.getIdentifier(resourceName, "array", packageName);
            int[] resValue = res.getIntArray(resId);
            Log.v(TAG, "resourceName = " + resourceName + " resourceId = " + resId + "resourceValue = " + Arrays.toString(resValue));
            return resValue;
        } catch (NameNotFoundException | NotFoundException | IllegalArgumentException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static String[] getStringArray(Context context, String resourceName) {
        try {
            Resources res = getResources(context);
            int resId = res.getIdentifier(resourceName, "array", packageName);
            String[] resValue = res.getStringArray(resId);
            Log.v(TAG, "resourceName = " + resourceName + " resourceId = " + resId + "resourceValue = " + Arrays.toString(resValue));
            return resValue;
        } catch (NameNotFoundException | NotFoundException | IllegalArgumentException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
