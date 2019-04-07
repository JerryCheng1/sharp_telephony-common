package com.google.android.mms.util;

import android.content.Context;
import android.drm.DrmManagerClient;
import android.util.Log;

public class DownloadDrmHelper {
    public static final String EXTENSION_DRM_MESSAGE = ".dm";
    public static final String EXTENSION_INTERNAL_FWDL = ".fl";
    public static final String MIMETYPE_DRM_MESSAGE = "application/vnd.oma.drm.message";
    private static final String TAG = "DownloadDrmHelper";

    public static String getOriginalMimeType(Context context, String str, String str2) {
        DrmManagerClient drmManagerClient = new DrmManagerClient(context);
        try {
            return drmManagerClient.canHandle(str, null) ? drmManagerClient.getOriginalMimeType(str) : str2;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Can't get original mime type since path is null or empty string.");
            return str2;
        } catch (IllegalStateException e2) {
            Log.w(TAG, "DrmManagerClient didn't initialize properly.");
            return str2;
        }
    }

    public static boolean isDrmConvertNeeded(String str) {
        return "application/vnd.oma.drm.message".equals(str);
    }

    public static boolean isDrmMimeType(Context context, String str) {
        if (context == null) {
            return false;
        }
        try {
            DrmManagerClient drmManagerClient = new DrmManagerClient(context);
            return (drmManagerClient == null || str == null || str.length() <= 0) ? false : drmManagerClient.canHandle("", str);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "DrmManagerClient instance could not be created, context is Illegal.");
            return false;
        } catch (IllegalStateException e2) {
            Log.w(TAG, "DrmManagerClient didn't initialize properly.");
            return false;
        }
    }

    public static String modifyDrmFwLockFileExtension(String str) {
        if (str == null) {
            return str;
        }
        int lastIndexOf = str.lastIndexOf(".");
        if (lastIndexOf != -1) {
            str = str.substring(0, lastIndexOf);
        }
        return str.concat(EXTENSION_INTERNAL_FWDL);
    }
}
