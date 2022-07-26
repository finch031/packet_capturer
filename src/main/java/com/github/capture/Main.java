package com.github.capture;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.model.CaptureSpeedMetric;
import com.github.capture.model.SinkSpeedMetric;
import com.github.capture.model.SpeedMetric;
import com.github.capture.monitor.CaptureTaskSpeedMonitor;
import com.github.capture.monitor.MemoryMonitor;
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

        SpeedMetric captureMetric = new CaptureSpeedMetric();
        ConcurrentLinkedQueue<ByteBuf> packetBuffer = new ConcurrentLinkedQueue<>();
        // 抓包任务
        PacketCapture packetCapture = new PacketCapture.Builder()
                .setAppConf(appConf)
                .setCmdArgs(args)
                .setCapturePauseSleepMillis(50)
                .setBufferPool(bufAllocator)
                .setBufferPackets(packetBuffer)
                .setCaptureMetric(captureMetric)
                .build();
        packetCapture.setDaemon(true);
        packetCapture.start();

        int taskParallelNumber = appConf.getInteger("client.task.parallel.number",4);

        List<PacketParallelTask> taskList = new ArrayList<>();
        List<SpeedMetric> sinkSpeedMetrics = new ArrayList<>();

        PacketSinkFactory packetSinkFactory = PacketSinkFactoryMaker.makeFactory(sinkType,appConf);
        for(int i = 0; i < taskParallelNumber; i++){
            // 解包任务
            SpeedMetric sinkSpeedMetric = new SinkSpeedMetric();
            PacketParallelTask task = new PacketParallelTask(packetBuffer,packetSinkFactory.getPacketSink(appConf),sinkSpeedMetric);
            task.setIdGenerator(new Snowflake(6,10));
            taskList.add(task);
            sinkSpeedMetrics.add(sinkSpeedMetric);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(taskParallelNumber);
        for (PacketParallelTask task : taskList) {
            executorService.submit(task);
        }

        // 抓包/解包速度监控任务
        CaptureTaskSpeedMonitor captureTaskSpeedMonitor = new CaptureTaskSpeedMonitor(packetCapture,appConf,appJvmObjectSizeCalculator,taskList);
        // 添加观察者
        captureMetric.addObserver(captureTaskSpeedMonitor);
        // 添加观察者
        sinkSpeedMetrics.forEach(metric -> metric.addObserver(captureTaskSpeedMonitor));

        Thread monitorThread = new Thread(captureTaskSpeedMonitor);
        monitorThread.setDaemon(true);
        monitorThread.start();

        MemoryMonitor memoryMonitor = new MemoryMonitor(bufAllocator);
        Thread memoryMonitorThread = new Thread(memoryMonitor);
        memoryMonitorThread.setDaemon(true);
        memoryMonitorThread.start();

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

                // 关闭内存监控线程
                memoryMonitorThread.interrupt();
            }
        }catch (InterruptedException ie){
            ie.printStackTrace();
        }

        LOG.info("main exit.");
    }
}