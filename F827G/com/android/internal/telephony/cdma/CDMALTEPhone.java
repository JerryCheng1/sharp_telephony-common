package com.android.internal.telephony.cdma;

import android.content.ContentValues;
import android.content.Context;
import android.database.SQLException;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Telephony.Carriers;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.IsimUiccRecords;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class CDMALTEPhone extends CDMAPhone {
    private static final boolean DBG = true;
    static final String LOG_LTE_TAG = "CDMALTEPhone";
    private IsimUiccRecords mIsimUiccRecords;
    private SIMRecords mSimRecords;

    /* renamed from: com.android.internal.telephony.cdma.CDMALTEPhone$1 */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.RETRYING.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.FAILED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.IDLE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.DISCONNECTING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.CONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[State.SCANNING.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
        }
    }

    public CDMALTEPhone(Context context, CommandsInterface commandsInterface, PhoneNotifier phoneNotifier, int i) {
        this(context, commandsInterface, phoneNotifier, false, i);
    }

    public CDMALTEPhone(Context context, CommandsInterface commandsInterface, PhoneNotifier phoneNotifier, boolean z, int i) {
        super(context, commandsInterface, phoneNotifier, i);
        Rlog.d("CDMAPhone", "CDMALTEPhone: constructor: sub = " + this.mPhoneId);
        this.mDcTracker = new DcTracker(this);
    }

    private void setProperties() {
        setSystemProperty("gsm.current.phone-type", new Integer(2).toString());
        String str = SystemProperties.get("ro.cdma.home.operator.alpha");
        if (!TextUtils.isEmpty(str)) {
            setSystemProperty("gsm.sim.operator.alpha", str);
        }
        str = SystemProperties.get(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
        log("update icc_operator_numeric=" + str);
        if (!TextUtils.isEmpty(str)) {
            setSystemProperty("gsm.sim.operator.numeric", str);
            SubscriptionController.getInstance().setMccMnc(str, getSubId());
            setIsoCountryProperty(str);
            log("update mccmnc=" + str);
            MccTable.updateMccMncConfiguration(this.mContext, str, false);
        }
        updateCurrentCarrierInProvider();
    }

    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            if (this.mSimRecords != null) {
                this.mSimRecords.unregisterForRecordsLoaded(this);
            }
            super.dispose();
        }
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("CDMALTEPhone extends:");
        super.dump(fileDescriptor, printWriter, strArr);
    }

    public void getAvailableNetworks(Message message) {
        this.mCi.getAvailableNetworks(message);
    }

    public DataState getDataConnectionState(String str) {
        Object obj = DataState.DISCONNECTED;
        if (this.mSST == null) {
            obj = DataState.DISCONNECTED;
        } else if (this.mSST.getCurrentDataConnectionState() == 0 || !this.mOosIsDisconnect) {
            if (this.mDcTracker.isApnTypeEnabled(str)) {
                switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mDcTracker.getState(str).ordinal()]) {
                    case 1:
                    case 2:
                    case 3:
                        obj = DataState.DISCONNECTED;
                        break;
                    case 4:
                    case 5:
                        if (this.mCT.mState != PhoneConstants.State.IDLE && !this.mSST.isConcurrentVoiceAndDataAllowed()) {
                            obj = DataState.SUSPENDED;
                            break;
                        }
                        obj = DataState.CONNECTED;
                        break;
                        break;
                    case 6:
                    case 7:
                        obj = DataState.CONNECTING;
                        break;
                }
            }
            obj = DataState.DISCONNECTED;
        } else {
            obj = DataState.DISCONNECTED;
            log("getDataConnectionState: Data is Out of Service. ret = " + obj);
        }
        log("getDataConnectionState apnType=" + str + " ret=" + obj);
        return obj;
    }

    public String getDeviceSvn() {
        return this.mImeiSv;
    }

    public String getGroupIdLevel1() {
        return this.mSimRecords != null ? this.mSimRecords.getGid1() : "";
    }

    public String getImei() {
        return this.mImei;
    }

    public IsimRecords getIsimRecords() {
        return this.mIsimUiccRecords;
    }

    public String getMsisdn() {
        return this.mSimRecords != null ? this.mSimRecords.getMsisdnNumber() : null;
    }

    public String getOperatorNumeric() {
        String str;
        IccRecords iccRecords;
        if (this.mCdmaSubscriptionSource == 1) {
            str = SystemProperties.get(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
            iccRecords = null;
        } else if (this.mCdmaSubscriptionSource == 0) {
            iccRecords = this.mSimRecords;
            if (iccRecords != null) {
                str = iccRecords.getOperatorNumeric();
            } else {
                iccRecords = (IccRecords) this.mIccRecords.get();
                str = (iccRecords == null || !(iccRecords instanceof RuimRecords)) ? null : ((RuimRecords) iccRecords).getOperatorNumeric();
            }
        } else {
            iccRecords = null;
            str = null;
        }
        if (str == null) {
            Rlog.e("CDMAPhone", "getOperatorNumeric: Cannot retrieve operatorNumeric: mCdmaSubscriptionSource = " + this.mCdmaSubscriptionSource + " mIccRecords = " + (iccRecords != null ? Boolean.valueOf(iccRecords.getRecordsLoaded()) : null));
        }
        Rlog.d("CDMAPhone", "getOperatorNumeric: mCdmaSubscriptionSource = " + this.mCdmaSubscriptionSource + " operatorNumeric = " + str);
        return str;
    }

    public String getSubscriberId() {
        return this.mSimRecords != null ? this.mSimRecords.getIMSI() : "";
    }

    public String getSystemProperty(String str, String str2) {
        return getUnitTestMode() ? null : TelephonyManager.getTelephonyProperty(this.mPhoneId, str, str2);
    }

    public void handleMessage(Message message) {
        switch (message.what) {
            case 16:
            case 17:
                super.handleMessage(message);
                return;
            default:
                if (this.mIsTheCurrentActivePhone) {
                    switch (message.what) {
                        case 3:
                            this.mSimRecordsLoadedRegistrants.notifyRegistrants();
                            return;
                        default:
                            super.handleMessage(message);
                            return;
                    }
                }
                Rlog.e("CDMAPhone", "Received message " + message + "[" + message.what + "] while being destroyed. Ignoring.");
                return;
        }
    }

    /* Access modifiers changed, original: protected */
    public void init(Context context, PhoneNotifier phoneNotifier) {
        this.mCi.setPhoneType(2);
        this.mCT = new CdmaCallTracker(this);
        this.mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context, this.mCi, this, 27, null);
        this.mRuimPhoneBookInterfaceManager = new RuimPhoneBookInterfaceManager(this);
        this.mSubInfo = new PhoneSubInfo(this);
        this.mEriManager = new EriManager(this, context, 0);
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForOffOrNotAvailable(this, 8, null);
        this.mCi.registerForOn(this, 5, null);
        this.mCi.setOnSuppServiceNotification(this, 2, null);
        this.mSST.registerForNetworkAttached(this, 19, null);
        this.mCi.setEmergencyCallbackMode(this, 25, null);
        this.mCi.registerForExitEmergencyCallbackMode(this, 26, null);
        this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, "CDMAPhone");
        this.mIsPhoneInEcmState = SystemProperties.get("ril.cdma.inecmmode", "false").equals("true");
        if (this.mIsPhoneInEcmState) {
            this.mCi.exitEmergencyCallbackMode(obtainMessage(26));
        }
        this.mCarrierOtaSpNumSchema = SystemProperties.get("ro.cdma.otaspnumschema", "");
        setProperties();
    }

    /* Access modifiers changed, original: protected */
    public void initSstIcc() {
        this.mSST = new CdmaLteServiceStateTracker(this);
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d(LOG_LTE_TAG, str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e(LOG_LTE_TAG, str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str, Throwable th) {
        Rlog.e(LOG_LTE_TAG, str, th);
    }

    /* Access modifiers changed, original: protected */
    public void onUpdateIccAvailability() {
        if (this.mSimRecords != null) {
            this.mSimRecords.unregisterForRecordsLoaded(this);
        }
        if (this.mUiccController != null) {
            UiccCardApplication uiccCardApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 3);
            this.mIsimUiccRecords = uiccCardApplication != null ? (IsimUiccRecords) uiccCardApplication.getIccRecords() : null;
            uiccCardApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 1);
            this.mSimRecords = uiccCardApplication != null ? (SIMRecords) uiccCardApplication.getIccRecords() : null;
            if (this.mSimRecords != null) {
                this.mSimRecords.registerForRecordsLoaded(this, 3, null);
            }
            super.onUpdateIccAvailability();
        }
    }

    public void registerForAllDataDisconnected(Handler handler, int i, Object obj) {
        ((DcTracker) this.mDcTracker).registerForAllDataDisconnected(handler, i, obj);
    }

    public void registerForSimRecordsLoaded(Handler handler, int i, Object obj) {
        this.mSimRecordsLoadedRegistrants.addUnique(handler, i, obj);
    }

    public void removeReferences() {
        super.removeReferences();
    }

    public void setInternalDataEnabled(boolean z, Message message) {
        ((DcTracker) this.mDcTracker).setInternalDataEnabled(z, message);
    }

    public boolean setInternalDataEnabledFlag(boolean z) {
        return ((DcTracker) this.mDcTracker).setInternalDataEnabledFlag(z);
    }

    public void setSystemProperty(String str, String str2) {
        if (!getUnitTestMode()) {
            TelephonyManager.setTelephonyProperty(this.mPhoneId, str, str2);
        }
    }

    public void unregisterForAllDataDisconnected(Handler handler) {
        ((DcTracker) this.mDcTracker).unregisterForAllDataDisconnected(handler);
    }

    public void unregisterForSimRecordsLoaded(Handler handler) {
        this.mSimRecordsLoadedRegistrants.remove(handler);
    }

    public boolean updateCurrentCarrierInProvider() {
        int defaultDataSubId = SubscriptionManager.getDefaultDataSubId();
        String operatorNumeric = getOperatorNumeric();
        Rlog.d("CDMAPhone", "updateCurrentCarrierInProvider: mSubscription = " + getSubId() + " currentDds = " + defaultDataSubId + " operatorNumeric = " + operatorNumeric);
        if (!TextUtils.isEmpty(operatorNumeric) && getSubId() == defaultDataSubId) {
            try {
                Uri withAppendedPath = Uri.withAppendedPath(Carriers.CONTENT_URI, Carriers.CURRENT);
                ContentValues contentValues = new ContentValues();
                contentValues.put(Carriers.NUMERIC, operatorNumeric);
                this.mContext.getContentResolver().insert(withAppendedPath, contentValues);
                return true;
            } catch (SQLException e) {
                Rlog.e("CDMAPhone", "Can't store current operator", e);
            }
        }
        return false;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean updateCurrentCarrierInProvider(String str) {
        boolean z = true;
        if (this.mUiccController.getUiccCardApplication(this.mPhoneId, 1) == null) {
            log("updateCurrentCarrierInProvider APP_FAM_3GPP == null");
            z = super.updateCurrentCarrierInProvider(str);
        } else {
            log("updateCurrentCarrierInProvider not updated");
        }
        log("updateCurrentCarrierInProvider X retVal=" + z);
        return z;
    }

    public void updateDataConnectionTracker() {
        ((DcTracker) this.mDcTracker).update();
    }
}
