package com.tjunicom.iptv.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * 播放器与天津联通 IPTV 网络转发配置管理
 */
public class IptvSettings {
    private static final String PREF_NAME = "tj_unicom_iptv_prefs";

    /**
     * 天津联通默认网络参数（依据 gabacai/tj-unicom-iptv 的 iptv.php 与天津实际组播网段）：
     * - 组播是 RTP 封装：rtp://225.1.1.x:5002 / rtp://225.1.2.x:5002（tcpdump 实测 1328=12B RTP+7x188 TS）
     * - udpxy 转单播默认 http://192.168.6.1:5002/udp/ip:port
     */
    public static final String DEFAULT_UDPXY_ADDR = "192.168.6.1:5002";
    public static final String DEFAULT_SCAN_SUBNET = "225.1.1";
    public static final String DEFAULT_LAST_CHANNEL_URL = "rtp://225.1.1.111:5002";

    // 流播放传输模式
    public static final int STREAM_MODE_UDPXY = 0;          // 路由器/软路由 udpxy 组播转单播 (如 http://192.168.2.1:8012/rtp/...)
    public static final int STREAM_MODE_LOCAL_PROXY = 1;    // 内置 Android 本地组播转 HTTP 代理 (127.0.0.1)
    public static final int STREAM_MODE_DIRECT_RTP = 2;     // ijkplayer 直接播放 rtp://239.3.1.x:port

    // 播放器内核模式 (参考 ijkplayer Settings)
    public static final int PLAYER_IJK = 0;                 // IjkMediaPlayer (推荐，支持 MPEG-TS/RTP/HEVC 4K)
    public static final int PLAYER_ANDROID_MEDIA = 1;       // AndroidMediaPlayer
    public static final int PLAYER_IJK_EXO = 2;             // IjkExoMediaPlayer

    private static final String KEY_STREAM_MODE = "stream_mode";
    private static final String KEY_UDPXY_ADDR = "udpxy_addr";
    private static final String KEY_PLAYER_TYPE = "player_type";
    private static final String KEY_MEDIACODEC_ENABLED = "mediacodec_enabled";
    private static final String KEY_MEDIACODEC_HEVC = "mediacodec_hevc";
    private static final String KEY_OPENSLES_ENABLED = "opensles_enabled";
    private static final String KEY_RENDER_VIEW_TYPE = "render_view_type"; // 0: SurfaceView, 1: TextureView
    private static final String KEY_AUTO_4K_EXO = "auto_4k_exo";
    private static final String KEY_ASPECT_RATIO = "aspect_ratio";
    private static final String KEY_INCLUDE_HISTORICAL = "include_historical_channels";
    private static final String KEY_ONLY_SHOW_AVAILABLE = "only_show_available";
    private static final String KEY_LAST_CHANNEL_URL = "last_channel_url";
    private static final String KEY_SCAN_SUBNET = "scan_subnet";
    private static final String KEY_SCAN_START_HOST = "scan_start_host";
    private static final String KEY_SCAN_END_HOST = "scan_end_host";
    private static final String KEY_SCAN_TIMEOUT_MS = "scan_timeout_ms";
    private static final String KEY_FAVORITES = "favorite_urls";

    private final SharedPreferences prefs;

    public IptvSettings(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getStreamMode() {
        return prefs.getInt(KEY_STREAM_MODE, STREAM_MODE_UDPXY);
    }

    public void setStreamMode(int mode) {
        prefs.edit().putInt(KEY_STREAM_MODE, mode).apply();
    }

    /**
     * 天津联通 udpxy 默认地址 192.168.6.1:5002（转单播走 /udp/ 路径）。
     * 路由器/软路由地址不同时，在「播放与网络设置」里改。
     */
    public String getUdpxyAddress() {
        String addr = prefs.getString(KEY_UDPXY_ADDR, DEFAULT_UDPXY_ADDR);
        return TextUtils.isEmpty(addr) ? DEFAULT_UDPXY_ADDR : addr.trim();
    }

    public void setUdpxyAddress(String addr) {
        prefs.edit().putString(KEY_UDPXY_ADDR, addr != null ? addr.trim() : "").apply();
    }

    public int getPlayerType() {
        return prefs.getInt(KEY_PLAYER_TYPE, PLAYER_IJK);
    }

    public void setPlayerType(int playerType) {
        prefs.edit().putInt(KEY_PLAYER_TYPE, playerType).apply();
    }

    public boolean isMediaCodecEnabled() {
        return prefs.getBoolean(KEY_MEDIACODEC_ENABLED, true);
    }

    public void setMediaCodecEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MEDIACODEC_ENABLED, enabled).apply();
    }

    public boolean isMediaCodecHevcEnabled() {
        return prefs.getBoolean(KEY_MEDIACODEC_HEVC, true);
    }

    public void setMediaCodecHevcEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MEDIACODEC_HEVC, enabled).apply();
    }

    public boolean isOpenSlEsEnabled() {
        return prefs.getBoolean(KEY_OPENSLES_ENABLED, false);
    }

    public void setOpenSlEsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_OPENSLES_ENABLED, enabled).apply();
    }

    public int getRenderViewType() {
        return prefs.getInt(KEY_RENDER_VIEW_TYPE, 0); // 默认 SurfaceRenderView 对 4K/TV 性能最佳
    }

    public void setRenderViewType(int type) {
        prefs.edit().putInt(KEY_RENDER_VIEW_TYPE, type).apply();
    }

    /**
     * 4K 频道是否自动切到 Exo 内核（IJK 播 HEVC 4K 常回退 FFmpeg 软解，只有 2fps）
     */
    public boolean isAutoExoFor4k() {
        return prefs.getBoolean(KEY_AUTO_4K_EXO, true);
    }

    public void setAutoExoFor4k(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_4K_EXO, enabled).apply();
    }

    public int getAspectRatio() {
        return prefs.getInt(KEY_ASPECT_RATIO, 0);
    }

    public void setAspectRatio(int aspectRatio) {
        prefs.edit().putInt(KEY_ASPECT_RATIO, aspectRatio).apply();
    }

    /**
     * 是否包含仓库全部 215 个频道（默认开启，完整覆盖项目中的所有频道）
     */
    public boolean isIncludeAllRepoChannels() {
        return prefs.getBoolean(KEY_INCLUDE_HISTORICAL, true);
    }

    public void setIncludeAllRepoChannels(boolean include) {
        prefs.edit().putBoolean(KEY_INCLUDE_HISTORICAL, include).apply();
    }

    public boolean isOnlyShowAvailable() {
        return prefs.getBoolean(KEY_ONLY_SHOW_AVAILABLE, false);
    }

    public void setOnlyShowAvailable(boolean onlyAvailable) {
        prefs.edit().putBoolean(KEY_ONLY_SHOW_AVAILABLE, onlyAvailable).apply();
    }

    public String getLastChannelUrl() {
        return prefs.getString(KEY_LAST_CHANNEL_URL, DEFAULT_LAST_CHANNEL_URL);
    }

    public void setLastChannelUrl(String url) {
        prefs.edit().putString(KEY_LAST_CHANNEL_URL, url).apply();
    }

    public String getScanSubnet() {
        return prefs.getString(KEY_SCAN_SUBNET, DEFAULT_SCAN_SUBNET);
    }

    public void setScanSubnet(String subnet) {
        prefs.edit().putString(KEY_SCAN_SUBNET, subnet != null ? subnet.trim() : DEFAULT_SCAN_SUBNET).apply();
    }

    public int getScanStartHost() {
        return prefs.getInt(KEY_SCAN_START_HOST, 1);
    }

    public void setScanStartHost(int start) {
        prefs.edit().putInt(KEY_SCAN_START_HOST, Math.max(1, Math.min(254, start))).apply();
    }

    public int getScanEndHost() {
        return prefs.getInt(KEY_SCAN_END_HOST, 254);
    }

    public void setScanEndHost(int end) {
        prefs.edit().putInt(KEY_SCAN_END_HOST, Math.max(1, Math.min(254, end))).apply();
    }

    public int getScanTimeoutMs() {
        return prefs.getInt(KEY_SCAN_TIMEOUT_MS, 1800);
    }

    public void setScanTimeoutMs(int timeoutMs) {
        prefs.edit().putInt(KEY_SCAN_TIMEOUT_MS, Math.max(300, Math.min(15000, timeoutMs))).apply();
    }

    public java.util.Set<String> getFavoriteUrls() {
        return new java.util.HashSet<>(prefs.getStringSet(KEY_FAVORITES, new java.util.HashSet<>()));
    }

    public void toggleFavorite(String rawUrl) {
        java.util.Set<String> set = getFavoriteUrls();
        if (set.contains(rawUrl)) {
            set.remove(rawUrl);
        } else {
            set.add(rawUrl);
        }
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply();
    }
}
