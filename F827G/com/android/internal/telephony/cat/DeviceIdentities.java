package com.android.internal.telephony.cat;

/* compiled from: CommandDetails.java */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
class DeviceIdentities extends ValueObject {
    public int destinationId;
    public int sourceId;

    @Override // com.android.internal.telephony.cat.ValueObject
    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.DEVICE_IDENTITIES;
    }
}
