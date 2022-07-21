package com.github.capture.sink.redis;

import com.github.capture.model.TcpPacketRecord;
import com.github.capture.sink.Callback;
import com.github.capture.sink.PacketSink;
import com.github.capture.utils.Utils;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/12 19:54
 * @description
 */
public class RedisSink implements PacketSink {
    private final Jedis jedis;

    public RedisSink(Jedis jedis){
        this.jedis = jedis;
    }

    @Override
    public SinkType sinkType() {
        return SinkType.REDIS_SINK;
    }

    @Override
    public void writeTo(TcpPacketRecord record, Callback callback) throws Exception {
        String key = record.getMessageID();
        Map<String,String> value = new HashMap<>();

        value.put("message_capture_time", Utils.timestampToDateTime(record.getMessageCaptureTs()));
        value.put("ip_src_address",record.getIpSrcAddress());
        value.put("ip_dst_address",record.getIpDstAddress());
        value.put("tcp_src_port",String.valueOf(record.getTcpSrcPort()));
        value.put("tcp_dst_port",String.valueOf(record.getTcpDstPort()));
        value.put("tcp_seq_number",String.valueOf(record.getTcpSeqNumber()));
        value.put("tcp_total_len",String.valueOf(record.getIpTotalLen()));
        value.put("tcp_window_size",String.valueOf(record.getTcpWindow()));

        jedis.hset(key,value);
        jedis.expire(key,300);

        record = null;
    }

    @Override
    public void gatherAndWriteTo(TcpPacketRecord record) throws Exception {
    }

    @Override
    public void open() {
        jedis.connect();
    }

    @Override
    public void close() throws IOException {
        jedis.close();
    }
}