package com.github.capture.disruptor;

import com.github.capture.model.CapturePacket;
import com.github.capture.model.Pcap4jCapturePacket;
import com.lmax.disruptor.EventFactory;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/22 10:01
 * @description
 */
public class PacketEventFactory implements EventFactory<CapturePacket> {
    private final String captureSourceType;

    public PacketEventFactory(String captureSourceType){
        this.captureSourceType = captureSourceType;
    }

    @Override
    public CapturePacket newInstance() {
        switch (captureSourceType){
            case "pcap4j":
                return new Pcap4jCapturePacket();
            case "none":
                return CapturePacket.emptyPacket();
            default:
                break;
        }
        return CapturePacket.emptyPacket();
    }
}
