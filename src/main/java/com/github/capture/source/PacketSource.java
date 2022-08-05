package com.github.capture.source;

import com.github.capture.model.CapturePacket;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/8/4 20:41
 * @description
 */
public interface PacketSource<T extends CapturePacket> {
    T nextPacket();
}
