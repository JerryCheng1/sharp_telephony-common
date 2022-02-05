package com.android.internal.telephony.cat;

/* loaded from: C:\Users\SampP\Desktop\oat2dex-python\boot.oat.0x1348340.odex */
public class TextAttribute {
    public TextAlignment align;
    public boolean bold;
    public TextColor color;
    public boolean italic;
    public int length;
    public FontSize size;
    public int start;
    public boolean strikeThrough;
    public boolean underlined;

    public TextAttribute(int start, int length, TextAlignment align, FontSize size, boolean bold, boolean italic, boolean underlined, boolean strikeThrough, TextColor color) {
        this.start = start;
        this.length = length;
        this.align = align;
        this.size = size;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikeThrough = strikeThrough;
        this.color = color;
    }
}
