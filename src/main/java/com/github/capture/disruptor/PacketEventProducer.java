package com.github.capture.disruptor;

import com.github.capture.model.CapturePacket;
import com.github.capture.source.PacketSource;
import com.google.common.util.concurrent.RateLimiter;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/22 10:25
 * @description
 */
public class PacketEventProducer implements Runnable{
    private static final Logger LOG = LogManager.getLogger(PacketEventProducer.class);

    private transient boolean isRunning;
    private final PacketSource<?> packetSource;
    private final RingBuffer<CapturePacket> ringBuffer;
    // 瞬时速度
    private float metricSpeed = 0f;
    // 生产者限速器
    @SuppressWarnings("UnstableApiUsage")
    private final RateLimiter rateLimiter;

    public PacketEventProducer(RingBuffer<CapturePacket> ringBuffer,PacketSource<?> packetSource,@SuppressWarnings("UnstableApiUsage") RateLimiter rateLimiter){
        this.ringBuffer = ringBuffer;
        this.packetSource = packetSource;
        this.rateLimiter = rateLimiter;
        this.isRunning = true;
    }

    public void stop(){
        this.isRunning = false;
    }

    public float getMetricSpeed() {
        return metricSpeed;
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void run() {
        long metricStartNano = 0;
        while(isRunning){
            metricStartNano = System.nanoTime();

            /*
            * acquires the given number of permits from this {@code RateLimiter},
            * blocking until the request can be granted. Tells the amount of time
            * slept, if any.
            * */
            double secondsSleepingToEnforceRate = rateLimiter.acquire(1);
            // System.out.println(Thread.currentThread().getName() + ",time spent sleeping to enforce rate=" + secondsSleepingToEnforceRate);

            CapturePacket capturePacket = packetSource.nextPacket();
            if(capturePacket != null){
                long sequence = ringBuffer.next();
                CapturePacket publishCapturePacket = ringBuffer.get(sequence);
                publishCapturePacket.setCaptureTimeStamp(capturePacket.captureTimeStamp());
                publishCapturePacket.setData(capturePacket.data());
                ringBuffer.publish(sequence);
                metricSpeed = 1_000_000_000f / (System.nanoTime() - metricStartNano);
            }
        }
    }
}
