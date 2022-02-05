package com.android.internal.telephony.cat;

/* compiled from: CommandDetails.java */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
class ActivateDescriptor extends ValueObject {
    public int target;

    @Override // com.android.internal.telephony.cat.ValueObject
    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.ACTIVATE_DESCRIPTOR;
    }
}
