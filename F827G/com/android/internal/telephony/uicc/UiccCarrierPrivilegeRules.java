package com.android.internal.telephony.uicc;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

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
    private String mRules;
    private AtomicInteger mState = new AtomicInteger(0);
    private String mStatusMessage = "Not loaded.";
    private UiccCard mUiccCard;

    private static class AccessRule {
        public long accessType;
        public byte[] certificateHash;
        public String packageName;

        AccessRule(byte[] bArr, String str, long j) {
            this.certificateHash = bArr;
            this.packageName = str;
            this.accessType = j;
        }

        /* Access modifiers changed, original: 0000 */
        public boolean matches(byte[] bArr, String str) {
            return bArr != null && Arrays.equals(this.certificateHash, bArr) && (this.packageName == null || this.packageName.equals(str));
        }

        public String toString() {
            return "cert: " + IccUtils.bytesToHexString(this.certificateHash) + " pkg: " + this.packageName + " access: " + this.accessType;
        }
    }

    private static class TLV {
        private static final int SINGLE_BYTE_MAX_LENGTH = 128;
        private Integer length;
        private String lengthBytes;
        private String tag;
        private String value;

        public TLV(String str) {
            this.tag = str;
        }

        public String parse(String str, boolean z) {
            Rlog.d(UiccCarrierPrivilegeRules.LOG_TAG, "Parse TLV: " + this.tag);
            if (str.startsWith(this.tag)) {
                int length = this.tag.length();
                if (length + 2 > str.length()) {
                    throw new IllegalArgumentException("No length.");
                }
                parseLength(str);
                length += this.lengthBytes.length();
                Rlog.d(UiccCarrierPrivilegeRules.LOG_TAG, "index=" + length + " length=" + this.length + "data.length=" + str.length());
                int length2 = str.length() - (this.length.intValue() + length);
                if (length2 < 0) {
                    throw new IllegalArgumentException("Not enough data.");
                } else if (!z || length2 == 0) {
                    this.value = str.substring(length, this.length.intValue() + length);
                    Rlog.d(UiccCarrierPrivilegeRules.LOG_TAG, "Got TLV: " + this.tag + "," + this.length + "," + this.value);
                    return str.substring(length + this.length.intValue());
                } else {
                    throw new IllegalArgumentException("Did not consume all.");
                }
            }
            throw new IllegalArgumentException("Tags don't match.");
        }

        public String parseLength(String str) {
            int length = this.tag.length();
            int parseInt = Integer.parseInt(str.substring(length, length + 2), 16);
            if (parseInt < 128) {
                this.length = Integer.valueOf(parseInt * 2);
                this.lengthBytes = str.substring(length, length + 2);
            } else {
                parseInt -= 128;
                this.length = Integer.valueOf(Integer.parseInt(str.substring(length + 2, (length + 2) + (parseInt * 2)), 16) * 2);
                this.lengthBytes = str.substring(length, (parseInt * 2) + (length + 2));
            }
            Rlog.d(UiccCarrierPrivilegeRules.LOG_TAG, "TLV parseLength length=" + this.length + "lenghtBytes: " + this.lengthBytes);
            return this.lengthBytes;
        }
    }

    public UiccCarrierPrivilegeRules(UiccCard uiccCard, Message message) {
        Rlog.d(LOG_TAG, "Creating UiccCarrierPrivilegeRules");
        this.mUiccCard = uiccCard;
        this.mLoadedCallback = message;
        this.mRules = DATA;
        this.mUiccCard.iccOpenLogicalChannel(AID, obtainMessage(1, null));
    }

    private static byte[] getCertHash(Signature signature) {
        try {
            return MessageDigest.getInstance("SHA").digest(((X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(signature.toByteArray()))).getEncoded());
        } catch (CertificateException e) {
            Rlog.e(LOG_TAG, "CertificateException: " + e);
        } catch (NoSuchAlgorithmException e2) {
            Rlog.e(LOG_TAG, "NoSuchAlgorithmException: " + e2);
        }
        Rlog.e(LOG_TAG, "Cannot compute cert hash");
        return null;
    }

    private String getPackageName(ResolveInfo resolveInfo) {
        return resolveInfo.activityInfo != null ? resolveInfo.activityInfo.packageName : resolveInfo.serviceInfo != null ? resolveInfo.serviceInfo.packageName : resolveInfo.providerInfo != null ? resolveInfo.providerInfo.packageName : null;
    }

    private String getStateString(int i) {
        switch (i) {
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

    private boolean isDataComplete() {
        Rlog.d(LOG_TAG, "isDataComplete mRules:" + this.mRules);
        if (this.mRules.startsWith(TAG_ALL_REF_AR_DO)) {
            TLV tlv = new TLV(TAG_ALL_REF_AR_DO);
            String parseLength = tlv.parseLength(this.mRules);
            Rlog.d(LOG_TAG, "isDataComplete lengthBytes: " + parseLength);
            if (this.mRules.length() == tlv.length.intValue() + (parseLength.length() + TAG_ALL_REF_AR_DO.length())) {
                Rlog.d(LOG_TAG, "isDataComplete yes");
                return true;
            }
            Rlog.d(LOG_TAG, "isDataComplete no");
            return false;
        }
        throw new IllegalArgumentException("Tags don't match.");
    }

    private static AccessRule parseRefArdo(String str) {
        Rlog.d(LOG_TAG, "Got rule: " + str);
        String str2 = null;
        String str3 = null;
        while (!str.isEmpty()) {
            TLV tlv;
            if (str.startsWith(TAG_REF_DO)) {
                TLV tlv2 = new TLV(TAG_REF_DO);
                str = tlv2.parse(str, false);
                if (!tlv2.value.startsWith(TAG_DEVICE_APP_ID_REF_DO)) {
                    return null;
                }
                TLV tlv3 = new TLV(TAG_DEVICE_APP_ID_REF_DO);
                String parse = tlv3.parse(tlv2.value, false);
                str2 = tlv3.value;
                if (parse.isEmpty()) {
                    str3 = null;
                } else if (!parse.startsWith(TAG_PKG_REF_DO)) {
                    return null;
                } else {
                    tlv = new TLV(TAG_PKG_REF_DO);
                    tlv.parse(parse, true);
                    str3 = new String(IccUtils.hexStringToBytes(tlv.value));
                }
            } else if (str.startsWith(TAG_AR_DO)) {
                TLV tlv4 = new TLV(TAG_AR_DO);
                str = tlv4.parse(str, false);
                if (!tlv4.value.startsWith(TAG_PERM_AR_DO)) {
                    return null;
                }
                tlv = new TLV(TAG_PERM_AR_DO);
                tlv.parse(tlv4.value, true);
                Rlog.e(LOG_TAG, tlv.value);
            } else {
                throw new RuntimeException("Invalid Rule type");
            }
        }
        Rlog.e(LOG_TAG, "Adding: " + str2 + " : " + str3 + " : " + 0);
        AccessRule accessRule = new AccessRule(IccUtils.hexStringToBytes(str2), str3, 0);
        Rlog.e(LOG_TAG, "Parsed rule: " + accessRule);
        return accessRule;
    }

    private static List<AccessRule> parseRules(String str) {
        Rlog.d(LOG_TAG, "Got rules: " + str);
        TLV tlv = new TLV(TAG_ALL_REF_AR_DO);
        tlv.parse(str, true);
        String access$100 = tlv.value;
        ArrayList arrayList = new ArrayList();
        while (!access$100.isEmpty()) {
            TLV tlv2 = new TLV(TAG_REF_AR_DO);
            access$100 = tlv2.parse(access$100, false);
            AccessRule parseRefArdo = parseRefArdo(tlv2.value);
            if (parseRefArdo != null) {
                arrayList.add(parseRefArdo);
            } else {
                Rlog.e(LOG_TAG, "Skip unrecognized rule." + tlv2.value);
            }
        }
        return arrayList;
    }

    private void updateState(int i, String str) {
        this.mState.set(i);
        if (this.mLoadedCallback != null) {
            this.mLoadedCallback.sendToTarget();
        }
        this.mStatusMessage = str;
        Rlog.e(LOG_TAG, this.mStatusMessage);
    }

    public boolean areCarrierPriviligeRulesLoaded() {
        return this.mState.get() != 0;
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("UiccCarrierPrivilegeRules: " + this);
        printWriter.println(" mState=" + getStateString(this.mState.get()));
        printWriter.println(" mStatusMessage='" + this.mStatusMessage + "'");
        if (this.mAccessRules != null) {
            printWriter.println(" mAccessRules: ");
            for (AccessRule accessRule : this.mAccessRules) {
                printWriter.println("  rule='" + accessRule + "'");
            }
        } else {
            printWriter.println(" mAccessRules: null");
        }
        printWriter.flush();
    }

    public List<String> getCarrierPackageNamesForIntent(PackageManager packageManager, Intent intent) {
        ArrayList arrayList = new ArrayList();
        ArrayList<ResolveInfo> arrayList2 = new ArrayList();
        arrayList2.addAll(packageManager.queryBroadcastReceivers(intent, 0));
        arrayList2.addAll(packageManager.queryIntentContentProviders(intent, 0));
        arrayList2.addAll(packageManager.queryIntentActivities(intent, 0));
        arrayList2.addAll(packageManager.queryIntentServices(intent, 0));
        for (ResolveInfo packageName : arrayList2) {
            String packageName2 = getPackageName(packageName);
            if (packageName2 != null) {
                int carrierPrivilegeStatus = getCarrierPrivilegeStatus(packageManager, packageName2);
                if (carrierPrivilegeStatus == 1) {
                    arrayList.add(packageName2);
                } else if (carrierPrivilegeStatus != 0) {
                    return null;
                }
            }
        }
        return arrayList;
    }

    public int getCarrierPrivilegeStatus(PackageManager packageManager, String str) {
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(str, 64);
            for (Signature carrierPrivilegeStatus : packageInfo.signatures) {
                int carrierPrivilegeStatus2 = getCarrierPrivilegeStatus(carrierPrivilegeStatus, packageInfo.packageName);
                if (carrierPrivilegeStatus2 != 0) {
                    return carrierPrivilegeStatus2;
                }
            }
        } catch (NameNotFoundException e) {
            Rlog.e(LOG_TAG, "NameNotFoundException", e);
        }
        return 0;
    }

    public int getCarrierPrivilegeStatus(Signature signature, String str) {
        Rlog.d(LOG_TAG, "hasCarrierPrivileges: " + signature + " : " + str);
        int i = this.mState.get();
        if (i == 0) {
            Rlog.d(LOG_TAG, "Rules not loaded.");
            i = -1;
        } else if (i == 2) {
            Rlog.d(LOG_TAG, "Error loading rules.");
            return -2;
        } else {
            byte[] certHash = getCertHash(signature);
            if (certHash != null) {
                Rlog.e(LOG_TAG, "Checking: " + IccUtils.bytesToHexString(certHash) + " : " + str);
                for (AccessRule matches : this.mAccessRules) {
                    if (matches.matches(certHash, str)) {
                        Rlog.d(LOG_TAG, "Match found!");
                        return 1;
                    }
                }
                Rlog.d(LOG_TAG, "No matching rule found. Returning false.");
                return 0;
            }
            i = 0;
        }
        return i;
    }

    public int getCarrierPrivilegeStatusForCurrentTransaction(PackageManager packageManager) {
        for (String carrierPrivilegeStatus : packageManager.getPackagesForUid(Binder.getCallingUid())) {
            int carrierPrivilegeStatus2 = getCarrierPrivilegeStatus(packageManager, carrierPrivilegeStatus);
            if (carrierPrivilegeStatus2 != 0) {
                return carrierPrivilegeStatus2;
            }
        }
        return 0;
    }

    public void handleMessage(Message message) {
        AsyncResult asyncResult;
        switch (message.what) {
            case 1:
                Rlog.d(LOG_TAG, "EVENT_OPEN_LOGICAL_CHANNEL_DONE");
                asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception != null || asyncResult.result == null) {
                    updateState(2, "Error opening channel");
                    return;
                }
                this.mChannelId = ((int[]) asyncResult.result)[0];
                this.mUiccCard.iccTransmitApduLogicalChannel(this.mChannelId, 128, COMMAND, 255, 64, 0, DATA, obtainMessage(2, new Integer(this.mChannelId)));
                return;
            case 2:
                Rlog.d(LOG_TAG, "EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE");
                asyncResult = (AsyncResult) message.obj;
                if (asyncResult.exception != null || asyncResult.result == null) {
                    updateState(2, "Error reading value from SIM.");
                } else {
                    IccIoResult iccIoResult = (IccIoResult) asyncResult.result;
                    if (iccIoResult.sw1 != 144 || iccIoResult.sw2 != 0 || iccIoResult.payload == null || iccIoResult.payload.length <= 0) {
                        updateState(2, "Invalid response: payload=" + iccIoResult.payload + " sw1=" + iccIoResult.sw1 + " sw2=" + iccIoResult.sw2);
                    } else {
                        try {
                            this.mRules += IccUtils.bytesToHexString(iccIoResult.payload).toUpperCase(Locale.US);
                            if (isDataComplete()) {
                                this.mAccessRules = parseRules(this.mRules);
                                updateState(1, "Success!");
                            } else {
                                this.mUiccCard.iccTransmitApduLogicalChannel(this.mChannelId, 128, COMMAND, 255, 96, 0, DATA, obtainMessage(2, new Integer(this.mChannelId)));
                                return;
                            }
                        } catch (IllegalArgumentException e) {
                            updateState(2, "Error parsing rules: " + e);
                        } catch (IndexOutOfBoundsException e2) {
                            updateState(2, "Error parsing rules: " + e2);
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
                Rlog.e(LOG_TAG, "Unknown event " + message.what);
                return;
        }
    }
}
