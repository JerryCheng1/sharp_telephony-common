package com.android.internal.telephony.gsm;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import com.android.internal.telephony.CellBroadcastHandler;
import com.android.internal.telephony.PhoneBase;
import java.util.HashMap;

public class GsmCellBroadcastHandler extends CellBroadcastHandler {
    private static final boolean VDBG = false;
    private final HashMap<SmsCbConcatInfo, byte[][]> mSmsCbPageMap = new HashMap(4);

    private static final class SmsCbConcatInfo {
        private final SmsCbHeader mHeader;
        private final SmsCbLocation mLocation;

        SmsCbConcatInfo(SmsCbHeader smsCbHeader, SmsCbLocation smsCbLocation) {
            this.mHeader = smsCbHeader;
            this.mLocation = smsCbLocation;
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof SmsCbConcatInfo)) {
                return false;
            }
            SmsCbConcatInfo smsCbConcatInfo = (SmsCbConcatInfo) obj;
            return this.mHeader.getSerialNumber() == smsCbConcatInfo.mHeader.getSerialNumber() && this.mLocation.equals(smsCbConcatInfo.mLocation);
        }

        public int hashCode() {
            return (this.mHeader.getSerialNumber() * 31) + this.mLocation.hashCode();
        }

        public boolean matchesLocation(String str, int i, int i2) {
            return this.mLocation.isInLocationArea(str, i, i2);
        }
    }

    protected GsmCellBroadcastHandler(Context context, PhoneBase phoneBase) {
        super("GsmCellBroadcastHandler", context, phoneBase);
        phoneBase.mCi.setOnNewGsmBroadcastSms(getHandler(), 1, null);
    }

    private android.telephony.SmsCbMessage handleGsmBroadcastSms(android.os.AsyncResult r12) {
        /*
        r11 = this;
        r2 = 0;
        r10 = 1;
        r3 = 0;
        r4 = -1;
        r0 = r12.result;	 Catch:{ RuntimeException -> 0x00ba }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x00ba }
        r0 = (byte[]) r0;	 Catch:{ RuntimeException -> 0x00ba }
        r7 = new com.android.internal.telephony.gsm.SmsCbHeader;	 Catch:{ RuntimeException -> 0x00ba }
        r7.<init>(r0);	 Catch:{ RuntimeException -> 0x00ba }
        r1 = "gsm.operator.numeric";
        r8 = android.os.SystemProperties.get(r1);	 Catch:{ RuntimeException -> 0x00ba }
        r1 = r11.mPhone;	 Catch:{ RuntimeException -> 0x00ba }
        r1 = r1.getCellLocation();	 Catch:{ RuntimeException -> 0x00ba }
        r5 = r1 instanceof android.telephony.gsm.GsmCellLocation;	 Catch:{ RuntimeException -> 0x00ba }
        if (r5 == 0) goto L_0x00d0;
    L_0x001f:
        r1 = (android.telephony.gsm.GsmCellLocation) r1;	 Catch:{ RuntimeException -> 0x00ba }
        r4 = r1.getLac();	 Catch:{ RuntimeException -> 0x00ba }
        r1 = r1.getCid();	 Catch:{ RuntimeException -> 0x00ba }
        r5 = r4;
        r6 = r1;
    L_0x002b:
        r1 = r7.getGeographicalScope();	 Catch:{ RuntimeException -> 0x00ba }
        switch(r1) {
            case 0: goto L_0x006e;
            case 1: goto L_0x0032;
            case 2: goto L_0x0066;
            case 3: goto L_0x006e;
            default: goto L_0x0032;
        };	 Catch:{ RuntimeException -> 0x00ba }
    L_0x0032:
        r1 = new android.telephony.SmsCbLocation;	 Catch:{ RuntimeException -> 0x00ba }
        r1.<init>(r8);	 Catch:{ RuntimeException -> 0x00ba }
        r4 = r1;
    L_0x0038:
        r9 = r7.getNumberOfPages();	 Catch:{ RuntimeException -> 0x00ba }
        if (r9 <= r10) goto L_0x00c2;
    L_0x003e:
        r10 = new com.android.internal.telephony.gsm.GsmCellBroadcastHandler$SmsCbConcatInfo;	 Catch:{ RuntimeException -> 0x00ba }
        r10.<init>(r7, r4);	 Catch:{ RuntimeException -> 0x00ba }
        r1 = r11.mSmsCbPageMap;	 Catch:{ RuntimeException -> 0x00ba }
        r1 = r1.get(r10);	 Catch:{ RuntimeException -> 0x00ba }
        r1 = (byte[][]) r1;	 Catch:{ RuntimeException -> 0x00ba }
        if (r1 != 0) goto L_0x0054;
    L_0x004d:
        r1 = new byte[r9][];	 Catch:{ RuntimeException -> 0x00ba }
        r9 = r11.mSmsCbPageMap;	 Catch:{ RuntimeException -> 0x00ba }
        r9.put(r10, r1);	 Catch:{ RuntimeException -> 0x00ba }
    L_0x0054:
        r9 = r7.getPageIndex();	 Catch:{ RuntimeException -> 0x00ba }
        r9 = r9 + -1;
        r1[r9] = r0;	 Catch:{ RuntimeException -> 0x00ba }
        r9 = r1.length;	 Catch:{ RuntimeException -> 0x00ba }
        r0 = r3;
    L_0x005e:
        if (r0 >= r9) goto L_0x0075;
    L_0x0060:
        r3 = r1[r0];
        if (r3 != 0) goto L_0x00cd;
    L_0x0064:
        r0 = r2;
    L_0x0065:
        return r0;
    L_0x0066:
        r1 = new android.telephony.SmsCbLocation;	 Catch:{ RuntimeException -> 0x00ba }
        r4 = -1;
        r1.<init>(r8, r5, r4);	 Catch:{ RuntimeException -> 0x00ba }
        r4 = r1;
        goto L_0x0038;
    L_0x006e:
        r1 = new android.telephony.SmsCbLocation;	 Catch:{ RuntimeException -> 0x00ba }
        r1.<init>(r8, r5, r6);	 Catch:{ RuntimeException -> 0x00ba }
        r4 = r1;
        goto L_0x0038;
    L_0x0075:
        r0 = r11.mSmsCbPageMap;	 Catch:{ RuntimeException -> 0x00ba }
        r0.remove(r10);	 Catch:{ RuntimeException -> 0x00ba }
    L_0x007a:
        r0 = com.android.internal.telephony.TelBrand.IS_DCM;	 Catch:{ RuntimeException -> 0x00ba }
        if (r0 == 0) goto L_0x009a;
    L_0x007e:
        r0 = r11.mPhone;	 Catch:{ RuntimeException -> 0x00ba }
        r0 = r0 instanceof com.android.internal.telephony.gsm.GSMPhone;	 Catch:{ RuntimeException -> 0x00ba }
        if (r0 == 0) goto L_0x009a;
    L_0x0084:
        r0 = r11.mPhone;	 Catch:{ RuntimeException -> 0x00ba }
        r0 = (com.android.internal.telephony.gsm.GSMPhone) r0;	 Catch:{ RuntimeException -> 0x00ba }
        r0 = r0.isReceivedMessage(r7);	 Catch:{ RuntimeException -> 0x00ba }
        if (r0 != 0) goto L_0x009a;
    L_0x008e:
        r0 = new android.content.Intent;	 Catch:{ RuntimeException -> 0x00ba }
        r3 = "jp.co.sharp.android.intent.action.NOTICE_TO_SAVING_ENERGY_SETTING";
        r0.<init>(r3);	 Catch:{ RuntimeException -> 0x00ba }
        r3 = r11.mContext;	 Catch:{ RuntimeException -> 0x00ba }
        r3.sendBroadcast(r0);	 Catch:{ RuntimeException -> 0x00ba }
    L_0x009a:
        r0 = r11.mSmsCbPageMap;	 Catch:{ RuntimeException -> 0x00ba }
        r0 = r0.keySet();	 Catch:{ RuntimeException -> 0x00ba }
        r3 = r0.iterator();	 Catch:{ RuntimeException -> 0x00ba }
    L_0x00a4:
        r0 = r3.hasNext();	 Catch:{ RuntimeException -> 0x00ba }
        if (r0 == 0) goto L_0x00c8;
    L_0x00aa:
        r0 = r3.next();	 Catch:{ RuntimeException -> 0x00ba }
        r0 = (com.android.internal.telephony.gsm.GsmCellBroadcastHandler.SmsCbConcatInfo) r0;	 Catch:{ RuntimeException -> 0x00ba }
        r0 = r0.matchesLocation(r8, r5, r6);	 Catch:{ RuntimeException -> 0x00ba }
        if (r0 != 0) goto L_0x00a4;
    L_0x00b6:
        r3.remove();	 Catch:{ RuntimeException -> 0x00ba }
        goto L_0x00a4;
    L_0x00ba:
        r0 = move-exception;
        r1 = "Error in decoding SMS CB pdu";
        r11.loge(r1, r0);
        r0 = r2;
        goto L_0x0065;
    L_0x00c2:
        r1 = 1;
        r1 = new byte[r1][];	 Catch:{ RuntimeException -> 0x00ba }
        r1[r3] = r0;
        goto L_0x007a;
    L_0x00c8:
        r0 = com.android.internal.telephony.gsm.GsmSmsCbMessage.createSmsCbMessage(r7, r4, r1);	 Catch:{ RuntimeException -> 0x00ba }
        goto L_0x0065;
    L_0x00cd:
        r0 = r0 + 1;
        goto L_0x005e;
    L_0x00d0:
        r5 = r4;
        r6 = r4;
        goto L_0x002b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.gsm.GsmCellBroadcastHandler.handleGsmBroadcastSms(android.os.AsyncResult):android.telephony.SmsCbMessage");
    }

    public static GsmCellBroadcastHandler makeGsmCellBroadcastHandler(Context context, PhoneBase phoneBase) {
        GsmCellBroadcastHandler gsmCellBroadcastHandler = new GsmCellBroadcastHandler(context, phoneBase);
        gsmCellBroadcastHandler.start();
        return gsmCellBroadcastHandler;
    }

    /* Access modifiers changed, original: protected */
    public boolean handleSmsMessage(Message message) {
        if (message.obj instanceof AsyncResult) {
            SmsCbMessage handleGsmBroadcastSms = handleGsmBroadcastSms((AsyncResult) message.obj);
            if (handleGsmBroadcastSms != null) {
                handleBroadcastSms(handleGsmBroadcastSms);
                return true;
            }
        }
        return super.handleSmsMessage(message);
    }

    /* Access modifiers changed, original: protected */
    public void onQuitting() {
        this.mPhone.mCi.unSetOnNewGsmBroadcastSms(getHandler());
        super.onQuitting();
    }
}
