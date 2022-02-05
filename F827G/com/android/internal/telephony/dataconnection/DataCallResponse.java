package com.android.internal.telephony.dataconnection;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class DataCallResponse {
    private final boolean DBG = true;
    private final String LOG_TAG = "DataCallResponse";
    public int version = 0;
    public int status = 0;
    public int cid = 0;
    public int active = 0;
    public String type = "";
    public String ifname = "";
    public String[] addresses = new String[0];
    public String[] dnses = new String[0];
    public String[] gateways = new String[0];
    public int suggestedRetryTime = -1;
    public String[] pcscf = new String[0];
    public int mtu = 0;

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public enum SetupResult {
        SUCCESS,
        ERR_BadCommand,
        ERR_UnacceptableParameter,
        ERR_GetLastErrorFromRil,
        ERR_Stale,
        ERR_RilError;
        
        public DcFailCause mFailCause = DcFailCause.fromInt(0);

        SetupResult() {
        }

        @Override // java.lang.Enum
        public String toString() {
            return name() + "  SetupResult.mFailCause=" + this.mFailCause;
        }
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("DataCallResponse: {").append("version=").append(this.version).append(" status=").append(this.status).append(" retry=").append(this.suggestedRetryTime).append(" cid=").append(this.cid).append(" active=").append(this.active).append(" type=").append(this.type).append(" ifname=").append(this.ifname).append(" mtu=").append(this.mtu).append(" addresses=[");
        for (String addr : this.addresses) {
            sb.append(addr);
            sb.append(",");
        }
        if (this.addresses.length > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("] dnses=[");
        for (String addr2 : this.dnses) {
            sb.append(addr2);
            sb.append(",");
        }
        if (this.dnses.length > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("] gateways=[");
        for (String addr3 : this.gateways) {
            sb.append(addr3);
            sb.append(",");
        }
        if (this.gateways.length > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("] pcscf=[");
        for (String addr4 : this.pcscf) {
            sb.append(addr4);
            sb.append(",");
        }
        if (this.pcscf.length > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("]}");
        return sb.toString();
    }

    /* JADX WARN: Removed duplicated region for block: B:78:0x024e A[Catch: UnknownHostException -> 0x00bf, TryCatch #2 {UnknownHostException -> 0x00bf, blocks: (B:7:0x0034, B:9:0x0043, B:11:0x004a, B:13:0x0052, B:17:0x0065, B:19:0x0072, B:20:0x007c, B:21:0x0080, B:24:0x0088, B:27:0x008e, B:36:0x0115, B:37:0x012f, B:39:0x0134, B:40:0x0152, B:41:0x0153, B:43:0x0159, B:45:0x0160, B:47:0x0168, B:50:0x0177, B:51:0x017b, B:53:0x0181, B:55:0x0188, B:56:0x01a2, B:58:0x01a5, B:60:0x01e5, B:63:0x01f4, B:64:0x01f8, B:66:0x01fe, B:68:0x0205, B:69:0x021f, B:70:0x0220, B:71:0x0227, B:72:0x0228, B:74:0x022e, B:76:0x0235, B:78:0x024e, B:79:0x0258, B:81:0x0260, B:83:0x026c, B:84:0x026f, B:85:0x0277, B:86:0x027b, B:88:0x0287, B:89:0x02a1, B:90:0x02a2), top: B:99:0x0034, inners: #0, #1, #3, #4 }] */
    /* JADX WARN: Removed duplicated region for block: B:81:0x0260 A[Catch: UnknownHostException -> 0x00bf, TryCatch #2 {UnknownHostException -> 0x00bf, blocks: (B:7:0x0034, B:9:0x0043, B:11:0x004a, B:13:0x0052, B:17:0x0065, B:19:0x0072, B:20:0x007c, B:21:0x0080, B:24:0x0088, B:27:0x008e, B:36:0x0115, B:37:0x012f, B:39:0x0134, B:40:0x0152, B:41:0x0153, B:43:0x0159, B:45:0x0160, B:47:0x0168, B:50:0x0177, B:51:0x017b, B:53:0x0181, B:55:0x0188, B:56:0x01a2, B:58:0x01a5, B:60:0x01e5, B:63:0x01f4, B:64:0x01f8, B:66:0x01fe, B:68:0x0205, B:69:0x021f, B:70:0x0220, B:71:0x0227, B:72:0x0228, B:74:0x022e, B:76:0x0235, B:78:0x024e, B:79:0x0258, B:81:0x0260, B:83:0x026c, B:84:0x026f, B:85:0x0277, B:86:0x027b, B:88:0x0287, B:89:0x02a1, B:90:0x02a2), top: B:99:0x0034, inners: #0, #1, #3, #4 }] */
    /* JADX WARN: Removed duplicated region for block: B:84:0x026f A[Catch: UnknownHostException -> 0x00bf, TRY_LEAVE, TryCatch #2 {UnknownHostException -> 0x00bf, blocks: (B:7:0x0034, B:9:0x0043, B:11:0x004a, B:13:0x0052, B:17:0x0065, B:19:0x0072, B:20:0x007c, B:21:0x0080, B:24:0x0088, B:27:0x008e, B:36:0x0115, B:37:0x012f, B:39:0x0134, B:40:0x0152, B:41:0x0153, B:43:0x0159, B:45:0x0160, B:47:0x0168, B:50:0x0177, B:51:0x017b, B:53:0x0181, B:55:0x0188, B:56:0x01a2, B:58:0x01a5, B:60:0x01e5, B:63:0x01f4, B:64:0x01f8, B:66:0x01fe, B:68:0x0205, B:69:0x021f, B:70:0x0220, B:71:0x0227, B:72:0x0228, B:74:0x022e, B:76:0x0235, B:78:0x024e, B:79:0x0258, B:81:0x0260, B:83:0x026c, B:84:0x026f, B:85:0x0277, B:86:0x027b, B:88:0x0287, B:89:0x02a1, B:90:0x02a2), top: B:99:0x0034, inners: #0, #1, #3, #4 }] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public com.android.internal.telephony.dataconnection.DataCallResponse.SetupResult setLinkProperties(android.net.LinkProperties r19, boolean r20) {
        /*
            Method dump skipped, instructions count: 705
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.dataconnection.DataCallResponse.setLinkProperties(android.net.LinkProperties, boolean):com.android.internal.telephony.dataconnection.DataCallResponse$SetupResult");
    }
}
