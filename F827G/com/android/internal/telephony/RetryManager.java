package com.android.internal.telephony;

import android.telephony.Rlog;
import android.text.TextUtils;
import android.util.Pair;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class RetryManager {
    public static final boolean DBG = false;
    public static final String LOG_TAG = "RetryManager";
    public static final boolean VDBG = false;
    private String mConfig;
    private int mCurMaxRetryCount;
    private int mMaxRetryCount;
    private ArrayList<RetryRec> mRetryArray = new ArrayList();
    private int mRetryCount;
    private boolean mRetryForever;
    private Random mRng = new Random();

    private static class RetryRec {
        int mDelayTime;
        int mRandomizationTime;

        RetryRec(int i, int i2) {
            this.mDelayTime = i;
            this.mRandomizationTime = i2;
        }
    }

    private void log(String str) {
        Rlog.d(LOG_TAG, "[RM] " + str);
    }

    private int nextRandomizationTime(int i) {
        int i2 = ((RetryRec) this.mRetryArray.get(i)).mRandomizationTime;
        return i2 == 0 ? 0 : this.mRng.nextInt(i2);
    }

    private Pair<Boolean, Integer> parseNonNegativeInt(String str, String str2) {
        try {
            int parseInt = Integer.parseInt(str2);
            return new Pair(Boolean.valueOf(validateNonNegativeInt(str, parseInt)), Integer.valueOf(parseInt));
        } catch (NumberFormatException e) {
            Rlog.e(LOG_TAG, str + " bad value: " + str2, e);
            return new Pair(Boolean.valueOf(false), Integer.valueOf(0));
        }
    }

    private boolean validateNonNegativeInt(String str, int i) {
        if (i >= 0) {
            return true;
        }
        Rlog.e(LOG_TAG, str + " bad value: is < 0");
        return false;
    }

    public boolean configure(int i, int i2, int i3) {
        if (!validateNonNegativeInt("maxRetryCount", i) || !validateNonNegativeInt("retryTime", i2) || !validateNonNegativeInt("randomizationTime", i3)) {
            return false;
        }
        this.mMaxRetryCount = i;
        this.mCurMaxRetryCount = this.mMaxRetryCount;
        resetRetryCount();
        this.mRetryArray.clear();
        this.mRetryArray.add(new RetryRec(i2, i3));
        return true;
    }

    public boolean configure(String str) {
        if (str.startsWith("\"") && str.endsWith("\"")) {
            str = str.substring(1, str.length() - 1);
        }
        this.mConfig = str;
        if (TextUtils.isEmpty(str)) {
            return false;
        }
        this.mMaxRetryCount = 0;
        resetRetryCount();
        this.mRetryArray.clear();
        String[] split = str.split(",");
        int i = 0;
        int i2 = 0;
        while (i2 < split.length) {
            String[] split2 = split[i2].split("=", 2);
            split2[0] = split2[0].trim();
            Pair parseNonNegativeInt;
            if (split2.length > 1) {
                split2[1] = split2[1].trim();
                if (TextUtils.equals(split2[0], "default_randomization")) {
                    Pair parseNonNegativeInt2 = parseNonNegativeInt(split2[0], split2[1]);
                    if (!((Boolean) parseNonNegativeInt2.first).booleanValue()) {
                        return false;
                    }
                    i = ((Integer) parseNonNegativeInt2.second).intValue();
                } else if (!TextUtils.equals(split2[0], "max_retries")) {
                    Rlog.e(LOG_TAG, "Unrecognized configuration name value pair: " + split[i2]);
                    return false;
                } else if (TextUtils.equals("infinite", split2[1])) {
                    this.mRetryForever = true;
                } else {
                    parseNonNegativeInt = parseNonNegativeInt(split2[0], split2[1]);
                    if (!((Boolean) parseNonNegativeInt.first).booleanValue()) {
                        return false;
                    }
                    this.mMaxRetryCount = ((Integer) parseNonNegativeInt.second).intValue();
                }
            } else {
                String[] split3 = split[i2].split(":", 2);
                split3[0] = split3[0].trim();
                RetryRec retryRec = new RetryRec(0, 0);
                Pair parseNonNegativeInt3 = parseNonNegativeInt("delayTime", split3[0]);
                if (!((Boolean) parseNonNegativeInt3.first).booleanValue()) {
                    return false;
                }
                retryRec.mDelayTime = ((Integer) parseNonNegativeInt3.second).intValue();
                if (split3.length > 1) {
                    split3[1] = split3[1].trim();
                    parseNonNegativeInt = parseNonNegativeInt("randomizationTime", split3[1]);
                    if (!((Boolean) parseNonNegativeInt.first).booleanValue()) {
                        return false;
                    }
                    retryRec.mRandomizationTime = ((Integer) parseNonNegativeInt.second).intValue();
                } else {
                    retryRec.mRandomizationTime = i;
                }
                this.mRetryArray.add(retryRec);
            }
            i2++;
            i = i;
        }
        if (this.mRetryArray.size() > this.mMaxRetryCount) {
            this.mMaxRetryCount = this.mRetryArray.size();
        }
        this.mCurMaxRetryCount = this.mMaxRetryCount;
        return true;
    }

    public int getRetryCount() {
        return this.mRetryCount;
    }

    public int getRetryTimer() {
        int size = this.mRetryCount < this.mRetryArray.size() ? this.mRetryCount : this.mRetryArray.size() - 1;
        return (size < 0 || size >= this.mRetryArray.size()) ? 0 : ((RetryRec) this.mRetryArray.get(size)).mDelayTime + nextRandomizationTime(size);
    }

    public void increaseRetryCount() {
        this.mRetryCount++;
        if (this.mRetryCount > this.mCurMaxRetryCount) {
            this.mRetryCount = this.mCurMaxRetryCount;
        }
    }

    public boolean isRetryForever() {
        return this.mRetryForever;
    }

    public boolean isRetryNeeded() {
        return this.mRetryForever || this.mRetryCount < this.mCurMaxRetryCount;
    }

    public void resetRetryCount() {
        this.mRetryCount = 0;
    }

    public void restoreCurMaxRetryCount() {
        this.mCurMaxRetryCount = this.mMaxRetryCount;
        setRetryCount(this.mRetryCount);
    }

    public void retryForeverUsingLastTimeout() {
        this.mRetryCount = this.mCurMaxRetryCount;
        this.mRetryForever = true;
    }

    public void setCurMaxRetryCount(int i) {
        this.mCurMaxRetryCount = i;
        if (this.mCurMaxRetryCount < 0) {
            this.mCurMaxRetryCount = 0;
        }
        setRetryCount(this.mRetryCount);
    }

    public void setRetryCount(int i) {
        this.mRetryCount = i;
        if (this.mRetryCount > this.mCurMaxRetryCount) {
            this.mRetryCount = this.mCurMaxRetryCount;
        }
        if (this.mRetryCount < 0) {
            this.mRetryCount = 0;
        }
    }

    public void setRetryForever(boolean z) {
        this.mRetryForever = z;
    }

    public String toString() {
        String str = "RetryManager: { forever=" + this.mRetryForever + " maxRetry=" + this.mMaxRetryCount + " curMaxRetry=" + this.mCurMaxRetryCount + " retry=" + this.mRetryCount + " config={" + this.mConfig + "} retryArray={";
        Iterator it = this.mRetryArray.iterator();
        while (true) {
            String str2 = str;
            if (!it.hasNext()) {
                return str2 + "}}";
            }
            RetryRec retryRec = (RetryRec) it.next();
            str = str2 + retryRec.mDelayTime + ":" + retryRec.mRandomizationTime + " ";
        }
    }
}
