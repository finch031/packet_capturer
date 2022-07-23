package com.github.capture.disruptor;

import com.github.capture.model.TcpPacketRecord;
import com.lmax.disruptor.EventFactory;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/22 10:01
 * @description
 */
public class PacketEventFactory implements EventFactory<TcpPacketRecord> {
    public PacketEventFactory(){
    }

    @Override
    public TcpPacketRecord newInstance() {
        return new TcpPacketRecord();
    }
}
