package com.android.internal.telephony;

import java.util.ArrayList;
import java.util.Iterator;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public abstract class IntRangeManager {
    private static final int INITIAL_CLIENTS_ARRAY_SIZE = 4;
    private ArrayList<IntRange> mRanges = new ArrayList<>();

    protected abstract void addRange(int i, int i2, boolean z);

    /*  JADX ERROR: Failed to decode insn: 0x0003: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0003: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0007: UNKNOWN(0x01EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0007: UNKNOWN(0x01EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0017: UNKNOWN(0x40E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0017: UNKNOWN(0x40E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x001F: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x001F: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0032: UNKNOWN(0x02EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0032: UNKNOWN(0x02EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0041: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0041: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0047: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0047: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x004D: UNKNOWN(0xD0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x004D: UNKNOWN(0xD0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0057: UNKNOWN(0xD0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0057: UNKNOWN(0xD0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0072: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0072: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x007A: UNKNOWN(0xD0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x007A: UNKNOWN(0xD0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x008F: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x008F: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0095: UNKNOWN(0x02EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0095: UNKNOWN(0x02EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x009B: UNKNOWN(0xC0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x009B: UNKNOWN(0xC0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00A7: UNKNOWN(0xC0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00A7: UNKNOWN(0xC0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00B1: UNKNOWN(0xC0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00B1: UNKNOWN(0xC0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00BF: UNKNOWN(0x40E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00BF: UNKNOWN(0x40E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00C7: UNKNOWN(0xD0E6), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00C7: UNKNOWN(0xD0E6)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00DA: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00DA: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00DF: UNKNOWN(0xD0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00DF: UNKNOWN(0xD0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00E3: UNKNOWN(0xC0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00E3: UNKNOWN(0xC0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00ED: UNKNOWN(0xC0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00ED: UNKNOWN(0xC0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00F3: UNKNOWN(0xD0E6), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00F3: UNKNOWN(0xD0E6)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00F5: UNKNOWN(0xD0E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00F5: UNKNOWN(0xD0E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00F9: UNKNOWN(0xC0E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00F9: UNKNOWN(0xC0E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x00FD: UNKNOWN(0x02EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x00FD: UNKNOWN(0x02EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0102: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0102: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0108: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0108: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0115: UNKNOWN(0xD0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0115: UNKNOWN(0xD0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0121: UNKNOWN(0xD0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0121: UNKNOWN(0xD0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0135: UNKNOWN(0x40E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0135: UNKNOWN(0x40E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x013D: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x013D: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0154: UNKNOWN(0x30E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0154: UNKNOWN(0x30E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x015F: UNKNOWN(0xD0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x015F: UNKNOWN(0xD0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0169: UNKNOWN(0xD0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0169: UNKNOWN(0xD0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0179: UNKNOWN(0x40E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0179: UNKNOWN(0x40E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0181: UNKNOWN(0xD0E6), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0181: UNKNOWN(0xD0E6)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x018E: UNKNOWN(0x03EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x018E: UNKNOWN(0x03EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x019F: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x019F: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01A5: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01A5: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01AD: UNKNOWN(0x60E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01AD: UNKNOWN(0x60E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01C1: UNKNOWN(0x40E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01C1: UNKNOWN(0x40E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01C9: UNKNOWN(0xD0E6), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01C9: UNKNOWN(0xD0E6)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01CD: UNKNOWN(0xD0E6), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01CD: UNKNOWN(0xD0E6)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01CF: UNKNOWN(0xD0E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01CF: UNKNOWN(0xD0E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01DA: UNKNOWN(0x03EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01DA: UNKNOWN(0x03EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01E4: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01E4: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01EA: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01EA: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01F0: UNKNOWN(0xD0E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01F0: UNKNOWN(0xD0E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01F4: UNKNOWN(0x90E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01F4: UNKNOWN(0x90E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01F8: UNKNOWN(0x02EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01F8: UNKNOWN(0x02EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x01FD: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x01FD: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0203: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0203: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0211: UNKNOWN(0x60E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0211: UNKNOWN(0x60E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x021B: UNKNOWN(0x60E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x021B: UNKNOWN(0x60E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x022B: UNKNOWN(0x40E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x022B: UNKNOWN(0x40E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0233: UNKNOWN(0xD0E6), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0233: UNKNOWN(0xD0E6)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x023B: UNKNOWN(0xD0E6), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x023B: UNKNOWN(0xD0E6)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x023D: UNKNOWN(0xD0E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x023D: UNKNOWN(0xD0E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0248: UNKNOWN(0x03EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0248: UNKNOWN(0x03EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0252: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0252: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0258: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0258: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x025E: UNKNOWN(0xD0E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x025E: UNKNOWN(0xD0E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0262: UNKNOWN(0x90E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0262: UNKNOWN(0x90E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0266: UNKNOWN(0x02EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0266: UNKNOWN(0x02EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x026B: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x026B: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0271: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0271: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x028D: UNKNOWN(0x40E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x028D: UNKNOWN(0x40E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0295: UNKNOWN(0xD0E6), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0295: UNKNOWN(0xD0E6)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0299: UNKNOWN(0xD0E6), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0299: UNKNOWN(0xD0E6)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x029B: UNKNOWN(0xD0E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x029B: UNKNOWN(0xD0E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x02A6: UNKNOWN(0x03EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x02A6: UNKNOWN(0x03EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x02B0: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x02B0: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x02B6: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x02B6: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x02BC: UNKNOWN(0xD0E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x02BC: UNKNOWN(0xD0E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x02C0: UNKNOWN(0x90E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x02C0: UNKNOWN(0x90E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x02C4: UNKNOWN(0x02EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x02C4: UNKNOWN(0x02EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x02C9: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x02C9: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x02CF: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x02CF: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x02DF: UNKNOWN(0xD0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x02DF: UNKNOWN(0xD0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x02E9: UNKNOWN(0xD0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x02E9: UNKNOWN(0xD0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0304: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0304: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0312: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0312: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0318: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0318: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0322: UNKNOWN(0x00E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0322: UNKNOWN(0x00E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x032E: UNKNOWN(0xD0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x032E: UNKNOWN(0xD0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x033E: UNKNOWN(0x40E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x033E: UNKNOWN(0x40E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0346: UNKNOWN(0xD0E6), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0346: UNKNOWN(0xD0E6)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0359: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0359: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x036A: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x036A: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0370: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0370: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0376: UNKNOWN(0x60E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0376: UNKNOWN(0x60E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0380: UNKNOWN(0x60E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0380: UNKNOWN(0x60E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0386: UNKNOWN(0xD0E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0386: UNKNOWN(0xD0E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0394: UNKNOWN(0x40E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0394: UNKNOWN(0x40E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x039A: UNKNOWN(0x60E3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x039A: UNKNOWN(0x60E3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x03A4: UNKNOWN(0x6BE3), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x03A4: UNKNOWN(0x6BE3)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x03A6: UNKNOWN(0xDBE6), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x03A6: UNKNOWN(0xDBE6)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x03B9: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x03B9: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x03C3: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x03C3: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x03C9: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x03C9: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x03CF: UNKNOWN(0xD0E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x03CF: UNKNOWN(0xD0E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x03D3: UNKNOWN(0x90E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x03D3: UNKNOWN(0x90E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x03D7: UNKNOWN(0x02EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x03D7: UNKNOWN(0x02EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x03DC: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x03DC: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x03E2: UNKNOWN(0x20E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x03E2: UNKNOWN(0x20E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x0404: UNKNOWN(0x40E9), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x0404: UNKNOWN(0x40E9)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x040C: UNKNOWN(0x00E5), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x040C: UNKNOWN(0x00E5)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    /*  JADX ERROR: Failed to decode insn: 0x041F: UNKNOWN(0x02EA), method: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean
        jadx.core.utils.exceptions.DecodeException: Unknown instruction: '0x041F: UNKNOWN(0x02EA)'
        	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:494)
        	at jadx.core.dex.instructions.InsnDecoder.lambda$process$0(InsnDecoder.java:50)
        	at jadx.plugins.input.dex.sections.DexCodeReader.visitInstructions(DexCodeReader.java:85)
        	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:45)
        	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:147)
        	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:365)
        	at jadx.core.ProcessClass.process(ProcessClass.java:57)
        	at jadx.core.ProcessClass.generateCode(ProcessClass.java:101)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:356)
        	at jadx.core.dex.nodes.ClassNode.decompile(ClassNode.java:302)
        */
    public synchronized boolean enableRange(int r21, int r22, java.lang.String r23) {
        /*
            Method dump skipped, instructions count: 1069
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.IntRangeManager.enableRange(int, int, java.lang.String):boolean");
    }

    protected abstract boolean finishUpdate();

    protected abstract void startUpdate();

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    public class IntRange {
        final ArrayList<ClientRange> mClients;
        int mEndId;
        int mStartId;

        IntRange(int startId, int endId, String client) {
            this.mStartId = startId;
            this.mEndId = endId;
            this.mClients = new ArrayList<>(4);
            this.mClients.add(new ClientRange(startId, endId, client));
        }

        IntRange(ClientRange clientRange) {
            this.mStartId = clientRange.mStartId;
            this.mEndId = clientRange.mEndId;
            this.mClients = new ArrayList<>(4);
            this.mClients.add(clientRange);
        }

        IntRange(IntRange intRange, int numElements) {
            this.mStartId = intRange.mStartId;
            this.mEndId = intRange.mEndId;
            this.mClients = new ArrayList<>(intRange.mClients.size());
            for (int i = 0; i < numElements; i++) {
                this.mClients.add(intRange.mClients.get(i));
            }
        }

        void insert(ClientRange range) {
            int len = this.mClients.size();
            int insert = -1;
            for (int i = 0; i < len; i++) {
                ClientRange nextRange = this.mClients.get(i);
                if (range.mStartId <= nextRange.mStartId) {
                    if (range.equals(nextRange)) {
                        return;
                    }
                    if (range.mStartId == nextRange.mStartId && range.mEndId > nextRange.mEndId) {
                        insert = i + 1;
                        if (insert >= len) {
                            break;
                        }
                    } else {
                        this.mClients.add(i, range);
                        return;
                    }
                }
            }
            if (insert == -1 || insert >= len) {
                this.mClients.add(range);
            } else {
                this.mClients.add(insert, range);
            }
        }
    }

    /* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
    private class ClientRange {
        final String mClient;
        final int mEndId;
        final int mStartId;

        ClientRange(int startId, int endId, String client) {
            this.mStartId = startId;
            this.mEndId = endId;
            this.mClient = client;
        }

        public boolean equals(Object o) {
            if (o == null || !(o instanceof ClientRange)) {
                return false;
            }
            ClientRange other = (ClientRange) o;
            return this.mStartId == other.mStartId && this.mEndId == other.mEndId && this.mClient.equals(other.mClient);
        }

        public int hashCode() {
            return (((this.mStartId * 31) + this.mEndId) * 31) + this.mClient.hashCode();
        }
    }

    public synchronized boolean disableRange(int startId, int endId, String client) {
        boolean z;
        int len = this.mRanges.size();
        int i = 0;
        while (true) {
            if (i >= len) {
                z = false;
                break;
            }
            IntRange range = this.mRanges.get(i);
            if (startId < range.mStartId) {
                z = false;
                break;
            }
            if (endId <= range.mEndId) {
                ArrayList<ClientRange> clients = range.mClients;
                int crLength = clients.size();
                if (crLength == 1) {
                    ClientRange cr = clients.get(0);
                    if (cr.mStartId == startId && cr.mEndId == endId && cr.mClient.equals(client)) {
                        this.mRanges.remove(i);
                        if (updateRanges()) {
                            z = true;
                        } else {
                            this.mRanges.add(i, range);
                            z = false;
                        }
                    } else {
                        z = false;
                    }
                } else {
                    int largestEndId = Integer.MIN_VALUE;
                    boolean updateStarted = false;
                    for (int crIndex = 0; crIndex < crLength; crIndex++) {
                        ClientRange cr2 = clients.get(crIndex);
                        if (cr2.mStartId != startId || cr2.mEndId != endId || !cr2.mClient.equals(client)) {
                            if (cr2.mEndId > largestEndId) {
                                largestEndId = cr2.mEndId;
                            }
                        } else if (crIndex != crLength - 1) {
                            IntRange rangeCopy = new IntRange(range, crIndex);
                            if (crIndex == 0) {
                                int nextStartId = clients.get(1).mStartId;
                                if (nextStartId != range.mStartId) {
                                    updateStarted = true;
                                    rangeCopy.mStartId = nextStartId;
                                }
                                largestEndId = clients.get(1).mEndId;
                            }
                            ArrayList<IntRange> newRanges = new ArrayList<>();
                            IntRange currentRange = rangeCopy;
                            for (int nextIndex = crIndex + 1; nextIndex < crLength; nextIndex++) {
                                ClientRange nextCr = clients.get(nextIndex);
                                if (nextCr.mStartId > largestEndId + 1) {
                                    updateStarted = true;
                                    currentRange.mEndId = largestEndId;
                                    newRanges.add(currentRange);
                                    currentRange = new IntRange(nextCr);
                                } else {
                                    if (currentRange.mEndId < nextCr.mEndId) {
                                        currentRange.mEndId = nextCr.mEndId;
                                    }
                                    currentRange.mClients.add(nextCr);
                                }
                                if (nextCr.mEndId > largestEndId) {
                                    largestEndId = nextCr.mEndId;
                                }
                            }
                            if (largestEndId < endId) {
                                updateStarted = true;
                                currentRange.mEndId = largestEndId;
                            }
                            newRanges.add(currentRange);
                            this.mRanges.remove(i);
                            this.mRanges.addAll(i, newRanges);
                            if (!updateStarted || updateRanges()) {
                                z = true;
                            } else {
                                this.mRanges.removeAll(newRanges);
                                this.mRanges.add(i, range);
                                z = false;
                            }
                        } else if (range.mEndId == largestEndId) {
                            clients.remove(crIndex);
                            z = true;
                        } else {
                            clients.remove(crIndex);
                            range.mEndId = largestEndId;
                            if (updateRanges()) {
                                z = true;
                            } else {
                                clients.add(crIndex, cr2);
                                range.mEndId = cr2.mEndId;
                                z = false;
                            }
                        }
                    }
                    continue;
                }
            }
            i++;
        }
        return z;
    }

    public boolean updateRanges() {
        startUpdate();
        populateAllRanges();
        return finishUpdate();
    }

    protected boolean tryAddRanges(int startId, int endId, boolean selected) {
        startUpdate();
        populateAllRanges();
        addRange(startId, endId, selected);
        return finishUpdate();
    }

    public boolean isEmpty() {
        return this.mRanges.isEmpty();
    }

    private void populateAllRanges() {
        Iterator<IntRange> itr = this.mRanges.iterator();
        while (itr.hasNext()) {
            IntRange currRange = itr.next();
            addRange(currRange.mStartId, currRange.mEndId, true);
        }
    }

    private void populateAllClientRanges() {
        int len = this.mRanges.size();
        for (int i = 0; i < len; i++) {
            IntRange range = this.mRanges.get(i);
            int clientLen = range.mClients.size();
            for (int j = 0; j < clientLen; j++) {
                ClientRange nextRange = range.mClients.get(j);
                addRange(nextRange.mStartId, nextRange.mEndId, true);
            }
        }
    }
}
