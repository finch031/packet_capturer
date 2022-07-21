package com.github.capture;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;
import com.github.capture.sink.PacketSinkFactoryMaker;
import com.github.capture.task.PacketParallelTask;
import com.github.capture.utils.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.List;
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
        PacketSink.SinkType sinkType = PacketSink.SinkType.valueOf(sinkTypeStr.toUpperCase());

        /*
        * 创建一个全局内存池
        * 内存池大小: 128MB
        * 内存池每个内存块的大小: 1024
        * */
        // BufferPool bufferPool = new BufferPool(128 * 1024 * 1024L, 1024);

        PooledByteBufAllocator bufAllocator = PooledByteBufAllocator.DEFAULT;

        ConcurrentLinkedQueue<ByteBuf> packetBuffer = new ConcurrentLinkedQueue<>();

        PacketCapture packetCapture = new PacketCapture.Builder()
                .setAppConf(appConf)
                .setCmdArgs(args)
                .setCapturePauseSleepMillis(50)
                .setBufferPool(bufAllocator)
                .setBufferPackets(packetBuffer)
                .build();
        packetCapture.setDaemon(true);
        packetCapture.start();

        int taskParallelNumber = appConf.getInteger("client.task.parallel.number",4);

        List<PacketParallelTask> taskList = new ArrayList<>();

        PacketSinkFactory packetSinkFactory = PacketSinkFactoryMaker.makeFactory(sinkType,appConf);

        for(int i = 0; i < taskParallelNumber; i++){
            PacketParallelTask task = new PacketParallelTask(packetBuffer,packetSinkFactory.getPacketSink(appConf));
            task.setIdGenerator(new Snowflake(6,10));
            taskList.add(task);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(taskParallelNumber);

        for (PacketParallelTask task : taskList) {
            executorService.submit(task);
        }

        float maxCaptureSpeed = appConf.getFloat("client.capture.max.speed",1000f);
        Runnable monitorTask = new Runnable() {
            @Override
            public void run() {
                Tuple<Long,Float> parseMetric;
                Triple<Long,Float,Long> processMetric;

                while(true){
                    parseMetric = packetCapture.captureMetric();
                    System.out.println("当前时间:" + Utils.timestampToDateTime(parseMetric.v1()) + ",当前抓包速度(包/秒):" + parseMetric.v2());

                    if(parseMetric.v2() > maxCaptureSpeed){
                        System.out.println("抓包速度过快,准备限速...");
                        packetCapture.pauseCapture();
                        Utils.sleepQuietly(50,TimeUnit.MILLISECONDS);
                        packetCapture.startCapture();
                    }

                    appJvmObjectSizeCalculator.reset();
                    for (PacketParallelTask task : taskList) {
                        processMetric = task.processMetric();
                        System.out.println("当前时间:" + Utils.timestampToDateTime(processMetric.getFirst()) + ",当前处理包速度(包/秒):" + processMetric.getSecond() + ",线程ID:" + processMetric.getThird());
                        appJvmObjectSizeCalculator.calculate(task);
                    }

                    System.out.println(bufAllocator.dumpStats());

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

        long captureMinutes = appConf.getLong("client.capture.minutes",5);

        try{
            boolean terminated = executorService.awaitTermination(captureMinutes == -1L ? 7 * 24 * 60L : captureMinutes, TimeUnit.MINUTES);
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