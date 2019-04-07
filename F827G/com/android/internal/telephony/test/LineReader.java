package com.android.internal.telephony.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

class LineReader {
    static final int BUFFER_SIZE = 4096;
    byte[] mBuffer = new byte[4096];
    InputStream mInStream;

    LineReader(InputStream inputStream) {
        this.mInStream = inputStream;
    }

    /* Access modifiers changed, original: 0000 */
    public String getNextLine() {
        return getNextLine(false);
    }

    /* Access modifiers changed, original: 0000 */
    public String getNextLine(boolean z) {
        int i = 0;
        while (true) {
            try {
                int read = this.mInStream.read();
                if (read < 0) {
                    return null;
                }
                if (!(z && read == 26)) {
                    if (read != 13 && read != 10) {
                        this.mBuffer[i] = (byte) read;
                        i++;
                    } else if (i == 0) {
                    }
                }
                try {
                    return new String(this.mBuffer, 0, i, "US-ASCII");
                } catch (UnsupportedEncodingException e) {
                    System.err.println("ATChannel: implausable UnsupportedEncodingException");
                    return null;
                }
            } catch (IOException e2) {
                return null;
            } catch (IndexOutOfBoundsException e3) {
                System.err.println("ATChannel: buffer overflow");
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public String getNextLineCtrlZ() {
        return getNextLine(true);
    }
}
