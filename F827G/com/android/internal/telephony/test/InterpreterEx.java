package com.android.internal.telephony.test;

class InterpreterEx extends Exception {
    String mResult;

    public InterpreterEx(String str) {
        this.mResult = str;
    }
}
