package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import java.util.Iterator;

/* compiled from: CommandParams.java */
/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
class SelectItemParams extends CommandParams {
    boolean mLoadTitleIcon;
    Menu mMenu;

    /* JADX INFO: Access modifiers changed from: package-private */
    public SelectItemParams(CommandDetails cmdDet, Menu menu, boolean loadTitleIcon) {
        super(cmdDet);
        this.mMenu = null;
        this.mLoadTitleIcon = false;
        this.mMenu = menu;
        this.mLoadTitleIcon = loadTitleIcon;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.android.internal.telephony.cat.CommandParams
    public boolean setIcon(Bitmap icon) {
        if (icon == null || this.mMenu == null) {
            return false;
        }
        if (!this.mLoadTitleIcon || this.mMenu.titleIcon != null) {
            Iterator i$ = this.mMenu.items.iterator();
            while (true) {
                if (!i$.hasNext()) {
                    break;
                }
                Item item = i$.next();
                if (item.icon == null) {
                    item.icon = icon;
                    break;
                }
            }
        } else {
            this.mMenu.titleIcon = icon;
        }
        return true;
    }
}
