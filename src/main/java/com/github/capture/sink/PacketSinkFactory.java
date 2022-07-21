package com.github.capture.sink;

import com.github.capture.conf.AppConfiguration;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 14:16
 * @description
 */
public interface PacketSinkFactory {
    PacketSink getPacketSink(AppConfiguration appConf);
}
