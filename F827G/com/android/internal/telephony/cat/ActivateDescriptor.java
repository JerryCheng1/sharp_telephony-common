package com.android.internal.telephony.cat;

class ActivateDescriptor extends ValueObject {
    public int target;

    ActivateDescriptor() {
    }

    /* Access modifiers changed, original: 0000 */
    public ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.ACTIVATE_DESCRIPTOR;
    }
}
