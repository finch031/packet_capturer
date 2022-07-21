package com.github.capture.sink.ch;

import com.github.capture.model.TcpPacketRecord;
import com.github.capture.sink.Callback;
import com.github.capture.sink.PacketSink;
import ru.yandex.clickhouse.ClickHouseConnection;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/24 13:53
 * @description
 */
public class ClickHouseSink implements PacketSink {

    private final ClickHouseJDBCClient client;

    private ClickHouseConnection conn;

    private final List<TcpPacketRecord> writeBuffer = new ArrayList<>();

    public ClickHouseSink(String chServerHost,
                          int chServerPort,
                          String chDB,
                          String chUser,
                          String chPassword){

        this.client = new ClickHouseJDBCClient(chServerHost,chServerPort,chDB,chUser,chPassword);
        try{
            this.conn = client.newConnection();
        }catch (SQLException se){
            se.printStackTrace();
        }
    }

    @Override
    public SinkType sinkType() {
        return SinkType.CLICKHOUSE_SINK;
    }

    @Override
    public void writeTo(TcpPacketRecord record, Callback callback) throws Exception {
        String sql = "insert into packet_capture values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        writeBuffer.add(record);

        if(writeBuffer.size() >= 200){
            System.out.println("start write batch...");

            client.insert(conn, sql, new ClickHouseJDBCClient.RowAppender() {
                @Override
                public void addRows(PreparedStatement preparedStatement) {

                    for(TcpPacketRecord record : writeBuffer) {

                        try {
                            preparedStatement.setString(1, record.getMessageID());

                            preparedStatement.setLong(2, record.getMessageTs());

                            preparedStatement.setLong(3, record.getMessageCaptureTs());

                            preparedStatement.setInt(4, record.getEtherType());

                            preparedStatement.setString(5, record.getEthSrcAddress());

                            preparedStatement.setString(6, record.getEthDstAddress());

                            preparedStatement.setInt(7, record.getIpVersion());

                            preparedStatement.setInt(8, record.getIpIhl());

                            preparedStatement.setInt(9, record.getIpTos());

                            preparedStatement.setInt(10, record.getIpTotalLen());

                            preparedStatement.setInt(11, record.getIpIdentification());

                            preparedStatement.setInt(12, record.getTcpReserved());

                            preparedStatement.setInt(13, record.isIpDontFragmentFlag() ? 1 : 0);

                            preparedStatement.setInt(14, record.isIpMoreFragmentFlag() ? 1 : 0);

                            preparedStatement.setInt(15, record.getIpFragmentOffset());

                            preparedStatement.setInt(16, record.getIpTtl());

                            preparedStatement.setInt(17, record.getIpProtocol());

                            preparedStatement.setInt(18, record.getIpHeaderChecksum());

                            preparedStatement.setString(19, record.getIpSrcAddress());

                            preparedStatement.setString(20, record.getIpDstAddress());

                            preparedStatement.setInt(21, record.getTcpSrcPort());

                            preparedStatement.setInt(22, record.getTcpDstPort());

                            preparedStatement.setLong(23, record.getTcpSeqNumber());

                            preparedStatement.setLong(24, record.getTcpAcknowledgmentNumber());

                            preparedStatement.setInt(25, record.getTcpDataOffset());

                            preparedStatement.setInt(26, record.getTcpReserved());

                            preparedStatement.setInt(27, record.isTcpSyn() ? 1 : 0);

                            preparedStatement.setInt(28, record.isTcpAck() ? 1 : 0);

                            preparedStatement.setInt(29, record.isTcpFin() ? 1 : 0);

                            preparedStatement.setInt(30, record.isTcpPsh() ? 1 : 0);

                            preparedStatement.setInt(31, record.isTcpRst() ? 1 : 0);

                            preparedStatement.setInt(32, record.isTcpUrg() ? 1 : 0);

                            preparedStatement.setInt(33, record.getTcpChecksum());

                            preparedStatement.setInt(34, record.getTcpWindow());

                            preparedStatement.setInt(35, record.getTcpUrgentPointer());

                            preparedStatement.addBatch();
                        } catch (SQLException se) {
                            se.printStackTrace();
                        }

                    }

                }
            });

            writeBuffer.clear();
        }

        record = null;
    }

    @Override
    public void gatherAndWriteTo(TcpPacketRecord record) throws Exception {

    }

    @Override
    public void open() {

    }

    @Override
    public void close() throws IOException {
        try{
            this.conn.close();
        }catch (SQLException se){
            se.printStackTrace();
        }
    }
}