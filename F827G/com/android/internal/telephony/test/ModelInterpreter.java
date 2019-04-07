package com.android.internal.telephony.test;

import android.os.HandlerThread;
import android.os.Looper;
import android.telephony.Rlog;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class ModelInterpreter implements Runnable, SimulatedRadioControl {
    static final int CONNECTING_PAUSE_MSEC = 500;
    static final String LOG_TAG = "ModelInterpreter";
    static final int MAX_CALLS = 6;
    static final int PROGRESS_CALL_STATE = 1;
    static final String[][] sDefaultResponses;
    private String mFinalResponse;
    HandlerThread mHandlerThread;
    InputStream mIn;
    LineReader mLineReader;
    OutputStream mOut;
    int mPausedResponseCount;
    Object mPausedResponseMonitor;
    ServerSocket mSS;
    SimulatedGsmCallState mSimulatedCallState;

    static {
        String[] strArr = new String[]{"E0Q0V1", null};
        String[] strArr2 = new String[]{"+CMEE=2", null};
        String[] strArr3 = new String[]{"+CREG=2", null};
        String[] strArr4 = new String[]{"+CGREG=2", null};
        String[] strArr5 = new String[]{"+CCWA=1", null};
        String[] strArr6 = new String[]{"+COPS=0", null};
        String[] strArr7 = new String[]{"+CFUN=1", null};
        String[] strArr8 = new String[]{"+CGMM", "+CGMM: Android Model AT Interpreter\r"};
        String[] strArr9 = new String[]{"+CGMR", "+CGMR: 1.0\r"};
        String[] strArr10 = new String[]{"+CGSN", "000000000000000\r"};
        String[] strArr11 = new String[]{"+CSCS=?", "+CSCS: (\"HEX\",\"UCS2\")\r"};
        String[] strArr12 = new String[]{"+CFUN?", "+CFUN: 1\r"};
        String[] strArr13 = new String[]{"+CGREG?", "+CGREG: 2,0\r"};
        String[] strArr14 = new String[]{"+CSQ", "+CSQ: 16,99\r"};
        String[] strArr15 = new String[]{"+CNMI?", "+CNMI: 1,2,2,1,1\r"};
        String[] strArr16 = new String[]{"+CLIR?", "+CLIR: 1,3\r"};
        String[] strArr17 = new String[]{"%CPVWI=2", "%CPVWI: 0\r"};
        String[] strArr18 = new String[]{"+CUSD=1,\"#646#\"", "+CUSD=0,\"You have used 23 minutes\"\r"};
        String[] strArr19 = new String[]{"+CRSM=176,12258,0,0,10", "+CRSM: 144,0,981062200050259429F6\r"};
        String[] strArr20 = new String[]{"+CRSM=192,12258,0,0,15", "+CRSM: 144,0,0000000A2FE204000FF55501020000\r"};
        String[] strArr21 = new String[]{"+CRSM=192,28474,0,0,15", "+CRSM: 144,0,0000005a6f3a040011f5220102011e\r"};
        String[] strArr22 = new String[]{"+CRSM=178,28474,1,4,30", "+CRSM: 144,0,437573746f6d65722043617265ffffff07818100398799f7ffffffffffff\r"};
        String[] strArr23 = new String[]{"+CRSM=178,28474,2,4,30", "+CRSM: 144,0,566f696365204d61696cffffffffffff07918150367742f3ffffffffffff\r"};
        String[] strArr24 = new String[]{"+CRSM=178,28490,1,4,13", "+CRSM: 144,0,0206092143658709ffffffffff\r"};
        r25 = new String[31][];
        r25[7] = new String[]{"+CGMI", "+CGMI: Android Model AT Interpreter\r"};
        r25[8] = strArr8;
        r25[9] = strArr9;
        r25[10] = strArr10;
        r25[11] = new String[]{"+CIMI", "320720000000000\r"};
        r25[12] = strArr11;
        r25[13] = strArr12;
        r25[14] = new String[]{"+COPS=3,0;+COPS?;+COPS=3,1;+COPS?;+COPS=3,2;+COPS?", "+COPS: 0,0,\"Android\"\r+COPS: 0,1,\"Android\"\r+COPS: 0,2,\"310995\"\r"};
        r25[15] = new String[]{"+CREG?", "+CREG: 2,5, \"0113\", \"6614\"\r"};
        r25[16] = strArr13;
        r25[17] = strArr14;
        r25[18] = strArr15;
        r25[19] = strArr16;
        r25[20] = strArr17;
        r25[21] = strArr18;
        r25[22] = strArr19;
        r25[23] = strArr20;
        r25[24] = strArr21;
        r25[25] = strArr22;
        r25[26] = strArr23;
        r25[27] = new String[]{"+CRSM=178,28474,3,4,30", "+CRSM: 144,0,4164676a6dffffffffffffffffffffff0b918188551512c221436587ff01\r"};
        r25[28] = new String[]{"+CRSM=178,28474,4,4,30", "+CRSM: 144,0,810101c1ffffffffffffffffffffffff068114455245f8ffffffffffffff\r"};
        r25[29] = new String[]{"+CRSM=192,28490,0,0,15", "+CRSM: 144,0,000000416f4a040011f5550102010d\r"};
        r25[30] = strArr24;
        sDefaultResponses = r25;
    }

    public ModelInterpreter(InputStream inputStream, OutputStream outputStream) {
        this.mPausedResponseMonitor = new Object();
        this.mIn = inputStream;
        this.mOut = outputStream;
        init();
    }

    public ModelInterpreter(InetSocketAddress inetSocketAddress) throws IOException {
        this.mPausedResponseMonitor = new Object();
        this.mSS = new ServerSocket();
        this.mSS.setReuseAddress(true);
        this.mSS.bind(inetSocketAddress);
        init();
    }

    private void init() {
        new Thread(this, LOG_TAG).start();
        this.mHandlerThread = new HandlerThread(LOG_TAG);
        this.mHandlerThread.start();
        this.mSimulatedCallState = new SimulatedGsmCallState(this.mHandlerThread.getLooper());
    }

    private void onAnswer() throws InterpreterEx {
        if (!this.mSimulatedCallState.onAnswer()) {
            throw new InterpreterEx("ERROR");
        }
    }

    private void onCHLD(String str) throws InterpreterEx {
        char c = 0;
        char charAt = str.charAt(6);
        if (str.length() >= 8) {
            c = str.charAt(7);
        }
        if (!this.mSimulatedCallState.onChld(charAt, c)) {
            throw new InterpreterEx("ERROR");
        }
    }

    private void onCLCC() {
        List clccLines = this.mSimulatedCallState.getClccLines();
        int size = clccLines.size();
        for (int i = 0; i < size; i++) {
            println((String) clccLines.get(i));
        }
    }

    private void onDial(String str) throws InterpreterEx {
        if (!this.mSimulatedCallState.onDial(str.substring(1))) {
            throw new InterpreterEx("ERROR");
        }
    }

    private void onHangup() throws InterpreterEx {
        if (this.mSimulatedCallState.onAnswer()) {
            this.mFinalResponse = "NO CARRIER";
            return;
        }
        throw new InterpreterEx("ERROR");
    }

    private void onSMSSend(String str) {
        print("> ");
        this.mLineReader.getNextLineCtrlZ();
        println("+CMGS: 1");
    }

    public void pauseResponses() {
        synchronized (this.mPausedResponseMonitor) {
            this.mPausedResponseCount++;
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void print(String str) {
        synchronized (this) {
            try {
                this.mOut.write(str.getBytes("US-ASCII"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public void println(String str) {
        synchronized (this) {
            try {
                this.mOut.write(str.getBytes("US-ASCII"));
                this.mOut.write(13);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    /* JADX WARNING: Removed duplicated region for block: B:45:0x0017 A:{SYNTHETIC} */
    /* JADX WARNING: Removed duplicated region for block: B:38:0x0076 A:{SYNTHETIC} */
    public void processLine(java.lang.String r8) throws com.android.internal.telephony.test.InterpreterEx {
        /*
        r7 = this;
        r3 = 1;
        r1 = 0;
        r4 = r7.splitCommands(r8);
        r0 = r1;
    L_0x0007:
        r2 = r4.length;
        if (r0 >= r2) goto L_0x0081;
    L_0x000a:
        r5 = r4[r0];
        r2 = "A";
        r2 = r5.equals(r2);
        if (r2 == 0) goto L_0x001a;
    L_0x0014:
        r7.onAnswer();
    L_0x0017:
        r0 = r0 + 1;
        goto L_0x0007;
    L_0x001a:
        r2 = "H";
        r2 = r5.equals(r2);
        if (r2 == 0) goto L_0x0026;
    L_0x0022:
        r7.onHangup();
        goto L_0x0017;
    L_0x0026:
        r2 = "+CHLD=";
        r2 = r5.startsWith(r2);
        if (r2 == 0) goto L_0x0032;
    L_0x002e:
        r7.onCHLD(r5);
        goto L_0x0017;
    L_0x0032:
        r2 = "+CLCC";
        r2 = r5.equals(r2);
        if (r2 == 0) goto L_0x003e;
    L_0x003a:
        r7.onCLCC();
        goto L_0x0017;
    L_0x003e:
        r2 = "D";
        r2 = r5.startsWith(r2);
        if (r2 == 0) goto L_0x004a;
    L_0x0046:
        r7.onDial(r5);
        goto L_0x0017;
    L_0x004a:
        r2 = "+CMGS=";
        r2 = r5.startsWith(r2);
        if (r2 == 0) goto L_0x0056;
    L_0x0052:
        r7.onSMSSend(r5);
        goto L_0x0017;
    L_0x0056:
        r2 = r1;
    L_0x0057:
        r6 = sDefaultResponses;
        r6 = r6.length;
        if (r2 >= r6) goto L_0x0082;
    L_0x005c:
        r6 = sDefaultResponses;
        r6 = r6[r2];
        r6 = r6[r1];
        r6 = r5.equals(r6);
        if (r6 == 0) goto L_0x007e;
    L_0x0068:
        r5 = sDefaultResponses;
        r2 = r5[r2];
        r2 = r2[r3];
        if (r2 == 0) goto L_0x0073;
    L_0x0070:
        r7.println(r2);
    L_0x0073:
        r2 = r3;
    L_0x0074:
        if (r2 != 0) goto L_0x0017;
    L_0x0076:
        r0 = new com.android.internal.telephony.test.InterpreterEx;
        r1 = "ERROR";
        r0.<init>(r1);
        throw r0;
    L_0x007e:
        r2 = r2 + 1;
        goto L_0x0057;
    L_0x0081:
        return;
    L_0x0082:
        r2 = r1;
        goto L_0x0074;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.test.ModelInterpreter.processLine(java.lang.String):void");
    }

    public void progressConnectingCallState() {
        this.mSimulatedCallState.progressConnectingCallState();
    }

    public void progressConnectingToActive() {
        this.mSimulatedCallState.progressConnectingToActive();
    }

    public void resumeResponses() {
        synchronized (this.mPausedResponseMonitor) {
            this.mPausedResponseCount--;
            if (this.mPausedResponseCount == 0) {
                this.mPausedResponseMonitor.notifyAll();
            }
        }
    }

    public void run() {
        while (true) {
            if (this.mSS != null) {
                try {
                    Socket accept = this.mSS.accept();
                    try {
                        this.mIn = accept.getInputStream();
                        this.mOut = accept.getOutputStream();
                        Rlog.i(LOG_TAG, "New connection accepted");
                    } catch (IOException e) {
                        Rlog.w(LOG_TAG, "IOException on accepted socket(); re-listening", e);
                    }
                } catch (IOException e2) {
                    Rlog.w(LOG_TAG, "IOException on socket.accept(); stopping", e2);
                    return;
                }
            }
            this.mLineReader = new LineReader(this.mIn);
            println("Welcome");
            while (true) {
                String nextLine = this.mLineReader.getNextLine();
                if (nextLine == null) {
                    Rlog.i(LOG_TAG, "Disconnected");
                    if (this.mSS == null) {
                        return;
                    }
                } else {
                    synchronized (this.mPausedResponseMonitor) {
                        while (this.mPausedResponseCount > 0) {
                            try {
                                this.mPausedResponseMonitor.wait();
                            } catch (InterruptedException e3) {
                            }
                        }
                    }
                    synchronized (this) {
                        try {
                            this.mFinalResponse = "OK";
                            processLine(nextLine);
                            println(this.mFinalResponse);
                        } catch (InterpreterEx e4) {
                            println(e4.mResult);
                        } catch (RuntimeException e5) {
                            e5.printStackTrace();
                            println("ERROR");
                        }
                    }
                }
            }
        }
        while (true) {
        }
    }

    public void sendUnsolicited(String str) {
        synchronized (this) {
            println(str);
        }
    }

    public void setAutoProgressConnectingCall(boolean z) {
        this.mSimulatedCallState.setAutoProgressConnectingCall(z);
    }

    public void setNextCallFailCause(int i) {
    }

    public void setNextDialFailImmediately(boolean z) {
        this.mSimulatedCallState.setNextDialFailImmediately(z);
    }

    public void shutdown() {
        Looper looper = this.mHandlerThread.getLooper();
        if (looper != null) {
            looper.quit();
        }
        try {
            this.mIn.close();
        } catch (IOException e) {
        }
        try {
            this.mOut.close();
        } catch (IOException e2) {
        }
    }

    /* Access modifiers changed, original: 0000 */
    public String[] splitCommands(String str) throws InterpreterEx {
        if (!str.startsWith("AT")) {
            throw new InterpreterEx("ERROR");
        } else if (str.length() == 2) {
            return new String[0];
        } else {
            return new String[]{str.substring(2)};
        }
    }

    public void triggerHangupAll() {
        if (this.mSimulatedCallState.triggerHangupAll()) {
            println("NO CARRIER");
        }
    }

    public void triggerHangupBackground() {
        if (this.mSimulatedCallState.triggerHangupBackground()) {
            println("NO CARRIER");
        }
    }

    public void triggerHangupForeground() {
        if (this.mSimulatedCallState.triggerHangupForeground()) {
            println("NO CARRIER");
        }
    }

    public void triggerIncomingSMS(String str) {
    }

    public void triggerIncomingUssd(String str, String str2) {
    }

    public void triggerRing(String str) {
        synchronized (this) {
            if (this.mSimulatedCallState.triggerRing(str)) {
                println("RING");
            }
        }
    }

    public void triggerSsn(int i, int i2) {
    }
}
