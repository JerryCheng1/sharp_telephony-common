package com.android.internal.telephony;

public class ATResponseParser {
    private String mLine;
    private int mNext = 0;
    private int mTokEnd;
    private int mTokStart;

    public ATResponseParser(String str) {
        this.mLine = str;
    }

    private void nextTok() {
        int length = this.mLine.length();
        if (this.mNext == 0) {
            skipPrefix();
        }
        if (this.mNext >= length) {
            throw new ATParseEx();
        }
        try {
            String str = this.mLine;
            int i = this.mNext;
            this.mNext = i + 1;
            char skipWhiteSpace = skipWhiteSpace(str.charAt(i));
            if (skipWhiteSpace != '\"') {
                this.mTokStart = this.mNext - 1;
                this.mTokEnd = this.mTokStart;
                while (skipWhiteSpace != ',') {
                    if (!Character.isWhitespace(skipWhiteSpace)) {
                        this.mTokEnd = this.mNext;
                    }
                    if (this.mNext != length) {
                        str = this.mLine;
                        i = this.mNext;
                        this.mNext = i + 1;
                        skipWhiteSpace = str.charAt(i);
                    } else {
                        return;
                    }
                }
            } else if (this.mNext >= length) {
                throw new ATParseEx();
            } else {
                str = this.mLine;
                i = this.mNext;
                this.mNext = i + 1;
                skipWhiteSpace = str.charAt(i);
                this.mTokStart = this.mNext - 1;
                while (skipWhiteSpace != '\"' && this.mNext < length) {
                    str = this.mLine;
                    i = this.mNext;
                    this.mNext = i + 1;
                    skipWhiteSpace = str.charAt(i);
                }
                if (skipWhiteSpace != '\"') {
                    throw new ATParseEx();
                }
                this.mTokEnd = this.mNext - 1;
                if (this.mNext < length) {
                    str = this.mLine;
                    length = this.mNext;
                    this.mNext = length + 1;
                    if (str.charAt(length) != ',') {
                        throw new ATParseEx();
                    }
                }
            }
        } catch (StringIndexOutOfBoundsException e) {
            throw new ATParseEx();
        }
    }

    private void skipPrefix() {
        this.mNext = 0;
        int length = this.mLine.length();
        while (this.mNext < length) {
            String str = this.mLine;
            int i = this.mNext;
            this.mNext = i + 1;
            if (str.charAt(i) == ':') {
                return;
            }
        }
        throw new ATParseEx("missing prefix");
    }

    private char skipWhiteSpace(char c) {
        int length = this.mLine.length();
        while (this.mNext < length && Character.isWhitespace(c)) {
            String str = this.mLine;
            int i = this.mNext;
            this.mNext = i + 1;
            c = str.charAt(i);
        }
        if (!Character.isWhitespace(c)) {
            return c;
        }
        throw new ATParseEx();
    }

    public boolean hasMore() {
        return this.mNext < this.mLine.length();
    }

    public boolean nextBoolean() {
        nextTok();
        if (this.mTokEnd - this.mTokStart > 1) {
            throw new ATParseEx();
        }
        char charAt = this.mLine.charAt(this.mTokStart);
        if (charAt == '0') {
            return false;
        }
        if (charAt == '1') {
            return true;
        }
        throw new ATParseEx();
    }

    public int nextInt() {
        int i = 0;
        nextTok();
        for (int i2 = this.mTokStart; i2 < this.mTokEnd; i2++) {
            char charAt = this.mLine.charAt(i2);
            if (charAt < '0' || charAt > '9') {
                throw new ATParseEx();
            }
            i = (i * 10) + (charAt - 48);
        }
        return i;
    }

    public String nextString() {
        nextTok();
        return this.mLine.substring(this.mTokStart, this.mTokEnd);
    }
}
