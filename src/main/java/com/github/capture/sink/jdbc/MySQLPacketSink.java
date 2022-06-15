package com.github.capture.sink.jdbc;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.db.QueryRunner;
import com.github.capture.db.ResultSetHandler;
import com.github.capture.model.TcpPacketRecord;
import com.github.capture.sink.Callback;
import com.github.capture.sink.PacketSink;
import com.github.capture.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;
import javax.sql.rowset.RowSetProvider;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/15 13:23
 * @description
 */
public class MySQLPacketSink implements PacketSink {
    private static final Logger LOG = LogManager.getLogger(MySQLPacketSink.class);

    private final QueryRunner queryRunner;
    private final Connection conn;
    private final int writeBatchSize;
    private final String insertSql;

    private final List<Object[]> writeBuffer = new ArrayList<>();

    private final MySQLLineConverter mySQLLineConverter;

    // insert/replace statement sql执行结果处理器
    private static final ResultSetHandler<Map<Integer, String>> defaultResultSetHandler = new ResultSetHandler<Map<Integer, String>>(){
        @Override
        public Map<Integer, String> handle(ResultSet rs) throws SQLException {
            RowSetFactory factory;
            // 离线结果集(避免结果集检索过程中连接断开后数据中断)
            CachedRowSet cachedRowSet = null;
            try{
                factory = RowSetProvider.newFactory();
                if(factory != null){
                    cachedRowSet = factory.createCachedRowSet();
                    cachedRowSet.populate(rs);
                }
            }catch (SQLException se){
                // ignore
            }

            Map<Integer, String> retCodeMap = new HashMap<>();
            if(cachedRowSet != null){
                // 离线ResultSet转TableListing
                int rowNumber = 1;
                while(cachedRowSet.next()){
                    String retCode = cachedRowSet.getString(1);
                    // 行号，Statement执行结果返回码
                    retCodeMap.put(rowNumber++,retCode);
                }
                return retCodeMap;
            }else{
                // 在线ResultSet转TableListing
                int rowNumber = 1;
                while(rs.next()){
                    String retCode = rs.getString(1);
                    // 行号，Statement执行结果返回码
                    retCodeMap.put(rowNumber++,retCode);
                }
                return retCodeMap;
            }
        }
    };

    public MySQLPacketSink(AppConfiguration appConf,
                           QueryRunner queryRunner,
                           Connection conn,
                           String insertSql,
                           MySQLLineConverter mySQLLineConverter){
        this.queryRunner = queryRunner;
        this.conn = conn;
        this.insertSql = insertSql;
        this.writeBatchSize = appConf.getInteger("client.packet.write.batch.size",100);
        this.mySQLLineConverter = mySQLLineConverter;
    }

    public interface MySQLLineConverter{
        Object[] toArray(TcpPacketRecord record);
    }

    public static class DefaultMySQLLineConverter implements MySQLLineConverter{
        @Override
        public Object[] toArray(TcpPacketRecord record) {
            Object[] lineParams = new Object[9];

            String messageID = record.getMessageID();
            lineParams[0] = messageID;

            String messageCaptureTime = Utils.timestampToDateTime(record.getMessageCaptureTs());
            lineParams[1] = messageCaptureTime;

            String ipSrcAddress = record.getIpSrcAddress();
            lineParams[2] = ipSrcAddress;

            String ipDstAddress = record.getIpDstAddress();
            lineParams[3] = ipDstAddress;

            int tcpSrcPort = record.getTcpSrcPort();
            lineParams[4] = tcpSrcPort;

            int tcpDstPort = record.getTcpDstPort();
            lineParams[5] = tcpDstPort;

            long tcpSeqNumber = record.getTcpSeqNumber();
            lineParams[6] = tcpSeqNumber;

            int ipTotalLen = record.getIpTotalLen();
            lineParams[7] = ipTotalLen;

            int tcpWindowSize = record.getTcpWindow();
            lineParams[8] = tcpWindowSize;

            return lineParams;
        }
    }

    @Override
    public SinkType sinkType() {
        return SinkType.MYSQL_SINK;
    }

    @Override
    public void writeTo(TcpPacketRecord record, Callback callback) throws Exception {
        Object[] lineFields = mySQLLineConverter.toArray(record);
        writeBuffer.add(lineFields);
        if(writeBuffer.size() >= writeBatchSize){
            Object[][] params = new Object[writeBuffer.size()][];
            for(int i = 0; i < writeBuffer.size(); i++){
                params[i] = writeBuffer.get(i);
            }
            System.out.println("write buffer: " + writeBuffer.size() + " to mysql.");

            // do insert.
            queryRunner.insertBatch(conn,insertSql,defaultResultSetHandler,params);
            // reset buffer.
            writeBuffer.clear();
        }
    }

    @Override
    public void gatherAndWriteTo(TcpPacketRecord record) throws Exception {

    }

    @Override
    public void open() {

    }

    @Override
    public void close() throws IOException {
        Utils.closeResources(conn,null);
    }
}
