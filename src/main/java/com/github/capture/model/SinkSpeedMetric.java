package com.github.capture.model;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/25 22:01
 * @description
 */
public class SinkSpeedMetric extends SpeedMetric{
    private long tid;

    public SinkSpeedMetric(){
        super(System.currentTimeMillis(),0f);
        this.tid = 0;
    }

    public SinkSpeedMetric(long ts, float speed){
        super(ts,speed);
        this.tid = 0;
    }

    @Override
    public String metricName() {
        return "sink_speed_metric";
    }

    public void setTid(){
        this.tid = Thread.currentThread().getId();
    }

    public long getTid() {
        return tid;
    }

    @Override
    public void update(float speed) {
        // 更新当前metric数据
        setTs(System.currentTimeMillis());
        setSpeed(speed);
        if(tid == 0){
            setTid();
        }

        // 标记被观察者对象已改变
        setChanged();
        // 发布被观察者实例本身并通知所有观察者
        notifyObservers(this);
    }
}
