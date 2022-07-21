package com.github.capture.sink.es;

import com.github.capture.model.TcpPacketRecord;
import com.github.capture.sink.Callback;
import com.github.capture.sink.PacketSink;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/6 17:59
 * @description
 */
public class EsSink implements PacketSink {
    private final List<IndexRequest> docs = new ArrayList<>();

    private final Es7xRestHighLevelClient client;
    private final String index;

    public EsSink(Es7xRestHighLevelClient client, String index){
        this.client = client;
        this.index = index;
    }

    @Override
    public SinkType sinkType() {
        return SinkType.ES_SINK;
    }

    @Override
    public void open() {
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    @Override
    public void gatherAndWriteTo(TcpPacketRecord record) throws Exception {
    }

    @Override
    public void writeTo(TcpPacketRecord record, Callback callback) throws Exception {
        if(record != null){
            Map<String,Object> doc = new HashMap<>();
            doc.put("message_id",record.getMessageID());
            doc.put("message_capture_ts",record.getMessageCaptureTs());
            doc.put("eth_src_address",record.getEthSrcAddress());
            doc.put("eth_dst_address",record.getEthDstAddress());
            doc.put("ip_src_address",record.getIpSrcAddress());
            doc.put("ip_dst_address",record.getIpDstAddress());
            doc.put("tcp_src_port",record.getTcpSrcPort());
            doc.put("tcp_dst_port",record.getTcpDstPort());

            IndexRequest indexRequest = client.indexRequestFromMap(index,doc);
            docs.add(indexRequest);
        }

        if(docs.size() >= 200){
            System.out.println("start write batch...");
            BulkRequest bulkRequest = client.bulkIndexRequest(docs);
            RequestOptions requestOptions = RequestOptions.DEFAULT;
            BulkResponse bulkResponse = client.bulk(bulkRequest,requestOptions);
            System.out.println(bulkResponse.status().toString());

            docs.clear();
        }

        record = null;
    }
}
