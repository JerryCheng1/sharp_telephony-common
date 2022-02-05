package com.android.internal.telephony.cat;

/* compiled from: CommandDetails.java */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
class IconId extends ValueObject {
    int recordNumber;
    boolean selfExplanatory;

    @Override // com.android.internal.telephony.cat.ValueObject
    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.ICON_ID;
    }
}
