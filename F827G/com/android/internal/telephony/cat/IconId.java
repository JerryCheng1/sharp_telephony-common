package com.android.internal.telephony.cat;

class IconId extends ValueObject {
    int recordNumber;
    boolean selfExplanatory;

    IconId() {
    }

    /* Access modifiers changed, original: 0000 */
    public ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.ICON_ID;
    }
}
