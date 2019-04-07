package com.android.internal.telephony.cat;

class DeviceIdentities extends ValueObject {
    public int destinationId;
    public int sourceId;

    DeviceIdentities() {
    }

    /* Access modifiers changed, original: 0000 */
    public ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.DEVICE_IDENTITIES;
    }
}