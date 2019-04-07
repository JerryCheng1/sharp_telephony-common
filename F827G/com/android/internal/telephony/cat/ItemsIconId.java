package com.android.internal.telephony.cat;

class ItemsIconId extends ValueObject {
    int[] recordNumbers;
    boolean selfExplanatory;

    ItemsIconId() {
    }

    /* Access modifiers changed, original: 0000 */
    public ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.ITEM_ICON_ID_LIST;
    }
}
