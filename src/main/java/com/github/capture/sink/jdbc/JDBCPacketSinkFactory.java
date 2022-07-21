package com.github.capture.sink.jdbc;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.db.QueryRunner;
import com.github.capture.db.StatementConfiguration;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;
import com.github.capture.utils.Utils;

import java.sql.Connection;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 14:28
 * @description
 */
public class JDBCPacketSinkFactory implements PacketSinkFactory {
    private final Connection writeConn;
    private final QueryRunner queryRunner;
    private final String insertSql;
    private final MySQLPacketSink.MySQLLineConverter mySQLLineConverter;

    public JDBCPacketSinkFactory(AppConfiguration appConf,MySQLPacketSink.MySQLLineConverter mySQLLineConverter){
        String mysqlDriver = appConf.getString("client.mysql.driver","");
        String mysqlUrl = appConf.getString("client.mysql.url","");
        String mysqlUser = appConf.getString("client.mysql.user","");
        String mysqlPassword = appConf.getString("client.mysql.password","");
        String serverMySQLDBName = appConf.getString("client.mysql.db","");
        String writeTable = appConf.getString("client.mysql.write.table","");

        writeConn = Utils.getConnection(mysqlDriver,mysqlUrl,mysqlUser,mysqlPassword);

        // Statement对象参数配置
        StatementConfiguration.Builder builder = new StatementConfiguration.Builder();
        // query timeout seconds
        builder.queryTimeout(180);

        // 创建执行器
        queryRunner = new QueryRunner(builder.build());

        // 写入SQL语句
        insertSql = "REPLACE INTO " + serverMySQLDBName + "." + writeTable + " VALUES(?,?,?,?,?,?,?,?,?) ";

        this.mySQLLineConverter = mySQLLineConverter;
    }

    public JDBCPacketSinkFactory(AppConfiguration appConf){
        this(appConf,new MySQLPacketSink.DefaultMySQLLineConverter());
    }

    @Override
    public PacketSink getPacketSink(AppConfiguration appConf) {
        return new MySQLPacketSink(appConf,queryRunner,writeConn,insertSql,this.mySQLLineConverter);
    }
}
