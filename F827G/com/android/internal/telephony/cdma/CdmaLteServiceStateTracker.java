package com.android.internal.telephony.cdma;

import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.ProxyController;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import jp.co.sharp.telephony.OemCdmaTelephonyManager.OEM_RIL_RDE_Data;

public class CdmaLteServiceStateTracker extends CdmaServiceStateTracker {
    private static final int EVENT_ALL_DATA_DISCONNECTED = 1001;
    private CDMALTEPhone mCdmaLtePhone;
    private final CellInfoLte mCellInfoLte;
    private CellIdentityLte mLasteCellIdentityLte = new CellIdentityLte();
    private CellIdentityLte mNewCellIdentityLte = new CellIdentityLte();

    public CdmaLteServiceStateTracker(CDMALTEPhone cDMALTEPhone) {
        super(cDMALTEPhone, new CellInfoLte());
        this.mCdmaLtePhone = cDMALTEPhone;
        this.mCdmaLtePhone.registerForSimRecordsLoaded(this, 16, null);
        this.mCellInfoLte = (CellInfoLte) this.mCellInfo;
        ((CellInfoLte) this.mCellInfo).setCellSignalStrength(new CellSignalStrengthLte());
        ((CellInfoLte) this.mCellInfo).setCellIdentity(new CellIdentityLte());
        log("CdmaLteServiceStateTracker Constructors");
    }

    private boolean isInHomeSidNid(int i, int i2) {
        if (!(isSidsAllZeros() || this.mHomeSystemId.length != this.mHomeNetworkId.length || i == 0)) {
            int i3 = 0;
            while (i3 < this.mHomeSystemId.length) {
                if (!(this.mHomeSystemId[i3] == i && (this.mHomeNetworkId[i3] == 0 || this.mHomeNetworkId[i3] == 65535 || i2 == 0 || i2 == 65535 || this.mHomeNetworkId[i3] == i2))) {
                    i3++;
                }
            }
            return false;
        }
        return true;
    }

    public void dispose() {
        this.mPhone.unregisterForSimRecordsLoaded(this);
        super.dispose();
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println("CdmaLteServiceStateTracker extends:");
        super.dump(fileDescriptor, printWriter, strArr);
        printWriter.println(" mCdmaLtePhone=" + this.mCdmaLtePhone);
    }

    public List<CellInfo> getAllCellInfo() {
        if (this.mCi.getRilVersion() >= 8) {
            return super.getAllCellInfo();
        }
        ArrayList arrayList = new ArrayList();
        synchronized (this.mCellInfo) {
            arrayList.add(this.mCellInfoLte);
        }
        log("getAllCellInfo: arrayList=" + arrayList);
        return arrayList;
    }

    /* Access modifiers changed, original: protected */
    public UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(((CDMALTEPhone) this.mPhone).getPhoneId(), 2);
    }

    public void handleMessage(Message message) {
        if (this.mPhone.mIsTheCurrentActivePhone) {
            log("handleMessage: " + message.what);
            switch (message.what) {
                case 5:
                    log("handleMessage EVENT_POLL_STATE_GPRS");
                    handlePollStateResult(message.what, (AsyncResult) message.obj);
                    return;
                case 16:
                    updatePhoneObject();
                    return;
                case OEM_RIL_RDE_Data.RDE_NV_OTKSL_I /*27*/:
                    updatePhoneObject();
                    RuimRecords ruimRecords = (RuimRecords) this.mIccRecords;
                    if (ruimRecords != null) {
                        if (ruimRecords.isProvisioned()) {
                            this.mMdn = ruimRecords.getMdn();
                            this.mMin = ruimRecords.getMin();
                            parseSidNid(ruimRecords.getSid(), ruimRecords.getNid());
                            this.mPrlVersion = ruimRecords.getPrlVersion();
                            this.mIsMinInfoReady = true;
                        }
                        updateOtaspState();
                    }
                    this.mPhone.prepareEri();
                    pollState();
                    return;
                case 1001:
                    ProxyController.getInstance().unregisterForAllDataDisconnected(SubscriptionManager.getDefaultDataSubId(), this);
                    synchronized (this) {
                        if (this.mPendingRadioPowerOffAfterDataOff) {
                            log("EVENT_ALL_DATA_DISCONNECTED, turn radio off now.");
                            hangupAndPowerOff();
                            this.mPendingRadioPowerOffAfterDataOff = false;
                        } else {
                            log("EVENT_ALL_DATA_DISCONNECTED is stale");
                        }
                    }
                    return;
                default:
                    super.handleMessage(message);
                    return;
            }
        }
        loge("Received message " + message + "[" + message.what + "]" + " while being destroyed. Ignoring.");
    }

    /* Access modifiers changed, original: protected */
    /* JADX WARNING: Removed duplicated region for block: B:71:0x0204  */
    /* JADX WARNING: Removed duplicated region for block: B:19:0x0051  */
    /* JADX WARNING: Removed duplicated region for block: B:19:0x0051  */
    /* JADX WARNING: Removed duplicated region for block: B:71:0x0204  */
    public void handlePollStateResultMessage(int r12, android.os.AsyncResult r13) {
        /*
        r11 = this;
        r10 = 7;
        r9 = 6;
        r8 = 2147483647; // 0x7fffffff float:NaN double:1.060997895E-314;
        r4 = 3;
        r1 = 0;
        r0 = 5;
        if (r12 != r0) goto L_0x01f0;
    L_0x000a:
        r0 = r13.result;
        r0 = (java.lang.String[]) r0;
        r0 = (java.lang.String[]) r0;
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "handlePollStateResultMessage: EVENT_POLL_STATE_GPRS states.length=";
        r2 = r2.append(r3);
        r3 = r0.length;
        r2 = r2.append(r3);
        r3 = " states=";
        r2 = r2.append(r3);
        r2 = r2.append(r0);
        r2 = r2.toString();
        r11.log(r2);
        r2 = -1;
        r3 = r0.length;
        if (r3 <= 0) goto L_0x020b;
    L_0x0035:
        r3 = 0;
        r3 = r0[r3];	 Catch:{ NumberFormatException -> 0x0102 }
        r7 = java.lang.Integer.parseInt(r3);	 Catch:{ NumberFormatException -> 0x0102 }
        r2 = r0.length;	 Catch:{ NumberFormatException -> 0x0200 }
        r3 = 4;
        if (r2 < r3) goto L_0x0208;
    L_0x0040:
        r2 = r0[r4];
        if (r2 == 0) goto L_0x0208;
    L_0x0044:
        r2 = 3;
        r2 = r0[r2];	 Catch:{ NumberFormatException -> 0x0200 }
        r1 = java.lang.Integer.parseInt(r2);	 Catch:{ NumberFormatException -> 0x0200 }
        r6 = r1;
    L_0x004c:
        r1 = r0.length;
        r2 = 10;
        if (r1 < r2) goto L_0x0204;
    L_0x0051:
        r1 = 0;
        r2 = r11.mNewSS;	 Catch:{ Exception -> 0x011d }
        r2 = r2.getOperatorNumeric();	 Catch:{ Exception -> 0x011d }
        r1 = 0;
        r3 = 3;
        r1 = r2.substring(r1, r3);	 Catch:{ Exception -> 0x01fc }
        r1 = java.lang.Integer.parseInt(r1);	 Catch:{ Exception -> 0x01fc }
        r3 = r2;
    L_0x0063:
        r2 = 3;
        r2 = r3.substring(r2);	 Catch:{ Exception -> 0x0158 }
        r2 = java.lang.Integer.parseInt(r2);	 Catch:{ Exception -> 0x0158 }
    L_0x006c:
        r3 = 6;
        r3 = r0[r3];	 Catch:{ Exception -> 0x017c }
        r3 = java.lang.Integer.decode(r3);	 Catch:{ Exception -> 0x017c }
        r5 = r3.intValue();	 Catch:{ Exception -> 0x017c }
    L_0x0077:
        r3 = 7;
        r3 = r0[r3];	 Catch:{ Exception -> 0x01a2 }
        r3 = java.lang.Integer.decode(r3);	 Catch:{ Exception -> 0x01a2 }
        r4 = r3.intValue();	 Catch:{ Exception -> 0x01a2 }
    L_0x0082:
        r3 = 8;
        r3 = r0[r3];	 Catch:{ Exception -> 0x01c8 }
        r3 = java.lang.Integer.decode(r3);	 Catch:{ Exception -> 0x01c8 }
        r3 = r3.intValue();	 Catch:{ Exception -> 0x01c8 }
    L_0x008e:
        r8 = 9;
        r0 = r0[r8];	 Catch:{ Exception -> 0x01f5 }
        r0 = java.lang.Integer.decode(r0);	 Catch:{ Exception -> 0x01f5 }
        r0.intValue();	 Catch:{ Exception -> 0x01f5 }
    L_0x0099:
        r0 = new android.telephony.CellIdentityLte;
        r0.<init>(r1, r2, r3, r4, r5);
        r11.mNewCellIdentityLte = r0;
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r1 = "handlePollStateResultMessage: mNewLteCellIdentity=";
        r0 = r0.append(r1);
        r1 = r11.mNewCellIdentityLte;
        r0 = r0.append(r1);
        r0 = r0.toString();
        r11.log(r0);
        r0 = r6;
        r2 = r7;
    L_0x00ba:
        r1 = r11.mNewSS;
        r1.setRilDataRadioTechnology(r0);
        r1 = r11.regCodeToServiceState(r2);
        r3 = r11.mNewSS;
        r3.setDataRegState(r1);
        r3 = r11.mNewSS;
        r4 = r11.regCodeIsRoaming(r2);
        r3.setDataRoaming(r4);
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "handlPollStateResultMessage: CdmaLteSST setDataRegState=";
        r3 = r3.append(r4);
        r1 = r3.append(r1);
        r3 = " regState=";
        r1 = r1.append(r3);
        r1 = r1.append(r2);
        r3 = " dataRadioTechnology=";
        r1 = r1.append(r3);
        r0 = r1.append(r0);
        r0 = r0.toString();
        r11.log(r0);
        r0 = r11.regCodeIsRoaming(r2);
        r11.mDataRoaming = r0;
    L_0x0101:
        return;
    L_0x0102:
        r3 = move-exception;
        r7 = r2;
    L_0x0104:
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r4 = "handlePollStateResultMessage: error parsing GprsRegistrationState: ";
        r2 = r2.append(r4);
        r2 = r2.append(r3);
        r2 = r2.toString();
        r11.loge(r2);
        r6 = r1;
        goto L_0x004c;
    L_0x011d:
        r2 = move-exception;
    L_0x011e:
        r2 = r11.mSS;	 Catch:{ Exception -> 0x0130 }
        r3 = r2.getOperatorNumeric();	 Catch:{ Exception -> 0x0130 }
        r1 = 0;
        r2 = 3;
        r1 = r3.substring(r1, r2);	 Catch:{ Exception -> 0x01f8 }
        r1 = java.lang.Integer.parseInt(r1);	 Catch:{ Exception -> 0x01f8 }
        goto L_0x0063;
    L_0x0130:
        r2 = move-exception;
        r3 = r1;
    L_0x0132:
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r4 = "handlePollStateResultMessage: bad mcc operatorNumeric=";
        r1 = r1.append(r4);
        r1 = r1.append(r3);
        r3 = " ex=";
        r1 = r1.append(r3);
        r1 = r1.append(r2);
        r1 = r1.toString();
        r11.loge(r1);
        r2 = "";
        r1 = r8;
        r3 = r2;
        goto L_0x0063;
    L_0x0158:
        r2 = move-exception;
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "handlePollStateResultMessage: bad mnc operatorNumeric=";
        r4 = r4.append(r5);
        r3 = r4.append(r3);
        r4 = " e=";
        r3 = r3.append(r4);
        r2 = r3.append(r2);
        r2 = r2.toString();
        r11.loge(r2);
        r2 = r8;
        goto L_0x006c;
    L_0x017c:
        r3 = move-exception;
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r5 = "handlePollStateResultMessage: bad tac states[6]=";
        r4 = r4.append(r5);
        r5 = r0[r9];
        r4 = r4.append(r5);
        r5 = " e=";
        r4 = r4.append(r5);
        r3 = r4.append(r3);
        r3 = r3.toString();
        r11.loge(r3);
        r5 = r8;
        goto L_0x0077;
    L_0x01a2:
        r3 = move-exception;
        r4 = new java.lang.StringBuilder;
        r4.<init>();
        r9 = "handlePollStateResultMessage: bad pci states[7]=";
        r4 = r4.append(r9);
        r9 = r0[r10];
        r4 = r4.append(r9);
        r9 = " e=";
        r4 = r4.append(r9);
        r3 = r4.append(r3);
        r3 = r3.toString();
        r11.loge(r3);
        r4 = r8;
        goto L_0x0082;
    L_0x01c8:
        r3 = move-exception;
        r9 = new java.lang.StringBuilder;
        r9.<init>();
        r10 = "handlePollStateResultMessage: bad eci states[8]=";
        r9 = r9.append(r10);
        r10 = 8;
        r10 = r0[r10];
        r9 = r9.append(r10);
        r10 = " e=";
        r9 = r9.append(r10);
        r3 = r9.append(r3);
        r3 = r3.toString();
        r11.loge(r3);
        r3 = r8;
        goto L_0x008e;
    L_0x01f0:
        super.handlePollStateResultMessage(r12, r13);
        goto L_0x0101;
    L_0x01f5:
        r0 = move-exception;
        goto L_0x0099;
    L_0x01f8:
        r1 = move-exception;
        r2 = r1;
        goto L_0x0132;
    L_0x01fc:
        r1 = move-exception;
        r1 = r2;
        goto L_0x011e;
    L_0x0200:
        r2 = move-exception;
        r3 = r2;
        goto L_0x0104;
    L_0x0204:
        r0 = r6;
        r2 = r7;
        goto L_0x00ba;
    L_0x0208:
        r6 = r1;
        goto L_0x004c;
    L_0x020b:
        r0 = r1;
        goto L_0x00ba;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cdma.CdmaLteServiceStateTracker.handlePollStateResultMessage(int, android.os.AsyncResult):void");
    }

    public boolean isConcurrentVoiceAndDataAllowed() {
        return this.mSS.getCssIndicator() == 1;
    }

    /* Access modifiers changed, original: protected */
    public void log(String str) {
        Rlog.d("CdmaSST", "[CdmaLteSST] " + str);
    }

    /* Access modifiers changed, original: protected */
    public void loge(String str) {
        Rlog.e("CdmaSST", "[CdmaLteSST] " + str);
    }

    /* Access modifiers changed, original: protected */
    public boolean onSignalStrengthResult(AsyncResult asyncResult, boolean z) {
        if (isRatLte(this.mSS.getRilDataRadioTechnology())) {
            z = true;
        }
        boolean onSignalStrengthResult = super.onSignalStrengthResult(asyncResult, z);
        synchronized (this.mCellInfo) {
            if (isRatLte(this.mSS.getRilDataRadioTechnology())) {
                this.mCellInfoLte.setTimeStamp(SystemClock.elapsedRealtime() * 1000);
                this.mCellInfoLte.setTimeStampType(4);
                this.mCellInfoLte.getCellSignalStrength().initialize(this.mSignalStrength, Integer.MAX_VALUE);
            }
            if (this.mCellInfoLte.getCellIdentity() != null) {
                ArrayList arrayList = new ArrayList();
                arrayList.add(this.mCellInfoLte);
                this.mPhoneBase.notifyCellInfo(arrayList);
            }
        }
        return onSignalStrengthResult;
    }

    public void pollState() {
        this.mPollingContext = new int[1];
        this.mPollingContext[0] = 0;
        switch (this.mCi.getRadioState()) {
            case RADIO_UNAVAILABLE:
                this.mNewSS.setStateOutOfService();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                pollStateDone();
                return;
            case RADIO_OFF:
                this.mNewSS.setStateOff();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                if (!(isIwlanFeatureAvailable() && 18 == this.mSS.getRilDataRadioTechnology())) {
                    pollStateDone();
                }
                if (!isIwlanFeatureAvailable()) {
                    return;
                }
                break;
        }
        int[] iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getOperator(obtainMessage(25, this.mPollingContext));
        iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getVoiceRegistrationState(obtainMessage(24, this.mPollingContext));
        iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getDataRegistrationState(obtainMessage(5, this.mPollingContext));
    }

    /* Access modifiers changed, original: protected */
    /* JADX WARNING: Removed duplicated region for block: B:220:0x0770  */
    /* JADX WARNING: Removed duplicated region for block: B:92:0x025d  */
    /* JADX WARNING: Removed duplicated region for block: B:100:0x0401  */
    /* JADX WARNING: Removed duplicated region for block: B:102:0x0406  */
    /* JADX WARNING: Removed duplicated region for block: B:108:0x0438  */
    /* JADX WARNING: Removed duplicated region for block: B:110:0x0441  */
    /* JADX WARNING: Removed duplicated region for block: B:160:0x05e8  */
    /* JADX WARNING: Removed duplicated region for block: B:169:0x061a  */
    /* JADX WARNING: Removed duplicated region for block: B:174:0x062f  */
    /* JADX WARNING: Removed duplicated region for block: B:176:0x0638  */
    /* JADX WARNING: Removed duplicated region for block: B:178:0x0641  */
    /* JADX WARNING: Removed duplicated region for block: B:180:0x064a  */
    /* JADX WARNING: Removed duplicated region for block: B:182:0x0653  */
    /* JADX WARNING: Removed duplicated region for block: B:185:0x0664 A:{SYNTHETIC} */
    /* JADX WARNING: Removed duplicated region for block: B:87:0x022f  */
    /* JADX WARNING: Removed duplicated region for block: B:92:0x025d  */
    /* JADX WARNING: Removed duplicated region for block: B:220:0x0770  */
    /* JADX WARNING: Removed duplicated region for block: B:100:0x0401  */
    /* JADX WARNING: Removed duplicated region for block: B:102:0x0406  */
    /* JADX WARNING: Removed duplicated region for block: B:108:0x0438  */
    /* JADX WARNING: Removed duplicated region for block: B:110:0x0441  */
    /* JADX WARNING: Removed duplicated region for block: B:160:0x05e8  */
    /* JADX WARNING: Removed duplicated region for block: B:165:0x05fc  */
    /* JADX WARNING: Removed duplicated region for block: B:169:0x061a  */
    /* JADX WARNING: Removed duplicated region for block: B:174:0x062f  */
    /* JADX WARNING: Removed duplicated region for block: B:176:0x0638  */
    /* JADX WARNING: Removed duplicated region for block: B:178:0x0641  */
    /* JADX WARNING: Removed duplicated region for block: B:180:0x064a  */
    /* JADX WARNING: Removed duplicated region for block: B:182:0x0653  */
    /* JADX WARNING: Removed duplicated region for block: B:185:0x0664 A:{SYNTHETIC} */
    /* JADX WARNING: Missing block: B:74:0x01db, code skipped:
            if (isRatLte(r22.mNewSS.getRilDataRadioTechnology()) != false) goto L_0x01dd;
     */
    public void pollStateDone() {
        /*
        r22 = this;
        r0 = r22;
        r3 = r0.mPhone;
        r0 = r22;
        r4 = r0.mNewSS;
        r4 = r4.getOperatorNumeric();
        r3 = r3.isMccMncMarkedAsNonRoaming(r4);
        if (r3 != 0) goto L_0x0024;
    L_0x0012:
        r0 = r22;
        r3 = r0.mPhone;
        r0 = r22;
        r4 = r0.mNewSS;
        r4 = r4.getSystemId();
        r3 = r3.isSidMarkedAsNonRoaming(r4);
        if (r3 == 0) goto L_0x06ea;
    L_0x0024:
        r3 = "pollStateDone: override - marked as non-roaming.";
        r0 = r22;
        r0.log(r3);
        r0 = r22;
        r3 = r0.mNewSS;
        r4 = 0;
        r3.setRoaming(r4);
        r0 = r22;
        r3 = r0.mNewSS;
        r4 = 1;
        r3.setCdmaEriIconIndex(r4);
    L_0x003b:
        r3 = android.os.Build.IS_DEBUGGABLE;
        if (r3 == 0) goto L_0x0050;
    L_0x003f:
        r3 = "telephony.test.forceRoaming";
        r4 = 0;
        r3 = android.os.SystemProperties.getBoolean(r3, r4);
        if (r3 == 0) goto L_0x0050;
    L_0x0048:
        r0 = r22;
        r3 = r0.mNewSS;
        r4 = 1;
        r3.setRoaming(r4);
    L_0x0050:
        r22.useDataRegStateForDataOnlyDevices();
        r22.resetServiceStateInIwlanMode();
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r4 = "pollStateDone: lte 1 ss=[";
        r3 = r3.append(r4);
        r0 = r22;
        r4 = r0.mSS;
        r3 = r3.append(r4);
        r4 = "] newSS=[";
        r3 = r3.append(r4);
        r0 = r22;
        r4 = r0.mNewSS;
        r3 = r3.append(r4);
        r4 = "]";
        r3 = r3.append(r4);
        r3 = r3.toString();
        r0 = r22;
        r0.log(r3);
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getVoiceRegState();
        if (r3 == 0) goto L_0x072f;
    L_0x0090:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getVoiceRegState();
        if (r3 != 0) goto L_0x072f;
    L_0x009a:
        r3 = 1;
        r4 = r3;
    L_0x009c:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getVoiceRegState();
        if (r3 != 0) goto L_0x0733;
    L_0x00a6:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getVoiceRegState();
        if (r3 == 0) goto L_0x0733;
    L_0x00b0:
        r3 = 1;
        r5 = r3;
    L_0x00b2:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getDataRegState();
        if (r3 == 0) goto L_0x0737;
    L_0x00bc:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getDataRegState();
        if (r3 != 0) goto L_0x0737;
    L_0x00c6:
        r3 = 1;
        r6 = r3;
    L_0x00c8:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getDataRegState();
        if (r3 != 0) goto L_0x073b;
    L_0x00d2:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getDataRegState();
        if (r3 == 0) goto L_0x073b;
    L_0x00dc:
        r3 = 1;
        r7 = r3;
    L_0x00de:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getDataRegState();
        r0 = r22;
        r8 = r0.mNewSS;
        r8 = r8.getDataRegState();
        if (r3 == r8) goto L_0x073f;
    L_0x00f0:
        r3 = 1;
        r8 = r3;
    L_0x00f2:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getRilVoiceRadioTechnology();
        r0 = r22;
        r9 = r0.mNewSS;
        r9 = r9.getRilVoiceRadioTechnology();
        if (r3 == r9) goto L_0x0743;
    L_0x0104:
        r3 = 1;
        r9 = r3;
    L_0x0106:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getRilDataRadioTechnology();
        r0 = r22;
        r10 = r0.mNewSS;
        r10 = r10.getRilDataRadioTechnology();
        if (r3 == r10) goto L_0x0747;
    L_0x0118:
        r3 = 1;
        r10 = r3;
    L_0x011a:
        r0 = r22;
        r3 = r0.mNewSS;
        r0 = r22;
        r11 = r0.mSS;
        r3 = r3.equals(r11);
        if (r3 != 0) goto L_0x074b;
    L_0x0128:
        r3 = 1;
        r11 = r3;
    L_0x012a:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getVoiceRoaming();
        if (r3 != 0) goto L_0x074f;
    L_0x0134:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getVoiceRoaming();
        if (r3 == 0) goto L_0x074f;
    L_0x013e:
        r3 = 1;
        r12 = r3;
    L_0x0140:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getVoiceRoaming();
        if (r3 == 0) goto L_0x0753;
    L_0x014a:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getVoiceRoaming();
        if (r3 != 0) goto L_0x0753;
    L_0x0154:
        r3 = 1;
        r13 = r3;
    L_0x0156:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getDataRoaming();
        if (r3 != 0) goto L_0x0757;
    L_0x0160:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getDataRoaming();
        if (r3 == 0) goto L_0x0757;
    L_0x016a:
        r3 = 1;
        r14 = r3;
    L_0x016c:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getDataRoaming();
        if (r3 == 0) goto L_0x075b;
    L_0x0176:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getDataRoaming();
        if (r3 != 0) goto L_0x075b;
    L_0x0180:
        r3 = 1;
        r15 = r3;
    L_0x0182:
        r0 = r22;
        r3 = r0.mNewCellLoc;
        r0 = r22;
        r0 = r0.mCellLoc;
        r16 = r0;
        r0 = r16;
        r3 = r3.equals(r0);
        if (r3 != 0) goto L_0x075f;
    L_0x0194:
        r3 = 1;
        r16 = r3;
    L_0x0197:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getDataRegState();
        if (r3 != 0) goto L_0x0764;
    L_0x01a1:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getRilDataRadioTechnology();
        r0 = r22;
        r3 = r0.isRatLte(r3);
        if (r3 == 0) goto L_0x01bf;
    L_0x01b1:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getRilDataRadioTechnology();
        r17 = 13;
        r0 = r17;
        if (r3 == r0) goto L_0x01dd;
    L_0x01bf:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getRilDataRadioTechnology();
        r17 = 13;
        r0 = r17;
        if (r3 != r0) goto L_0x0764;
    L_0x01cd:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getRilDataRadioTechnology();
        r0 = r22;
        r3 = r0.isRatLte(r3);
        if (r3 == 0) goto L_0x0764;
    L_0x01dd:
        r3 = 1;
        r17 = r3;
    L_0x01e0:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getRilDataRadioTechnology();
        r0 = r22;
        r3 = r0.isRatLte(r3);
        if (r3 != 0) goto L_0x01fe;
    L_0x01f0:
        r0 = r22;
        r3 = r0.mNewSS;
        r3 = r3.getRilDataRadioTechnology();
        r18 = 13;
        r0 = r18;
        if (r3 != r0) goto L_0x0769;
    L_0x01fe:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getRilDataRadioTechnology();
        r0 = r22;
        r3 = r0.isRatLte(r3);
        if (r3 != 0) goto L_0x0769;
    L_0x020e:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getRilDataRadioTechnology();
        r18 = 13;
        r0 = r18;
        if (r3 == r0) goto L_0x0769;
    L_0x021c:
        r3 = 1;
    L_0x021d:
        r0 = r22;
        r0 = r0.mNewSS;
        r18 = r0;
        r18 = r18.getRilDataRadioTechnology();
        r19 = 4;
        r0 = r18;
        r1 = r19;
        if (r0 < r1) goto L_0x076c;
    L_0x022f:
        r0 = r22;
        r0 = r0.mNewSS;
        r18 = r0;
        r18 = r18.getRilDataRadioTechnology();
        r19 = 8;
        r0 = r18;
        r1 = r19;
        if (r0 > r1) goto L_0x076c;
    L_0x0241:
        r18 = 1;
    L_0x0243:
        r0 = r22;
        r0 = r0.mSS;
        r19 = r0;
        r19 = r19.getCssIndicator();
        r0 = r22;
        r0 = r0.mNewSS;
        r20 = r0;
        r20 = r20.getCssIndicator();
        r0 = r19;
        r1 = r20;
        if (r0 == r1) goto L_0x0770;
    L_0x025d:
        r19 = 1;
    L_0x025f:
        r20 = new java.lang.StringBuilder;
        r20.<init>();
        r21 = "pollStateDone: hasRegistered=";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r4);
        r21 = " hasDeegistered=";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r5);
        r21 = " hasCdmaDataConnectionAttached=";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r6);
        r21 = " hasCdmaDataConnectionDetached=";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r7);
        r21 = " hasCdmaDataConnectionChanged=";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r8);
        r21 = " hasVoiceRadioTechnologyChanged= ";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r9);
        r21 = " hasDataRadioTechnologyChanged=";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r10);
        r21 = " hasChanged=";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r11);
        r21 = " hasVoiceRoamingOn=";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r12);
        r21 = " hasVoiceRoamingOff=";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r13);
        r21 = " hasDataRoamingOn=";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r14);
        r21 = " hasDataRoamingOff=";
        r20 = r20.append(r21);
        r0 = r20;
        r20 = r0.append(r15);
        r21 = " hasLocationChanged=";
        r20 = r20.append(r21);
        r0 = r20;
        r1 = r16;
        r20 = r0.append(r1);
        r21 = " has4gHandoff = ";
        r20 = r20.append(r21);
        r0 = r20;
        r1 = r17;
        r20 = r0.append(r1);
        r21 = " hasMultiApnSupport=";
        r20 = r20.append(r21);
        r0 = r20;
        r3 = r0.append(r3);
        r20 = " hasLostMultiApnSupport=";
        r0 = r20;
        r3 = r3.append(r0);
        r0 = r18;
        r3 = r3.append(r0);
        r3 = r3.toString();
        r0 = r22;
        r0.log(r3);
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getVoiceRegState();
        r0 = r22;
        r0 = r0.mNewSS;
        r18 = r0;
        r18 = r18.getVoiceRegState();
        r0 = r18;
        if (r3 != r0) goto L_0x035f;
    L_0x0349:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getDataRegState();
        r0 = r22;
        r0 = r0.mNewSS;
        r18 = r0;
        r18 = r18.getDataRegState();
        r0 = r18;
        if (r3 == r0) goto L_0x03b7;
    L_0x035f:
        r3 = 50116; // 0xc3c4 float:7.0227E-41 double:2.47606E-319;
        r18 = 4;
        r0 = r18;
        r0 = new java.lang.Object[r0];
        r18 = r0;
        r20 = 0;
        r0 = r22;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getVoiceRegState();
        r21 = java.lang.Integer.valueOf(r21);
        r18[r20] = r21;
        r20 = 1;
        r0 = r22;
        r0 = r0.mSS;
        r21 = r0;
        r21 = r21.getDataRegState();
        r21 = java.lang.Integer.valueOf(r21);
        r18[r20] = r21;
        r20 = 2;
        r0 = r22;
        r0 = r0.mNewSS;
        r21 = r0;
        r21 = r21.getVoiceRegState();
        r21 = java.lang.Integer.valueOf(r21);
        r18[r20] = r21;
        r20 = 3;
        r0 = r22;
        r0 = r0.mNewSS;
        r21 = r0;
        r21 = r21.getDataRegState();
        r21 = java.lang.Integer.valueOf(r21);
        r18[r20] = r21;
        r0 = r18;
        android.util.EventLog.writeEvent(r3, r0);
    L_0x03b7:
        r0 = r22;
        r3 = r0.mSS;
        r0 = r22;
        r0 = r0.mNewSS;
        r18 = r0;
        r0 = r18;
        r1 = r22;
        r1.mSS = r0;
        r0 = r22;
        r0.mNewSS = r3;
        r0 = r22;
        r3 = r0.mNewSS;
        r3.setStateOutOfService();
        r0 = r22;
        r3 = r0.mCellLoc;
        r0 = r22;
        r0 = r0.mNewCellLoc;
        r18 = r0;
        r0 = r18;
        r1 = r22;
        r1.mCellLoc = r0;
        r0 = r22;
        r0.mNewCellLoc = r3;
        r0 = r22;
        r3 = r0.mNewSS;
        r3.setStateOutOfService();
        r0 = r22;
        r3 = r0.mPhone;
        r3 = r3.getContext();
        r18 = "phone";
        r0 = r18;
        r3 = r3.getSystemService(r0);
        r3 = (android.telephony.TelephonyManager) r3;
        if (r9 == 0) goto L_0x0404;
    L_0x0401:
        r22.updatePhoneObject();
    L_0x0404:
        if (r10 == 0) goto L_0x0436;
    L_0x0406:
        r0 = r22;
        r9 = r0.mPhone;
        r9 = r9.getPhoneId();
        r0 = r22;
        r0 = r0.mSS;
        r18 = r0;
        r18 = r18.getRilDataRadioTechnology();
        r0 = r18;
        r3.setDataNetworkTypeForPhone(r9, r0);
        r3 = r22.isIwlanFeatureAvailable();
        if (r3 == 0) goto L_0x0436;
    L_0x0423:
        r3 = 18;
        r0 = r22;
        r9 = r0.mSS;
        r9 = r9.getRilDataRadioTechnology();
        if (r3 != r9) goto L_0x0436;
    L_0x042f:
        r3 = "pollStateDone: IWLAN enabled";
        r0 = r22;
        r0.log(r3);
    L_0x0436:
        if (r4 == 0) goto L_0x043f;
    L_0x0438:
        r0 = r22;
        r3 = r0.mNetworkAttachedRegistrants;
        r3.notifyRegistrants();
    L_0x043f:
        if (r11 == 0) goto L_0x05e6;
    L_0x0441:
        r0 = r22;
        r3 = r0.mUiccController;
        r9 = r22.getPhoneId();
        r3 = r3.getUiccCard(r9);
        if (r3 != 0) goto L_0x0774;
    L_0x044f:
        r3 = 0;
    L_0x0450:
        if (r3 != 0) goto L_0x04c0;
    L_0x0452:
        r0 = r22;
        r3 = r0.mCi;
        r3 = r3.getRadioState();
        r3 = r3.isOn();
        if (r3 == 0) goto L_0x04c0;
    L_0x0460:
        r0 = r22;
        r3 = r0.mPhone;
        r3 = r3.isEriFileLoaded();
        if (r3 == 0) goto L_0x04c0;
    L_0x046a:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getRilVoiceRadioTechnology();
        r0 = r22;
        r3 = r0.isRatLte(r3);
        if (r3 == 0) goto L_0x048f;
    L_0x047a:
        r0 = r22;
        r3 = r0.mPhone;
        r3 = r3.getContext();
        r3 = r3.getResources();
        r9 = 17957042; // 0x11200b2 float:2.6816464E-38 double:8.8719576E-317;
        r3 = r3.getBoolean(r9);
        if (r3 == 0) goto L_0x04c0;
    L_0x048f:
        r0 = r22;
        r3 = r0.mIsSubscriptionFromRuim;
        if (r3 != 0) goto L_0x04c0;
    L_0x0495:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getOperatorAlphaLong();
        r0 = r22;
        r9 = r0.mSS;
        r9 = r9.getVoiceRegState();
        if (r9 == 0) goto L_0x04b1;
    L_0x04a7:
        r0 = r22;
        r9 = r0.mSS;
        r9 = r9.getDataRegState();
        if (r9 != 0) goto L_0x078c;
    L_0x04b1:
        r0 = r22;
        r3 = r0.mPhone;
        r3 = r3.getCdmaEriText();
    L_0x04b9:
        r0 = r22;
        r9 = r0.mSS;
        r9.setOperatorAlphaLong(r3);
    L_0x04c0:
        r0 = r22;
        r3 = r0.mUiccApplcation;
        if (r3 == 0) goto L_0x0526;
    L_0x04c6:
        r0 = r22;
        r3 = r0.mUiccApplcation;
        r3 = r3.getState();
        r9 = com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState.APPSTATE_READY;
        if (r3 != r9) goto L_0x0526;
    L_0x04d2:
        r0 = r22;
        r3 = r0.mIccRecords;
        if (r3 == 0) goto L_0x0526;
    L_0x04d8:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getVoiceRegState();
        if (r3 != 0) goto L_0x0526;
    L_0x04e2:
        r0 = r22;
        r3 = r0.mIccRecords;
        r3 = (com.android.internal.telephony.uicc.RuimRecords) r3;
        r3 = r3.getCsimSpnDisplayCondition();
        r0 = r22;
        r9 = r0.mSS;
        r9 = r9.getCdmaEriIconIndex();
        if (r3 == 0) goto L_0x0526;
    L_0x04f6:
        r3 = 1;
        if (r9 != r3) goto L_0x0526;
    L_0x04f9:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getSystemId();
        r0 = r22;
        r9 = r0.mSS;
        r9 = r9.getNetworkId();
        r0 = r22;
        r3 = r0.isInHomeSidNid(r3, r9);
        if (r3 == 0) goto L_0x0526;
    L_0x0511:
        r0 = r22;
        r3 = r0.mIccRecords;
        if (r3 == 0) goto L_0x0526;
    L_0x0517:
        r0 = r22;
        r3 = r0.mSS;
        r0 = r22;
        r9 = r0.mIccRecords;
        r9 = r9.getServiceProviderName();
        r3.setOperatorAlphaLong(r9);
    L_0x0526:
        r0 = r22;
        r3 = r0.mPhone;
        r9 = "gsm.operator.alpha";
        r0 = r22;
        r11 = r0.mSS;
        r11 = r11.getOperatorAlphaLong();
        r3.setSystemProperty(r9, r11);
        r3 = "gsm.operator.numeric";
        r9 = "";
        r18 = android.os.SystemProperties.get(r3, r9);
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getOperatorNumeric();
        r0 = r22;
        r9 = r0.isInvalidOperatorNumeric(r3);
        if (r9 == 0) goto L_0x055d;
    L_0x054f:
        r0 = r22;
        r9 = r0.mSS;
        r9 = r9.getSystemId();
        r0 = r22;
        r3 = r0.fixUnknownMcc(r3, r9);
    L_0x055d:
        r0 = r22;
        r9 = r0.mPhone;
        r11 = "gsm.operator.numeric";
        r9.setSystemProperty(r11, r3);
        r0 = r22;
        r9 = r0.mPhone;
        r9 = r9.getContext();
        r0 = r22;
        r1 = r18;
        r0.updateCarrierMccMncConfiguration(r3, r1, r9);
        r0 = r22;
        r9 = r0.isInvalidOperatorNumeric(r3);
        if (r9 == 0) goto L_0x07d4;
    L_0x057d:
        r3 = "operatorNumeric is null";
        r0 = r22;
        r0.log(r3);
        r0 = r22;
        r3 = r0.mPhone;
        r9 = "gsm.operator.iso-country";
        r11 = "";
        r3.setSystemProperty(r9, r11);
        r3 = 0;
        r0 = r22;
        r0.mGotCountryCode = r3;
    L_0x0594:
        r0 = r22;
        r9 = r0.mPhone;
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getVoiceRoaming();
        if (r3 != 0) goto L_0x05ac;
    L_0x05a2:
        r0 = r22;
        r3 = r0.mSS;
        r3 = r3.getDataRoaming();
        if (r3 == 0) goto L_0x0859;
    L_0x05ac:
        r3 = "true";
    L_0x05ae:
        r11 = "gsm.operator.isroaming";
        r9.setSystemProperty(r11, r3);
        r22.updateSpnDisplay();
        r0 = r22;
        r3 = r0.mSS;
        r0 = r22;
        r0.setRoamingType(r3);
        r3 = new java.lang.StringBuilder;
        r3.<init>();
        r9 = "Broadcasting ServiceState : ";
        r3 = r3.append(r9);
        r0 = r22;
        r9 = r0.mSS;
        r3 = r3.append(r9);
        r3 = r3.toString();
        r0 = r22;
        r0.log(r3);
        r0 = r22;
        r3 = r0.mPhone;
        r0 = r22;
        r9 = r0.mSS;
        r3.notifyServiceStateChanged(r9);
    L_0x05e6:
        if (r7 == 0) goto L_0x05ef;
    L_0x05e8:
        r0 = r22;
        r3 = r0.mDetachedRegistrants;
        r3.notifyRegistrants();
    L_0x05ef:
        if (r8 != 0) goto L_0x05f3;
    L_0x05f1:
        if (r10 == 0) goto L_0x0618;
    L_0x05f3:
        r22.notifyDataRegStateRilRadioTechnologyChanged();
        r3 = r22.isIwlanFeatureAvailable();
        if (r3 == 0) goto L_0x085d;
    L_0x05fc:
        r3 = 18;
        r0 = r22;
        r7 = r0.mSS;
        r7 = r7.getRilDataRadioTechnology();
        if (r3 != r7) goto L_0x085d;
    L_0x0608:
        r0 = r22;
        r3 = r0.mPhone;
        r7 = "iwlanAvailable";
        r3.notifyDataConnection(r7);
        r19 = 0;
        r3 = 1;
        r0 = r22;
        r0.mIwlanRatAvailable = r3;
    L_0x0618:
        if (r19 == 0) goto L_0x0622;
    L_0x061a:
        r0 = r22;
        r3 = r0.mPhone;
        r7 = 0;
        r3.notifyDataConnection(r7);
    L_0x0622:
        if (r6 != 0) goto L_0x0626;
    L_0x0624:
        if (r17 == 0) goto L_0x062d;
    L_0x0626:
        r0 = r22;
        r3 = r0.mAttachedRegistrants;
        r3.notifyRegistrants();
    L_0x062d:
        if (r12 == 0) goto L_0x0636;
    L_0x062f:
        r0 = r22;
        r3 = r0.mVoiceRoamingOnRegistrants;
        r3.notifyRegistrants();
    L_0x0636:
        if (r13 == 0) goto L_0x063f;
    L_0x0638:
        r0 = r22;
        r3 = r0.mVoiceRoamingOffRegistrants;
        r3.notifyRegistrants();
    L_0x063f:
        if (r14 == 0) goto L_0x0648;
    L_0x0641:
        r0 = r22;
        r3 = r0.mDataRoamingOnRegistrants;
        r3.notifyRegistrants();
    L_0x0648:
        if (r15 == 0) goto L_0x0651;
    L_0x064a:
        r0 = r22;
        r3 = r0.mDataRoamingOffRegistrants;
        r3.notifyRegistrants();
    L_0x0651:
        if (r16 == 0) goto L_0x065a;
    L_0x0653:
        r0 = r22;
        r3 = r0.mPhone;
        r3.notifyLocationChanged();
    L_0x065a:
        r8 = new java.util.ArrayList;
        r8.<init>();
        r0 = r22;
        r9 = r0.mCellInfo;
        monitor-enter(r9);
        r0 = r22;
        r3 = r0.mCellInfo;	 Catch:{ all -> 0x0876 }
        r3 = (android.telephony.CellInfoLte) r3;	 Catch:{ all -> 0x0876 }
        r0 = r22;
        r6 = r0.mNewCellIdentityLte;	 Catch:{ all -> 0x0876 }
        r0 = r22;
        r7 = r0.mLasteCellIdentityLte;	 Catch:{ all -> 0x0876 }
        r6 = r6.equals(r7);	 Catch:{ all -> 0x0876 }
        if (r6 != 0) goto L_0x086f;
    L_0x0678:
        r6 = 1;
        r7 = r6;
    L_0x067a:
        if (r4 != 0) goto L_0x0680;
    L_0x067c:
        if (r5 != 0) goto L_0x0680;
    L_0x067e:
        if (r7 == 0) goto L_0x06e1;
    L_0x0680:
        android.os.SystemClock.elapsedRealtime();	 Catch:{ all -> 0x0876 }
        r0 = r22;
        r6 = r0.mSS;	 Catch:{ all -> 0x0876 }
        r6 = r6.getVoiceRegState();	 Catch:{ all -> 0x0876 }
        if (r6 != 0) goto L_0x0873;
    L_0x068d:
        r6 = 1;
    L_0x068e:
        r0 = r22;
        r10 = r0.mNewCellIdentityLte;	 Catch:{ all -> 0x0876 }
        r0 = r22;
        r0.mLasteCellIdentityLte = r10;	 Catch:{ all -> 0x0876 }
        r3.setRegistered(r6);	 Catch:{ all -> 0x0876 }
        r0 = r22;
        r6 = r0.mLasteCellIdentityLte;	 Catch:{ all -> 0x0876 }
        r3.setCellIdentity(r6);	 Catch:{ all -> 0x0876 }
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0876 }
        r3.<init>();	 Catch:{ all -> 0x0876 }
        r6 = "pollStateDone: hasRegistered=";
        r3 = r3.append(r6);	 Catch:{ all -> 0x0876 }
        r3 = r3.append(r4);	 Catch:{ all -> 0x0876 }
        r4 = " hasDeregistered=";
        r3 = r3.append(r4);	 Catch:{ all -> 0x0876 }
        r3 = r3.append(r5);	 Catch:{ all -> 0x0876 }
        r4 = " cidChanged=";
        r3 = r3.append(r4);	 Catch:{ all -> 0x0876 }
        r3 = r3.append(r7);	 Catch:{ all -> 0x0876 }
        r4 = " mCellInfo=";
        r3 = r3.append(r4);	 Catch:{ all -> 0x0876 }
        r0 = r22;
        r4 = r0.mCellInfo;	 Catch:{ all -> 0x0876 }
        r3 = r3.append(r4);	 Catch:{ all -> 0x0876 }
        r3 = r3.toString();	 Catch:{ all -> 0x0876 }
        r0 = r22;
        r0.log(r3);	 Catch:{ all -> 0x0876 }
        r0 = r22;
        r3 = r0.mCellInfo;	 Catch:{ all -> 0x0876 }
        r8.add(r3);	 Catch:{ all -> 0x0876 }
    L_0x06e1:
        r0 = r22;
        r3 = r0.mPhoneBase;	 Catch:{ all -> 0x0876 }
        r3.notifyCellInfo(r8);	 Catch:{ all -> 0x0876 }
        monitor-exit(r9);	 Catch:{ all -> 0x0876 }
        return;
    L_0x06ea:
        r0 = r22;
        r3 = r0.mPhone;
        r0 = r22;
        r4 = r0.mNewSS;
        r4 = r4.getOperatorNumeric();
        r3 = r3.isMccMncMarkedAsRoaming(r4);
        if (r3 != 0) goto L_0x070e;
    L_0x06fc:
        r0 = r22;
        r3 = r0.mPhone;
        r0 = r22;
        r4 = r0.mNewSS;
        r4 = r4.getSystemId();
        r3 = r3.isSidMarkedAsRoaming(r4);
        if (r3 == 0) goto L_0x003b;
    L_0x070e:
        r3 = "pollStateDone: override - marked as roaming.";
        r0 = r22;
        r0.log(r3);
        r0 = r22;
        r3 = r0.mNewSS;
        r4 = 1;
        r3.setRoaming(r4);
        r0 = r22;
        r3 = r0.mNewSS;
        r4 = 0;
        r3.setCdmaEriIconIndex(r4);
        r0 = r22;
        r3 = r0.mNewSS;
        r4 = 0;
        r3.setCdmaEriIconMode(r4);
        goto L_0x003b;
    L_0x072f:
        r3 = 0;
        r4 = r3;
        goto L_0x009c;
    L_0x0733:
        r3 = 0;
        r5 = r3;
        goto L_0x00b2;
    L_0x0737:
        r3 = 0;
        r6 = r3;
        goto L_0x00c8;
    L_0x073b:
        r3 = 0;
        r7 = r3;
        goto L_0x00de;
    L_0x073f:
        r3 = 0;
        r8 = r3;
        goto L_0x00f2;
    L_0x0743:
        r3 = 0;
        r9 = r3;
        goto L_0x0106;
    L_0x0747:
        r3 = 0;
        r10 = r3;
        goto L_0x011a;
    L_0x074b:
        r3 = 0;
        r11 = r3;
        goto L_0x012a;
    L_0x074f:
        r3 = 0;
        r12 = r3;
        goto L_0x0140;
    L_0x0753:
        r3 = 0;
        r13 = r3;
        goto L_0x0156;
    L_0x0757:
        r3 = 0;
        r14 = r3;
        goto L_0x016c;
    L_0x075b:
        r3 = 0;
        r15 = r3;
        goto L_0x0182;
    L_0x075f:
        r3 = 0;
        r16 = r3;
        goto L_0x0197;
    L_0x0764:
        r3 = 0;
        r17 = r3;
        goto L_0x01e0;
    L_0x0769:
        r3 = 0;
        goto L_0x021d;
    L_0x076c:
        r18 = 0;
        goto L_0x0243;
    L_0x0770:
        r19 = 0;
        goto L_0x025f;
    L_0x0774:
        r0 = r22;
        r3 = r0.mUiccController;
        r9 = r22.getPhoneId();
        r3 = r3.getUiccCard(r9);
        r3 = r3.getOperatorBrandOverride();
        if (r3 == 0) goto L_0x0789;
    L_0x0786:
        r3 = 1;
        goto L_0x0450;
    L_0x0789:
        r3 = 0;
        goto L_0x0450;
    L_0x078c:
        r0 = r22;
        r9 = r0.mSS;
        r9 = r9.getVoiceRegState();
        r11 = 3;
        if (r9 != r11) goto L_0x07b5;
    L_0x0797:
        r0 = r22;
        r3 = r0.mIccRecords;
        if (r3 == 0) goto L_0x07b3;
    L_0x079d:
        r0 = r22;
        r3 = r0.mIccRecords;
        r3 = r3.getServiceProviderName();
    L_0x07a5:
        r9 = android.text.TextUtils.isEmpty(r3);
        if (r9 == 0) goto L_0x04b9;
    L_0x07ab:
        r3 = "ro.cdma.home.operator.alpha";
        r3 = android.os.SystemProperties.get(r3);
        goto L_0x04b9;
    L_0x07b3:
        r3 = 0;
        goto L_0x07a5;
    L_0x07b5:
        r0 = r22;
        r9 = r0.mSS;
        r9 = r9.getDataRegState();
        if (r9 == 0) goto L_0x04b9;
    L_0x07bf:
        r0 = r22;
        r3 = r0.mPhone;
        r3 = r3.getContext();
        r9 = 17039620; // 0x1040104 float:2.42453E-38 double:8.418691E-317;
        r3 = r3.getText(r9);
        r3 = r3.toString();
        goto L_0x04b9;
    L_0x07d4:
        r9 = "";
        r11 = 0;
        r20 = 3;
        r0 = r20;
        r3.substring(r11, r0);
        r11 = 0;
        r20 = 3;
        r0 = r20;
        r11 = r3.substring(r11, r0);	 Catch:{ NumberFormatException -> 0x0821, StringIndexOutOfBoundsException -> 0x083d }
        r11 = java.lang.Integer.parseInt(r11);	 Catch:{ NumberFormatException -> 0x0821, StringIndexOutOfBoundsException -> 0x083d }
        r9 = com.android.internal.telephony.MccTable.countryCodeForMcc(r11);	 Catch:{ NumberFormatException -> 0x0821, StringIndexOutOfBoundsException -> 0x083d }
    L_0x07ef:
        r0 = r22;
        r11 = r0.mPhone;
        r20 = "gsm.operator.iso-country";
        r0 = r20;
        r11.setSystemProperty(r0, r9);
        r11 = 1;
        r0 = r22;
        r0.mGotCountryCode = r11;
        r0 = r22;
        r0.setOperatorIdd(r3);
        r0 = r22;
        r11 = r0.mPhone;
        r0 = r22;
        r0 = r0.mNeedFixZone;
        r20 = r0;
        r0 = r22;
        r1 = r18;
        r2 = r20;
        r3 = r0.shouldFixTimeZoneNow(r11, r3, r1, r2);
        if (r3 == 0) goto L_0x0594;
    L_0x081a:
        r0 = r22;
        r0.fixTimeZone(r9);
        goto L_0x0594;
    L_0x0821:
        r11 = move-exception;
        r20 = new java.lang.StringBuilder;
        r20.<init>();
        r21 = "countryCodeForMcc error";
        r20 = r20.append(r21);
        r0 = r20;
        r11 = r0.append(r11);
        r11 = r11.toString();
        r0 = r22;
        r0.loge(r11);
        goto L_0x07ef;
    L_0x083d:
        r11 = move-exception;
        r20 = new java.lang.StringBuilder;
        r20.<init>();
        r21 = "countryCodeForMcc error";
        r20 = r20.append(r21);
        r0 = r20;
        r11 = r0.append(r11);
        r11 = r11.toString();
        r0 = r22;
        r0.loge(r11);
        goto L_0x07ef;
    L_0x0859:
        r3 = "false";
        goto L_0x05ae;
    L_0x085d:
        r0 = r22;
        r3 = r0.mSS;
        r0 = r22;
        r0.processIwlanToWwanTransition(r3);
        r19 = 1;
        r3 = 0;
        r0 = r22;
        r0.mIwlanRatAvailable = r3;
        goto L_0x0618;
    L_0x086f:
        r6 = 0;
        r7 = r6;
        goto L_0x067a;
    L_0x0873:
        r6 = 0;
        goto L_0x068e;
    L_0x0876:
        r3 = move-exception;
        monitor-exit(r9);	 Catch:{ all -> 0x0876 }
        throw r3;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.cdma.CdmaLteServiceStateTracker.pollStateDone():void");
    }

    public void powerOffRadioSafely(DcTrackerBase dcTrackerBase) {
        synchronized (this) {
            if (!this.mPendingRadioPowerOffAfterDataOff) {
                int defaultDataSubId = SubscriptionManager.getDefaultDataSubId();
                if (!dcTrackerBase.isDisconnected() || (defaultDataSubId != this.mPhone.getSubId() && ((defaultDataSubId == this.mPhone.getSubId() || !ProxyController.getInstance().isDataDisconnected(defaultDataSubId)) && SubscriptionManager.isValidSubscriptionId(defaultDataSubId)))) {
                    dcTrackerBase.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    if (!(defaultDataSubId == this.mPhone.getSubId() || ProxyController.getInstance().isDataDisconnected(defaultDataSubId))) {
                        log("Data is active on DDS.  Wait for all data disconnect");
                        ProxyController.getInstance().registerForAllDataDisconnected(defaultDataSubId, this, 1001, null);
                        this.mPendingRadioPowerOffAfterDataOff = true;
                    }
                    Message obtain = Message.obtain(this);
                    obtain.what = 38;
                    int i = this.mPendingRadioPowerOffAfterDataOffTag + 1;
                    this.mPendingRadioPowerOffAfterDataOffTag = i;
                    obtain.arg1 = i;
                    if (sendMessageDelayed(obtain, 30000)) {
                        log("Wait upto 30s for data to disconnect, then turn off radio.");
                        this.mPendingRadioPowerOffAfterDataOff = true;
                    } else {
                        log("Cannot send delayed Msg, turn off radio right away.");
                        hangupAndPowerOff();
                        this.mPendingRadioPowerOffAfterDataOff = false;
                    }
                } else {
                    dcTrackerBase.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    log("Data disconnected, turn off radio right away.");
                    hangupAndPowerOff();
                }
            }
        }
    }

    /* Access modifiers changed, original: protected */
    public void updateCdmaSubscription() {
        this.mCi.getCDMASubscription(obtainMessage(34));
    }

    /* Access modifiers changed, original: protected */
    public void updatePhoneObject() {
        int rilVoiceRadioTechnology = this.mSS.getRilVoiceRadioTechnology();
        if (this.mPhone.getContext().getResources().getBoolean(17957009)) {
            int integer = this.mPhoneBase.getContext().getResources().getInteger(17694816);
            Rlog.d("CdmaSST", "updatePhoneObject: volteReplacementRat=" + integer);
            if (isRatLte(rilVoiceRadioTechnology) && integer == 0) {
                rilVoiceRadioTechnology = 6;
            }
            this.mPhoneBase.updatePhoneObject(rilVoiceRadioTechnology);
        }
    }
}
