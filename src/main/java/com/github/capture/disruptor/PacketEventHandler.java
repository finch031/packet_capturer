package com.github.capture.disruptor;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.model.CapturePacket;
import com.github.capture.model.TcpPacketRecord;
import com.github.capture.parser.PacketParser;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;
import com.github.capture.utils.IdGenerator;
import com.github.capture.utils.Snowflake;
import com.lmax.disruptor.EventHandler;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/22 10:14
 * @description
 */
public class PacketEventHandler implements EventHandler<CapturePacket> {
    private final String handlerName;
    private final PacketSink sink;
    private float metricSpeed = 0f;
    private static final IdGenerator idGenerator = new Snowflake(6,10);
    private static final TcpPacketRecord EMPTY_RECORD = new TcpPacketRecord();

    public PacketEventHandler(AppConfiguration appConf, PacketSinkFactory factory, String handlerName){
        this.sink = factory.getPacketSink(appConf);
        this.handlerName = handlerName;
        EMPTY_RECORD.setMessageID("000000000000000000");
    }

    public float getMetricSpeed() {
        return metricSpeed;
    }

    public String getHandlerName(){
        return handlerName;
    }

    @Override
    public void onEvent(CapturePacket record, long l, boolean b) throws Exception {
        long metricStartNano = System.nanoTime();
        byte[] data = record.data();

        // 数据包解析
        TcpPacketRecord tcpPacketRecord = PacketParser.parse(data, 0, data.length, String.valueOf(idGenerator.id()), record.captureTimeStamp());

        sink.writeTo(tcpPacketRecord == null ? updateEmptyRecord() : tcpPacketRecord,null);

        metricSpeed = 1_000_000_000f / (System.nanoTime() - metricStartNano);
    }

    private TcpPacketRecord updateEmptyRecord(){
        EMPTY_RECORD.setMessageCaptureTs(System.currentTimeMillis());
        return EMPTY_RECORD;
    }
}