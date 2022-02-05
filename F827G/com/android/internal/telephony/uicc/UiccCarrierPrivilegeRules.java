package com.android.internal.telephony.uicc;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import java.io.ByteArrayInputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class UiccCarrierPrivilegeRules extends Handler {
    private static final String AID = "A00000015141434C00";
    private static final int CLA = 128;
    private static final int COMMAND = 202;
    private static final String DATA = "";
    private static final int EVENT_CLOSE_LOGICAL_CHANNEL_DONE = 3;
    private static final int EVENT_OPEN_LOGICAL_CHANNEL_DONE = 1;
    private static final int EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE = 2;
    private static final String LOG_TAG = "UiccCarrierPrivilegeRules";
    private static final int P1 = 255;
    private static final int P2 = 64;
    private static final int P2_EXTENDED_DATA = 96;
    private static final int P3 = 0;
    private static final int STATE_ERROR = 2;
    private static final int STATE_LOADED = 1;
    private static final int STATE_LOADING = 0;
    private static final String TAG_ALL_REF_AR_DO = "FF40";
    private static final String TAG_AR_DO = "E3";
    private static final String TAG_DEVICE_APP_ID_REF_DO = "C1";
    private static final String TAG_PERM_AR_DO = "DB";
    private static final String TAG_PKG_REF_DO = "CA";
    private static final String TAG_REF_AR_DO = "E2";
    private static final String TAG_REF_DO = "E1";
    private List<AccessRule> mAccessRules;
    private int mChannelId;
    private Message mLoadedCallback;
    private UiccCard mUiccCard;
    private AtomicInteger mState = new AtomicInteger(0);
    private String mStatusMessage = "Not loaded.";
    private String mRules = DATA;

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class AccessRule {
        public long accessType;
        public byte[] certificateHash;
        public String packageName;

        AccessRule(byte[] certificateHash, String packageName, long accessType) {
            this.certificateHash = certificateHash;
            this.packageName = packageName;
            this.accessType = accessType;
        }

        boolean matches(byte[] certHash, String packageName) {
            return certHash != null && Arrays.equals(this.certificateHash, certHash) && (this.packageName == null || this.packageName.equals(packageName));
        }

        public String toString() {
            return "cert: " + IccUtils.bytesToHexString(this.certificateHash) + " pkg: " + this.packageName + " access: " + this.accessType;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public static class TLV {
        private static final int SINGLE_BYTE_MAX_LENGTH = 128;
        private Integer length;
        private String lengthBytes;
        private String tag;
        private String value;

        public TLV(String tag) {
            this.tag = tag;
        }

        public String parseLength(String data) {
            int offset = this.tag.length();
            int firstByte = Integer.parseInt(data.substring(offset, offset + 2), 16);
            if (firstByte < 128) {
                this.length = Integer.valueOf(firstByte * 2);
                this.lengthBytes = data.substring(offset, offset + 2);
            } else {
                int numBytes = firstByte - 128;
                this.length = Integer.valueOf(Integer.parseInt(data.substring(offset + 2, offset + 2 + (numBytes * 2)), 16) * 2);
                this.lengthBytes = data.substring(offset, offset + 2 + (numBytes * 2));
            }
            Rlog.d(UiccCarrierPrivilegeRules.LOG_TAG, "TLV parseLength length=" + this.length + "lenghtBytes: " + this.lengthBytes);
            return this.lengthBytes;
        }

        public String parse(String data, boolean shouldConsumeAll) {
            Rlog.d(UiccCarrierPrivilegeRules.LOG_TAG, "Parse TLV: " + this.tag);
            if (!data.startsWith(this.tag)) {
                throw new IllegalArgumentException("Tags don't match.");
            }
            int index = this.tag.length();
            if (index + 2 > data.length()) {
                throw new IllegalArgumentException("No length.");
            }
            parseLength(data);
            int index2 = index + this.lengthBytes.length();
            Rlog.d(UiccCarrierPrivilegeRules.LOG_TAG, "index=" + index2 + " length=" + this.length + "data.length=" + data.length());
            int remainingLength = data.length() - (this.length.intValue() + index2);
            if (remainingLength < 0) {
                throw new IllegalArgumentException("Not enough data.");
            } else if (!shouldConsumeAll || remainingLength == 0) {
                this.value = data.substring(index2, this.length.intValue() + index2);
                Rlog.d(UiccCarrierPrivilegeRules.LOG_TAG, "Got TLV: " + this.tag + "," + this.length + "," + this.value);
                return data.substring(this.length.intValue() + index2);
            } else {
                throw new IllegalArgumentException("Did not consume all.");
            }
        }
    }

    public UiccCarrierPrivilegeRules(UiccCard uiccCard, Message loadedCallback) {
        Rlog.d(LOG_TAG, "Creating UiccCarrierPrivilegeRules");
        this.mUiccCard = uiccCard;
        this.mLoadedCallback = loadedCallback;
        this.mUiccCard.iccOpenLogicalChannel(AID, obtainMessage(1, null));
    }

    public boolean areCarrierPriviligeRulesLoaded() {
        return this.mState.get() != 0;
    }

    public int getCarrierPrivilegeStatus(Signature signature, String packageName) {
        Rlog.d(LOG_TAG, "hasCarrierPrivileges: " + signature + " : " + packageName);
        int state = this.mState.get();
        if (state == 0) {
            Rlog.d(LOG_TAG, "Rules not loaded.");
            return -1;
        } else if (state == 2) {
            Rlog.d(LOG_TAG, "Error loading rules.");
            return -2;
        } else {
            byte[] certHash = getCertHash(signature);
            if (certHash == null) {
                return 0;
            }
            Rlog.e(LOG_TAG, "Checking: " + IccUtils.bytesToHexString(certHash) + " : " + packageName);
            for (AccessRule ar : this.mAccessRules) {
                if (ar.matches(certHash, packageName)) {
                    Rlog.d(LOG_TAG, "Match found!");
                    return 1;
                }
            }
            Rlog.d(LOG_TAG, "No matching rule found. Returning false.");
            return 0;
        }
    }

    public int getCarrierPrivilegeStatus(PackageManager packageManager, String packageName) {
        try {
            PackageInfo pInfo = packageManager.getPackageInfo(packageName, 64);
            for (Signature sig : pInfo.signatures) {
                int accessStatus = getCarrierPrivilegeStatus(sig, pInfo.packageName);
                if (accessStatus != 0) {
                    return accessStatus;
                }
            }
        } catch (PackageManager.NameNotFoundException ex) {
            Rlog.e(LOG_TAG, "NameNotFoundException", ex);
        }
        return 0;
    }

    public int getCarrierPrivilegeStatusForCurrentTransaction(PackageManager packageManager) {
        for (String pkg : packageManager.getPackagesForUid(Binder.getCallingUid())) {
            int accessStatus = getCarrierPrivilegeStatus(packageManager, pkg);
            if (accessStatus != 0) {
                return accessStatus;
            }
        }
        return 0;
    }

    public List<String> getCarrierPackageNamesForIntent(PackageManager packageManager, Intent intent) {
        List<String> packages = new ArrayList<>();
        List<ResolveInfo> receivers = new ArrayList<>();
        receivers.addAll(packageManager.queryBroadcastReceivers(intent, 0));
        receivers.addAll(packageManager.queryIntentContentProviders(intent, 0));
        receivers.addAll(packageManager.queryIntentActivities(intent, 0));
        receivers.addAll(packageManager.queryIntentServices(intent, 0));
        for (ResolveInfo resolveInfo : receivers) {
            String packageName = getPackageName(resolveInfo);
            if (packageName != null) {
                int status = getCarrierPrivilegeStatus(packageManager, packageName);
                if (status == 1) {
                    packages.add(packageName);
                } else if (status != 0) {
                    return null;
                }
            }
        }
        return packages;
    }

    private String getPackageName(ResolveInfo resolveInfo) {
        if (resolveInfo.activityInfo != null) {
            return resolveInfo.activityInfo.packageName;
        }
        if (resolveInfo.serviceInfo != null) {
            return resolveInfo.serviceInfo.packageName;
        }
        if (resolveInfo.providerInfo != null) {
            return resolveInfo.providerInfo.packageName;
        }
        return null;
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                Rlog.d(LOG_TAG, "EVENT_OPEN_LOGICAL_CHANNEL_DONE");
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.exception != null || ar.result == null) {
                    updateState(2, "Error opening channel");
                    return;
                }
                this.mChannelId = ((int[]) ar.result)[0];
                this.mUiccCard.iccTransmitApduLogicalChannel(this.mChannelId, 128, COMMAND, 255, 64, 0, DATA, obtainMessage(2, new Integer(this.mChannelId)));
                return;
            case 2:
                Rlog.d(LOG_TAG, "EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE");
                AsyncResult ar2 = (AsyncResult) msg.obj;
                if (ar2.exception != null || ar2.result == null) {
                    updateState(2, "Error reading value from SIM.");
                } else {
                    IccIoResult response = (IccIoResult) ar2.result;
                    if (response.sw1 != 144 || response.sw2 != 0 || response.payload == null || response.payload.length <= 0) {
                        updateState(2, "Invalid response: payload=" + response.payload + " sw1=" + response.sw1 + " sw2=" + response.sw2);
                    } else {
                        try {
                            this.mRules += IccUtils.bytesToHexString(response.payload).toUpperCase(Locale.US);
                            if (isDataComplete()) {
                                this.mAccessRules = parseRules(this.mRules);
                                updateState(1, "Success!");
                            } else {
                                this.mUiccCard.iccTransmitApduLogicalChannel(this.mChannelId, 128, COMMAND, 255, 96, 0, DATA, obtainMessage(2, new Integer(this.mChannelId)));
                                return;
                            }
                        } catch (IllegalArgumentException ex) {
                            updateState(2, "Error parsing rules: " + ex);
                        } catch (IndexOutOfBoundsException ex2) {
                            updateState(2, "Error parsing rules: " + ex2);
                        }
                    }
                }
                this.mUiccCard.iccCloseLogicalChannel(this.mChannelId, obtainMessage(3));
                this.mChannelId = -1;
                return;
            case 3:
                Rlog.d(LOG_TAG, "EVENT_CLOSE_LOGICAL_CHANNEL_DONE");
                return;
            default:
                Rlog.e(LOG_TAG, "Unknown event " + msg.what);
                return;
        }
    }

    private boolean isDataComplete() {
        Rlog.d(LOG_TAG, "isDataComplete mRules:" + this.mRules);
        if (this.mRules.startsWith(TAG_ALL_REF_AR_DO)) {
            TLV allRules = new TLV(TAG_ALL_REF_AR_DO);
            String lengthBytes = allRules.parseLength(this.mRules);
            Rlog.d(LOG_TAG, "isDataComplete lengthBytes: " + lengthBytes);
            if (this.mRules.length() == TAG_ALL_REF_AR_DO.length() + lengthBytes.length() + allRules.length.intValue()) {
                Rlog.d(LOG_TAG, "isDataComplete yes");
                return true;
            }
            Rlog.d(LOG_TAG, "isDataComplete no");
            return false;
        }
        throw new IllegalArgumentException("Tags don't match.");
    }

    private static List<AccessRule> parseRules(String rules) {
        Rlog.d(LOG_TAG, "Got rules: " + rules);
        TLV allRefArDo = new TLV(TAG_ALL_REF_AR_DO);
        allRefArDo.parse(rules, true);
        String arDos = allRefArDo.value;
        List<AccessRule> accessRules = new ArrayList<>();
        while (!arDos.isEmpty()) {
            TLV refArDo = new TLV(TAG_REF_AR_DO);
            arDos = refArDo.parse(arDos, false);
            AccessRule accessRule = parseRefArdo(refArDo.value);
            if (accessRule != null) {
                accessRules.add(accessRule);
            } else {
                Rlog.e(LOG_TAG, "Skip unrecognized rule." + refArDo.value);
            }
        }
        return accessRules;
    }

    private static AccessRule parseRefArdo(String rule) {
        Rlog.d(LOG_TAG, "Got rule: " + rule);
        String certificateHash = null;
        String packageName = null;
        while (!rule.isEmpty()) {
            if (rule.startsWith(TAG_REF_DO)) {
                TLV refDo = new TLV(TAG_REF_DO);
                rule = refDo.parse(rule, false);
                if (!refDo.value.startsWith(TAG_DEVICE_APP_ID_REF_DO)) {
                    return null;
                }
                TLV deviceDo = new TLV(TAG_DEVICE_APP_ID_REF_DO);
                String tmp = deviceDo.parse(refDo.value, false);
                certificateHash = deviceDo.value;
                if (tmp.isEmpty()) {
                    packageName = null;
                } else if (!tmp.startsWith(TAG_PKG_REF_DO)) {
                    return null;
                } else {
                    TLV pkgDo = new TLV(TAG_PKG_REF_DO);
                    pkgDo.parse(tmp, true);
                    packageName = new String(IccUtils.hexStringToBytes(pkgDo.value));
                }
            } else if (rule.startsWith(TAG_AR_DO)) {
                TLV arDo = new TLV(TAG_AR_DO);
                rule = arDo.parse(rule, false);
                if (!arDo.value.startsWith(TAG_PERM_AR_DO)) {
                    return null;
                }
                TLV permDo = new TLV(TAG_PERM_AR_DO);
                permDo.parse(arDo.value, true);
                Rlog.e(LOG_TAG, permDo.value);
            } else {
                throw new RuntimeException("Invalid Rule type");
            }
        }
        Rlog.e(LOG_TAG, "Adding: " + certificateHash + " : " + packageName + " : 0");
        AccessRule accessRule = new AccessRule(IccUtils.hexStringToBytes(certificateHash), packageName, 0L);
        Rlog.e(LOG_TAG, "Parsed rule: " + accessRule);
        return accessRule;
    }

    private static byte[] getCertHash(Signature signature) {
        try {
            return MessageDigest.getInstance("SHA").digest(((X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(signature.toByteArray()))).getEncoded());
        } catch (NoSuchAlgorithmException ex) {
            Rlog.e(LOG_TAG, "NoSuchAlgorithmException: " + ex);
            Rlog.e(LOG_TAG, "Cannot compute cert hash");
            return null;
        } catch (CertificateException ex2) {
            Rlog.e(LOG_TAG, "CertificateException: " + ex2);
            Rlog.e(LOG_TAG, "Cannot compute cert hash");
            return null;
        }
    }

    private void updateState(int newState, String statusMessage) {
        this.mState.set(newState);
        if (this.mLoadedCallback != null) {
            this.mLoadedCallback.sendToTarget();
        }
        this.mStatusMessage = statusMessage;
        Rlog.e(LOG_TAG, this.mStatusMessage);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UiccCarrierPrivilegeRules: " + this);
        pw.println(" mState=" + getStateString(this.mState.get()));
        pw.println(" mStatusMessage='" + this.mStatusMessage + "'");
        if (this.mAccessRules != null) {
            pw.println(" mAccessRules: ");
            Iterator i$ = this.mAccessRules.iterator();
            while (i$.hasNext()) {
                pw.println("  rule='" + i$.next() + "'");
            }
        } else {
            pw.println(" mAccessRules: null");
        }
        pw.flush();
    }

    private String getStateString(int state) {
        switch (state) {
            case 0:
                return "STATE_LOADING";
            case 1:
                return "STATE_LOADED";
            case 2:
                return "STATE_ERROR";
            default:
                return "UNKNOWN";
        }
    }
}
