package com.android.internal.telephony.cdma;

import android.content.ContentValues;
import android.content.Context;
import android.database.SQLException;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.PhoneConstants;
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

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class CDMALTEPhone extends CDMAPhone {
    private static final boolean DBG = true;
    static final String LOG_LTE_TAG = "CDMALTEPhone";
    private IsimUiccRecords mIsimUiccRecords;
    private SIMRecords mSimRecords;

    public CDMALTEPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, int phoneId) {
        this(context, ci, notifier, false, phoneId);
    }

    public CDMALTEPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode, int phoneId) {
        super(context, ci, notifier, phoneId);
        Rlog.d("CDMAPhone", "CDMALTEPhone: constructor: sub = " + this.mPhoneId);
        this.mDcTracker = new DcTracker(this);
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone
    protected void initSstIcc() {
        this.mSST = new CdmaLteServiceStateTracker(this);
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            if (this.mSimRecords != null) {
                this.mSimRecords.unregisterForRecordsLoaded(this);
            }
            super.dispose();
        }
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void removeReferences() {
        super.removeReferences();
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.PhoneBase, android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 16:
            case 17:
                super.handleMessage(msg);
                return;
            default:
                if (!this.mIsTheCurrentActivePhone) {
                    Rlog.e("CDMAPhone", "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
                    return;
                }
                switch (msg.what) {
                    case 3:
                        this.mSimRecordsLoadedRegistrants.notifyRegistrants();
                        return;
                    default:
                        super.handleMessage(msg);
                        return;
                }
        }
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        PhoneConstants.DataState ret = PhoneConstants.DataState.DISCONNECTED;
        if (this.mSST == null) {
            ret = PhoneConstants.DataState.DISCONNECTED;
        } else if (this.mSST.getCurrentDataConnectionState() == 0 || !this.mOosIsDisconnect) {
            if (this.mDcTracker.isApnTypeEnabled(apnType)) {
                switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mDcTracker.getState(apnType).ordinal()]) {
                    case 1:
                    case 2:
                    case 3:
                        ret = PhoneConstants.DataState.DISCONNECTED;
                        break;
                    case 4:
                    case 5:
                        if (this.mCT.mState != PhoneConstants.State.IDLE && !this.mSST.isConcurrentVoiceAndDataAllowed()) {
                            ret = PhoneConstants.DataState.SUSPENDED;
                            break;
                        } else {
                            ret = PhoneConstants.DataState.CONNECTED;
                            break;
                        }
                        break;
                    case 6:
                    case 7:
                        ret = PhoneConstants.DataState.CONNECTING;
                        break;
                }
            } else {
                ret = PhoneConstants.DataState.DISCONNECTED;
            }
        } else {
            ret = PhoneConstants.DataState.DISCONNECTED;
            log("getDataConnectionState: Data is Out of Service. ret = " + ret);
        }
        log("getDataConnectionState apnType=" + apnType + " ret=" + ret);
        return ret;
    }

    /* renamed from: com.android.internal.telephony.cdma.CDMALTEPhone$1 */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[DctConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.RETRYING.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.FAILED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.IDLE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.DISCONNECTING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.SCANNING.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.android.internal.telephony.cdma.CDMAPhone
    public boolean updateCurrentCarrierInProvider(String operatorNumeric) {
        boolean retVal;
        if (this.mUiccController.getUiccCardApplication(this.mPhoneId, 1) == null) {
            log("updateCurrentCarrierInProvider APP_FAM_3GPP == null");
            retVal = super.updateCurrentCarrierInProvider(operatorNumeric);
        } else {
            log("updateCurrentCarrierInProvider not updated");
            retVal = true;
        }
        log("updateCurrentCarrierInProvider X retVal=" + retVal);
        return retVal;
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone
    public boolean updateCurrentCarrierInProvider() {
        int currentDds = SubscriptionManager.getDefaultDataSubId();
        String operatorNumeric = getOperatorNumeric();
        Rlog.d("CDMAPhone", "updateCurrentCarrierInProvider: mSubscription = " + getSubId() + " currentDds = " + currentDds + " operatorNumeric = " + operatorNumeric);
        if (!TextUtils.isEmpty(operatorNumeric) && getSubId() == currentDds) {
            try {
                Uri uri = Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, Telephony.Carriers.CURRENT);
                ContentValues map = new ContentValues();
                map.put(Telephony.Carriers.NUMERIC, operatorNumeric);
                this.mContext.getContentResolver().insert(uri, map);
                return true;
            } catch (SQLException e) {
                Rlog.e("CDMAPhone", "Can't store current operator", e);
            }
        }
        return false;
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public String getSubscriberId() {
        return this.mSimRecords != null ? this.mSimRecords.getIMSI() : "";
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public String getGroupIdLevel1() {
        return this.mSimRecords != null ? this.mSimRecords.getGid1() : "";
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public String getImei() {
        return this.mImei;
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public String getDeviceSvn() {
        return this.mImeiSv;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public IsimRecords getIsimRecords() {
        return this.mIsimUiccRecords;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public String getMsisdn() {
        if (this.mSimRecords != null) {
            return this.mSimRecords.getMsisdnNumber();
        }
        return null;
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public void getAvailableNetworks(Message response) {
        this.mCi.getAvailableNetworks(response);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.PhoneBase
    public void onUpdateIccAvailability() {
        if (this.mSimRecords != null) {
            this.mSimRecords.unregisterForRecordsLoaded(this);
        }
        if (this.mUiccController != null) {
            UiccCardApplication newUiccApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 3);
            IsimUiccRecords newIsimUiccRecords = null;
            if (newUiccApplication != null) {
                newIsimUiccRecords = (IsimUiccRecords) newUiccApplication.getIccRecords();
            }
            this.mIsimUiccRecords = newIsimUiccRecords;
            UiccCardApplication newUiccApplication2 = this.mUiccController.getUiccCardApplication(this.mPhoneId, 1);
            SIMRecords newSimRecords = null;
            if (newUiccApplication2 != null) {
                newSimRecords = (SIMRecords) newUiccApplication2.getIccRecords();
            }
            this.mSimRecords = newSimRecords;
            if (this.mSimRecords != null) {
                this.mSimRecords.registerForRecordsLoaded(this, 3, null);
            }
            super.onUpdateIccAvailability();
        }
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone
    protected void init(Context context, PhoneNotifier notifier) {
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

    private void setProperties() {
        setSystemProperty("gsm.current.phone-type", new Integer(2).toString());
        String operatorAlpha = SystemProperties.get("ro.cdma.home.operator.alpha");
        if (!TextUtils.isEmpty(operatorAlpha)) {
            setSystemProperty("gsm.sim.operator.alpha", operatorAlpha);
        }
        String operatorNumeric = SystemProperties.get(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
        log("update icc_operator_numeric=" + operatorNumeric);
        if (!TextUtils.isEmpty(operatorNumeric)) {
            setSystemProperty("gsm.sim.operator.numeric", operatorNumeric);
            SubscriptionController.getInstance().setMccMnc(operatorNumeric, getSubId());
            setIsoCountryProperty(operatorNumeric);
            log("update mccmnc=" + operatorNumeric);
            MccTable.updateMccMncConfiguration(this.mContext, operatorNumeric, false);
        }
        updateCurrentCarrierInProvider();
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.PhoneBase
    public void setSystemProperty(String property, String value) {
        if (!getUnitTestMode()) {
            TelephonyManager.setTelephonyProperty(this.mPhoneId, property, value);
        }
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.PhoneBase
    public String getSystemProperty(String property, String defValue) {
        if (getUnitTestMode()) {
            return null;
        }
        return TelephonyManager.getTelephonyProperty(this.mPhoneId, property, defValue);
    }

    public void updateDataConnectionTracker() {
        ((DcTracker) this.mDcTracker).update();
    }

    public void setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        ((DcTracker) this.mDcTracker).setInternalDataEnabled(enable, onCompleteMsg);
    }

    public boolean setInternalDataEnabledFlag(boolean enable) {
        return ((DcTracker) this.mDcTracker).setInternalDataEnabledFlag(enable);
    }

    public String getOperatorNumeric() {
        String operatorNumeric = null;
        IccRecords curIccRecords = null;
        if (this.mCdmaSubscriptionSource == 1) {
            operatorNumeric = SystemProperties.get(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
        } else if (this.mCdmaSubscriptionSource == 0) {
            curIccRecords = this.mSimRecords;
            if (curIccRecords != null) {
                operatorNumeric = curIccRecords.getOperatorNumeric();
            } else {
                curIccRecords = (IccRecords) this.mIccRecords.get();
                if (curIccRecords != null && (curIccRecords instanceof RuimRecords)) {
                    operatorNumeric = ((RuimRecords) curIccRecords).getOperatorNumeric();
                }
            }
        }
        if (operatorNumeric == null) {
            Rlog.e("CDMAPhone", "getOperatorNumeric: Cannot retrieve operatorNumeric: mCdmaSubscriptionSource = " + this.mCdmaSubscriptionSource + " mIccRecords = " + (curIccRecords != null ? Boolean.valueOf(curIccRecords.getRecordsLoaded()) : null));
        }
        Rlog.d("CDMAPhone", "getOperatorNumeric: mCdmaSubscriptionSource = " + this.mCdmaSubscriptionSource + " operatorNumeric = " + operatorNumeric);
        return operatorNumeric;
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        ((DcTracker) this.mDcTracker).registerForAllDataDisconnected(h, what, obj);
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        ((DcTracker) this.mDcTracker).unregisterForAllDataDisconnected(h);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        this.mSimRecordsLoadedRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void unregisterForSimRecordsLoaded(Handler h) {
        this.mSimRecordsLoadedRegistrants.remove(h);
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone
    protected void log(String s) {
        Rlog.d(LOG_LTE_TAG, s);
    }

    protected void loge(String s) {
        Rlog.e(LOG_LTE_TAG, s);
    }

    protected void loge(String s, Throwable e) {
        Rlog.e(LOG_LTE_TAG, s, e);
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.PhoneBase
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CDMALTEPhone extends:");
        super.dump(fd, pw, args);
    }
}
