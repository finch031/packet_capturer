package com.github.capture.sink;

import com.github.capture.model.TcpPacketRecord;

import java.io.Closeable;

/**
 * @author yusheng
 * @datetime 2022/6/14 16:30
 * @description
 */
public interface PacketSink extends Closeable {
    enum SinkType{
        CONSOLE_SINK,
        CSV_SINK,
        MYSQL_SINK,
        KAFKA_SINK,
        CLICKHOUSE_SINK
    }

    SinkType sinkType();

    void writeTo(TcpPacketRecord record, Callback callback) throws Exception;

    void gatherAndWriteTo(TcpPacketRecord record) throws Exception;

    void open();
}