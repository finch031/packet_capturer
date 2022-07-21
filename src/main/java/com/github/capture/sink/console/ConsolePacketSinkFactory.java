package com.github.capture.sink.console;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 14:33
 * @description
 */
public class ConsolePacketSinkFactory implements PacketSinkFactory {

    public ConsolePacketSinkFactory(){
    }

    @Override
    public PacketSink getPacketSink(AppConfiguration appConf) {
        return new ConsolePacketSink();
    }
}
