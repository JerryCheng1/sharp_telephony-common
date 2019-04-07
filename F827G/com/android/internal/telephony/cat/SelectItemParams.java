package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

class SelectItemParams extends CommandParams {
    boolean mLoadTitleIcon = false;
    Menu mMenu = null;

    SelectItemParams(CommandDetails commandDetails, Menu menu, boolean z) {
        super(commandDetails);
        this.mMenu = menu;
        this.mLoadTitleIcon = z;
    }

    /* Access modifiers changed, original: 0000 */
    public boolean setIcon(Bitmap bitmap) {
        if (bitmap == null || this.mMenu == null) {
            return false;
        }
        if (!this.mLoadTitleIcon || this.mMenu.titleIcon != null) {
            for (Item item : this.mMenu.items) {
                if (item.icon == null) {
                    item.icon = bitmap;
                    break;
                }
            }
        }
        this.mMenu.titleIcon = bitmap;
        return true;
    }
}
