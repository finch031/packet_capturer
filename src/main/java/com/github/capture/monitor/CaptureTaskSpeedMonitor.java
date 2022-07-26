package com.github.capture.monitor;

import com.github.capture.PacketCapture;
import com.github.capture.conf.AppConfiguration;
import com.github.capture.model.CaptureSpeedMetric;
import com.github.capture.model.SinkSpeedMetric;
import com.github.capture.task.PacketParallelTask;
import com.github.capture.utils.AppJvmObjectSizeCalculator;
import com.github.capture.utils.Utils;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 15:47
 * @description
 *      观察者
 */
public class CaptureTaskSpeedMonitor implements Runnable, Observer {
    private final AppConfiguration appConf;
    private final PacketCapture packetCapture;
    private final AppJvmObjectSizeCalculator appJvmObjectSizeCalculator;
    private final List<PacketParallelTask> taskList;

    private CaptureSpeedMetric captureSpeedMetric = new CaptureSpeedMetric();
    private static final Map<Long,SinkSpeedMetric> sinkSpeedMetricMap = new HashMap<>();

    public CaptureTaskSpeedMonitor(PacketCapture packetCapture,
                                   AppConfiguration appConf,
                                   AppJvmObjectSizeCalculator appJvmObjectSizeCalculator,
                                   List<PacketParallelTask> taskList){
        this.packetCapture = packetCapture;
        this.appConf = appConf;
        this.appJvmObjectSizeCalculator = appJvmObjectSizeCalculator;
        this.taskList = taskList;
    }

    /**
     * 观察者回调方法
     * */
    @Override
    public void update(Observable o, Object arg) {
        if(arg instanceof CaptureSpeedMetric){
            this.captureSpeedMetric = (CaptureSpeedMetric)arg;
        }else if(arg instanceof SinkSpeedMetric){
            SinkSpeedMetric sinkSpeedMetric = (SinkSpeedMetric) arg;
            sinkSpeedMetricMap.put(sinkSpeedMetric.getTid(),sinkSpeedMetric);
        }
    }

    @Override
    public void run() {
        float maxCaptureSpeed = appConf.getFloat("client.capture.max.speed",1000f);

        while(true){
            System.out.println("当前时间:" + Utils.timestampToDateTime(captureSpeedMetric.getTs()) + ",当前抓包速度(包/秒):" + captureSpeedMetric.getSpeed());
            sinkSpeedMetricMap.forEach((tid,sinkSpeedMetric) -> {
                System.out.println("当前时间:" + Utils.timestampToDateTime(sinkSpeedMetric.getTs()) + ",当前处理包速度(包/秒):" + sinkSpeedMetric.getSpeed() + ",线程ID:" + sinkSpeedMetric.getTid());

            });

            if(captureSpeedMetric.getSpeed() > maxCaptureSpeed){
                System.out.println("抓包速度过快,准备限速...");
                packetCapture.pauseCapture();
                Utils.sleepQuietly(50, TimeUnit.MILLISECONDS);
                packetCapture.startCapture();
            }

            appJvmObjectSizeCalculator.reset();
            for (PacketParallelTask task : taskList) {
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
