package com.android.internal.telephony;

import java.util.ArrayList;
import java.util.Iterator;

public abstract class IntRangeManager {
    private static final int INITIAL_CLIENTS_ARRAY_SIZE = 4;
    private ArrayList<IntRange> mRanges = new ArrayList();

    private class ClientRange {
        final String mClient;
        final int mEndId;
        final int mStartId;

        ClientRange(int i, int i2, String str) {
            this.mStartId = i;
            this.mEndId = i2;
            this.mClient = str;
        }

        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof ClientRange)) {
                return false;
            }
            ClientRange clientRange = (ClientRange) obj;
            return this.mStartId == clientRange.mStartId && this.mEndId == clientRange.mEndId && this.mClient.equals(clientRange.mClient);
        }

        public int hashCode() {
            return (((this.mStartId * 31) + this.mEndId) * 31) + this.mClient.hashCode();
        }
    }

    private class IntRange {
        final ArrayList<ClientRange> mClients;
        int mEndId;
        int mStartId;

        IntRange(int i, int i2, String str) {
            this.mStartId = i;
            this.mEndId = i2;
            this.mClients = new ArrayList(4);
            this.mClients.add(new ClientRange(i, i2, str));
        }

        IntRange(ClientRange clientRange) {
            this.mStartId = clientRange.mStartId;
            this.mEndId = clientRange.mEndId;
            this.mClients = new ArrayList(4);
            this.mClients.add(clientRange);
        }

        IntRange(IntRange intRange, int i) {
            this.mStartId = intRange.mStartId;
            this.mEndId = intRange.mEndId;
            this.mClients = new ArrayList(intRange.mClients.size());
            for (int i2 = 0; i2 < i; i2++) {
                this.mClients.add(intRange.mClients.get(i2));
            }
        }

        /* Access modifiers changed, original: 0000 */
        public void insert(ClientRange clientRange) {
            int i;
            int size = this.mClients.size();
            int i2 = 0;
            int i3 = -1;
            while (i2 < size) {
                ClientRange clientRange2 = (ClientRange) this.mClients.get(i2);
                if (clientRange.mStartId > clientRange2.mStartId) {
                    i = i3;
                } else if (!clientRange.equals(clientRange2)) {
                    if (clientRange.mStartId == clientRange2.mStartId && clientRange.mEndId > clientRange2.mEndId) {
                        i = i2 + 1;
                        if (i >= size) {
                            break;
                        }
                    } else {
                        this.mClients.add(i2, clientRange);
                        return;
                    }
                } else {
                    return;
                }
                i2++;
                i3 = i;
            }
            i = i3;
            if (i == -1 || i >= size) {
                this.mClients.add(clientRange);
            } else {
                this.mClients.add(i, clientRange);
            }
        }
    }

    protected IntRangeManager() {
    }

    private void populateAllClientRanges() {
        int size = this.mRanges.size();
        for (int i = 0; i < size; i++) {
            IntRange intRange = (IntRange) this.mRanges.get(i);
            int size2 = intRange.mClients.size();
            for (int i2 = 0; i2 < size2; i2++) {
                ClientRange clientRange = (ClientRange) intRange.mClients.get(i2);
                addRange(clientRange.mStartId, clientRange.mEndId, true);
            }
        }
    }

    private void populateAllRanges() {
        Iterator it = this.mRanges.iterator();
        while (it.hasNext()) {
            IntRange intRange = (IntRange) it.next();
            addRange(intRange.mStartId, intRange.mEndId, true);
        }
    }

    public abstract void addRange(int i, int i2, boolean z);

    public boolean disableRange(int i, int i2, String str) {
        boolean z;
        synchronized (this) {
            int size = this.mRanges.size();
            for (int i3 = 0; i3 < size; i3++) {
                IntRange intRange = (IntRange) this.mRanges.get(i3);
                if (i < intRange.mStartId) {
                    z = false;
                    break;
                }
                if (i2 <= intRange.mEndId) {
                    ArrayList arrayList = intRange.mClients;
                    int size2 = arrayList.size();
                    ClientRange clientRange;
                    if (size2 == 1) {
                        clientRange = (ClientRange) arrayList.get(0);
                        if (clientRange.mStartId == i && clientRange.mEndId == i2 && clientRange.mClient.equals(str)) {
                            this.mRanges.remove(i3);
                            if (updateRanges()) {
                                z = true;
                            } else {
                                this.mRanges.add(i3, intRange);
                                z = false;
                            }
                        } else {
                            z = false;
                        }
                    } else {
                        int i4 = Integer.MIN_VALUE;
                        Object obj = null;
                        int i5 = 0;
                        while (i5 < size2) {
                            clientRange = (ClientRange) arrayList.get(i5);
                            if (clientRange.mStartId != i || clientRange.mEndId != i2 || !clientRange.mClient.equals(str)) {
                                i5++;
                                i4 = clientRange.mEndId > i4 ? clientRange.mEndId : i4;
                            } else if (i5 != size2 - 1) {
                                int i6;
                                IntRange intRange2 = new IntRange(intRange, i5);
                                if (i5 == 0) {
                                    int i7 = ((ClientRange) arrayList.get(1)).mStartId;
                                    if (i7 != intRange.mStartId) {
                                        obj = 1;
                                        intRange2.mStartId = i7;
                                    } else {
                                        obj = null;
                                    }
                                    i6 = ((ClientRange) arrayList.get(1)).mEndId;
                                } else {
                                    i6 = i4;
                                }
                                ArrayList arrayList2 = new ArrayList();
                                i5++;
                                Object obj2 = obj;
                                while (i5 < size2) {
                                    clientRange = (ClientRange) arrayList.get(i5);
                                    if (clientRange.mStartId > i6 + 1) {
                                        obj2 = 1;
                                        intRange2.mEndId = i6;
                                        arrayList2.add(intRange2);
                                        intRange2 = new IntRange(clientRange);
                                    } else {
                                        if (intRange2.mEndId < clientRange.mEndId) {
                                            intRange2.mEndId = clientRange.mEndId;
                                        }
                                        intRange2.mClients.add(clientRange);
                                    }
                                    i5++;
                                    i6 = clientRange.mEndId > i6 ? clientRange.mEndId : i6;
                                }
                                if (i6 < i2) {
                                    obj2 = 1;
                                    intRange2.mEndId = i6;
                                }
                                arrayList2.add(intRange2);
                                this.mRanges.remove(i3);
                                this.mRanges.addAll(i3, arrayList2);
                                if (obj2 == null || updateRanges()) {
                                    z = true;
                                } else {
                                    this.mRanges.removeAll(arrayList2);
                                    this.mRanges.add(i3, intRange);
                                    z = false;
                                }
                            } else if (intRange.mEndId == i4) {
                                arrayList.remove(i5);
                                z = true;
                            } else {
                                arrayList.remove(i5);
                                intRange.mEndId = i4;
                                if (updateRanges()) {
                                    z = true;
                                } else {
                                    arrayList.add(i5, clientRange);
                                    intRange.mEndId = clientRange.mEndId;
                                    z = false;
                                }
                            }
                        }
                        continue;
                    }
                }
            }
            z = false;
        }
        return z;
    }

    public boolean enableRange(int i, int i2, String str) {
        boolean z;
        synchronized (this) {
            int size = this.mRanges.size();
            if (size != 0) {
                int i3 = 0;
                while (i3 < size) {
                    IntRange intRange = (IntRange) this.mRanges.get(i3);
                    IntRange intRange2;
                    int i4;
                    int i5;
                    int i6;
                    if (i >= intRange.mStartId && i2 <= intRange.mEndId) {
                        intRange.insert(new ClientRange(i, i2, str));
                        z = true;
                        break;
                    } else if (i - 1 == intRange.mEndId) {
                        if (i3 + 1 < size) {
                            intRange2 = (IntRange) this.mRanges.get(i3 + 1);
                            if (intRange2.mStartId - 1 <= i2) {
                                i4 = i2 <= intRange2.mEndId ? intRange2.mStartId - 1 : i2;
                            } else {
                                i4 = i2;
                                intRange2 = null;
                            }
                        } else {
                            i4 = i2;
                            intRange2 = null;
                        }
                        if (tryAddRanges(i, i4, true)) {
                            intRange.mEndId = i2;
                            intRange.insert(new ClientRange(i, i2, str));
                            if (intRange2 != null) {
                                if (intRange.mEndId < intRange2.mEndId) {
                                    intRange.mEndId = intRange2.mEndId;
                                }
                                intRange.mClients.addAll(intRange2.mClients);
                                this.mRanges.remove(intRange2);
                            }
                            z = true;
                        } else {
                            z = false;
                        }
                    } else if (i < intRange.mStartId) {
                        if (i2 + 1 < intRange.mStartId) {
                            if (tryAddRanges(i, i2, true)) {
                                this.mRanges.add(i3, new IntRange(i, i2, str));
                                z = true;
                            } else {
                                z = false;
                            }
                        } else if (i2 > intRange.mEndId) {
                            i5 = i3 + 1;
                            while (i5 < size) {
                                intRange2 = (IntRange) this.mRanges.get(i5);
                                if (i2 + 1 < intRange2.mStartId) {
                                    if (tryAddRanges(i, i2, true)) {
                                        intRange.mStartId = i;
                                        intRange.mEndId = i2;
                                        intRange.mClients.add(0, new ClientRange(i, i2, str));
                                        i4 = i3 + 1;
                                        for (i6 = i4; i6 < i5; i6++) {
                                            intRange2 = (IntRange) this.mRanges.get(i4);
                                            intRange.mClients.addAll(intRange2.mClients);
                                            this.mRanges.remove(intRange2);
                                        }
                                        z = true;
                                    } else {
                                        z = false;
                                    }
                                } else if (i2 > intRange2.mEndId) {
                                    i5++;
                                } else if (tryAddRanges(i, intRange2.mStartId - 1, true)) {
                                    intRange.mStartId = i;
                                    intRange.mEndId = intRange2.mEndId;
                                    intRange.mClients.add(0, new ClientRange(i, i2, str));
                                    i4 = i3 + 1;
                                    for (i6 = i4; i6 <= i5; i6++) {
                                        intRange2 = (IntRange) this.mRanges.get(i4);
                                        intRange.mClients.addAll(intRange2.mClients);
                                        this.mRanges.remove(intRange2);
                                    }
                                    z = true;
                                } else {
                                    z = false;
                                }
                            }
                            if (tryAddRanges(i, i2, true)) {
                                intRange.mStartId = i;
                                intRange.mEndId = i2;
                                intRange.mClients.add(0, new ClientRange(i, i2, str));
                                i4 = i3 + 1;
                                for (i6 = i4; i6 < size; i6++) {
                                    intRange2 = (IntRange) this.mRanges.get(i4);
                                    intRange.mClients.addAll(intRange2.mClients);
                                    this.mRanges.remove(intRange2);
                                }
                                z = true;
                            } else {
                                z = false;
                            }
                        } else if (tryAddRanges(i, intRange.mStartId - 1, true)) {
                            intRange.mStartId = i;
                            intRange.mClients.add(0, new ClientRange(i, i2, str));
                            z = true;
                        } else {
                            z = false;
                        }
                    } else if (i + 1 > intRange.mEndId) {
                        i3++;
                    } else if (i2 <= intRange.mEndId) {
                        intRange.insert(new ClientRange(i, i2, str));
                        z = true;
                    } else {
                        i4 = i3 + 1;
                        i5 = i3;
                        while (i4 < size && i2 + 1 >= ((IntRange) this.mRanges.get(i4)).mStartId) {
                            i5 = i4;
                            i4++;
                        }
                        if (i5 != i3) {
                            intRange2 = (IntRange) this.mRanges.get(i5);
                            if (tryAddRanges(intRange.mEndId + 1, i2 <= intRange2.mEndId ? intRange2.mStartId - 1 : i2, true)) {
                                intRange.mEndId = i2 <= intRange2.mEndId ? intRange2.mEndId : i2;
                                intRange.insert(new ClientRange(i, i2, str));
                                i4 = i3 + 1;
                                for (i6 = i4; i6 <= i5; i6++) {
                                    intRange2 = (IntRange) this.mRanges.get(i4);
                                    intRange.mClients.addAll(intRange2.mClients);
                                    this.mRanges.remove(intRange2);
                                }
                                z = true;
                            } else {
                                z = false;
                            }
                        } else if (tryAddRanges(intRange.mEndId + 1, i2, true)) {
                            intRange.mEndId = i2;
                            intRange.insert(new ClientRange(i, i2, str));
                            z = true;
                        } else {
                            z = false;
                        }
                    }
                }
                if (tryAddRanges(i, i2, true)) {
                    this.mRanges.add(new IntRange(i, i2, str));
                    z = true;
                } else {
                    z = false;
                }
            } else if (tryAddRanges(i, i2, true)) {
                this.mRanges.add(new IntRange(i, i2, str));
                z = true;
            } else {
                z = false;
            }
        }
        return z;
    }

    public abstract boolean finishUpdate();

    public boolean isEmpty() {
        return this.mRanges.isEmpty();
    }

    public abstract void startUpdate();

    /* Access modifiers changed, original: protected */
    public boolean tryAddRanges(int i, int i2, boolean z) {
        startUpdate();
        populateAllRanges();
        addRange(i, i2, z);
        return finishUpdate();
    }

    public boolean updateRanges() {
        startUpdate();
        populateAllRanges();
        return finishUpdate();
    }
}
