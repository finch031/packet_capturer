package com.github.capture.source;

import com.github.capture.model.Pcap4jCapturePacket;
import com.github.capture.pcap4j.Pcap4jCapture;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/8/4 20:44
 * @description
 */
public class Pcap4jPacketSource implements PacketSource<Pcap4jCapturePacket>{
    private static final Pcap4jCapture pcap4jCapture = Pcap4jCapture.getInstance();

    public Pcap4jPacketSource(){}

    @Override
    public Pcap4jCapturePacket nextPacket() {
        return pcap4jCapture.getNextPacket();
    }
}
