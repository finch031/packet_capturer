package com.github.capture.model;

import java.util.Observable;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/25 21:10
 * @description
 *      被观察者对象
 */
public abstract class SpeedMetric extends Observable {
    private long ts;
    private float speed;

    public SpeedMetric(){}

    public SpeedMetric(long ts, float speed){
        this.ts = ts;
        this.speed = speed;
    }

    public abstract String metricName();

    /**
     * update方法中实现状态变更及通知
     * */
    public abstract void update(float speed);

    public void setTs(long ts) {
        this.ts = ts;
    }

    public long getTs() {
        return ts;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getSpeed() {
        return speed;
    }
}
