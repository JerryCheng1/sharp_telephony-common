package com.android.internal.telephony.gsm;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.TimeUtils;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.ProxyController;
import com.android.internal.telephony.RestrictedState;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelBrand;
import com.android.internal.telephony.cdma.CallFailCause;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.SpnOverride;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import jp.co.sharp.android.internal.telephony.OemTelephonyIntents;
import jp.co.sharp.telephony.OemCdmaTelephonyManager;

/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public final class GsmServiceStateTracker extends ServiceStateTracker {
    static final int CS_DISABLED = 1004;
    static final int CS_EMERGENCY_ENABLED = 1006;
    static final int CS_ENABLED = 1003;
    static final int CS_NORMAL_ENABLED = 1005;
    static final int CS_NOTIFICATION = 999;
    private static final int EVENT_ALL_DATA_DISCONNECTED = 1001;
    static final String LOG_TAG = "GsmSST";
    static final int PS_DISABLED = 1002;
    static final int PS_ENABLED = 1001;
    static final int PS_NOTIFICATION = 888;
    static final boolean VDBG = false;
    private static final String WAKELOCK_TAG = "ServiceStateTracker";
    private int locationUpdatingContext;
    private ContentResolver mCr;
    private Notification mNotification;
    private GSMPhone mPhone;
    int mPreferredNetworkType;
    long mSavedAtTime;
    long mSavedTime;
    String mSavedTimeZone;
    private PowerManager.WakeLock mWakeLock;
    private boolean mZoneDst;
    private int mZoneOffset;
    private long mZoneTime;
    private int mMaxDataCalls = 1;
    private int mNewMaxDataCalls = 1;
    private int mReasonDataDenied = -1;
    private int mNewReasonDataDenied = -1;
    private boolean mGsmRoaming = false;
    private boolean mDataRoaming = false;
    private boolean mEmergencyOnly = false;
    private boolean mNeedFixZoneAfterNitz = false;
    private boolean mGotCountryCode = false;
    private boolean mNitzUpdatedTime = false;
    private boolean mStartedGprsRegCheck = false;
    private boolean mReportedGprsNoReg = false;
    private boolean mIsSmsMemRptSent = false;
    private String mCurSpn = null;
    private String mCurPlmn = null;
    private boolean mCurShowPlmn = false;
    private boolean mCurShowSpn = false;
    private int mLAC = -1;
    private int mCid = -1;
    private int mDataLAC = -1;
    private int mDataCid = -1;
    int mLteActiveBand = Integer.MAX_VALUE;
    int mLteBand = -1;
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.gsm.GsmServiceStateTracker.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (!GsmServiceStateTracker.this.mPhone.mIsTheCurrentActivePhone) {
                Rlog.e(GsmServiceStateTracker.LOG_TAG, "Received Intent " + intent + " while being destroyed. Ignoring.");
            } else if (intent.getAction().equals("android.intent.action.LOCALE_CHANGED")) {
                GsmServiceStateTracker.this.updateSpnDisplay();
            } else if (intent.getAction().equals("android.intent.action.ACTION_RADIO_OFF")) {
                GsmServiceStateTracker.this.mAlarmSwitch = false;
                GsmServiceStateTracker.this.powerOffRadioSafely(GsmServiceStateTracker.this.mPhone.mDcTracker);
            }
        }
    };
    private ContentObserver mAutoTimeObserver = new ContentObserver(new Handler()) { // from class: com.android.internal.telephony.gsm.GsmServiceStateTracker.2
        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange) {
            Rlog.i("GsmServiceStateTracker", "Auto time state changed");
            GsmServiceStateTracker.this.revertToNitzTime();
        }
    };
    private ContentObserver mAutoTimeZoneObserver = new ContentObserver(new Handler()) { // from class: com.android.internal.telephony.gsm.GsmServiceStateTracker.3
        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange) {
            Rlog.i("GsmServiceStateTracker", "Auto time zone state changed");
            GsmServiceStateTracker.this.revertToNitzTimeZone();
        }
    };
    GsmCellLocation mCellLoc = new GsmCellLocation();
    GsmCellLocation mNewCellLoc = new GsmCellLocation();
    SpnOverride mSpnOverride = new SpnOverride();

    /*  JADX ERROR: Failed to decode insn: 0x0002: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0002: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x000F: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x000F: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0013: UNKNOWN(0x41E5), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0013: UNKNOWN(0x41E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0014: UNKNOWN(0x00EC), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0014: UNKNOWN(0x00EC)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0015: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0015: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0019: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0019: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x001D: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x001D: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0027: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0027: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x002B: UNKNOWN(0x41E5), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x002B: UNKNOWN(0x41E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0031: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0031: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0035: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0035: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x003F: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x003F: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0043: UNKNOWN(0x41E5), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0043: UNKNOWN(0x41E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0049: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0049: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x004D: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x004D: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0057: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0057: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x005B: UNKNOWN(0x41E5), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x005B: UNKNOWN(0x41E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0061: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0061: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0065: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0065: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x006F: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x006F: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0073: UNKNOWN(0x41E5), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0073: UNKNOWN(0x41E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0074: UNKNOWN(0x00E4), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0074: UNKNOWN(0x00E4)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0075: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0075: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0079: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0079: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x007D: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x007D: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0087: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0087: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x008B: UNKNOWN(0x41E3), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x008B: UNKNOWN(0x41E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0091: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0091: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0095: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0095: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x009F: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x009F: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00A3: UNKNOWN(0x41E3), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00A3: UNKNOWN(0x41E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00A9: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00A9: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00AD: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00AD: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00B7: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00B7: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00BB: UNKNOWN(0x41E3), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00BB: UNKNOWN(0x41E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00C1: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00C1: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00C5: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00C5: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00CF: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00CF: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00D3: UNKNOWN(0x41E3), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00D3: UNKNOWN(0x41E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00D4: UNKNOWN(0x0140), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00D4: UNKNOWN(0x0140)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00D5: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00D5: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00D9: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00D9: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00DD: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00DD: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00E7: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00E7: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00EB: UNKNOWN(0x41E3), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00EB: UNKNOWN(0x41E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00F1: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00F1: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00F5: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00F5: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00FF: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00FF: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0105: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0105: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0109: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0109: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x010D: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x010D: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0117: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0117: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x011D: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x011D: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0121: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0121: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0125: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0125: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x012F: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x012F: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0135: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0135: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0139: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0139: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x013D: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x013D: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0147: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0147: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x014D: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x014D: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0151: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0151: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0155: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0155: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0158: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0158: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0162: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0162: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0166: UNKNOWN(0x41E3), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0166: UNKNOWN(0x41E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x016C: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x016C: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0170: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0170: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x017A: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x017A: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0180: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0180: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0184: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0184: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0188: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0188: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0192: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0192: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0196: UNKNOWN(0x42E4), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0196: UNKNOWN(0x42E4)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0198: UNKNOWN(0x30E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0198: UNKNOWN(0x30E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x019C: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x019C: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01A0: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01A0: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01AA: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01AA: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01B0: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01B0: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01B4: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01B4: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01B8: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01B8: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01C2: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01C2: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01C8: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01C8: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01CC: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01CC: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01D0: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01D0: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01DA: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01DA: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01DE: UNKNOWN(0x41E5), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01DE: UNKNOWN(0x41E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01DF: UNKNOWN(0x00F0), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01DF: UNKNOWN(0x00F0)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01E0: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01E0: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01E4: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01E4: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01E8: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01E8: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01F2: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01F2: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01F6: UNKNOWN(0x42E4), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01F6: UNKNOWN(0x42E4)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01FC: UNKNOWN(0x10E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01FC: UNKNOWN(0x10E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0200: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0200: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x020A: UNKNOWN(0x20E9), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x020A: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x020E: UNKNOWN(0x42E4), method: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x020E: UNKNOWN(0x42E4)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:93)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    @Override // com.android.internal.telephony.ServiceStateTracker
    public void dump(java.io.FileDescriptor r5, java.io.PrintWriter r6, java.lang.String[] r7) {
        /*
            Method dump skipped, instructions count: 735
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.GsmServiceStateTracker.dump(java.io.FileDescriptor, java.io.PrintWriter, java.lang.String[]):void");
    }

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public GsmServiceStateTracker(GSMPhone phone) {
        super(phone, phone.mCi, new CellInfoGsm());
        boolean z = false;
        this.mPhone = phone;
        this.mWakeLock = ((PowerManager) phone.getContext().getSystemService("power")).newWakeLock(1, WAKELOCK_TAG);
        this.mCi.registerForAvailable(this, 13, null);
        this.mCi.registerForRadioStateChanged(this, 1, null);
        this.mCi.registerForVoiceNetworkStateChanged(this, 2, null);
        this.mCi.setOnNITZTime(this, 11, null);
        this.mCi.setOnRestrictedStateChanged(this, 23, null);
        if (TelBrand.IS_SBM) {
            this.mCi.setOnSpeechCodec(this, 10003, null);
        }
        if (TelBrand.IS_SBM) {
            this.mCi.setOnOemSignalStrengthUpdate(this, 10000, null);
        }
        this.mCi.setOnLteBandInfo(this, 10002, null);
        this.mDesiredPowerState = Settings.Global.getInt(phone.getContext().getContentResolver(), "airplane_mode_on", 0) <= 0 ? true : z;
        this.mCr = phone.getContext().getContentResolver();
        this.mCr.registerContentObserver(Settings.Global.getUriFor("auto_time"), true, this.mAutoTimeObserver);
        this.mCr.registerContentObserver(Settings.Global.getUriFor("auto_time_zone"), true, this.mAutoTimeZoneObserver);
        setSignalStrengthDefaultValues();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.LOCALE_CHANGED");
        phone.getContext().registerReceiver(this.mIntentReceiver, filter);
        IntentFilter filter2 = new IntentFilter();
        Context context = phone.getContext();
        filter2.addAction("android.intent.action.ACTION_RADIO_OFF");
        context.registerReceiver(this.mIntentReceiver, filter2);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public void dispose() {
        checkCorrectThread();
        log("ServiceStateTracker dispose");
        this.mCi.unregisterForAvailable(this);
        this.mCi.unregisterForRadioStateChanged(this);
        this.mCi.unregisterForVoiceNetworkStateChanged(this);
        if (this.mUiccApplcation != null) {
            this.mUiccApplcation.unregisterForReady(this);
        }
        if (this.mIccRecords != null) {
            this.mIccRecords.unregisterForRecordsLoaded(this);
        }
        this.mCi.unSetOnRestrictedStateChanged(this);
        this.mCi.unSetOnNITZTime(this);
        if (TelBrand.IS_SBM) {
            this.mCi.unSetOnSpeechCodec(this);
        }
        if (TelBrand.IS_SBM) {
            this.mCi.unSetOnOemSignalStrengthUpdate(this);
        }
        this.mCi.unSetOnLteBandInfo(this);
        this.mCr.unregisterContentObserver(this.mAutoTimeObserver);
        this.mCr.unregisterContentObserver(this.mAutoTimeZoneObserver);
        this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        super.dispose();
    }

    protected void finalize() {
        log("finalize");
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected Phone getPhone() {
        return this.mPhone;
    }

    @Override // com.android.internal.telephony.ServiceStateTracker, android.os.Handler
    public void handleMessage(Message msg) {
        if (!this.mPhone.mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        switch (msg.what) {
            case 1:
                setPowerStateToDesired();
                pollState();
                return;
            case 2:
                pollState();
                return;
            case 3:
                if (this.mCi.getRadioState().isOn()) {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (!TelBrand.IS_SBM) {
                        onSignalStrengthResult(ar, true);
                    }
                    queueNextSignalStrengthPoll();
                    return;
                }
                return;
            case 4:
            case 5:
            case 6:
            case 14:
                handlePollStateResult(msg.what, (AsyncResult) msg.obj);
                return;
            case 10:
                this.mCi.getSignalStrength(obtainMessage(3));
                return;
            case 11:
                AsyncResult ar2 = (AsyncResult) msg.obj;
                setTimeFromNITZString((String) ((Object[]) ar2.result)[0], ((Long) ((Object[]) ar2.result)[1]).longValue());
                return;
            case 12:
                if (!TelBrand.IS_SBM) {
                    this.mDontPollSignalStrength = true;
                    onSignalStrengthResult((AsyncResult) msg.obj, true);
                    return;
                }
                return;
            case 13:
                return;
            case 15:
                AsyncResult ar3 = (AsyncResult) msg.obj;
                if (ar3.exception == null) {
                    String[] states = (String[]) ar3.result;
                    int lac = -1;
                    int cid = -1;
                    if (states.length >= 3) {
                        try {
                            if (states[1] != null && states[1].length() > 0) {
                                lac = Integer.parseInt(states[1], 16);
                            }
                            if (states[2] != null && states[2].length() > 0) {
                                cid = Integer.parseInt(states[2], 16);
                            }
                        } catch (NumberFormatException ex) {
                            Rlog.w(LOG_TAG, "error parsing location: " + ex);
                        }
                    }
                    this.mLAC = lac;
                    this.mCid = cid;
                    this.mCellLoc.setLacAndCid(getLacOrDataLac(), getCidOrDataCid());
                }
                this.locationUpdatingContext--;
                if (this.locationUpdatingContext == 0) {
                    this.mPhone.notifyLocationChanged();
                    disableSingleLocationUpdate();
                    return;
                }
                return;
            case 16:
                log("EVENT_SIM_RECORDS_LOADED: what=" + msg.what);
                this.mPhone.notifyOtaspChanged(3);
                updatePhoneObject();
                updateSpnDisplay();
                return;
            case 17:
                if (!this.mPhone.getContext().getResources().getBoolean(17956953)) {
                    this.mPhone.restoreSavedNetworkSelection(null);
                }
                pollState();
                queueNextSignalStrengthPoll();
                return;
            case 18:
                if (((AsyncResult) msg.obj).exception == null) {
                    this.locationUpdatingContext = 0;
                    this.locationUpdatingContext++;
                    this.mCi.getVoiceRegistrationState(obtainMessage(15, null));
                    this.locationUpdatingContext++;
                    this.mCi.getDataRegistrationState(obtainMessage(10001, null));
                    return;
                }
                return;
            case 19:
                AsyncResult ar4 = (AsyncResult) msg.obj;
                if (ar4.exception == null) {
                    this.mPreferredNetworkType = ((int[]) ar4.result)[0];
                } else {
                    this.mPreferredNetworkType = 7;
                }
                this.mCi.setPreferredNetworkType(7, obtainMessage(20, ar4.userObj));
                return;
            case 20:
                this.mCi.setPreferredNetworkType(this.mPreferredNetworkType, obtainMessage(21, ((AsyncResult) msg.obj).userObj));
                return;
            case 21:
                AsyncResult ar5 = (AsyncResult) msg.obj;
                if (ar5.userObj != null) {
                    AsyncResult.forMessage((Message) ar5.userObj).exception = ar5.exception;
                    ((Message) ar5.userObj).sendToTarget();
                    return;
                }
                return;
            case 22:
                if (this.mSS != null && !isGprsConsistent(this.mSS.getDataRegState(), this.mSS.getVoiceRegState())) {
                    GsmCellLocation loc = (GsmCellLocation) this.mPhone.getCellLocation();
                    Object[] objArr = new Object[2];
                    objArr[0] = this.mSS.getOperatorNumeric();
                    objArr[1] = Integer.valueOf(loc != null ? loc.getCid() : -1);
                    EventLog.writeEvent((int) EventLogTags.DATA_NETWORK_REGISTRATION_FAIL, objArr);
                    this.mReportedGprsNoReg = true;
                }
                this.mStartedGprsRegCheck = false;
                return;
            case 23:
                log("EVENT_RESTRICTED_STATE_CHANGED");
                onRestrictedStateChanged((AsyncResult) msg.obj);
                return;
            case OemCdmaTelephonyManager.OEM_RIL_RDE_Data.RDE_NV_CDMA_SO73_ENABLED_I /* 45 */:
                log("EVENT_CHANGE_IMS_STATE:");
                setPowerStateToDesired();
                return;
            case 60:
                if (TelBrand.IS_DCM) {
                    log("EVENT_SEND_SMS_MEMORY_FULL");
                    if (this.mSS.getVoiceRegState() == 0 || this.mSS.getDataRegState() == 0) {
                        log("Either is in service, now send EVENT_SEND_SMS_MEMORY_FULL");
                        setSendSmsMemoryFull();
                        return;
                    }
                    return;
                }
                return;
            case 61:
                if (TelBrand.IS_DCM) {
                    log("EVENT_REPORT_SMS_MEMORY_FULL");
                    AsyncResult ar6 = (AsyncResult) msg.obj;
                    if (ar6.exception != null) {
                        log("handleReportSmsMemoryFullDone, exception:" + ar6.exception);
                        this.mIsSmsMemRptSent = false;
                        return;
                    }
                    log("ReportSmsMemory success!");
                    this.mIsSmsMemRptSent = true;
                    return;
                }
                return;
            case CallFailCause.CDMA_DROP /* 1001 */:
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
            case 10000:
                if (TelBrand.IS_SBM) {
                    this.mDontPollSignalStrength = true;
                    onOemSignalStrengthResult((AsyncResult) msg.obj);
                    return;
                }
                return;
            case 10001:
                AsyncResult ar7 = (AsyncResult) msg.obj;
                if (ar7.exception == null) {
                    String[] dataStates = (String[]) ar7.result;
                    int dataLac = -1;
                    int dataCid = -1;
                    if (dataStates.length >= 3) {
                        try {
                            if (dataStates[1] != null && dataStates[1].length() > 0) {
                                dataLac = Integer.parseInt(dataStates[1], 16);
                            }
                            if (dataStates[2] != null && dataStates[2].length() > 0) {
                                dataCid = Integer.parseInt(dataStates[2], 16);
                            }
                        } catch (NumberFormatException ex2) {
                            Rlog.w(LOG_TAG, "error parsing location: " + ex2);
                        }
                    }
                    this.mDataLAC = dataLac;
                    this.mDataCid = dataCid;
                    this.mCellLoc.setLacAndCid(getLacOrDataLac(), getCidOrDataCid());
                }
                this.locationUpdatingContext--;
                if (this.locationUpdatingContext == 0) {
                    this.mPhone.notifyLocationChanged();
                    disableSingleLocationUpdate();
                    return;
                }
                return;
            case 10002:
                onLteBandInfo((AsyncResult) msg.obj);
                return;
            case 10003:
                if (TelBrand.IS_SBM) {
                    onSpeechCodec((AsyncResult) msg.obj);
                    return;
                }
                return;
            default:
                super.handleMessage(msg);
                return;
        }
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void setPowerStateToDesired() {
        log("mDeviceShuttingDown = " + this.mDeviceShuttingDown);
        log("mDesiredPowerState = " + this.mDesiredPowerState);
        log("getRadioState = " + this.mCi.getRadioState());
        log("mPowerOffDelayNeed = " + this.mPowerOffDelayNeed);
        log("mAlarmSwitch = " + this.mAlarmSwitch);
        if (this.mAlarmSwitch) {
            log("mAlarmSwitch == true");
            ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.mRadioOffIntent);
            this.mAlarmSwitch = false;
        }
        if (this.mDesiredPowerState && this.mCi.getRadioState() == CommandsInterface.RadioState.RADIO_OFF) {
            if (SystemProperties.getBoolean("persist.radio.init_dun_profile", false)) {
                this.mPhone.resetDunProfiles();
                SystemProperties.set("persist.radio.init_dun_profile", "false");
            }
            this.mCi.setRadioPower(true, null);
        } else if (this.mDesiredPowerState || !this.mCi.getRadioState().isOn()) {
            if (this.mDeviceShuttingDown && this.mCi.getRadioState().isAvailable()) {
                this.mCi.requestShutdown(null);
            }
        } else if (!this.mPowerOffDelayNeed) {
            powerOffRadioSafely(this.mPhone.mDcTracker);
        } else if (!this.mImsRegistrationOnOff || this.mAlarmSwitch) {
            powerOffRadioSafely(this.mPhone.mDcTracker);
        } else {
            log("mImsRegistrationOnOff == true");
            Context context = this.mPhone.getContext();
            this.mRadioOffIntent = PendingIntent.getBroadcast(context, 0, new Intent("android.intent.action.ACTION_RADIO_OFF"), 0);
            this.mAlarmSwitch = true;
            log("Alarm setting");
            ((AlarmManager) context.getSystemService("alarm")).set(2, SystemClock.elapsedRealtime() + 3000, this.mRadioOffIntent);
        }
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void hangupAndPowerOff() {
        if (this.mPhone.isInCall()) {
            this.mPhone.mCT.mRingingCall.hangupIfAlive();
            this.mPhone.mCT.mBackgroundCall.hangupIfAlive();
            this.mPhone.mCT.mForegroundCall.hangupIfAlive();
        }
        this.mCi.setRadioPower(false, null);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void updateSpnDisplay() {
        boolean showPlmn;
        String plmn;
        IccRecords iccRecords = this.mIccRecords;
        int rule = iccRecords != null ? iccRecords.getDisplayRule(this.mSS.getOperatorNumeric()) : 0;
        int combinedRegState = getCombinedRegState();
        if (combinedRegState == 1 || combinedRegState == 2) {
            showPlmn = true;
            if (this.mEmergencyOnly) {
                plmn = Resources.getSystem().getText(17040368).toString();
            } else {
                plmn = Resources.getSystem().getText(17040344).toString();
            }
            log("updateSpnDisplay: radio is on but out of service, set plmn='" + plmn + "'");
        } else if (combinedRegState == 0) {
            plmn = this.mSS.getOperatorAlphaLong();
            showPlmn = !TextUtils.isEmpty(plmn) && (rule & 2) == 2;
        } else {
            showPlmn = true;
            plmn = Resources.getSystem().getText(17040344).toString();
            log("updateSpnDisplay: radio is off w/ showPlmn=true plmn=" + plmn);
        }
        String spn = iccRecords != null ? iccRecords.getServiceProviderName() : "";
        boolean showSpn = !TextUtils.isEmpty(spn) && (rule & 1) == 1;
        if (this.mSS.getVoiceRegState() == 3 || (showPlmn && TextUtils.equals(spn, plmn))) {
            spn = null;
            showSpn = false;
        }
        if (showPlmn != this.mCurShowPlmn || showSpn != this.mCurShowSpn || !TextUtils.equals(spn, this.mCurSpn) || !TextUtils.equals(plmn, this.mCurPlmn)) {
            log(String.format("updateSpnDisplay: changed sending intent rule=" + rule + " showPlmn='%b' plmn='%s' showSpn='%b' spn='%s'", Boolean.valueOf(showPlmn), plmn, Boolean.valueOf(showSpn), spn));
            Intent intent = new Intent("android.provider.Telephony.SPN_STRINGS_UPDATED");
            intent.addFlags(536870912);
            intent.putExtra("showSpn", showSpn);
            intent.putExtra("spn", spn);
            intent.putExtra("showPlmn", showPlmn);
            intent.putExtra(Telephony.CellBroadcasts.PLMN, plmn);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhone.getPhoneId());
            this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
        this.mCurShowSpn = showSpn;
        this.mCurShowPlmn = showPlmn;
        this.mCurSpn = spn;
        this.mCurPlmn = plmn;
    }

    private int getCombinedRegState() {
        int regState = this.mSS.getVoiceRegState();
        int dataRegState = this.mSS.getDataRegState();
        if (regState != 1 || dataRegState != 0) {
            return regState;
        }
        log("getCombinedRegState: return STATE_IN_SERVICE as Data is in service");
        return dataRegState;
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void handlePollStateResult(int what, AsyncResult ar) {
        String strOperatorLong;
        if (ar.userObj == this.mPollingContext) {
            if (ar.exception != null) {
                CommandException.Error err = null;
                if (ar.exception instanceof CommandException) {
                    err = ((CommandException) ar.exception).getCommandError();
                }
                if (err == CommandException.Error.RADIO_NOT_AVAILABLE) {
                    cancelPollState();
                    return;
                } else if (err != CommandException.Error.OP_NOT_ALLOWED_BEFORE_REG_NW) {
                    loge("RIL implementation has returned an error where it must succeed" + ar.exception);
                }
            } else {
                try {
                    switch (what) {
                        case 4:
                            String[] states = (String[]) ar.result;
                            int lac = -1;
                            int cid = -1;
                            int type = 0;
                            int regState = 4;
                            int psc = -1;
                            int cssIndicator = 0;
                            if (states.length > 0) {
                                try {
                                    regState = Integer.parseInt(states[0]);
                                    if (states.length >= 3) {
                                        if (states[1] != null && states[1].length() > 0) {
                                            lac = Integer.parseInt(states[1], 16);
                                        }
                                        if (states[2] != null && states[2].length() > 0) {
                                            cid = Integer.parseInt(states[2], 16);
                                        }
                                        if (states.length >= 4 && states[3] != null) {
                                            type = Integer.parseInt(states[3]);
                                        }
                                    }
                                    if (states.length >= 7 && states[7] != null) {
                                        cssIndicator = Integer.parseInt(states[7]);
                                    }
                                    if (states.length > 14 && states[14] != null && states[14].length() > 0) {
                                        psc = Integer.parseInt(states[14], 16);
                                    }
                                } catch (NumberFormatException ex) {
                                    loge("error parsing RegistrationState: " + ex);
                                }
                            }
                            this.mGsmRoaming = regCodeIsRoaming(regState);
                            this.mNewSS.setState(regCodeToServiceState(regState));
                            this.mNewSS.setRilVoiceRadioTechnology(type);
                            this.mNewSS.setCssIndicator(cssIndicator);
                            if ((regState == 3 || regState == 13) && states.length >= 14) {
                                try {
                                    if (Integer.parseInt(states[13]) == 10) {
                                        log(" Posting Managed roaming intent sub = " + this.mPhone.getSubId());
                                        Intent intent = new Intent("codeaurora.intent.action.ACTION_MANAGED_ROAMING_IND");
                                        intent.putExtra("subscription", this.mPhone.getSubId());
                                        this.mPhone.getContext().sendBroadcast(intent);
                                    }
                                } catch (NumberFormatException ex2) {
                                    loge("error parsing regCode: " + ex2);
                                }
                            }
                            boolean isVoiceCapable = this.mPhoneBase.getContext().getResources().getBoolean(17956947);
                            if ((regState == 13 || regState == 10 || regState == 12 || regState == 14) && isVoiceCapable) {
                                this.mEmergencyOnly = true;
                            } else {
                                this.mEmergencyOnly = false;
                            }
                            this.mLAC = lac;
                            this.mCid = cid;
                            this.mNewCellLoc.setLacAndCid(getLacOrDataLac(), getCidOrDataCid());
                            this.mNewCellLoc.setPsc(psc);
                            break;
                        case 5:
                            String[] states2 = (String[]) ar.result;
                            int type2 = 0;
                            int dataLac = -1;
                            int dataCid = -1;
                            int regState2 = 4;
                            this.mNewReasonDataDenied = -1;
                            this.mNewMaxDataCalls = 1;
                            if (states2.length > 0) {
                                try {
                                    regState2 = Integer.parseInt(states2[0]);
                                    if (states2.length >= 4 && states2[3] != null) {
                                        type2 = Integer.parseInt(states2[3]);
                                    }
                                    if (states2.length >= 5 && regState2 == 3) {
                                        this.mNewReasonDataDenied = Integer.parseInt(states2[4]);
                                    }
                                    if (states2.length >= 6) {
                                        this.mNewMaxDataCalls = Integer.parseInt(states2[5]);
                                    }
                                    if (states2[1] != null && states2[1].length() > 0) {
                                        dataLac = Integer.parseInt(states2[1], 16);
                                    }
                                    if (states2[2] != null && states2[2].length() > 0) {
                                        dataCid = Integer.parseInt(states2[2], 16);
                                    }
                                } catch (NumberFormatException ex3) {
                                    loge("error parsing GprsRegistrationState: " + ex3);
                                }
                            }
                            int dataRegState = regCodeToServiceState(regState2);
                            this.mNewSS.setDataRegState(dataRegState);
                            this.mDataRoaming = regCodeIsRoaming(regState2);
                            this.mNewSS.setRilDataRadioTechnology(type2);
                            log("handlPollStateResultMessage: GsmSST setDataRegState=" + dataRegState + " regState=" + regState2 + " dataRadioTechnology=" + type2);
                            this.mDataLAC = dataLac;
                            this.mDataCid = dataCid;
                            this.mNewCellLoc.setLacAndCid(getLacOrDataLac(), getCidOrDataCid());
                            break;
                        case 6:
                            String[] opNames = (String[]) ar.result;
                            if (opNames != null && opNames.length >= 3) {
                                String brandOverride = this.mUiccController.getUiccCard(getPhoneId()) != null ? this.mUiccController.getUiccCard(getPhoneId()).getOperatorBrandOverride() : null;
                                if (brandOverride == null) {
                                    if (this.mSpnOverride.containsCarrier(opNames[2])) {
                                        log("EVENT_POLL_STATE_OPERATOR: use spnOverride");
                                        strOperatorLong = this.mSpnOverride.getSpn(opNames[2]);
                                    } else {
                                        log("EVENT_POLL_STATE_OPERATOR: use value from ril");
                                        strOperatorLong = opNames[0];
                                    }
                                    log("EVENT_POLL_STATE_OPERATOR: " + strOperatorLong);
                                    this.mNewSS.setOperatorName(strOperatorLong, opNames[1], opNames[2]);
                                    break;
                                } else {
                                    log("EVENT_POLL_STATE_OPERATOR: use brandOverride=" + brandOverride);
                                    this.mNewSS.setOperatorName(brandOverride, brandOverride, opNames[2]);
                                    break;
                                }
                            }
                            break;
                        case 14:
                            int[] ints = (int[]) ar.result;
                            this.mNewSS.setIsManualSelection(ints[0] == 1);
                            if (ints[0] == 1 && !this.mPhone.isManualNetSelAllowed()) {
                                this.mPhone.setNetworkSelectionModeAutomatic(null);
                                log(" Forcing Automatic Network Selection, manual selection is not allowed");
                                break;
                            }
                            break;
                    }
                } catch (RuntimeException ex4) {
                    loge("Exception while polling service state. Probably malformed RIL response." + ex4);
                }
            }
            int[] iArr = this.mPollingContext;
            iArr[0] = iArr[0] - 1;
            if (this.mPollingContext[0] == 0) {
                boolean roaming = this.mGsmRoaming || this.mDataRoaming;
                if (this.mGsmRoaming && !isOperatorConsideredRoaming(this.mNewSS) && (isSameNamedOperators(this.mNewSS) || isOperatorConsideredNonRoaming(this.mNewSS))) {
                    roaming = false;
                }
                if (this.mPhone.isMccMncMarkedAsNonRoaming(this.mNewSS.getOperatorNumeric())) {
                    roaming = false;
                } else if (this.mPhone.isMccMncMarkedAsRoaming(this.mNewSS.getOperatorNumeric())) {
                    roaming = true;
                }
                this.mNewSS.setVoiceRoaming(roaming);
                this.mNewSS.setDataRoaming(roaming);
                this.mNewSS.setEmergencyOnly(this.mEmergencyOnly);
                pollStateDone();
            }
        }
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void setRoamingType(ServiceState currentServiceState) {
        boolean isVoiceInService;
        if (currentServiceState.getVoiceRegState() == 0) {
            isVoiceInService = true;
        } else {
            isVoiceInService = false;
        }
        if (isVoiceInService) {
            if (!currentServiceState.getVoiceRoaming()) {
                currentServiceState.setVoiceRoamingType(0);
            } else if (inSameCountry(currentServiceState.getVoiceOperatorNumeric())) {
                currentServiceState.setVoiceRoamingType(2);
            } else {
                currentServiceState.setVoiceRoamingType(3);
            }
        }
        boolean isDataInService = currentServiceState.getDataRegState() == 0;
        int dataRegType = currentServiceState.getRilDataRadioTechnology();
        if (!isDataInService) {
            return;
        }
        if (!currentServiceState.getDataRoaming()) {
            currentServiceState.setDataRoamingType(0);
        } else if (!ServiceState.isGsm(dataRegType)) {
            currentServiceState.setDataRoamingType(1);
        } else if (isVoiceInService) {
            currentServiceState.setDataRoamingType(currentServiceState.getVoiceRoamingType());
        } else {
            currentServiceState.setDataRoamingType(1);
        }
    }

    private void setSignalStrengthDefaultValues() {
        this.mSignalStrength = new SignalStrength(true);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public void pollState() {
        this.mPollingContext = new int[1];
        this.mPollingContext[0] = 0;
        switch (this.mCi.getRadioState()) {
            case RADIO_UNAVAILABLE:
                this.mNewSS.setStateOutOfService();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                this.mNitzUpdatedTime = false;
                pollStateDone();
                return;
            case RADIO_OFF:
                this.mNewSS.setStateOff();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                this.mNitzUpdatedTime = false;
                if (!isIwlanFeatureAvailable() || 18 != this.mSS.getRilDataRadioTechnology()) {
                    pollStateDone();
                }
                if (!isIwlanFeatureAvailable()) {
                    return;
                }
                break;
        }
        int[] iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getOperator(obtainMessage(6, this.mPollingContext));
        int[] iArr2 = this.mPollingContext;
        iArr2[0] = iArr2[0] + 1;
        this.mCi.getDataRegistrationState(obtainMessage(5, this.mPollingContext));
        int[] iArr3 = this.mPollingContext;
        iArr3[0] = iArr3[0] + 1;
        this.mCi.getVoiceRegistrationState(obtainMessage(4, this.mPollingContext));
        int[] iArr4 = this.mPollingContext;
        iArr4[0] = iArr4[0] + 1;
        this.mCi.getNetworkSelectionMode(obtainMessage(14, this.mPollingContext));
    }

    private void pollStateDone() {
        TimeZone zone;
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("telephony.test.forceRoaming", false)) {
            this.mNewSS.setVoiceRoaming(true);
            this.mNewSS.setDataRoaming(true);
        }
        useDataRegStateForDataOnlyDevices();
        resetServiceStateInIwlanMode();
        log("Poll ServiceState done:  oldSS=[" + this.mSS + "] newSS=[" + this.mNewSS + "] oldMaxDataCalls=" + this.mMaxDataCalls + " mNewMaxDataCalls=" + this.mNewMaxDataCalls + " oldReasonDataDenied=" + this.mReasonDataDenied + " mNewReasonDataDenied=" + this.mNewReasonDataDenied);
        boolean hasRegistered = this.mSS.getVoiceRegState() != 0 && this.mNewSS.getVoiceRegState() == 0;
        if (this.mSS.getVoiceRegState() != 0 || this.mNewSS.getVoiceRegState() == 0) {
        }
        boolean hasGprsAttached = this.mSS.getDataRegState() != 0 && this.mNewSS.getDataRegState() == 0;
        boolean hasGprsDetached = this.mSS.getDataRegState() == 0 && this.mNewSS.getDataRegState() != 0;
        boolean hasDataRegStateChanged = this.mSS.getDataRegState() != this.mNewSS.getDataRegState();
        boolean hasVoiceRegStateChanged = this.mSS.getVoiceRegState() != this.mNewSS.getVoiceRegState();
        boolean hasRilVoiceRadioTechnologyChanged = this.mSS.getRilVoiceRadioTechnology() != this.mNewSS.getRilVoiceRadioTechnology();
        boolean hasRilDataRadioTechnologyChanged = this.mSS.getRilDataRadioTechnology() != this.mNewSS.getRilDataRadioTechnology();
        boolean hasChanged = !this.mNewSS.equals(this.mSS);
        boolean hasVoiceRoamingOn = !this.mSS.getVoiceRoaming() && this.mNewSS.getVoiceRoaming();
        boolean hasVoiceRoamingOff = this.mSS.getVoiceRoaming() && !this.mNewSS.getVoiceRoaming();
        boolean hasDataRoamingOn = !this.mSS.getDataRoaming() && this.mNewSS.getDataRoaming();
        boolean hasDataRoamingOff = this.mSS.getDataRoaming() && !this.mNewSS.getDataRoaming();
        boolean hasLocationChanged = !this.mNewCellLoc.equals(this.mCellLoc);
        boolean needNotifyData = this.mSS.getCssIndicator() != this.mNewSS.getCssIndicator();
        if (TelBrand.IS_DCM && !this.mIsSmsMemRptSent && (hasRegistered || hasGprsAttached)) {
            log("Called setSendSmsMemoryFull while voice:" + hasRegistered + " or data:" + hasGprsAttached);
            if (SystemProperties.get("vold.decrypt", "0").equals("trigger_restart_min_framework")) {
                setSendSmsMemoryFull();
            } else {
                sendMessageDelayed(obtainMessage(60), 15000L);
            }
        }
        if (hasVoiceRegStateChanged || hasDataRegStateChanged) {
            EventLog.writeEvent((int) EventLogTags.GSM_SERVICE_STATE_CHANGE, Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState()));
        }
        if (hasRilVoiceRadioTechnologyChanged) {
            int cid = -1;
            GsmCellLocation loc = this.mNewCellLoc;
            if (loc != null) {
                cid = loc.getCid();
            }
            EventLog.writeEvent((int) EventLogTags.GSM_RAT_SWITCHED_NEW, Integer.valueOf(cid), Integer.valueOf(this.mSS.getRilVoiceRadioTechnology()), Integer.valueOf(this.mNewSS.getRilVoiceRadioTechnology()));
            log("RAT switched " + ServiceState.rilRadioTechnologyToString(this.mSS.getRilVoiceRadioTechnology()) + " -> " + ServiceState.rilRadioTechnologyToString(this.mNewSS.getRilVoiceRadioTechnology()) + " at cell " + cid);
        }
        ServiceState tss = this.mSS;
        this.mSS = this.mNewSS;
        this.mNewSS = tss;
        this.mNewSS.setStateOutOfService();
        GsmCellLocation tcl = this.mCellLoc;
        this.mCellLoc = this.mNewCellLoc;
        this.mNewCellLoc = tcl;
        this.mReasonDataDenied = this.mNewReasonDataDenied;
        this.mMaxDataCalls = this.mNewMaxDataCalls;
        if (hasRilVoiceRadioTechnologyChanged) {
            updatePhoneObject();
        }
        if (hasRilDataRadioTechnologyChanged) {
            this.mPhone.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(this.mSS.getRilVoiceRadioTechnology()));
            if (isIwlanFeatureAvailable() && 18 == this.mSS.getRilDataRadioTechnology()) {
                log("pollStateDone: IWLAN enabled");
            }
        }
        if (hasRegistered) {
            this.mNetworkAttachedRegistrants.notifyRegistrants();
            log("pollStateDone: registering current mNitzUpdatedTime=" + this.mNitzUpdatedTime + " changing to false");
            this.mNitzUpdatedTime = false;
        }
        if (hasChanged) {
            updateSpnDisplay();
            this.mPhone.setSystemProperty("gsm.operator.alpha", this.mSS.getOperatorAlphaLong());
            String prevOperatorNumeric = SystemProperties.get("gsm.operator.numeric", "");
            String operatorNumeric = this.mSS.getOperatorNumeric();
            this.mPhone.setSystemProperty("gsm.operator.numeric", operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric, prevOperatorNumeric, this.mPhone.getContext());
            if (operatorNumeric == null) {
                log("operatorNumeric is null");
                this.mPhone.setSystemProperty("gsm.operator.iso-country", "");
                this.mGotCountryCode = false;
                this.mNitzUpdatedTime = false;
            } else {
                String iso = "";
                String mcc = "";
                try {
                    mcc = operatorNumeric.substring(0, 3);
                    iso = MccTable.countryCodeForMcc(Integer.parseInt(mcc));
                } catch (NumberFormatException ex) {
                    loge("pollStateDone: countryCodeForMcc error" + ex);
                } catch (StringIndexOutOfBoundsException ex2) {
                    loge("pollStateDone: countryCodeForMcc error" + ex2);
                }
                this.mPhone.setSystemProperty("gsm.operator.iso-country", iso);
                this.mGotCountryCode = true;
                if (!this.mNitzUpdatedTime && !mcc.equals("000") && !TextUtils.isEmpty(iso) && getAutoTimeZone()) {
                    boolean testOneUniqueOffsetPath = SystemProperties.getBoolean("telephony.test.ignore.nitz", false) && (SystemClock.uptimeMillis() & 1) == 0;
                    ArrayList<TimeZone> uniqueZones = TimeUtils.getTimeZonesWithUniqueOffsets(iso);
                    if (uniqueZones.size() == 1 || testOneUniqueOffsetPath) {
                        TimeZone zone2 = uniqueZones.get(0);
                        log("pollStateDone: no nitz but one TZ for iso-cc=" + iso + " with zone.getID=" + zone2.getID() + " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath);
                        setAndBroadcastNetworkSetTimeZone(zone2.getID());
                    } else {
                        log("pollStateDone: there are " + uniqueZones.size() + " unique offsets for iso-cc='" + iso + " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath + "', do nothing");
                    }
                }
                if (shouldFixTimeZoneNow(this.mPhone, operatorNumeric, prevOperatorNumeric, this.mNeedFixZoneAfterNitz)) {
                    String zoneName = SystemProperties.get("persist.sys.timezone");
                    log("pollStateDone: fix time zone zoneName='" + zoneName + "' mZoneOffset=" + this.mZoneOffset + " mZoneDst=" + this.mZoneDst + " iso-cc='" + iso + "' iso-cc-idx=" + Arrays.binarySearch(GMT_COUNTRY_CODES, iso));
                    if ("".equals(iso) && this.mNeedFixZoneAfterNitz) {
                        zone = getNitzTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime);
                        log("pollStateDone: using NITZ TimeZone");
                    } else if (this.mZoneOffset != 0 || this.mZoneDst || zoneName == null || zoneName.length() <= 0 || Arrays.binarySearch(GMT_COUNTRY_CODES, iso) >= 0) {
                        zone = TimeUtils.getTimeZone(this.mZoneOffset, this.mZoneDst, this.mZoneTime, iso);
                        log("pollStateDone: using getTimeZone(off, dst, time, iso)");
                    } else {
                        zone = TimeZone.getDefault();
                        if (this.mNeedFixZoneAfterNitz) {
                            long ctm = System.currentTimeMillis();
                            long tzOffset = zone.getOffset(ctm);
                            log("pollStateDone: tzOffset=" + tzOffset + " ltod=" + TimeUtils.logTimeOfDay(ctm));
                            if (getAutoTime()) {
                                long adj = ctm - tzOffset;
                                log("pollStateDone: adj ltod=" + TimeUtils.logTimeOfDay(adj));
                                setAndBroadcastNetworkSetTime(adj);
                            } else {
                                this.mSavedTime -= tzOffset;
                            }
                        }
                        log("pollStateDone: using default TimeZone");
                    }
                    this.mNeedFixZoneAfterNitz = false;
                    if (zone != null) {
                        log("pollStateDone: zone != null zone.getID=" + zone.getID());
                        if (getAutoTimeZone()) {
                            setAndBroadcastNetworkSetTimeZone(zone.getID());
                        }
                        saveNitzTimeZone(zone.getID());
                    } else {
                        log("pollStateDone: zone == null");
                    }
                }
            }
            this.mPhone.setSystemProperty("gsm.operator.isroaming", this.mSS.getVoiceRoaming() ? "true" : "false");
            setRoamingType(this.mSS);
            log("Broadcasting ServiceState : " + this.mSS);
            this.mPhone.notifyServiceStateChanged(this.mSS);
        }
        if (hasGprsDetached) {
            this.mDetachedRegistrants.notifyRegistrants();
        }
        if (hasDataRegStateChanged || hasRilDataRadioTechnologyChanged) {
            notifyDataRegStateRilRadioTechnologyChanged();
            if (!isIwlanFeatureAvailable() || 18 != this.mSS.getRilDataRadioTechnology()) {
                processIwlanToWwanTransition(this.mSS);
                needNotifyData = true;
                this.mIwlanRatAvailable = false;
            } else {
                this.mPhone.notifyDataConnection(Phone.REASON_IWLAN_AVAILABLE);
                needNotifyData = false;
                this.mIwlanRatAvailable = true;
            }
        }
        if (needNotifyData) {
            this.mPhone.notifyDataConnection(null);
        }
        if (hasGprsAttached) {
            this.mAttachedRegistrants.notifyRegistrants();
        }
        if (hasVoiceRoamingOn) {
            this.mVoiceRoamingOnRegistrants.notifyRegistrants();
        }
        if (hasVoiceRoamingOff) {
            this.mVoiceRoamingOffRegistrants.notifyRegistrants();
        }
        if (hasDataRoamingOn) {
            this.mDataRoamingOnRegistrants.notifyRegistrants();
        }
        if (hasDataRoamingOff) {
            this.mDataRoamingOffRegistrants.notifyRegistrants();
        }
        if (hasLocationChanged) {
            this.mPhone.notifyLocationChanged();
        }
        if (isGprsConsistent(this.mSS.getDataRegState(), this.mSS.getVoiceRegState())) {
            this.mReportedGprsNoReg = false;
        } else if (!this.mStartedGprsRegCheck && !this.mReportedGprsNoReg) {
            this.mStartedGprsRegCheck = true;
            sendMessageDelayed(obtainMessage(22), Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "gprs_register_check_period_ms", ServiceStateTracker.DEFAULT_GPRS_CHECK_PERIOD_MILLIS));
        }
    }

    private boolean isGprsConsistent(int dataRegState, int voiceRegState) {
        return voiceRegState != 0 || dataRegState == 0;
    }

    private TimeZone getNitzTimeZone(int offset, boolean dst, long when) {
        TimeZone guess = findTimeZone(offset, dst, when);
        if (guess == null) {
            guess = findTimeZone(offset, !dst, when);
        }
        log("getNitzTimeZone returning " + ((Object) (guess == null ? guess : guess.getID())));
        return guess;
    }

    private TimeZone findTimeZone(int offset, boolean dst, long when) {
        int rawOffset = offset;
        if (dst) {
            rawOffset -= 3600000;
        }
        String[] zones = TimeZone.getAvailableIDs(rawOffset);
        Date d = new Date(when);
        for (String zone : zones) {
            TimeZone tz = TimeZone.getTimeZone(zone);
            if (tz.getOffset(when) == offset && tz.inDaylightTime(d) == dst) {
                return tz;
            }
        }
        return null;
    }

    private void queueNextSignalStrengthPoll() {
        if (!this.mDontPollSignalStrength) {
            Message msg = obtainMessage();
            msg.what = 10;
            sendMessageDelayed(msg, 20000L);
        }
    }

    private void onRestrictedStateChanged(AsyncResult ar) {
        boolean z;
        boolean z2 = true;
        RestrictedState newRs = new RestrictedState();
        log("onRestrictedStateChanged: E rs " + this.mRestrictedState);
        if (ar.exception == null) {
            int state = ((int[]) ar.result)[0];
            if ((state & 1) == 0 && (state & 4) == 0) {
                z = false;
            } else {
                z = true;
            }
            newRs.setCsEmergencyRestricted(z);
            if (this.mUiccApplcation != null && this.mUiccApplcation.getState() == IccCardApplicationStatus.AppState.APPSTATE_READY) {
                newRs.setCsNormalRestricted(((state & 2) == 0 && (state & 4) == 0) ? false : true);
                if ((state & 16) == 0) {
                    z2 = false;
                }
                newRs.setPsRestricted(z2);
            }
            log("onRestrictedStateChanged: new rs " + newRs);
            if (!this.mRestrictedState.isPsRestricted() && newRs.isPsRestricted()) {
                this.mPsRestrictEnabledRegistrants.notifyRegistrants();
                setNotification(CallFailCause.CDMA_DROP);
            } else if (this.mRestrictedState.isPsRestricted() && !newRs.isPsRestricted()) {
                this.mPsRestrictDisabledRegistrants.notifyRegistrants();
                setNotification(1002);
            }
            int csstat_new = 0;
            int csstat_prv = 0;
            if (this.mRestrictedState.isCsEmergencyRestricted()) {
                csstat_prv = 0 + 2;
            }
            if (this.mRestrictedState.isCsNormalRestricted()) {
                csstat_prv++;
            }
            if (newRs.isCsEmergencyRestricted()) {
                csstat_new = 0 + 2;
            }
            if (newRs.isCsNormalRestricted()) {
                csstat_new++;
            }
            if (csstat_prv != csstat_new) {
                switch (csstat_new) {
                    case 0:
                        setNotification(1004);
                        SystemProperties.set("gsm.cs_normal_restricted", "false");
                        break;
                    case 1:
                        setNotification(1005);
                        SystemProperties.set("gsm.cs_normal_restricted", "true");
                        break;
                    case 2:
                        setNotification(1006);
                        SystemProperties.set("gsm.cs_normal_restricted", "false");
                        break;
                    case 3:
                        setNotification(1003);
                        SystemProperties.set("gsm.cs_normal_restricted", "true");
                        break;
                }
            }
            this.mRestrictedState = newRs;
        }
        log("onRestrictedStateChanged: X rs " + this.mRestrictedState);
    }

    private int regCodeToServiceState(int code) {
        switch (code) {
            case 0:
            case 2:
            case 3:
            case 4:
            case 10:
            case 12:
            case 13:
            case 14:
                return 1;
            case 1:
                return 0;
            case 5:
                return 0;
            case 6:
            case 7:
            case 8:
            case 9:
            case 11:
            default:
                loge("regCodeToServiceState: unexpected service state " + code);
                return 1;
        }
    }

    private boolean regCodeIsRoaming(int code) {
        return 5 == code;
    }

    private boolean isSameNamedOperators(ServiceState s) {
        boolean equalsOnsl;
        boolean equalsOnss;
        String spn = getSystemProperty("gsm.sim.operator.alpha", "empty");
        String onsl = s.getOperatorAlphaLong();
        String onss = s.getOperatorAlphaShort();
        if (onsl == null || !spn.equals(onsl)) {
            equalsOnsl = false;
        } else {
            equalsOnsl = true;
        }
        if (onss == null || !spn.equals(onss)) {
            equalsOnss = false;
        } else {
            equalsOnss = true;
        }
        return currentMccEqualsSimMcc(s) && (equalsOnsl || equalsOnss);
    }

    private boolean currentMccEqualsSimMcc(ServiceState s) {
        try {
            return getSystemProperty("gsm.sim.operator.numeric", "").substring(0, 3).equals(s.getOperatorNumeric().substring(0, 3));
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isOperatorConsideredNonRoaming(ServiceState s) {
        String operatorNumeric = s.getOperatorNumeric();
        String[] numericArray = this.mPhone.getContext().getResources().getStringArray(17236020);
        if (numericArray.length == 0 || operatorNumeric == null) {
            return false;
        }
        for (String numeric : numericArray) {
            if (operatorNumeric.startsWith(numeric)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOperatorConsideredRoaming(ServiceState s) {
        String operatorNumeric = s.getOperatorNumeric();
        String[] numericArray = this.mPhone.getContext().getResources().getStringArray(17236021);
        if (numericArray.length == 0 || operatorNumeric == null) {
            return false;
        }
        for (String numeric : numericArray) {
            if (operatorNumeric.startsWith(numeric)) {
                return true;
            }
        }
        return false;
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public int getCurrentDataConnectionState() {
        return this.mSS.getDataRegState();
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public boolean isConcurrentVoiceAndDataAllowed() {
        return this.mSS.getRilVoiceRadioTechnology() != 16 || this.mSS.getCssIndicator() == 1;
    }

    public CellLocation getCellLocation() {
        if (this.mCellLoc.getLac() < 0 || this.mCellLoc.getCid() < 0) {
            List<CellInfo> result = getAllCellInfo();
            if (result != null) {
                GsmCellLocation cellLocOther = new GsmCellLocation();
                for (CellInfo ci : result) {
                    if (ci instanceof CellInfoGsm) {
                        CellIdentityGsm cellIdentityGsm = ((CellInfoGsm) ci).getCellIdentity();
                        cellLocOther.setLacAndCid(cellIdentityGsm.getLac(), cellIdentityGsm.getCid());
                        cellLocOther.setPsc(cellIdentityGsm.getPsc());
                        log("getCellLocation(): X ret GSM info=" + cellLocOther);
                        return cellLocOther;
                    } else if (ci instanceof CellInfoWcdma) {
                        CellIdentityWcdma cellIdentityWcdma = ((CellInfoWcdma) ci).getCellIdentity();
                        cellLocOther.setLacAndCid(cellIdentityWcdma.getLac(), cellIdentityWcdma.getCid());
                        cellLocOther.setPsc(cellIdentityWcdma.getPsc());
                        log("getCellLocation(): X ret WCDMA info=" + cellLocOther);
                        return cellLocOther;
                    } else if ((ci instanceof CellInfoLte) && (cellLocOther.getLac() < 0 || cellLocOther.getCid() < 0)) {
                        CellIdentityLte cellIdentityLte = ((CellInfoLte) ci).getCellIdentity();
                        if (!(cellIdentityLte.getTac() == Integer.MAX_VALUE || cellIdentityLte.getCi() == Integer.MAX_VALUE)) {
                            cellLocOther.setLacAndCid(cellIdentityLte.getTac(), cellIdentityLte.getCi());
                            cellLocOther.setPsc(0);
                            log("getCellLocation(): possible LTE cellLocOther=" + cellLocOther);
                        }
                    }
                }
                log("getCellLocation(): X ret best answer cellLocOther=" + cellLocOther);
                return cellLocOther;
            }
            log("getCellLocation(): X empty mCellLoc and CellInfo mCellLoc=" + this.mCellLoc);
            return this.mCellLoc;
        }
        log("getCellLocation(): X good mCellLoc=" + this.mCellLoc);
        return this.mCellLoc;
    }

    /* JADX WARN: Code restructure failed: missing block: B:31:0x0186, code lost:
        if (r32.mZoneDst != (r8 != 0)) goto L_0x0188;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    private void setTimeFromNITZString(java.lang.String r33, long r34) {
        /*
            Method dump skipped, instructions count: 866
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.GsmServiceStateTracker.setTimeFromNITZString(java.lang.String, long):void");
    }

    private boolean getAutoTime() {
        try {
            return Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time") > 0;
        } catch (Settings.SettingNotFoundException e) {
            return true;
        }
    }

    private boolean getAutoTimeZone() {
        try {
            return Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time_zone") > 0;
        } catch (Settings.SettingNotFoundException e) {
            return true;
        }
    }

    private void saveNitzTimeZone(String zoneId) {
        this.mSavedTimeZone = zoneId;
    }

    private void saveNitzTime(long time) {
        this.mSavedTime = time;
        this.mSavedAtTime = SystemClock.elapsedRealtime();
    }

    private void setAndBroadcastNetworkSetTimeZone(String zoneId) {
        log("setAndBroadcastNetworkSetTimeZone: setTimeZone=" + zoneId);
        ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).setTimeZone(zoneId);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIMEZONE");
        intent.addFlags(536870912);
        intent.putExtra("time-zone", zoneId);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        log("setAndBroadcastNetworkSetTimeZone: call alarm.setTimeZone and broadcast zoneId=" + zoneId);
    }

    private void setAndBroadcastNetworkSetTime(long time) {
        log("setAndBroadcastNetworkSetTime: time=" + time + "ms");
        SystemClock.setCurrentTimeMillis(time);
        Intent intent = new Intent("android.intent.action.NETWORK_SET_TIME");
        intent.addFlags(536870912);
        intent.putExtra("time", time);
        this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void revertToNitzTime() {
        if (Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time", 0) != 0) {
            log("Reverting to NITZ Time: mSavedTime=" + this.mSavedTime + " mSavedAtTime=" + this.mSavedAtTime);
            if (this.mSavedTime != 0 && this.mSavedAtTime != 0) {
                setAndBroadcastNetworkSetTime(this.mSavedTime + (SystemClock.elapsedRealtime() - this.mSavedAtTime));
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void revertToNitzTimeZone() {
        if (Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "auto_time_zone", 0) != 0) {
            log("Reverting to NITZ TimeZone: tz='" + this.mSavedTimeZone);
            if (this.mSavedTimeZone != null) {
                setAndBroadcastNetworkSetTimeZone(this.mSavedTimeZone);
            }
        }
    }

    private void setNotification(int notifyType) {
        log("setNotification: create notification " + notifyType);
        if (!this.mPhone.getContext().getResources().getBoolean(17956948)) {
            log("Ignore all the notifications");
            return;
        }
        Context context = this.mPhone.getContext();
        this.mNotification = new Notification();
        this.mNotification.when = System.currentTimeMillis();
        this.mNotification.flags = 16;
        this.mNotification.icon = 17301642;
        Intent intent = new Intent();
        this.mNotification.contentIntent = PendingIntent.getActivity(context, 0, intent, 268435456);
        CharSequence details = "";
        CharSequence title = context.getText(17039586);
        int notificationId = CS_NOTIFICATION;
        switch (notifyType) {
            case CallFailCause.CDMA_DROP /* 1001 */:
                if (SubscriptionManager.getDefaultDataSubId() == this.mPhone.getSubId()) {
                    notificationId = PS_NOTIFICATION;
                    details = context.getText(17039587);
                    break;
                } else {
                    return;
                }
            case 1002:
                notificationId = PS_NOTIFICATION;
                break;
            case 1003:
                details = context.getText(17039590);
                break;
            case 1005:
                details = context.getText(17039589);
                break;
            case 1006:
                details = context.getText(17039588);
                break;
        }
        log("setNotification: put notification " + ((Object) title) + " / " + ((Object) details));
        this.mNotification.tickerText = title;
        this.mNotification.color = context.getResources().getColor(17170521);
        this.mNotification.setLatestEventInfo(context, title, details, this.mNotification.contentIntent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
        if (notifyType == 1002 || notifyType == 1004) {
            notificationManager.cancel(notificationId);
        } else {
            notificationManager.notify(notificationId, this.mNotification);
        }
    }

    private UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(this.mPhone.getPhoneId(), 1);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void onUpdateIccAvailability() {
        UiccCardApplication newUiccApplication;
        if (this.mUiccController != null && this.mUiccApplcation != (newUiccApplication = getUiccCardApplication())) {
            if (this.mUiccApplcation != null) {
                log("Removing stale icc objects.");
                this.mUiccApplcation.unregisterForReady(this);
                if (this.mIccRecords != null) {
                    this.mIccRecords.unregisterForRecordsLoaded(this);
                }
                this.mIccRecords = null;
                this.mUiccApplcation = null;
            }
            if (newUiccApplication != null) {
                log("New card found");
                this.mUiccApplcation = newUiccApplication;
                this.mIccRecords = this.mUiccApplcation.getIccRecords();
                this.mUiccApplcation.registerForReady(this, 17, null);
                if (this.mIccRecords != null) {
                    this.mIccRecords.registerForRecordsLoaded(this, 16, null);
                }
            }
        }
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[GsmSST] " + s);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[GsmSST] " + s);
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public void powerOffRadioSafely(DcTrackerBase dcTracker) {
        synchronized (this) {
            if (!this.mPendingRadioPowerOffAfterDataOff) {
                int dds = SubscriptionManager.getDefaultDataSubId();
                if (!dcTracker.isDisconnected() || (dds != this.mPhone.getSubId() && ((dds == this.mPhone.getSubId() || !ProxyController.getInstance().isDataDisconnected(dds)) && SubscriptionManager.isValidSubscriptionId(dds)))) {
                    if (this.mPhone.isInCall()) {
                        this.mPhone.mCT.mRingingCall.hangupIfAlive();
                        this.mPhone.mCT.mBackgroundCall.hangupIfAlive();
                        this.mPhone.mCT.mForegroundCall.hangupIfAlive();
                    }
                    dcTracker.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    if (dds != this.mPhone.getSubId() && !ProxyController.getInstance().isDataDisconnected(dds)) {
                        log("Data is active on DDS.  Wait for all data disconnect");
                        ProxyController.getInstance().registerForAllDataDisconnected(dds, this, CallFailCause.CDMA_DROP, null);
                        this.mPendingRadioPowerOffAfterDataOff = true;
                    }
                    Message msg = Message.obtain(this);
                    msg.what = 38;
                    int i = this.mPendingRadioPowerOffAfterDataOffTag + 1;
                    this.mPendingRadioPowerOffAfterDataOffTag = i;
                    msg.arg1 = i;
                    if (sendMessageDelayed(msg, 4000L)) {
                        log("Wait upto 4s for data to disconnect, then turn off radio.");
                        this.mPendingRadioPowerOffAfterDataOff = true;
                    } else {
                        log("Cannot send delayed Msg, turn off radio right away.");
                        hangupAndPowerOff();
                        this.mPendingRadioPowerOffAfterDataOff = false;
                    }
                } else {
                    dcTracker.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    log("Data disconnected, turn off radio right away.");
                    hangupAndPowerOff();
                }
            }
        }
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public void setImsRegistrationState(boolean registered) {
        if (!this.mImsRegistrationOnOff || registered || !this.mAlarmSwitch) {
            this.mImsRegistrationOnOff = registered;
            return;
        }
        this.mImsRegistrationOnOff = registered;
        ((AlarmManager) this.mPhone.getContext().getSystemService("alarm")).cancel(this.mRadioOffIntent);
        this.mAlarmSwitch = false;
        sendMessage(obtainMessage(45));
    }

    private void onSpeechCodec(AsyncResult ar) {
        if (ar.exception == null && ar.result != null) {
            this.mPhone.mCT.updateSpeechCodec((int[]) ar.result);
        }
    }

    private int getLacOrDataLac() {
        if (this.mLAC != -1) {
            return this.mLAC;
        }
        if (this.mDataLAC != -1) {
            return this.mDataLAC;
        }
        return -1;
    }

    private int getCidOrDataCid() {
        if (this.mCid != -1) {
            return this.mCid;
        }
        if (this.mDataCid != -1) {
            return this.mDataCid;
        }
        return -1;
    }

    private void setSendSmsMemoryFull() {
        boolean z = false;
        if (TelBrand.IS_DCM) {
            String decryptState = SystemProperties.get("vold.decrypt", "0");
            log("Get decryptState is: " + decryptState);
            if (decryptState.equals("trigger_restart_min_framework")) {
                log("report encrypt state to WMS");
                this.mCi.reportDecryptStatus(false, obtainMessage(61));
            } else {
                boolean isAvailable = isStorageAvailable();
                StringBuilder append = new StringBuilder().append("is sms really memory full:");
                if (!isAvailable) {
                    z = true;
                }
                log(append.append(z).toString());
                if (isAvailable) {
                    log("report decrypt state to WMS");
                    this.mCi.reportDecryptStatus(true, obtainMessage(61));
                }
            }
            this.mIsSmsMemRptSent = true;
        }
    }

    private boolean isStorageAvailable() {
        if (!TelBrand.IS_DCM || this.mPhone.mSmsStorageMonitor == null) {
            return true;
        }
        return this.mPhone.mSmsStorageMonitor.isStorageAvailable();
    }

    private void onOemSignalStrengthResult(AsyncResult ar) {
        SignalStrength oldSignalStrength = this.mSignalStrength;
        if (ar.exception != null || ar.result == null) {
            log("onSignalStrengthResult() Exception from RIL : " + ar.exception);
            this.mSignalStrength = new SignalStrength(true);
        } else {
            int gwRssi = 99;
            int gwBer = -1;
            int gwEcIo = -1;
            int lteSignalStrength = 99;
            int lteRsrp = Integer.MAX_VALUE;
            int lteRsrq = Integer.MAX_VALUE;
            int lteRssnr = Integer.MAX_VALUE;
            int lteCqi = Integer.MAX_VALUE;
            int lteActiveBand = Integer.MAX_VALUE;
            int[] ints = (int[]) ar.result;
            if (ints.length > 15) {
                gwRssi = ints[0] >= 0 ? ints[0] : 99;
                gwBer = ints[1];
                gwEcIo = ints[2];
                lteSignalStrength = ints[8];
                lteRsrp = ints[9];
                lteRsrq = ints[10];
                lteRssnr = ints[11];
                lteCqi = ints[12];
                lteActiveBand = ints[14];
            }
            log("[EXTDBG] rssi=" + gwRssi + ", bitErrorRate=" + gwBer + ", ecio=" + gwEcIo);
            log("[EXTDBG] lteSignalStrength=" + lteSignalStrength + ", lteRsrp=" + lteRsrp + ", lteRsrq=" + lteRsrq + ", lteRssnr=" + lteRssnr + ", lteCqi=" + lteCqi + ", lteActiveBand=" + lteActiveBand);
            this.mSignalStrength = new SignalStrength(gwRssi, gwBer, -1, -1, -1, -1, -1, lteSignalStrength, lteRsrp, lteRsrq, lteRssnr, lteCqi, -1, true, gwEcIo, lteActiveBand);
            this.mSignalStrength.validateInput();
        }
        if (!this.mSignalStrength.equals(oldSignalStrength)) {
            try {
                this.mPhone.notifySignalStrength();
            } catch (NullPointerException ex) {
                log("onSignalStrengthResult() Phone already destroyed: " + ex + "SignalStrength not notified");
            }
        }
    }

    private void onLteBandInfo(AsyncResult ar) {
        int lteBand;
        if (ar.exception == null && ar.result != null) {
            this.mLteActiveBand = ((Integer) ar.result).intValue();
            switch (this.mLteActiveBand) {
                case 120:
                    lteBand = 1;
                    break;
                case 121:
                    lteBand = 2;
                    break;
                case 122:
                    lteBand = 3;
                    break;
                case 123:
                    lteBand = 4;
                    break;
                case 127:
                    lteBand = 8;
                    break;
                case 149:
                    lteBand = 41;
                    break;
                default:
                    lteBand = -1;
                    break;
            }
            if (this.mLteBand != lteBand && lteBand != -1) {
                this.mLteBand = lteBand;
                Intent intent = new Intent(OemTelephonyIntents.ACTION_LTE_BAND_CHANGED);
                intent.addFlags(536870912);
                intent.putExtra(OemTelephonyIntents.EXTRA_LTE_BAND, lteBand);
                this.mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
            }
        }
    }
}
