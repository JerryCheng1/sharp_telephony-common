package org.codeaurora.ims;

public class QtiCallConstants {
    public static final String CALL_ENCRYPTION_EXTRA_KEY = "CallEncryption";
    public static final int CALL_FAIL_EXTRA_CODE_CALL_CS_RETRY_REQUIRED = 146;
    public static final int CALL_FAIL_EXTRA_CODE_LOCAL_LOW_BATTERY = 112;
    public static final int CALL_FAIL_EXTRA_CODE_LOCAL_VALIDATE_NUMBER = 150;
    public static final int CALL_FAIL_EXTRA_CODE_LTE_3G_HA_FAILED = 149;
    public static final int CALL_SUBSTATE_ALL = 15;
    public static final int CALL_SUBSTATE_AUDIO_CONNECTED_SUSPENDED = 1;
    public static final int CALL_SUBSTATE_AVP_RETRY = 4;
    public static final String CALL_SUBSTATE_EXTRA_KEY = "CallSubstate";
    public static final int CALL_SUBSTATE_MEDIA_PAUSED = 8;
    public static final int CALL_SUBSTATE_NONE = 0;
    public static final int CALL_SUBSTATE_VIDEO_CONNECTED_SUSPENDED = 2;
    public static final int CAMERA_MAX_ZOOM = 100;
    public static final int CAPABILITY_ADD_PARTICIPANT = 33554432;
    public static final int CAPABILITY_SUPPORTS_DOWNGRADE_TO_VOICE_LOCAL = 8388608;
    public static final int CAPABILITY_SUPPORTS_DOWNGRADE_TO_VOICE_REMOTE = 16777216;
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_GENERIC_ERROR = 11;
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_LIPSYNC = 10;
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_LOCAL_REQ = 3;
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_LOW_THRPUT = 8;
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_PACKET_LOSS = 7;
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_QOS = 6;
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_REMOTE_REQ = 4;
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_RTP_TIMEOUT = 5;
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_THERM_MITIGATION = 9;
    public static final int CAUSE_CODE_SESSION_MODIFY_UPGRADE_LOCAL_REQ = 1;
    public static final int CAUSE_CODE_SESSION_MODIFY_UPGRADE_REMOTE_REQ = 2;
    public static final int CAUSE_CODE_UNSPECIFIED = 0;
    public static final int DISCONNECT_CAUSE_UNSPECIFIED = -1;
    public static final int DOMAIN_AUTOMATIC = 0;
    public static final int DOMAIN_CS = 1;
    public static final int DOMAIN_PS = 2;
    public static final int ERROR_CALL_CODE_UNSPECIFIED = -1;
    public static final int ERROR_CALL_SUPP_SVC_CANCELLED = 2;
    public static final int ERROR_CALL_SUPP_SVC_FAILED = 1;
    public static final int ERROR_CALL_SUPP_SVC_REINVITE_COLLISION = 3;
    public static final String EXTRAS_KEY_CALL_FAIL_EXTRA_CODE = "CallFailExtraCode";
    public static final String EXTRA_CALL_DOMAIN = "org.codeaurora.extra.CALL_DOMAIN";
    public static final String IMS_TO_CS_RETRY_ENABLED = "qti.settings.cs_retry";
    public static final int INVALID_PHONE_ID = -1;
    public static final String LOW_BATTERY_EXTRA_KEY = "LowBattery";
    public static final int ORIENTATION_MODE_DYNAMIC = 3;
    public static final String ORIENTATION_MODE_EXTRA_KEY = "OrientationMode";
    public static final int ORIENTATION_MODE_LANDSCAPE = 1;
    public static final int ORIENTATION_MODE_PORTRAIT = 2;
    public static final int ORIENTATION_MODE_UNSPECIFIED = -1;
    public static final String SESSION_MODIFICATION_CAUSE_EXTRA_KEY = "SessionModificationCause";
    public static final int SESSION_MODIFY_REQUEST_FAILED_LOW_BATTERY = 50;
    public static final String VIDEO_CALL_DATA_USAGE_KEY = "dataUsage";
    public static final String VOWIFI_CALL_QUALITY_EXTRA_KEY = "VoWiFiCallQuality";
    public static final int VOWIFI_QUALITY_EXCELLENT = 1;
    public static final int VOWIFI_QUALITY_FAIR = 2;
    public static final int VOWIFI_QUALITY_NONE = 0;
    public static final int VOWIFI_QUALITY_POOR = 4;

    private QtiCallConstants() {
    }
}
