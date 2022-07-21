package com.github.capture.sink.file;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.model.TcpPacketRecord;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;
import com.github.capture.utils.Utils;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 14:18
 * @description
 */
public class CsvPacketSinkFactory implements PacketSinkFactory {
    private static final CsvPacketSink.CsvLineConverter DEFAULT_CSV_LINE_CONVERTER = new CsvPacketSink.CsvLineConverter() {
        @Override
        public String header() {
            return  "message_id" +
                    "," +
                    "capture_time" +
                    "," +
                    "ip_src_address" +
                    "," +
                    "ip_dst_address" +
                    "," +
                    "tcp_src_port" +
                    "," +
                    "tcp_dst_port" +
                    "," +
                    "tcp_seq_number";
        }

        @Override
        public String toLine(TcpPacketRecord record) {
            return record.getMessageID() +
                    "," +
                    Utils.timestampToDateTime(record.getMessageCaptureTs()) +
                    "," +
                    record.getIpSrcAddress() +
                    "," +
                    record.getIpDstAddress() +
                    "," +
                    record.getTcpSrcPort() +
                    "," +
                    record.getTcpDstPort() +
                    "," +
                    record.getTcpSeqNumber();
        }
    };

    private final CsvPacketSink.CsvLineConverter csvLineConverter;

    public CsvPacketSinkFactory(CsvPacketSink.CsvLineConverter csvLineConverter){
        this.csvLineConverter = csvLineConverter;
    }

    public CsvPacketSinkFactory(){
        this.csvLineConverter = DEFAULT_CSV_LINE_CONVERTER;
    }

    @Override
    public PacketSink getPacketSink(AppConfiguration appConf) {
        return new CsvPacketSink(appConf,csvLineConverter);
    }
}
