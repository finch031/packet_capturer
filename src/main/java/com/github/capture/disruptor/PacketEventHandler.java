package com.github.capture.disruptor;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.model.TcpPacketRecord;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;
import com.lmax.disruptor.EventHandler;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/22 10:14
 * @description
 */
public class PacketEventHandler implements EventHandler<TcpPacketRecord> {
    private final String handlerName;
    private final PacketSink sink;
    private float metricSpeed = 0f;

    public PacketEventHandler(AppConfiguration appConf, PacketSinkFactory factory, String handlerName){
        this.sink = factory.getPacketSink(appConf);
        this.handlerName = handlerName;
    }

    public float getMetricSpeed() {
        return metricSpeed;
    }

    public String getHandlerName(){
        return handlerName;
    }

    @Override
    public void onEvent(TcpPacketRecord record, long l, boolean b) throws Exception {
        long metricStartNano = System.nanoTime();
        sink.writeTo(record,null);
        metricSpeed = 1_000_000_000f / (System.nanoTime() - metricStartNano);
    }
}