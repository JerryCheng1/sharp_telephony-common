package android.telephony;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.service.carrier.ICarrierMessagingService;
import android.service.carrier.ICarrierMessagingService.Stub;
import com.android.internal.util.Preconditions;

public abstract class CarrierMessagingServiceManager {
    private volatile CarrierMessagingServiceConnection mCarrierMessagingServiceConnection;

    private final class CarrierMessagingServiceConnection implements ServiceConnection {
        private CarrierMessagingServiceConnection() {
        }

        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            CarrierMessagingServiceManager.this.onServiceReady(Stub.asInterface(iBinder));
        }

        public void onServiceDisconnected(ComponentName componentName) {
        }
    }

    public boolean bindToCarrierMessagingService(Context context, String str) {
        Preconditions.checkState(this.mCarrierMessagingServiceConnection == null);
        Intent intent = new Intent("android.service.carrier.CarrierMessagingService");
        intent.setPackage(str);
        this.mCarrierMessagingServiceConnection = new CarrierMessagingServiceConnection();
        return context.bindService(intent, this.mCarrierMessagingServiceConnection, 1);
    }

    public void disposeConnection(Context context) {
        Preconditions.checkNotNull(this.mCarrierMessagingServiceConnection);
        context.unbindService(this.mCarrierMessagingServiceConnection);
        this.mCarrierMessagingServiceConnection = null;
    }

    public abstract void onServiceReady(ICarrierMessagingService iCarrierMessagingService);
}
