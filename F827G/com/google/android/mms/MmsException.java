package com.google.android.mms;

public class MmsException extends Exception {
    private static final long serialVersionUID = -7323249827281485390L;

    public MmsException(String str) {
        super(str);
    }

    public MmsException(String str, Throwable th) {
        super(str, th);
    }

    public MmsException(Throwable th) {
        super(th);
    }
}
