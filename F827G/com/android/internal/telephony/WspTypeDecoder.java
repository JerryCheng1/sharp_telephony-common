package com.android.internal.telephony;

import com.google.android.mms.ContentType;
import java.util.HashMap;

public class WspTypeDecoder {
    public static final String CONTENT_TYPE_B_MMS = "application/vnd.wap.mms-message";
    public static final String CONTENT_TYPE_B_PUSH_CO = "application/vnd.wap.coc";
    public static final String CONTENT_TYPE_B_PUSH_SYNCML_NOTI = "application/vnd.syncml.notification";
    public static final int PARAMETER_ID_X_WAP_APPLICATION_ID = 47;
    public static final int PDU_TYPE_CONFIRMED_PUSH = 7;
    public static final int PDU_TYPE_PUSH = 6;
    private static final int Q_VALUE = 0;
    private static final int WAP_PDU_LENGTH_QUOTE = 31;
    private static final int WAP_PDU_SHORT_LENGTH_MAX = 30;
    private static final HashMap<Integer, String> WELL_KNOWN_MIME_TYPES = new HashMap();
    private static final HashMap<Integer, String> WELL_KNOWN_PARAMETERS = new HashMap();
    HashMap<String, String> mContentParameters;
    int mDataLength;
    String mStringValue;
    long mUnsigned32bit;
    byte[] mWspData;

    static {
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(0), "*/*");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(1), "text/*");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(2), ContentType.TEXT_HTML);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(3), ContentType.TEXT_PLAIN);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(4), "text/x-hdml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(5), "text/x-ttml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(6), ContentType.TEXT_VCALENDAR);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(7), ContentType.TEXT_VCARD);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(8), "text/vnd.wap.wml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(9), "text/vnd.wap.wmlscript");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(10), "text/vnd.wap.wta-event");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(11), "multipart/*");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(12), "multipart/mixed");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(13), "multipart/form-data");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(14), "multipart/byterantes");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(15), "multipart/alternative");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(16), "application/*");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(17), "application/java-vm");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(18), "application/x-www-form-urlencoded");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(19), "application/x-hdmlc");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(20), "application/vnd.wap.wmlc");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(21), "application/vnd.wap.wmlscriptc");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(22), "application/vnd.wap.wta-eventc");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(23), "application/vnd.wap.uaprof");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(24), "application/vnd.wap.wtls-ca-certificate");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(25), "application/vnd.wap.wtls-user-certificate");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(26), "application/x-x509-ca-cert");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(27), "application/x-x509-user-cert");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(28), ContentType.IMAGE_UNSPECIFIED);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(29), ContentType.IMAGE_GIF);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(30), ContentType.IMAGE_JPEG);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(31), "image/tiff");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(32), ContentType.IMAGE_PNG);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(33), ContentType.IMAGE_WBMP);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(34), "application/vnd.wap.multipart.*");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(35), ContentType.MULTIPART_MIXED);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(36), "application/vnd.wap.multipart.form-data");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(37), "application/vnd.wap.multipart.byteranges");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(38), ContentType.MULTIPART_ALTERNATIVE);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(39), "application/xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(40), "text/xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(41), "application/vnd.wap.wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(42), "application/x-x968-cross-cert");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(43), "application/x-x968-ca-cert");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(44), "application/x-x968-user-cert");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(45), "text/vnd.wap.si");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(46), "application/vnd.wap.sic");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(47), "text/vnd.wap.sl");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(48), "application/vnd.wap.slc");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(49), "text/vnd.wap.co");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(50), CONTENT_TYPE_B_PUSH_CO);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(51), ContentType.MULTIPART_RELATED);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(52), "application/vnd.wap.sia");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(53), "text/vnd.wap.connectivity-xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(54), "application/vnd.wap.connectivity-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(55), "application/pkcs7-mime");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(56), "application/vnd.wap.hashed-certificate");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(57), "application/vnd.wap.signed-certificate");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(58), "application/vnd.wap.cert-response");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(59), ContentType.APP_XHTML);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(60), "application/wml+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(61), "text/css");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(62), "application/vnd.wap.mms-message");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(63), "application/vnd.wap.rollover-certificate");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(64), "application/vnd.wap.locc+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(65), "application/vnd.wap.loc+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(66), "application/vnd.syncml.dm+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(67), "application/vnd.syncml.dm+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(68), CONTENT_TYPE_B_PUSH_SYNCML_NOTI);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(69), ContentType.APP_WAP_XHTML);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(70), "application/vnd.wv.csp.cir");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(71), "application/vnd.oma.dd+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(72), "application/vnd.oma.drm.message");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(73), ContentType.APP_DRM_CONTENT);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(74), "application/vnd.oma.drm.rights+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(75), "application/vnd.oma.drm.rights+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(76), "application/vnd.wv.csp+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(77), "application/vnd.wv.csp+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(78), "application/vnd.syncml.ds.notification");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(79), ContentType.AUDIO_UNSPECIFIED);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(80), ContentType.VIDEO_UNSPECIFIED);
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(81), "application/vnd.oma.dd2+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(82), "application/mikey");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(83), "application/vnd.oma.dcd");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(84), "application/vnd.oma.dcdc");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(513), "application/vnd.uplanet.cacheop-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(514), "application/vnd.uplanet.signal");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(515), "application/vnd.uplanet.alert-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(516), "application/vnd.uplanet.list-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(517), "application/vnd.uplanet.listcmd-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(518), "application/vnd.uplanet.channel-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(519), "application/vnd.uplanet.provisioning-status-uri");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(520), "x-wap.multipart/vnd.uplanet.header-set");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(521), "application/vnd.uplanet.bearer-choice-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(522), "application/vnd.phonecom.mmc-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(523), "application/vnd.nokia.syncset+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(524), "image/x-up-wpng");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(768), "application/iota.mmc-wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(769), "application/iota.mmc-xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(770), "application/vnd.syncml+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(771), "application/vnd.syncml+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(772), "text/vnd.wap.emn+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(773), "text/calendar");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(774), "application/vnd.omads-email+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(775), "application/vnd.omads-file+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(776), "application/vnd.omads-folder+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(777), "text/directory;profile=vCard");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(778), "application/vnd.wap.emn+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(779), "application/vnd.nokia.ipdc-purchase-response");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(780), "application/vnd.motorola.screen3+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(781), "application/vnd.motorola.screen3+gzip");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(782), "application/vnd.cmcc.setting+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(783), "application/vnd.cmcc.bombing+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(784), "application/vnd.docomo.pf");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(785), "application/vnd.docomo.ub");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(786), "application/vnd.omaloc-supl-init");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(787), "application/vnd.oma.group-usage-list+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(788), "application/oma-directory+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(789), "application/vnd.docomo.pf2");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(790), "application/vnd.oma.drm.roap-trigger+wbxml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(791), "application/vnd.sbm.mid2");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(792), "application/vnd.wmf.bootstrap");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(793), "application/vnc.cmcc.dcd+xml");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(794), "application/vnd.sbm.cid");
        WELL_KNOWN_MIME_TYPES.put(Integer.valueOf(795), "application/vnd.oma.bcast.provisioningtrigger");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(0), "Q");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(1), "Charset");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(2), "Level");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(3), "Type");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(7), "Differences");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(8), "Padding");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(9), "Type");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(14), "Max-Age");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(16), "Secure");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(17), "SEC");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(18), "MAC");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(19), "Creation-date");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(20), "Modification-date");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(21), "Read-date");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(22), "Size");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(23), "Name");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(24), "Filename");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(25), "Start");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(26), "Start-info");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(27), "Comment");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(28), "Domain");
        WELL_KNOWN_PARAMETERS.put(Integer.valueOf(29), "Path");
    }

    public WspTypeDecoder(byte[] bArr) {
        this.mWspData = bArr;
    }

    private boolean decodeNoValue(int i) {
        if (this.mWspData[i] != (byte) 0) {
            return false;
        }
        this.mDataLength = 1;
        return true;
    }

    private void expandWellKnownMimeType() {
        if (this.mStringValue == null) {
            this.mStringValue = (String) WELL_KNOWN_MIME_TYPES.get(Integer.valueOf((int) this.mUnsigned32bit));
            return;
        }
        this.mUnsigned32bit = -1;
    }

    private boolean readContentParameters(int i, int i2, int i3) {
        if (i2 > 0) {
            Object obj;
            int i4;
            Object obj2;
            byte b = this.mWspData[i];
            if ((b & 128) != 0 || b <= (byte) 31) {
                if (decodeIntegerValue(i)) {
                    int i5 = this.mDataLength + 0;
                    int i6 = (int) this.mUnsigned32bit;
                    String str = (String) WELL_KNOWN_PARAMETERS.get(Integer.valueOf(i6));
                    if (str == null) {
                        obj = "unassigned/0x" + Long.toHexString((long) i6);
                    } else {
                        String obj3 = str;
                    }
                    if (i6 != 0) {
                        i4 = i5;
                    } else if (decodeUintvarInteger(i + i5)) {
                        i4 = this.mDataLength + i5;
                        this.mContentParameters.put(obj3, String.valueOf(this.mUnsigned32bit));
                        return readContentParameters(i + i4, i2 - i4, i4 + i3);
                    }
                }
                return false;
            }
            decodeTokenText(i);
            i4 = this.mDataLength + 0;
            obj3 = this.mStringValue;
            if (decodeNoValue(i + i4)) {
                i4 += this.mDataLength;
                obj2 = null;
            } else if (decodeIntegerValue(i + i4)) {
                i4 += this.mDataLength;
                obj2 = String.valueOf((int) this.mUnsigned32bit);
            } else {
                decodeTokenText(i + i4);
                i4 += this.mDataLength;
                obj2 = this.mStringValue;
                if (obj2.startsWith("\"")) {
                    obj2 = obj2.substring(1);
                }
            }
            this.mContentParameters.put(obj3, obj2);
            return readContentParameters(i + i4, i2 - i4, i4 + i3);
        }
        this.mDataLength = i3;
        return true;
    }

    public boolean decodeConstrainedEncoding(int i) {
        if (!decodeShortInteger(i)) {
            return decodeExtensionMedia(i);
        }
        this.mStringValue = null;
        return true;
    }

    public boolean decodeContentLength(int i) {
        return decodeIntegerValue(i);
    }

    public boolean decodeContentLocation(int i) {
        return decodeTextString(i);
    }

    public boolean decodeContentType(int i) {
        this.mContentParameters = new HashMap();
        try {
            if (decodeValueLength(i)) {
                int i2 = (int) this.mUnsigned32bit;
                int decodedDataLength = getDecodedDataLength();
                int i3;
                long j;
                String str;
                if (decodeIntegerValue(i + decodedDataLength)) {
                    this.mDataLength += decodedDataLength;
                    i3 = this.mDataLength;
                    this.mStringValue = null;
                    expandWellKnownMimeType();
                    j = this.mUnsigned32bit;
                    str = this.mStringValue;
                    if (!readContentParameters(this.mDataLength + i, i2 - (this.mDataLength - decodedDataLength), 0)) {
                        return false;
                    }
                    this.mDataLength += i3;
                    this.mUnsigned32bit = j;
                    this.mStringValue = str;
                    return true;
                }
                if (decodeExtensionMedia(i + decodedDataLength)) {
                    this.mDataLength += decodedDataLength;
                    i3 = this.mDataLength;
                    expandWellKnownMimeType();
                    j = this.mUnsigned32bit;
                    str = this.mStringValue;
                    if (readContentParameters(this.mDataLength + i, i2 - (this.mDataLength - decodedDataLength), 0)) {
                        this.mDataLength += i3;
                        this.mUnsigned32bit = j;
                        this.mStringValue = str;
                        return true;
                    }
                }
                return false;
            }
            boolean decodeConstrainedEncoding = decodeConstrainedEncoding(i);
            if (!decodeConstrainedEncoding) {
                return decodeConstrainedEncoding;
            }
            expandWellKnownMimeType();
            return decodeConstrainedEncoding;
        } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    public boolean decodeExtensionMedia(int i) {
        int i2;
        boolean z = false;
        this.mDataLength = 0;
        this.mStringValue = null;
        int length = this.mWspData.length;
        if (i < length) {
            z = true;
            i2 = i;
        } else {
            i2 = i;
        }
        while (i2 < length && this.mWspData[i2] != (byte) 0) {
            i2++;
        }
        this.mDataLength = (i2 - i) + 1;
        this.mStringValue = new String(this.mWspData, i, this.mDataLength - 1);
        return z;
    }

    public boolean decodeIntegerValue(int i) {
        return decodeShortInteger(i) ? true : decodeLongInteger(i);
    }

    public boolean decodeLongInteger(int i) {
        int i2 = this.mWspData[i] & 255;
        if (i2 > 30) {
            return false;
        }
        this.mUnsigned32bit = 0;
        for (int i3 = 1; i3 <= i2; i3++) {
            this.mUnsigned32bit = (this.mUnsigned32bit << 8) | ((long) (this.mWspData[i + i3] & 255));
        }
        this.mDataLength = i2 + 1;
        return true;
    }

    public boolean decodeShortInteger(int i) {
        if ((this.mWspData[i] & 128) == 0) {
            return false;
        }
        this.mUnsigned32bit = (long) (this.mWspData[i] & 127);
        this.mDataLength = 1;
        return true;
    }

    public boolean decodeTextString(int i) {
        int i2 = i;
        while (this.mWspData[i2] != (byte) 0) {
            i2++;
        }
        this.mDataLength = (i2 - i) + 1;
        if (this.mWspData[i] == Byte.MAX_VALUE) {
            this.mStringValue = new String(this.mWspData, i + 1, this.mDataLength - 2);
        } else {
            this.mStringValue = new String(this.mWspData, i, this.mDataLength - 1);
        }
        return true;
    }

    public boolean decodeTokenText(int i) {
        int i2 = i;
        while (this.mWspData[i2] != (byte) 0) {
            i2++;
        }
        this.mDataLength = (i2 - i) + 1;
        this.mStringValue = new String(this.mWspData, i, this.mDataLength - 1);
        return true;
    }

    public boolean decodeUintvarInteger(int i) {
        this.mUnsigned32bit = 0;
        int i2 = i;
        while ((this.mWspData[i2] & 128) != 0) {
            if (i2 - i >= 4) {
                return false;
            }
            this.mUnsigned32bit = (this.mUnsigned32bit << 7) | ((long) (this.mWspData[i2] & 127));
            i2++;
        }
        this.mUnsigned32bit = (this.mUnsigned32bit << 7) | ((long) (this.mWspData[i2] & 127));
        this.mDataLength = (i2 - i) + 1;
        return true;
    }

    public boolean decodeValueLength(int i) {
        if ((this.mWspData[i] & 255) > 31) {
            return false;
        }
        if (this.mWspData[i] < (byte) 31) {
            this.mUnsigned32bit = (long) this.mWspData[i];
            this.mDataLength = 1;
            return true;
        }
        decodeUintvarInteger(i + 1);
        this.mDataLength++;
        return true;
    }

    public boolean decodeXWapApplicationId(int i) {
        if (!decodeIntegerValue(i)) {
            return decodeTextString(i);
        }
        this.mStringValue = null;
        return true;
    }

    public boolean decodeXWapContentURI(int i) {
        return decodeTextString(i);
    }

    public boolean decodeXWapInitiatorURI(int i) {
        return decodeTextString(i);
    }

    public HashMap<String, String> getContentParameters() {
        return this.mContentParameters;
    }

    public int getDecodedDataLength() {
        return this.mDataLength;
    }

    public long getValue32() {
        return this.mUnsigned32bit;
    }

    public String getValueString() {
        return this.mStringValue;
    }

    public boolean seekXWapApplicationId(int i, int i2) {
        while (i <= i2) {
            try {
                if (!decodeIntegerValue(i)) {
                    if (!decodeTextString(i)) {
                        break;
                    }
                } else if (((int) getValue32()) == 47) {
                    this.mUnsigned32bit = (long) (i + 1);
                    return true;
                }
                int decodedDataLength = getDecodedDataLength() + i;
                if (decodedDataLength > i2) {
                    break;
                }
                byte b = this.mWspData[decodedDataLength];
                if (b < (byte) 0 || b > (byte) 30) {
                    if (b != (byte) 31) {
                        if ((byte) 31 < b && b <= Byte.MAX_VALUE) {
                            if (!decodeTextString(decodedDataLength)) {
                                break;
                            }
                            i = decodedDataLength + getDecodedDataLength();
                        } else {
                            i = decodedDataLength + 1;
                        }
                    } else if (decodedDataLength + 1 >= i2) {
                        break;
                    } else {
                        decodedDataLength++;
                        if (!decodeUintvarInteger(decodedDataLength)) {
                            break;
                        }
                        i = decodedDataLength + getDecodedDataLength();
                    }
                } else {
                    i = decodedDataLength + (this.mWspData[decodedDataLength] + 1);
                }
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }
        return false;
    }
}
