package com.github.capture.sink.ch;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 14:32
 * @description
 */
public class ClickHousePacketSinkFactory implements PacketSinkFactory {

    public ClickHousePacketSinkFactory(){
    }

    @Override
    public PacketSink getPacketSink(AppConfiguration appConf) {
        String chServerHost = appConf.getString("client.ch.server.hosts","");
        int chServerPort = appConf.getInteger("client.ch.server.port",8123);
        String chDB = appConf.getString("client.ch.server.db","");
        String chUser = appConf.getString("client.ch.server.user","");
        String chPassword = appConf.getString("client.ch.server.password","");

        return new ClickHouseSink(chServerHost,chServerPort,chDB,chUser,chPassword);
    }
}
