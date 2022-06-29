package com.github.capture;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.db.QueryRunner;
import com.github.capture.db.StatementConfiguration;
import com.github.capture.model.TcpPacketRecord;
import com.github.capture.sink.ch.ClickHouseSink;
import com.github.capture.sink.file.CsvPacketSink;
import com.github.capture.sink.jdbc.MySQLPacketSink;
import com.github.capture.sink.kafka.KafkaSink;
import com.github.capture.task.PacketParallelTask;
import com.github.capture.utils.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import java.sql.Connection;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author yusheng
 * @version 0.0.1.20220614_alpha
 * @project packet_capturer
 * @datetime 2022/6/14 17:43
 * @description
 */
public class Main {

    public static void main(String[] args){
        AppConfiguration appConf = AppConfiguration.loadFromPropertiesResource("client.properties");

        int intervalSeconds = appConf.getInteger("client.app.monitor.interval.second",15);
        OsResourceMonitorTask osResourceMonitorTask = new OsResourceMonitorTask(intervalSeconds);
        Thread osResourceMonitorThread = new Thread(osResourceMonitorTask);
        osResourceMonitorThread.setName("os_resource_monitor_thread");
        // 守护线程,main退出时,自动结束生命周期
        osResourceMonitorThread.setDaemon(true);
        // 启动系统资源监控线程任务
        osResourceMonitorThread.start();

        AppJvmObjectSizeCalculator appJvmObjectSizeCalculator = new AppJvmObjectSizeCalculator();

        ConcurrentLinkedQueue<byte[]> packetBuffer = new ConcurrentLinkedQueue<>();

        PacketCapture packetCapture = new PacketCapture.Builder()
                .setAppConf(appConf)
                .setCmdArgs(args)
                .setCapturePauseSleepMillis(50)
                .setBufferPackets(packetBuffer)
                .build();
        packetCapture.setDaemon(true);
        packetCapture.start();

        ExecutorService executorService = Executors.newFixedThreadPool(4);

        CsvPacketSink.CsvLineConverter csvLineConverter = new CsvPacketSink.CsvLineConverter() {
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

        Snowflake snowflake = new Snowflake(6,10);

        String mysqlDriver = appConf.getString("client.mysql.driver","");
        String mysqlUrl = appConf.getString("client.mysql.url","");
        String mysqlUser = appConf.getString("client.mysql.user","");
        String mysqlPassword = appConf.getString("client.mysql.password","");

        Connection writeConn = Utils.getConnection(mysqlDriver,mysqlUrl,mysqlUser,mysqlPassword);

        // Statement对象参数配置
        StatementConfiguration.Builder builder = new StatementConfiguration.Builder();
        // query timeout seconds
        builder.queryTimeout(180);

        // 创建执行器
        QueryRunner queryRunner = new QueryRunner(builder.build());

        String serverMySQLDBName = "bdp";
        String writeTable = "tcp_packet_record";

        String insertSql = "REPLACE INTO " + serverMySQLDBName + "." + writeTable + " VALUES(?,?,?,?,?,?,?,?,?) ";

        // MySQLPacketSink mySQLPacketSink = new MySQLPacketSink(appConf,queryRunner,writeConn,insertSql,new MySQLPacketSink.DefaultMySQLLineConverter());

        /*
        PacketParallelTask task1 = new PacketParallelTask(
                packetBuffer,
                new MySQLPacketSink(appConf,queryRunner,writeConn,insertSql,new MySQLPacketSink.DefaultMySQLLineConverter()));
        task1.setIdGenerator(snowflake);

        PacketParallelTask task2 = new PacketParallelTask(
                packetBuffer,
                new MySQLPacketSink(appConf,queryRunner,writeConn,insertSql,new MySQLPacketSink.DefaultMySQLLineConverter())
                );
        task2.setIdGenerator(snowflake);

        PacketParallelTask task3 = new PacketParallelTask(
                packetBuffer,
                new MySQLPacketSink(appConf,queryRunner,writeConn,insertSql,new MySQLPacketSink.DefaultMySQLLineConverter()));
        task3.setIdGenerator(snowflake);

        PacketParallelTask task4 = new PacketParallelTask(
                packetBuffer,
                new MySQLPacketSink(appConf,queryRunner,writeConn,insertSql,new MySQLPacketSink.DefaultMySQLLineConverter()));
        task4.setIdGenerator(snowflake);
        */

        // PacketParallelTask task1 = new PacketParallelTask(packetBuffer,new CsvPacketSink(appConf,csvLineConverter));
        // PacketParallelTask task2 = new PacketParallelTask(packetBuffer,new CsvPacketSink(appConf,csvLineConverter));
        // PacketParallelTask task3 = new PacketParallelTask(packetBuffer,new CsvPacketSink(appConf,csvLineConverter));
        // PacketParallelTask task4 = new PacketParallelTask(packetBuffer,new CsvPacketSink(appConf,csvLineConverter));

        Properties kafkaProducerProps =  new Properties();
        kafkaProducerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "node4:9092");
        kafkaProducerProps.put(ProducerConfig.CLIENT_ID_CONFIG, "PcapCaptureProducer01");
        kafkaProducerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProducerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // 幂等和事务共同保证kafka-0.11.0.0的精确一致性。

        // 开启幂等性(一条消息被反复消费多次并不会对计算结果产生影响)保障
        // kafkaProducerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,"true");
        // must set acks to all in order to use the idempotent producer.otherwise we cannot guarantee idempotence.
        // kafkaProducerProps.put(ProducerConfig.ACKS_CONFIG,"all");

        /*
         * 开启事务语义保障
         * The TransactionalId to use for transactional delivery. This enables reliability semantics
         * which span multiple producer sessions since it allows the client to guarantee that
         * transactions using the same TransactionalId have been completed prior to starting
         * any new transactions. If no TransactionalId is provided, then the producer is limited
         * to idempotent delivery. Note that enable.idempotence must be enabled if a TransactionalId
         * is configured. The default is null, which means transactions cannot be used. Note that,
         * by default, transactions require a cluster of at least three brokers which is the recommended
         * setting for production.
         * */
        // kafkaProducerProps.setProperty(ProducerConfig.TRANSACTIONAL_ID_CONFIG,"producer_transaction_id");

        /*
         * The maximum amount of time in ms that the transaction coordinator will wait for a
         * transaction status update from the producer before proactively aborting the ongoing
         * transaction.If this value is larger than the transaction.max.timeout.ms setting in
         * the broker, the request will fail with a InvalidTransactionTimeout error.
         * */
        // kafkaProducerProps.setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG,"60000");

        String topic = "packet_capture_topic_01";
        PacketParallelTask task1 = new PacketParallelTask(packetBuffer,new KafkaSink(kafkaProducerProps,topic));
        PacketParallelTask task2 = new PacketParallelTask(packetBuffer,new KafkaSink(kafkaProducerProps,topic));
        PacketParallelTask task3 = new PacketParallelTask(packetBuffer,new KafkaSink(kafkaProducerProps,topic));
        PacketParallelTask task4 = new PacketParallelTask(packetBuffer,new KafkaSink(kafkaProducerProps,topic));

        /*
        String chServerHost = "node2";
        int chServerPort = 8123;
        String chDB = "bdp";
        String chUser = "default";
        String chPassword = "xxxxxx";

        PacketParallelTask task1 = new PacketParallelTask(packetBuffer,new ClickHouseSink(chServerHost,chServerPort,chDB,chUser,chPassword));
        PacketParallelTask task2 = new PacketParallelTask(packetBuffer,new ClickHouseSink(chServerHost,chServerPort,chDB,chUser,chPassword));
        PacketParallelTask task3 = new PacketParallelTask(packetBuffer,new ClickHouseSink(chServerHost,chServerPort,chDB,chUser,chPassword));
        PacketParallelTask task4 = new PacketParallelTask(packetBuffer,new ClickHouseSink(chServerHost,chServerPort,chDB,chUser,chPassword));
        */

        task1.setIdGenerator(new Snowflake(6,10));
        task2.setIdGenerator(new Snowflake(6,10));
        task3.setIdGenerator(new Snowflake(6,10));
        task4.setIdGenerator(new Snowflake(6,10));

        executorService.submit(task1);
        executorService.submit(task2);
        executorService.submit(task3);
        executorService.submit(task4);

        Runnable monitorTask = new Runnable() {
            @Override
            public void run() {

                while(true){
                    Tuple<Long,Float> parseMetric = packetCapture.captureMetric();
                    System.out.println("当前时间:" + Utils.timestampToDateTime(parseMetric.v1()) + ",当前抓包速度(包/秒):" + parseMetric.v2());

                    if(parseMetric.v2() > 10000f){
                        System.out.println("抓包速度过快,准备限速...");
                        packetCapture.pauseCapture();
                        Utils.sleepQuietly(50,TimeUnit.MILLISECONDS);
                        packetCapture.startCapture();
                    }

                    Triple<Long,Float,Long> processMetric = task1.processMetric();
                    System.out.println("当前时间:" + Utils.timestampToDateTime(processMetric.getFirst()) + ",当前处理包速度(包/秒):" + processMetric.getSecond() + ",线程ID:" + processMetric.getThird());

                    processMetric = task2.processMetric();
                    System.out.println("当前时间:" + Utils.timestampToDateTime(processMetric.getFirst()) + ",当前处理包速度(包/秒):" + processMetric.getSecond() + ",线程ID:" + processMetric.getThird());

                    processMetric = task3.processMetric();
                    System.out.println("当前时间:" + Utils.timestampToDateTime(processMetric.getFirst()) + ",当前处理包速度(包/秒):" + processMetric.getSecond() + ",线程ID:" + processMetric.getThird());

                    processMetric = task4.processMetric();
                    System.out.println("当前时间:" + Utils.timestampToDateTime(processMetric.getFirst()) + ",当前处理包速度(包/秒):" + processMetric.getSecond() + ",线程ID:" + processMetric.getThird());

                    appJvmObjectSizeCalculator.calculate(packetBuffer);
                    appJvmObjectSizeCalculator.calculate(task1);
                    appJvmObjectSizeCalculator.calculate(task2);
                    appJvmObjectSizeCalculator.calculate(task3);
                    appJvmObjectSizeCalculator.calculate(task4);

                    System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");

                    Utils.sleepQuietly(5000,TimeUnit.MILLISECONDS);

                    if(Thread.currentThread().isInterrupted()){
                        System.out.println("monitor thread is interrupted,exit loop now...");
                        break;
                    }
                }

            }
        };

        Thread monitorThread = new Thread(monitorTask);
        monitorThread.setDaemon(true);
        monitorThread.start();

        try{
            boolean terminated = executorService.awaitTermination(180L, TimeUnit.MINUTES);
            System.out.println("线程池停止成功=" + terminated);
            if(!terminated){
                // executorService.shutdown();
                task1.stop();
                task2.stop();
                task3.stop();
                task4.stop();

                osResourceMonitorTask.stop();
                packetCapture.stopRunning();
                monitorThread.interrupt();
            }

        }catch (InterruptedException ie){
            ie.printStackTrace();
        }

        System.out.println("main exit.");
    }
}