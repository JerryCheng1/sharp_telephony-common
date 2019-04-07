package com.android.internal.telephony.cat;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

class CommandDetails extends ValueObject implements Parcelable {
    public static final Creator<CommandDetails> CREATOR = new Creator<CommandDetails>() {
        public CommandDetails createFromParcel(Parcel parcel) {
            return new CommandDetails(parcel);
        }

        public CommandDetails[] newArray(int i) {
            return new CommandDetails[i];
        }
    };
    public int commandNumber;
    public int commandQualifier;
    public boolean compRequired;
    public int typeOfCommand;

    CommandDetails() {
    }

    public CommandDetails(Parcel parcel) {
        this.compRequired = parcel.readInt() != 0;
        this.commandNumber = parcel.readInt();
        this.typeOfCommand = parcel.readInt();
        this.commandQualifier = parcel.readInt();
    }

    public boolean compareTo(CommandDetails commandDetails) {
        return this.compRequired == commandDetails.compRequired && this.commandNumber == commandDetails.commandNumber && this.commandQualifier == commandDetails.commandQualifier && this.typeOfCommand == commandDetails.typeOfCommand;
    }

    public int describeContents() {
        return 0;
    }

    public ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.COMMAND_DETAILS;
    }

    public String toString() {
        return "CmdDetails: compRequired=" + this.compRequired + " commandNumber=" + this.commandNumber + " typeOfCommand=" + this.typeOfCommand + " commandQualifier=" + this.commandQualifier;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.compRequired ? 1 : 0);
        parcel.writeInt(this.commandNumber);
        parcel.writeInt(this.typeOfCommand);
        parcel.writeInt(this.commandQualifier);
    }
}
