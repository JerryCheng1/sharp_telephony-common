package com.android.internal.telephony.cat;

abstract class ValueObject {
    ValueObject() {
    }

    public abstract ComprehensionTlvTag getTag();
}
