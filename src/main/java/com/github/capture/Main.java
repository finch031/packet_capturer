package com.github.capture;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.db.QueryRunner;
import com.github.capture.db.StatementConfiguration;
import com.github.capture.model.TcpPacketRecord;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.ch.ClickHouseSink;
import com.github.capture.sink.console.ConsolePacketSink;
import com.github.capture.sink.es.Es7xRestClient;
import com.github.capture.sink.es.Es7xRestHighLevelClient;
import com.github.capture.sink.es.EsSink;
import com.github.capture.sink.file.CsvPacketSink;
import com.github.capture.sink.jdbc.MySQLPacketSink;
import com.github.capture.sink.kafka.KafkaSink;
import com.github.capture.task.PacketParallelTask;
import com.github.capture.utils.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.Settings;

import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
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
    private static final Logger LOG = LogManager.getLogger(Main.class);

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

        String sinkTypeStr = appConf.getString("client.sink.type","console");
        PacketSink.SinkType sinkType = PacketSink.SinkType.valueOf(sinkTypeStr);

        ConcurrentLinkedQueue<byte[]> packetBuffer = new ConcurrentLinkedQueue<>();

        PacketCapture packetCapture = new PacketCapture.Builder()
                .setAppConf(appConf)
                .setCmdArgs(args)
                .setCapturePauseSleepMillis(50)
                .setBufferPackets(packetBuffer)
                .build();
        packetCapture.setDaemon(true);
        packetCapture.start();

        int taskParallelNumber = appConf.getInteger("client.task.parallel.number",4);

        List<PacketParallelTask> taskList = new ArrayList<>();

        switch (sinkType){
            case CSV_SINK:
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

                for(int i = 0; i < taskParallelNumber; i++){
                    PacketParallelTask task = new PacketParallelTask(packetBuffer,new CsvPacketSink(appConf,csvLineConverter));
                    taskList.add(task);
                }

                break;
            case MYSQL_SINK:
                String mysqlDriver = appConf.getString("client.mysql.driver","");
                String mysqlUrl = appConf.getString("client.mysql.url","");
                String mysqlUser = appConf.getString("client.mysql.user","");
                String mysqlPassword = appConf.getString("client.mysql.password","");
                String serverMySQLDBName = appConf.getString("client.mysql.db","");
                String writeTable = appConf.getString("client.mysql.write.table","");

                Connection writeConn = Utils.getConnection(mysqlDriver,mysqlUrl,mysqlUser,mysqlPassword);

                // Statement对象参数配置
                StatementConfiguration.Builder builder = new StatementConfiguration.Builder();
                // query timeout seconds
                builder.queryTimeout(180);

                // 创建执行器
                QueryRunner queryRunner = new QueryRunner(builder.build());

                // 写入SQL语句
                String insertSql = "REPLACE INTO " + serverMySQLDBName + "." + writeTable + " VALUES(?,?,?,?,?,?,?,?,?) ";

                for(int i = 0; i < taskParallelNumber; i++){
                    PacketParallelTask task = new PacketParallelTask(
                            packetBuffer,
                            new MySQLPacketSink(appConf,queryRunner,writeConn,insertSql,new MySQLPacketSink.DefaultMySQLLineConverter()));
                    taskList.add(task);
                }
                break;
            case KAFKA_SINK:
                String bootstrapServers = appConf.getString("client.kafka.bootstrap.servers","");
                Properties kafkaProducerProps =  new Properties();
                kafkaProducerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                kafkaProducerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                kafkaProducerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                String topic = appConf.getString("client.kafka.topic","");

                for(int i = 0; i < taskParallelNumber; i++){
                    PacketParallelTask task = new PacketParallelTask(packetBuffer,new KafkaSink(kafkaProducerProps,topic));
                    taskList.add(task);
                }

                break;
            case CLICKHOUSE_SINK:
                String chServerHost = appConf.getString("client.ch.server.hosts","");
                int chServerPort = appConf.getInteger("client.ch.server.port",8123);
                String chDB = appConf.getString("client.ch.server.db","");
                String chUser = appConf.getString("client.ch.server.user","");
                String chPassword = appConf.getString("client.ch.server.password","");

                for(int i = 0; i < taskParallelNumber; i++){
                    PacketParallelTask task = new PacketParallelTask(packetBuffer,new ClickHouseSink(chServerHost,chServerPort,chDB,chUser,chPassword));
                    taskList.add(task);
                }

                break;
            case ES:
                Es7xRestClient es7xRestClient = new Es7xRestClient();
                String cluster = appConf.getString("client.es.cluster","");
                String schema = "http";
                String user = appConf.getString("client.es.user","");
                String password = appConf.getString("client.es.password","");
                String index = appConf.getString("client.es.write.index","");

                try{
                    es7xRestClient.initClient(cluster,schema,user,password,null);
                    es7xRestClient.printEsClusterNodes();

                    Settings indexSettings = Settings.builder()
                            .put("number_of_replicas","1")
                            .put("index.number_of_shards","5")
                            .build();
                    // es7xRestClient.createIndex(index,indexSettings,"","");

                }catch (IOException ioe){
                    ioe.printStackTrace();
                }

                Es7xRestHighLevelClient highLevelClient = new Es7xRestHighLevelClient(es7xRestClient.getRestClient());

                for(int i = 0; i < taskParallelNumber; i++){
                    PacketParallelTask task = new PacketParallelTask(packetBuffer,new EsSink(highLevelClient,index));
                    taskList.add(task);
                }

                break;
            case CONSOLE_SINK:
                for(int i = 0; i < taskParallelNumber; i++){
                    PacketParallelTask task = new PacketParallelTask(packetBuffer,new ConsolePacketSink());
                    taskList.add(task);
                }

                break;
            default:
                LOG.error("unsupported sink type {}", sinkType);
                System.exit(1);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(taskParallelNumber);

        for (PacketParallelTask task : taskList) {
            task.setIdGenerator(new Snowflake(6,10));
            executorService.submit(task);
        }

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

                    appJvmObjectSizeCalculator.reset();
                    Triple<Long,Float,Long> processMetric;
                    for (PacketParallelTask task : taskList) {
                        processMetric = task.processMetric();
                        System.out.println("当前时间:" + Utils.timestampToDateTime(processMetric.getFirst()) + ",当前处理包速度(包/秒):" + processMetric.getSecond() + ",线程ID:" + processMetric.getThird());

                        appJvmObjectSizeCalculator.calculate(task);
                    }

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

        long captureHours = appConf.getLong("client.capture.minutes",5);

        try{
            boolean terminated = executorService.awaitTermination(captureHours == -1L ? 7 * 24 * 60L : captureHours, TimeUnit.MINUTES);
            LOG.info("任务结束,关闭资源...");

            // 关闭抓包任务
            packetCapture.stopRunning();

            // 关闭资源监控任务
            osResourceMonitorTask.stop();

            if(!terminated){
                // 关闭sink任务
                taskList.forEach(PacketParallelTask::stop);

                // 关闭线程池
                executorService.shutdown();

                // 关闭任务监控线程
                monitorThread.interrupt();
            }
        }catch (InterruptedException ie){
            ie.printStackTrace();
        }

        LOG.info("main exit.");
    }
}