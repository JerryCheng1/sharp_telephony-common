package com.android.internal.telephony.sip;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public abstract class SipCallBase extends Call {
    protected abstract void setState(Call.State state);

    @Override // com.android.internal.telephony.Call
    public List<Connection> getConnections() {
        return this.mConnections;
    }

    @Override // com.android.internal.telephony.Call
    public boolean isMultiparty() {
        return this.mConnections.size() > 1;
    }

    public String toString() {
        return this.mState.toString() + ":" + super.toString();
    }

    void clearDisconnected() {
        Iterator<Connection> it = this.mConnections.iterator();
        while (it.hasNext()) {
            if (it.next().getState() == Call.State.DISCONNECTED) {
                it.remove();
            }
        }
        if (this.mConnections.isEmpty()) {
            setState(Call.State.IDLE);
        }
    }
}
