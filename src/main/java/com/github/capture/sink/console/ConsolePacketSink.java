package com.github.capture.sink.console;

import com.github.capture.model.TcpPacketRecord;
import com.github.capture.sink.Callback;
import com.github.capture.sink.PacketSink;
import com.github.capture.utils.Utils;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/14 16:38
 * @description
 */
public class ConsolePacketSink implements PacketSink {

    public ConsolePacketSink(){
    }

    @Override
    public SinkType sinkType() {
        return SinkType.CONSOLE_SINK;
    }

    @Override
    public void writeTo(TcpPacketRecord record, Callback callback) throws Exception {
        System.out.println(Utils.timestampToDateTime(record.getMessageCaptureTs()) + "," + record);

        record = null;
    }

    @Override
    public void gatherAndWriteTo(TcpPacketRecord record) throws Exception {
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }
}