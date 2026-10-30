package com.bettersticker.qq;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.bettersticker.format.ImageSniffer;
import com.bettersticker.shared.LogX;

/**
 * 从 QQ 的新 AIO 消息对象 AIOMsgItem 提取本地媒体。
 *
 * 已核对（QQ 9.3.75.42350）：
 * - AIOMsgItem.getMsgRecord() -> MsgRecord
 * - MsgRecord.getElements() -> ArrayList<MsgElement>
 * - MsgElement.getPttElement()/getPicElement()/getMarketFaceElement()
 * - PttElement.getFilePath()/getFileName()/getFileSize()/getDuration()
 * - PicElement.getSourcePath()/getFileName()/getThumbPath()
 * - MarketFaceElement.getDynamicFacePath()/getStaticFacePath()/getFaceName()
 *
 * 只读本地已下载文件，不发起任何网络请求。
 */
public final class QqMsgRecordAdapter implements QqAdapter {

    private static final int MAX_FILE = 64 * 1024 * 1024;

    @Override
    public List<QqMedia> extract(Object aioMsgItem) throws Exception {
        List<QqMedia> out = new ArrayList<>();
        if (aioMsgItem == null) {
            return out;
        }
        Object record = invoke(aioMsgItem, "getMsgRecord");
        if (record == null) {
            LogX.w("AIOMsgItem.getMsgRecord() 返回 null");
            return out;
        }
        String source = buildSource(record);
        Object elementsObj = invoke(record, "getElements");
        if (!(elementsObj instanceof List)) {
            LogX.w("MsgRecord.getElements() 不是 List");
            return out;
        }
        @SuppressWarnings("unchecked")
        List<Object> elements = (List<Object>) elementsObj;
        for (Object element : elements) {
            if (element == null) {
                continue;
            }
            try {
                addPtt(element, source, out);
                addMarketFace(element, source, out);
                addPic(element, source, out);
            } catch (Throwable t) {
                LogX.e("解析元素失败: " + element.getClass().getName(), t);
            }
        }
        return out;
    }

    private void addPtt(Object element, String source, List<QqMedia> out) {
        Object ptt = invoke(element, "getPttElement");
        if (ptt == null) {
            return;
        }
        File f = fileOf(invoke(ptt, "getFilePath"));
        if (f == null) {
            LogX.probe("语音元素没有本地 filePath，跳过（不联网下载）");
            return;
        }
        byte[] data = readFile(f);
        if (data == null) {
            return;
        }
        if (!ImageSniffer.isSilk(data)) {
            LogX.w("语音文件不是 SILK v3，头部=" + ImageSniffer.hexPrefix(data, 16) + "，跳过: " + f);
            return;
        }
        QqMedia m = new QqMedia();
        m.type = "voice";
        m.data = data;
        m.originalName = str(invoke(ptt, "getFileName"), f.getName());
        m.source = source;
        Object duration = invoke(ptt, "getDuration");
        m.durationRaw = duration instanceof Integer ? (Integer) duration : 0;
        m.debug = "PttElement filePath=" + f.getAbsolutePath();
        out.add(m);
    }

    private void addMarketFace(Object element, String source, List<QqMedia> out) {
        Object face = invoke(element, "getMarketFaceElement");
        if (face == null) {
            return;
        }
        File f = fileOf(invoke(face, "getDynamicFacePath"));
        if (f == null) {
            f = fileOf(invoke(face, "getStaticFacePath"));
        }
        if (f == null) {
            LogX.probe("商城表情没有本地路径，跳过（不联网下载）");
            return;
        }
        byte[] data = readFile(f);
        if (data == null) {
            return;
        }
        QqMedia m = new QqMedia();
        m.type = "sticker";
        m.data = data;
        String name = str(invoke(face, "getFaceName"), f.getName());
        m.originalName = name + extensionOf(f.getName());
        m.source = source;
        Object w = invoke(face, "getImageWidth");
        Object h = invoke(face, "getImageHeight");
        m.width = w instanceof Integer ? (Integer) w : 0;
        m.height = h instanceof Integer ? (Integer) h : 0;
        m.debug = "MarketFaceElement path=" + f.getAbsolutePath();
        out.add(m);
    }

    private void addPic(Object element, String source, List<QqMedia> out) {
        Object pic = invoke(element, "getPicElement");
        if (pic == null) {
            return;
        }
        File f = fileOf(invoke(pic, "getSourcePath"));
        if (f == null) {
            Object thumbs = invoke(pic, "getThumbPath");
            if (thumbs instanceof Map) {
                for (Object v : ((Map<?, ?>) thumbs).values()) {
                    File tf = fileOf(v);
                    if (tf != null) {
                        f = tf;
                        break;
                    }
                }
            }
        }
        if (f == null) {
            LogX.probe("图片元素没有本地 sourcePath/thumbPath，跳过（不联网下载）");
            return;
        }
        byte[] data = readFile(f);
        if (data == null) {
            return;
        }
        QqMedia m = new QqMedia();
        m.type = "sticker";
        m.data = data;
        m.originalName = str(invoke(pic, "getFileName"), f.getName());
        m.source = source;
        Object w = invoke(pic, "getPicWidth");
        Object h = invoke(pic, "getPicHeight");
        m.width = w instanceof Integer ? (Integer) w : 0;
        m.height = h instanceof Integer ? (Integer) h : 0;
        m.debug = "PicElement path=" + f.getAbsolutePath();
        out.add(m);
    }

    private static String buildSource(Object record) {
        Object peerName = invoke(record, "getPeerName");
        Object peerUid = invoke(record, "getPeerUid");
        Object chatType = invoke(record, "getChatType");
        String who = str(peerName, str(peerUid, ""));
        if (who.isEmpty()) {
            return "QQ";
        }
        return "QQ:" + who + (chatType instanceof Integer ? "#" + chatType : "");
    }

    private static File fileOf(Object path) {
        if (!(path instanceof String)) {
            return null;
        }
        String s = ((String) path).trim();
        if (s.isEmpty()) {
            return null;
        }
        File f = new File(s);
        return f.isFile() ? f : null;
    }

    private static byte[] readFile(File f) {
        if (f.length() <= 0 || f.length() > MAX_FILE) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
            return data;
        } catch (IOException e) {
            LogX.e("读取文件失败: " + f, e);
            return null;
        }
    }

    private static Object invoke(Object obj, String method) {
        if (obj == null) {
            return null;
        }
        try {
            Method m = obj.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Throwable t) {
            LogX.probe("反射调用失败 " + obj.getClass().getSimpleName() + "#" + method + ": " + t);
            return null;
        }
    }

    private static String str(Object o, String fallback) {
        if (o instanceof String && !((String) o).isEmpty()) {
            return (String) o;
        }
        return fallback;
    }

    private static String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    /** 生成便于日志查看的摘要。 */
    public static String describe(Object aioMsgItem) {
        if (aioMsgItem == null) {
            return "null";
        }
        try {
            Object record = invoke(aioMsgItem, "getMsgRecord");
            Object elements = invoke(record, "getElements");
            return "msgId=" + invoke(aioMsgItem, "getMsgId")
                    + " elements=" + (elements instanceof List ? ((List<?>) elements).size() : "?");
        } catch (Throwable t) {
            return "describe-error:" + t;
        }
    }
}
