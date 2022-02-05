package com.android.internal.telephony.dataconnection;

import android.content.Context;
import android.content.res.Resources;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelBrand;
import java.util.HashMap;

/*  JADX ERROR: NullPointerException in pass: EnumVisitor
    java.lang.NullPointerException: Cannot invoke "java.util.List.isEmpty()" because the return value of "jadx.core.dex.nodes.MethodNode.getBasicBlocks()" is null
    	at jadx.core.dex.visitors.EnumVisitor.convertToEnum(EnumVisitor.java:93)
    	at jadx.core.dex.visitors.EnumVisitor.visit(EnumVisitor.java:74)
    */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public enum DcFailCause {
    private static final /* synthetic */ DcFailCause[] $VALUES = null;
    public static final DcFailCause ACTIVATION_REJECT_GGSN = null;
    public static final DcFailCause ACTIVATION_REJECT_UNSPECIFIED = null;
    public static final DcFailCause CONNECTION_TO_DATACONNECTIONAC_BROKEN = null;
    public static final DcFailCause CUST_NOT_READY_FOR_DATA = null;
    public static final DcFailCause ERROR_UNSPECIFIED = null;
    public static final DcFailCause GPRS_REGISTRATION_FAIL = null;
    public static final DcFailCause INSUFFICIENT_RESOURCES = null;
    public static final DcFailCause LOST_CONNECTION = null;
    public static final DcFailCause MISSING_UNKNOWN_APN = null;
    public static final DcFailCause NONE = null;
    public static final DcFailCause NSAPI_IN_USE = null;
    public static final DcFailCause ONLY_IPV4_ALLOWED = null;
    public static final DcFailCause ONLY_IPV6_ALLOWED = null;
    public static final DcFailCause ONLY_SINGLE_BEARER_ALLOWED = null;
    public static final DcFailCause OPERATOR_BARRED = null;
    public static final DcFailCause PREF_RADIO_TECH_CHANGED = null;
    public static final DcFailCause PROTOCOL_ERRORS = null;
    public static final DcFailCause RADIO_NOT_AVAILABLE = null;
    public static final DcFailCause RADIO_POWER_OFF = null;
    public static final DcFailCause REGISTRATION_FAIL = null;
    public static final DcFailCause REGULAR_DEACTIVATION = null;
    public static final DcFailCause RESET_BY_FRAMEWORK = null;
    public static final DcFailCause SERVICE_OPTION_NOT_SUBSCRIBED = null;
    public static final DcFailCause SERVICE_OPTION_NOT_SUPPORTED = null;
    public static final DcFailCause SERVICE_OPTION_OUT_OF_ORDER = null;
    public static final DcFailCause SIGNAL_LOST = null;
    public static final DcFailCause TETHERED_CALL_ACTIVE = null;
    public static final DcFailCause UNACCEPTABLE_NETWORK_PARAMETER = null;
    public static final DcFailCause UNKNOWN = null;
    public static final DcFailCause UNKNOWN_PDP_ADDRESS_TYPE = null;
    public static final DcFailCause USER_AUTHENTICATION = null;
    private static final HashMap<Integer, DcFailCause> sErrorCodeToFailCauseMap = null;
    private final int mErrorCode;
    private final boolean mRestartRadioOnRegularDeactivation = Resources.getSystem().getBoolean(17957008);

    public static DcFailCause valueOf(String name) {
        return (DcFailCause) Enum.valueOf(DcFailCause.class, name);
    }

    public static DcFailCause[] values() {
        return (DcFailCause[]) $VALUES.clone();
    }

    DcFailCause(String str, int i, int errorCode) {
        this.mErrorCode = errorCode;
    }

    public int getErrorCode() {
        return this.mErrorCode;
    }

    public boolean isRestartRadioFail() {
        return this == REGULAR_DEACTIVATION && this.mRestartRadioOnRegularDeactivation;
    }

    public boolean isPermanentFail() {
        Context context = PhoneFactory.getContext();
        if (this == ACTIVATION_REJECT_GGSN) {
            return context.getResources().getBoolean(17957020);
        }
        if (this == PROTOCOL_ERRORS) {
            return context.getResources().getBoolean(17957021);
        }
        if (!TelBrand.IS_KDDI || this != USER_AUTHENTICATION) {
            return this == OPERATOR_BARRED || this == MISSING_UNKNOWN_APN || this == UNKNOWN_PDP_ADDRESS_TYPE || this == USER_AUTHENTICATION || this == SERVICE_OPTION_NOT_SUPPORTED || this == SERVICE_OPTION_NOT_SUBSCRIBED || this == NSAPI_IN_USE || this == ONLY_IPV4_ALLOWED || this == ONLY_IPV6_ALLOWED || this == RADIO_POWER_OFF || this == TETHERED_CALL_ACTIVE || this == RADIO_NOT_AVAILABLE || this == UNACCEPTABLE_NETWORK_PARAMETER;
        }
        return false;
    }

    public boolean isEventLoggable() {
        return this == OPERATOR_BARRED || this == INSUFFICIENT_RESOURCES || this == UNKNOWN_PDP_ADDRESS_TYPE || this == USER_AUTHENTICATION || this == ACTIVATION_REJECT_GGSN || this == ACTIVATION_REJECT_UNSPECIFIED || this == SERVICE_OPTION_NOT_SUBSCRIBED || this == SERVICE_OPTION_NOT_SUPPORTED || this == SERVICE_OPTION_OUT_OF_ORDER || this == NSAPI_IN_USE || this == ONLY_IPV4_ALLOWED || this == ONLY_IPV6_ALLOWED || this == PROTOCOL_ERRORS || this == SIGNAL_LOST || this == RADIO_POWER_OFF || this == TETHERED_CALL_ACTIVE || this == UNACCEPTABLE_NETWORK_PARAMETER;
    }

    public static DcFailCause fromInt(int errorCode) {
        DcFailCause fc = sErrorCodeToFailCauseMap.get(Integer.valueOf(errorCode));
        if (fc == null) {
            return UNKNOWN;
        }
        return fc;
    }
}
