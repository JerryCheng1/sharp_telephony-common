package com.android.internal.telephony.uicc;

public class IccCardStatus {
    public static final int CARD_MAX_APPS = 8;
    public IccCardApplicationStatus[] mApplications;
    public CardState mCardState;
    public int mCdmaSubscriptionAppIndex;
    public int mGsmUmtsSubscriptionAppIndex;
    public int mImsSubscriptionAppIndex;
    public PinState mUniversalPinState;

    public enum CardState {
        CARDSTATE_ABSENT,
        CARDSTATE_PRESENT,
        CARDSTATE_ERROR;

        /* Access modifiers changed, original: 0000 */
        public boolean isCardPresent() {
            return this == CARDSTATE_PRESENT;
        }
    }

    public enum PinState {
        PINSTATE_UNKNOWN,
        PINSTATE_ENABLED_NOT_VERIFIED,
        PINSTATE_ENABLED_VERIFIED,
        PINSTATE_DISABLED,
        PINSTATE_ENABLED_BLOCKED,
        PINSTATE_ENABLED_PERM_BLOCKED;

        /* Access modifiers changed, original: 0000 */
        public boolean isPermBlocked() {
            return this == PINSTATE_ENABLED_PERM_BLOCKED;
        }

        /* Access modifiers changed, original: 0000 */
        public boolean isPinRequired() {
            return this == PINSTATE_ENABLED_NOT_VERIFIED;
        }

        /* Access modifiers changed, original: 0000 */
        public boolean isPukRequired() {
            return this == PINSTATE_ENABLED_BLOCKED;
        }
    }

    public void setCardState(int state) {
        switch (state) {
            case 0:
                this.mCardState = CardState.CARDSTATE_ABSENT;
                return;
            case 1:
                this.mCardState = CardState.CARDSTATE_PRESENT;
                return;
            case 2:
                this.mCardState = CardState.CARDSTATE_ERROR;
                return;
            default:
                throw new RuntimeException("Unrecognized RIL_CardState: " + state);
        }
    }

    public void setUniversalPinState(int state) {
        switch (state) {
            case 0:
                this.mUniversalPinState = PinState.PINSTATE_UNKNOWN;
                return;
            case 1:
                this.mUniversalPinState = PinState.PINSTATE_ENABLED_NOT_VERIFIED;
                return;
            case 2:
                this.mUniversalPinState = PinState.PINSTATE_ENABLED_VERIFIED;
                return;
            case 3:
                this.mUniversalPinState = PinState.PINSTATE_DISABLED;
                return;
            case 4:
                this.mUniversalPinState = PinState.PINSTATE_ENABLED_BLOCKED;
                return;
            case 5:
                this.mUniversalPinState = PinState.PINSTATE_ENABLED_PERM_BLOCKED;
                return;
            default:
                throw new RuntimeException("Unrecognized RIL_PinState: " + state);
        }
    }

    public String toString() {
        IccCardApplicationStatus app;
        StringBuilder sb = new StringBuilder();
        sb.append("IccCardState {").append(this.mCardState).append(",").append(this.mUniversalPinState).append(",num_apps=").append(this.mApplications.length).append(",gsm_id=").append(this.mGsmUmtsSubscriptionAppIndex);
        if (this.mGsmUmtsSubscriptionAppIndex >= 0 && this.mGsmUmtsSubscriptionAppIndex < 8) {
            app = this.mApplications[this.mGsmUmtsSubscriptionAppIndex];
            if (app == null) {
                app = "null";
            }
            sb.append(app);
        }
        sb.append(",cdma_id=").append(this.mCdmaSubscriptionAppIndex);
        if (this.mCdmaSubscriptionAppIndex >= 0 && this.mCdmaSubscriptionAppIndex < 8) {
            app = this.mApplications[this.mCdmaSubscriptionAppIndex];
            if (app == null) {
                app = "null";
            }
            sb.append(app);
        }
        sb.append(",ims_id=").append(this.mImsSubscriptionAppIndex);
        if (this.mImsSubscriptionAppIndex >= 0 && this.mImsSubscriptionAppIndex < 8) {
            app = this.mApplications[this.mImsSubscriptionAppIndex];
            if (app == null) {
                app = "null";
            }
            sb.append(app);
        }
        sb.append("}");
        return sb.toString();
    }
}
