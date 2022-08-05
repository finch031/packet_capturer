package com.github.capture.sink.ch;

import com.github.capture.db.QueryRunner;
import com.github.capture.db.ResultSetHandler;
import com.github.capture.db.StatementConfiguration;
import com.github.capture.utils.TableListing;
import ru.yandex.clickhouse.BalancedClickhouseDataSource;
import ru.yandex.clickhouse.ClickHouseConnection;
import ru.yandex.clickhouse.ClickHouseDataSource;
import ru.yandex.clickhouse.ClickHouseStatement;
import ru.yandex.clickhouse.domain.ClickHouseCompression;
import ru.yandex.clickhouse.domain.ClickHouseFormat;
import ru.yandex.clickhouse.settings.ClickHouseProperties;
import ru.yandex.clickhouse.util.ClickHouseStreamCallback;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;
import javax.sql.rowset.RowSetProvider;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022-01-07 10:10
 * @description
 *      ClickHouse java client that a wrapper of https://github.com/ClickHouse/clickhouse-jdbc.
 */
public class ClickHouseJDBCClient {

    private static final AtomicInteger counter = new AtomicInteger(0);
    private final ClickHouseProperties clickHouseProperties;
    private final ClickHouseDataSource dataSource;
    private final QueryRunner queryRunner;

    // 结果集解析
    private static final ResultSetHandler<TableListing> DEFAULT_HANDLER = new ResultSetHandler<TableListing>() {
        @Override
        public TableListing handle(ResultSet rs) throws SQLException {
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

            if(cachedRowSet != null){
                // 离线ResultSet转TableListing
                return toTableListing(cachedRowSet);
            }else{
                // 在线ResultSet转TableListing
                return toTableListing(rs);
            }
        }
    };

    public ClickHouseJDBCClient(String chServerHost,
                                int chServerPort,
                                String chDB,
                                String chUser,
                                String chPassword){
        String url = "jdbc:clickhouse://" + chServerHost + ":" + chServerPort + "/" + chDB;
        clickHouseProperties = new ClickHouseProperties();
        clickHouseProperties.setClientName("clickhouse-j-client-" + tid() + "_" + counter.getAndIncrement());
        clickHouseProperties.setSessionId("clickhouse-j-session-" + tid() + "_" + counter.getAndIncrement());
        clickHouseProperties.setCompress(true);
        clickHouseProperties.setHost(chServerHost);
        clickHouseProperties.setPort(chServerPort);
        clickHouseProperties.setDatabase(chDB);
        clickHouseProperties.setUser(chUser);
        clickHouseProperties.setPassword(chPassword);

        clickHouseProperties.setApacheBufferSize(1024);
        clickHouseProperties.setBufferSize(1024);

        /*
         *  reference code:
         *   ru.yandex.clickhouse.settings.ClickHouseConnectionSettings
         *   ru.yandex.clickhouse.settings.ClickHouseQueryParam
         * */
        this.clickHouseProperties.setConnectionTimeout(30 * 1000); // default: 10 * 1000
        this.dataSource = new ClickHouseDataSource(url,clickHouseProperties);

        // 高可用模式下的数据源，此时utl中多个地址使用逗号分隔
        // BalancedClickhouseDataSource balanced = new BalancedClickhouseDataSource("",this.clickHouseProperties);

        // 对每个host进行ping操作,排除不可用的dead连接.
        // balanced.actualize();

        // 周期型ping探活
        // balanced.scheduleActualization(1, TimeUnit.SECONDS);

        // Statement对象参数配置
        StatementConfiguration.Builder builder = new StatementConfiguration.Builder();
        // query timeout seconds
        builder.queryTimeout(180);
        // 创建执行器
        queryRunner = new QueryRunner(builder.build());
    }

    public ClickHouseJDBCClient(ClickHouseProperties clickHouseProperties,String url){
        this.clickHouseProperties = clickHouseProperties;
        this.dataSource = new ClickHouseDataSource(url,clickHouseProperties);

        // Statement对象参数配置
        StatementConfiguration.Builder builder = new StatementConfiguration.Builder();
        // query timeout seconds
        builder.queryTimeout(180);
        // 创建执行器
        queryRunner = new QueryRunner(builder.build());
    }

    public ClickHouseConnection newConnection() throws SQLException{
        // return an auto close http connection.
        return dataSource.getConnection();
    }

    public void debugQuery(Connection connection,String sql) throws SQLException{
        long startTs = System.currentTimeMillis();

        TableListing queryResultListing = queryRunner.query(connection,sql,DEFAULT_HANDLER);

        System.out.println("millis taken:" + (System.currentTimeMillis() - startTs));
        System.out.println(queryResultListing.toString());
    }

    @SuppressWarnings("unchecked")
    public void query(Connection connection,String sql,ResultSetHandler handler) throws SQLException{
        long startTs = System.currentTimeMillis();
        queryRunner.query(connection,sql,handler);
        System.out.println("millis taken:" + (System.currentTimeMillis() - startTs));
    }

    /**
     * @param connection connection.
     * @param sql sql.
     * @param rowAppender rowAppender.
     * @return retCode.
     * */
    public int insert(Connection connection, String sql, RowAppender rowAppender) throws Exception{
        PreparedStatement preparedStatement = connection.prepareStatement(sql,ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_UPDATABLE);
        rowAppender.addRows(preparedStatement);
        return execBatchUpdateSQL(preparedStatement);
    }

    @FunctionalInterface
    public interface RowAppender{
        void addRows(PreparedStatement preparedStatement) throws Exception;
    }

    /**
     * row binary stream write.
     * @param connection connection.
     * @param insertSql insertSql.
     * @param callback callback.
     * */
    public void rowBinaryStreamWrite(ClickHouseConnection connection,
                                     String insertSql,
                                     ClickHouseStreamCallback callback) throws SQLException{
        ClickHouseStatement stmt = connection.createStatement();
        stmt.write().send(insertSql,callback, ClickHouseFormat.RowBinary);
        stmt.close();
    }

    public void csvFileWrite(ClickHouseConnection connection,
                             String table,
                             String csvFilePath,
                             String formatDelimiter,
                             ClickHouseCompression compression
    ) throws SQLException{
        ClickHouseStatement stmt = connection.createStatement();

        stmt.write()
                .table(table)
                .option("format_csv_delimiter",formatDelimiter)
                .data(new File(csvFilePath),ClickHouseFormat.CSV,compression)
                .send();
    }

    public void parquetFileWrite(ClickHouseConnection connection,
                                 String table,
                                 String parquetFilePath,
                                 ClickHouseCompression compression
    ) throws SQLException{
        ClickHouseStatement stmt = connection.createStatement();
        stmt.write()
                .table(table)
                .data(new File(parquetFilePath),ClickHouseFormat.Parquet,compression)
                .send();
    }

    /**
     *  execute update.
     * @param preparedStatement preparedStatement
     * @return retCode 0-成功,1-失败
     * */
    public static int execUpdateSQL(PreparedStatement preparedStatement){
        int retCode = 0;

        try{
            retCode = preparedStatement.executeUpdate();
            preparedStatement.clearParameters();
        }catch (SQLException se){
            retCode = -1;
            String errorMsg = stackTrace(se);
            System.err.println(errorMsg);
        }

        return retCode;
    }

    /**
     *  execute batch update.
     * @param preparedStatement preparedStatement
     * @return retCode 0-成功,1-失败
     * */
    public static int execBatchUpdateSQL(PreparedStatement preparedStatement){
        int retCode = 0;
        int[] tempRetCodeArr = null;

        try{
            tempRetCodeArr = preparedStatement.executeBatch();
            preparedStatement.clearParameters();
        }catch (SQLException se){
            String errorMsg = stackTrace(se);
            System.err.println(errorMsg);
        }

        if(tempRetCodeArr != null){
            // debug something.
            // TODO
            /*
            for (int subCode : tempRetCodeArr) {
                if(subCode != 1){
                    retCode = 2;
                }
            }
            */
        }else{
            retCode = 1;
        }

        return retCode;
    }


    /**
     * ResultSet to TableListing.
     * @param rs ResultSet.
     * */
    private static TableListing toTableListing(final ResultSet rs) throws SQLException {
        final ResultSetMetaData rsmd = rs.getMetaData();
        final int cols = rsmd.getColumnCount();

        TableListing.Builder builder = new TableListing.Builder();

        for (int i = 1; i <= cols; i++) {
            String columnName = rsmd.getColumnLabel(i);
            if (null == columnName || 0 == columnName.length()) {
                columnName = rsmd.getColumnName(i);
            }
            builder.addField(columnName);
        }

        TableListing tableListing = builder.build();

        while(rs.next()){
            String[] values = new String[cols];
            for(int i = 1; i <= cols; i++){
                String columnValue = rs.getString(i);
                values[i-1] = columnValue;
            }
            tableListing.addRow(values);
        }

        return tableListing;
    }

    private static long tid(){
        return Thread.currentThread().getId();
    }

    /**
     * Get the stack trace from an exception as a string
     */
    private static String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}