package com.tjunicom.iptv;

import com.tjunicom.iptv.model.IptvChannel;
import com.tjunicom.iptv.parser.M3uParser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class M3uParserTest {

    @Test
    public void testParseTianjinUnicomM3uAndExport() {
        String sample = "#EXTM3U x-tvg-url=\"http://epg.51zmt.top:8000/e.xml.gz\"\n" +
                "#EXTINF:-1 tvg-id=\"1\" tvg-name=\"CCTV1\" tvg-logo=\"http://epg.51zmt.top:8000/tb1/CCTV/CCTV1.png\" group-title=\"[高清]央视\",CCTV-1综合[1][高清]\n" +
                "udp://225.1.1.111:5002\n" +
                "#EXTINF:-1 tvg-id=\"106\" tvg-name=\"CCTV4K\" tvg-logo=\"http://epg.51zmt.top:8000/tb1/CCTV/CCTV4k.png\" group-title=\"[高清]央视;[高清]天津IPTV;4K\",CCTV 4K[85][4K]\n" +
                "udp://225.1.2.44:5002\n";

        List<IptvChannel> list = M3uParser.parseString(sample);
        assertEquals(2, list.size());

        IptvChannel cctv1 = list.get(0);
        assertEquals(1, cctv1.getChannelNumber());
        assertEquals("CCTV-1综合", cctv1.getDisplayName());
        assertEquals("高清", cctv1.getResolutionTag());
        assertEquals("225.1.1.111", cctv1.getMulticastIp());
        assertEquals(5002, cctv1.getMulticastPort());

        IptvChannel cctv4k = list.get(1);
        assertEquals(85, cctv4k.getChannelNumber());
        assertEquals("4K", cctv4k.getResolutionTag());
        assertTrue(cctv4k.getGroups().contains("4K"));

        cctv1.setConnected(true);
        cctv1.setActualResolutionFlag("高清");
        cctv1.setVideoTrackInfo("#1 h264, 1920x1080,25fps");
        cctv1.setAudioTrackInfo("#1 mp2,2ch,48khz");

        cctv4k.setConnected(false);
        cctv4k.setActualResolutionFlag("错误");

        String modifiedM3u = M3uParser.toM3uContent(list, true, "192.168.6.1:5002");
        // 天津联通是 udp:// 组播，udpxy 路径必须是 /udp/ 而不是 /rtp/
        assertTrue(modifiedM3u.contains("http://192.168.6.1:5002/udp/225.1.1.111:5002"));
        assertFalse(modifiedM3u.contains("225.1.2.44"));

        String csv = M3uParser.toResultsCsvContent(list);
        assertTrue(csv.contains("编号,频道名称,video track,audio track,screenshot_name,连接flag,分辨率flag"));
        assertTrue(csv.contains("#1 h264, 1920x1080,25fps"));
    }
}
