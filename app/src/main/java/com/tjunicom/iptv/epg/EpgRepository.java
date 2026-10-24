package com.tjunicom.iptv.epg;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

/**
 * 电子节目单（EPG）：拉取 XMLTV，按频道名匹配出「当前节目 / 下一档节目」。
 *
 * 数据源用的是 m3u 头部里那家（http://epg.51zmt.top:8000/e.xml.gz）。
 * 注意：这个地址虽然叫 .gz，实际返回的是**明文 XML**（2026-10 实测），所以代码里两种都兼容。
 */
public class EpgRepository {

    private static final String TAG = "EpgRepository";
    private static final String EPG_URL = "http://epg.51zmt.top:8000/e.xml.gz";
    private static final String CACHE_NAME = "epg.xml";
    private static final long CACHE_TTL_MS = 6 * 60 * 60 * 1000L;   // 6 小时

    private static volatile EpgRepository instance;

    public static class Program {
        public String title;
        public long startMs;
        public long stopMs;

        public String rangeText() {
            SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
            return f.format(new Date(startMs)) + "-" + f.format(new Date(stopMs));
        }
    }

    private final Context appContext;
    private final Map<String, String> keyToChannelId = new HashMap<>();
    private final Map<String, List<Program>> programsByChannel = new HashMap<>();
    private volatile boolean loaded;
    private volatile boolean loading;
    private volatile long loadedAt;

    private EpgRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static EpgRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (EpgRepository.class) {
                if (instance == null) {
                    instance = new EpgRepository(context);
                }
            }
        }
        return instance;
    }

    public boolean isReady() {
        return loaded && System.currentTimeMillis() - loadedAt < CACHE_TTL_MS;
    }

    /** 后台加载：先用本地缓存，缓存过期再联网刷新 */
    public void loadAsync(final Runnable onDone) {
        if (loading) {
            return;
        }
        loading = true;
        new Thread(() -> {
            try {
                File cache = new File(appContext.getCacheDir(), CACHE_NAME);
                boolean fresh = cache.exists()
                        && System.currentTimeMillis() - cache.lastModified() < CACHE_TTL_MS;
                if (fresh) {
                    parse(readFile(cache));
                    Log.i(TAG, "EPG loaded from cache: " + programsByChannel.size() + " channels");
                } else {
                    byte[] data = download();
                    if (data != null && data.length > 0) {
                        parse(data);
                        writeFile(cache, data);
                        Log.i(TAG, "EPG downloaded: " + programsByChannel.size() + " channels");
                    } else if (cache.exists()) {
                        parse(readFile(cache));
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "EPG load failed: " + t.getMessage());
            } finally {
                loading = false;
                if (programsByChannel.size() > 0) {
                    loaded = true;
                    loadedAt = System.currentTimeMillis();
                }
                if (onDone != null) {
                    onDone.run();
                }
            }
        }, "epg-loader").start();
    }

    /** 当前正在播的节目；找不到返回 null */
    public Program currentProgram(String channelTitle) {
        List<Program> list = programsOf(channelTitle);
        if (list == null || list.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (Program p : list) {
            if (now >= p.startMs && now < p.stopMs) {
                return p;
            }
        }
        return null;
    }

    /** 下一档节目 */
    public Program nextProgram(String channelTitle) {
        List<Program> list = programsOf(channelTitle);
        if (list == null || list.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (Program p : list) {
            if (p.startMs > now) {
                return p;
            }
        }
        return null;
    }

    public List<Program> programsOf(String channelTitle) {
        String key = normalize(channelTitle);
        if (key.isEmpty()) {
            return null;
        }
        String id = keyToChannelId.get(key);
        if (id == null) {
            // 二次兜底：互相包含匹配
            for (Map.Entry<String, String> e : keyToChannelId.entrySet()) {
                if (e.getKey().contains(key) || key.contains(e.getKey())) {
                    id = e.getValue();
                    break;
                }
            }
        }
        return id == null ? null : programsByChannel.get(id);
    }

    // ---------------------------------------------------------------- 解析

    private void parse(byte[] data) throws Exception {
        InputStream in = new java.io.ByteArrayInputStream(data);
        if (data.length > 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B) {
            in = new GZIPInputStream(in);
        }
        Map<String, String> idToName = new HashMap<>();
        Map<String, List<Program>> programmes = new HashMap<>();

        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(in, "UTF-8");

        String curChannelId = null;
        Program curProgram = null;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            String tag = parser.getName();
            if (event == XmlPullParser.START_TAG) {
                if ("channel".equals(tag)) {
                    curChannelId = parser.getAttributeValue(null, "id");
                } else if ("programme".equals(tag)) {
                    curProgram = new Program();
                    curProgram.startMs = parseTime(parser.getAttributeValue(null, "start"));
                    curProgram.stopMs = parseTime(parser.getAttributeValue(null, "stop"));
                    String ch = parser.getAttributeValue(null, "channel");
                    if (ch != null) {
                        programmes.computeIfAbsent(ch, k -> new ArrayList<>()).add(curProgram);
                    } else {
                        curProgram = null;
                    }
                } else if ("display-name".equals(tag) && curChannelId != null) {
                    String name = parser.nextText();
                    if (name != null) {
                        idToName.put(curChannelId, name.trim());
                    }
                } else if ("title".equals(tag) && curProgram != null) {
                    curProgram.title = parser.nextText();
                }
            } else if (event == XmlPullParser.END_TAG) {
                if ("channel".equals(tag)) {
                    curChannelId = null;
                } else if ("programme".equals(tag)) {
                    curProgram = null;
                }
            }
            event = parser.next();
        }

        keyToChannelId.clear();
        for (Map.Entry<String, String> e : idToName.entrySet()) {
            String key = normalize(e.getValue());
            if (!key.isEmpty()) {
                keyToChannelId.put(key, e.getKey());
            }
        }
        for (Map.Entry<String, String> e : idToName.entrySet()) {
            // 数字 id 的频道也支持按名字直接查
            String key = normalize(e.getKey());
            if (!key.isEmpty() && !keyToChannelId.containsKey(key)) {
                keyToChannelId.put(key, e.getKey());
            }
        }
        programsByChannel.clear();
        programsByChannel.putAll(programmes);
        for (List<Program> list : programsByChannel.values()) {
            list.sort((a, b) -> Long.compare(a.startMs, b.startMs));
        }
    }

    private static long parseTime(String xmltv) {
        if (xmltv == null || xmltv.length() < 14) {
            return 0L;
        }
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            return f.parse(xmltv.substring(0, 14)).getTime();
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 频道名归一化，便于跨数据源匹配：
     * "中央13高清" / "中央—13" → "CCTV13"，"CCTV1高清直播" → "CCTV1"，去掉 [高清] 等标记
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        s = s.replaceAll("\\[[^\\]]*\\]", "");
        s = s.replace("—", "-").replace("－", "-").replace("——", "-");
        s = s.replaceAll("(?i)(超高清|高清|标清|蓝光|uhd|hd|4k|8k|直播|频道|套)", "");
        s = s.replaceAll("^中央\\s*-?\\s*(\\d+)", "CCTV$1");
        s = s.replaceAll("\\s+", "");
        return s.toUpperCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- IO

    private byte[] download() {
        String urlStr = EPG_URL;
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection conn = null;
            try {
                HttpURLConnection.setFollowRedirects(true);
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("Accept-Encoding", "gzip");
                conn.setRequestProperty("User-Agent", "tjTV/0.1 (+Android)");
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    // 51zmt 会 http -> https 跳到带 token 的地址，而 HttpURLConnection 不跨协议跟随
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        return null;
                    }
                    urlStr = new URL(url, location).toString();
                    continue;
                }
                if (code != 200) {
                    Log.w(TAG, "EPG http " + code + " for " + urlStr);
                    return null;
                }
                InputStream in = conn.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream(1024 * 1024);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                in.close();
                return bos.toByteArray();
            } catch (Throwable t) {
                Log.w(TAG, "EPG download failed: " + t.getMessage());
                return null;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        return null;
    }

    private static byte[] readFile(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) f.length());
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static void writeFile(File f, byte[] data) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        } catch (Exception ignored) {
        }
    }
}
