package com.android.internal.telephony.dataconnection;

import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.os.SystemProperties;
import android.telephony.Rlog;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class DataCallResponse {
    private final boolean DBG = true;
    private final String LOG_TAG = "DataCallResponse";
    public int active = 0;
    public String[] addresses = new String[0];
    public int cid = 0;
    public String[] dnses = new String[0];
    public String[] gateways = new String[0];
    public String ifname = "";
    public int mtu = 0;
    public String[] pcscf = new String[0];
    public int status = 0;
    public int suggestedRetryTime = -1;
    public String type = "";
    public int version = 0;

    public enum SetupResult {
        SUCCESS,
        ERR_BadCommand,
        ERR_UnacceptableParameter,
        ERR_GetLastErrorFromRil,
        ERR_Stale,
        ERR_RilError;
        
        public DcFailCause mFailCause;

        public String toString() {
            return name() + "  SetupResult.mFailCause=" + this.mFailCause;
        }
    }

    public SetupResult setLinkProperties(LinkProperties linkProperties, boolean z) {
        SetupResult setupResult;
        int i = 0;
        if (linkProperties == null) {
            linkProperties = new LinkProperties();
        } else {
            linkProperties.clear();
        }
        if (this.status == DcFailCause.NONE.getErrorCode()) {
            String str = "net." + this.ifname + ".";
            String trim;
            String trim2;
            String trim3;
            try {
                linkProperties.setInterfaceName(this.ifname);
                if (this.addresses == null || this.addresses.length <= 0) {
                    throw new UnknownHostException("no address for ifname=" + this.ifname);
                }
                String str2;
                String[] split;
                int parseInt;
                for (String str22 : this.addresses) {
                    trim = str22.trim();
                    if (!trim.isEmpty()) {
                        split = trim.split("/");
                        if (split.length == 2) {
                            trim = split[0];
                            parseInt = Integer.parseInt(split[1]);
                        } else {
                            parseInt = 0;
                        }
                        InetAddress numericToInetAddress = NetworkUtils.numericToInetAddress(trim);
                        if (!numericToInetAddress.isAnyLocalAddress()) {
                            if (parseInt == 0) {
                                parseInt = numericToInetAddress instanceof Inet4Address ? 32 : 128;
                            }
                            Rlog.d("DataCallResponse", "addr/pl=" + trim + "/" + parseInt);
                            linkProperties.addLinkAddress(new LinkAddress(numericToInetAddress, parseInt));
                        }
                    }
                }
                InetAddress numericToInetAddress2;
                if (this.dnses != null && this.dnses.length > 0) {
                    for (String trim22 : this.dnses) {
                        trim22 = trim22.trim();
                        if (!trim22.isEmpty()) {
                            numericToInetAddress2 = NetworkUtils.numericToInetAddress(trim22);
                            if (!numericToInetAddress2.isAnyLocalAddress()) {
                                linkProperties.addDnsServer(numericToInetAddress2);
                            }
                        }
                    }
                } else if (z) {
                    for (String trim222 : new String[]{SystemProperties.get(str + "dns1"), SystemProperties.get(str + "dns2")}) {
                        trim222 = trim222.trim();
                        if (!trim222.isEmpty()) {
                            numericToInetAddress2 = NetworkUtils.numericToInetAddress(trim222);
                            if (!numericToInetAddress2.isAnyLocalAddress()) {
                                linkProperties.addDnsServer(numericToInetAddress2);
                            }
                        }
                    }
                } else {
                    throw new UnknownHostException("Empty dns response and no system default dns");
                }
                if (this.gateways == null || this.gateways.length == 0) {
                    str22 = SystemProperties.get(str + "gw");
                    if (str22 != null) {
                        this.gateways = str22.split(" ");
                    } else {
                        this.gateways = new String[0];
                    }
                }
                split = this.gateways;
                int length = split.length;
                while (i < length) {
                    trim3 = split[i].trim();
                    if (!trim3.isEmpty()) {
                        linkProperties.addRoute(new RouteInfo(NetworkUtils.numericToInetAddress(trim3)));
                    }
                    i++;
                }
                linkProperties.setMtu(this.mtu);
                setupResult = SetupResult.SUCCESS;
            } catch (IllegalArgumentException e) {
                throw new UnknownHostException("Non-numeric gateway addr=" + trim3);
            } catch (IllegalArgumentException e2) {
                throw new UnknownHostException("Non-numeric dns addr=" + trim222);
            } catch (IllegalArgumentException e3) {
                throw new UnknownHostException("Non-numeric dns addr=" + trim222);
            } catch (IllegalArgumentException e4) {
                throw new UnknownHostException("Non-numeric ip addr=" + trim);
            } catch (UnknownHostException e5) {
                Rlog.d("DataCallResponse", "setLinkProperties: UnknownHostException " + e5);
                e5.printStackTrace();
                setupResult = SetupResult.ERR_UnacceptableParameter;
            }
        } else {
            setupResult = this.version < 4 ? SetupResult.ERR_GetLastErrorFromRil : SetupResult.ERR_RilError;
        }
        if (setupResult != SetupResult.SUCCESS) {
            Rlog.d("DataCallResponse", "setLinkProperties: error clearing LinkProperties status=" + this.status + " result=" + setupResult);
            linkProperties.clear();
        }
        return setupResult;
    }

    public String toString() {
        int i = 0;
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("DataCallResponse: {").append("version=").append(this.version).append(" status=").append(this.status).append(" retry=").append(this.suggestedRetryTime).append(" cid=").append(this.cid).append(" active=").append(this.active).append(" type=").append(this.type).append(" ifname=").append(this.ifname).append(" mtu=").append(this.mtu).append(" addresses=[");
        for (String append : this.addresses) {
            stringBuffer.append(append);
            stringBuffer.append(",");
        }
        if (this.addresses.length > 0) {
            stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        }
        stringBuffer.append("] dnses=[");
        for (String append2 : this.dnses) {
            stringBuffer.append(append2);
            stringBuffer.append(",");
        }
        if (this.dnses.length > 0) {
            stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        }
        stringBuffer.append("] gateways=[");
        for (String append22 : this.gateways) {
            stringBuffer.append(append22);
            stringBuffer.append(",");
        }
        if (this.gateways.length > 0) {
            stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        }
        stringBuffer.append("] pcscf=[");
        String[] strArr = this.pcscf;
        int length = strArr.length;
        while (i < length) {
            stringBuffer.append(strArr[i]);
            stringBuffer.append(",");
            i++;
        }
        if (this.pcscf.length > 0) {
            stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        }
        stringBuffer.append("]}");
        return stringBuffer.toString();
    }
}
