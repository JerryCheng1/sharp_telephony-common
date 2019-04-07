package com.google.android.mms.pdu;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class PduBody {
    private Map<String, PduPart> mPartMapByContentId;
    private Map<String, PduPart> mPartMapByContentLocation;
    private Map<String, PduPart> mPartMapByFileName;
    private Map<String, PduPart> mPartMapByName;
    private Vector<PduPart> mParts;

    public PduBody() {
        this.mParts = null;
        this.mPartMapByContentId = null;
        this.mPartMapByContentLocation = null;
        this.mPartMapByName = null;
        this.mPartMapByFileName = null;
        this.mParts = new Vector();
        this.mPartMapByContentId = new HashMap();
        this.mPartMapByContentLocation = new HashMap();
        this.mPartMapByName = new HashMap();
        this.mPartMapByFileName = new HashMap();
    }

    private void putPartToMaps(PduPart pduPart) {
        byte[] contentId = pduPart.getContentId();
        if (contentId != null) {
            this.mPartMapByContentId.put(new String(contentId), pduPart);
        }
        contentId = pduPart.getContentLocation();
        if (contentId != null) {
            this.mPartMapByContentLocation.put(new String(contentId), pduPart);
        }
        contentId = pduPart.getName();
        if (contentId != null) {
            this.mPartMapByName.put(new String(contentId), pduPart);
        }
        contentId = pduPart.getFilename();
        if (contentId != null) {
            this.mPartMapByFileName.put(new String(contentId), pduPart);
        }
    }

    public void addPart(int i, PduPart pduPart) {
        if (pduPart == null) {
            throw new NullPointerException();
        }
        putPartToMaps(pduPart);
        this.mParts.add(i, pduPart);
    }

    public boolean addPart(PduPart pduPart) {
        if (pduPart == null) {
            throw new NullPointerException();
        }
        putPartToMaps(pduPart);
        return this.mParts.add(pduPart);
    }

    public PduPart getPart(int i) {
        return (PduPart) this.mParts.get(i);
    }

    public PduPart getPartByContentId(String str) {
        return (PduPart) this.mPartMapByContentId.get(str);
    }

    public PduPart getPartByContentLocation(String str) {
        return (PduPart) this.mPartMapByContentLocation.get(str);
    }

    public PduPart getPartByFileName(String str) {
        return (PduPart) this.mPartMapByFileName.get(str);
    }

    public PduPart getPartByName(String str) {
        return (PduPart) this.mPartMapByName.get(str);
    }

    public int getPartIndex(PduPart pduPart) {
        return this.mParts.indexOf(pduPart);
    }

    public int getPartsNum() {
        return this.mParts.size();
    }

    public void removeAll() {
        this.mParts.clear();
    }

    public PduPart removePart(int i) {
        return (PduPart) this.mParts.remove(i);
    }
}
