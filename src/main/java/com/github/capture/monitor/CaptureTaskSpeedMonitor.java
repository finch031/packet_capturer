package com.github.capture.monitor;

import com.github.capture.PacketCapture;
import com.github.capture.conf.AppConfiguration;
import com.github.capture.task.PacketParallelTask;
import com.github.capture.utils.AppJvmObjectSizeCalculator;
import com.github.capture.utils.Triple;
import com.github.capture.utils.Tuple;
import com.github.capture.utils.Utils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 15:47
 * @description
 */
public class CaptureTaskSpeedMonitor implements Runnable{
    private final AppConfiguration appConf;
    private final PacketCapture packetCapture;
    private final AppJvmObjectSizeCalculator appJvmObjectSizeCalculator;
    private final List<PacketParallelTask> taskList;

    public CaptureTaskSpeedMonitor(PacketCapture packetCapture,
                                   AppConfiguration appConf,
                                   AppJvmObjectSizeCalculator appJvmObjectSizeCalculator,
                                   List<PacketParallelTask> taskList){
        this.packetCapture = packetCapture;
        this.appConf = appConf;
        this.appJvmObjectSizeCalculator = appJvmObjectSizeCalculator;
        this.taskList = taskList;
    }

    @Override
    public void run() {
        float maxCaptureSpeed = appConf.getFloat("client.capture.max.speed",1000f);
        Tuple<Long,Float> parseMetric;
        Triple<Long,Float,Long> processMetric;

        while(true){
            parseMetric = packetCapture.captureMetric();
            System.out.println("当前时间:" + Utils.timestampToDateTime(parseMetric.v1()) + ",当前抓包速度(包/秒):" + parseMetric.v2());

            if(parseMetric.v2() > maxCaptureSpeed){
                System.out.println("抓包速度过快,准备限速...");
                packetCapture.pauseCapture();
                Utils.sleepQuietly(50, TimeUnit.MILLISECONDS);
                packetCapture.startCapture();
            }

            appJvmObjectSizeCalculator.reset();
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
}
