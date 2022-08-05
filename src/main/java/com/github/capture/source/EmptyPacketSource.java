package com.github.capture.source;

import com.github.capture.model.CapturePacket;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/8/5 22:29
 * @description
 */
public class EmptyPacketSource implements PacketSource<CapturePacket>{
    @Override
    public CapturePacket nextPacket() {
        return CapturePacket.emptyPacket();
    }
}
