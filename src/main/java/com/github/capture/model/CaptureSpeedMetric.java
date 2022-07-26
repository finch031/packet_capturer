package com.github.capture.model;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/25 21:13
 * @description
 */
public class CaptureSpeedMetric extends SpeedMetric{
    public CaptureSpeedMetric(){
        super(System.currentTimeMillis(),0f);
    }

    public CaptureSpeedMetric(long ts, float speed){
        super(ts,speed);
    }

    @Override
    public String metricName() {
        return "capture_speed_metric";
    }

    @Override
    public void update(float speed){
        // 更新当前metric数据
        setTs(System.currentTimeMillis());
        setSpeed(speed);

        // 标记被观察者对象已改变
        setChanged();
        // 发布被观察者实例本身并通知所有观察者
        notifyObservers(this);
    }
}
