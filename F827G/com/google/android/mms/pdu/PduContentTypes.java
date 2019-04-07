package com.google.android.mms.pdu;

import com.android.internal.telephony.WspTypeDecoder;
import com.google.android.mms.ContentType;

public class PduContentTypes {
    static final String[] contentTypes = new String[]{"*/*", "text/*", ContentType.TEXT_HTML, ContentType.TEXT_PLAIN, "text/x-hdml", "text/x-ttml", ContentType.TEXT_VCALENDAR, ContentType.TEXT_VCARD, "text/vnd.wap.wml", "text/vnd.wap.wmlscript", "text/vnd.wap.wta-event", "multipart/*", "multipart/mixed", "multipart/form-data", "multipart/byterantes", "multipart/alternative", "application/*", "application/java-vm", "application/x-www-form-urlencoded", "application/x-hdmlc", "application/vnd.wap.wmlc", "application/vnd.wap.wmlscriptc", "application/vnd.wap.wta-eventc", "application/vnd.wap.uaprof", "application/vnd.wap.wtls-ca-certificate", "application/vnd.wap.wtls-user-certificate", "application/x-x509-ca-cert", "application/x-x509-user-cert", ContentType.IMAGE_UNSPECIFIED, ContentType.IMAGE_GIF, ContentType.IMAGE_JPEG, "image/tiff", ContentType.IMAGE_PNG, ContentType.IMAGE_WBMP, "application/vnd.wap.multipart.*", ContentType.MULTIPART_MIXED, "application/vnd.wap.multipart.form-data", "application/vnd.wap.multipart.byteranges", ContentType.MULTIPART_ALTERNATIVE, "application/xml", "text/xml", "application/vnd.wap.wbxml", "application/x-x968-cross-cert", "application/x-x968-ca-cert", "application/x-x968-user-cert", "text/vnd.wap.si", "application/vnd.wap.sic", "text/vnd.wap.sl", "application/vnd.wap.slc", "text/vnd.wap.co", WspTypeDecoder.CONTENT_TYPE_B_PUSH_CO, ContentType.MULTIPART_RELATED, "application/vnd.wap.sia", "text/vnd.wap.connectivity-xml", "application/vnd.wap.connectivity-wbxml", "application/pkcs7-mime", "application/vnd.wap.hashed-certificate", "application/vnd.wap.signed-certificate", "application/vnd.wap.cert-response", ContentType.APP_XHTML, "application/wml+xml", "text/css", "application/vnd.wap.mms-message", "application/vnd.wap.rollover-certificate", "application/vnd.wap.locc+wbxml", "application/vnd.wap.loc+xml", "application/vnd.syncml.dm+wbxml", "application/vnd.syncml.dm+xml", WspTypeDecoder.CONTENT_TYPE_B_PUSH_SYNCML_NOTI, ContentType.APP_WAP_XHTML, "application/vnd.wv.csp.cir", "application/vnd.oma.dd+xml", "application/vnd.oma.drm.message", ContentType.APP_DRM_CONTENT, "application/vnd.oma.drm.rights+xml", "application/vnd.oma.drm.rights+wbxml", "application/vnd.wv.csp+xml", "application/vnd.wv.csp+wbxml", "application/vnd.syncml.ds.notification", ContentType.AUDIO_UNSPECIFIED, ContentType.VIDEO_UNSPECIFIED, "application/vnd.oma.dd2+xml", "application/mikey"};
}
