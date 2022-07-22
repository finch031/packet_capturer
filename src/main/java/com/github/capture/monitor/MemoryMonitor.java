package com.github.capture.monitor;

import com.github.capture.utils.Utils;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocatorMetric;

import java.util.concurrent.TimeUnit;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 16:26
 * @description
 */
public class MemoryMonitor implements Runnable{

    private final PooledByteBufAllocator allocator;

    public MemoryMonitor(PooledByteBufAllocator allocator){
        this.allocator = allocator;
    }

    @Override
    public void run() {

        while(true){
            PooledByteBufAllocatorMetric metric = allocator.metric();
            System.out.println("used heap memory: " + metric.usedHeapMemory());
            System.out.println("used direct memory: " + metric.usedDirectMemory());
            System.out.println("chunk size: " + metric.chunkSize());

            metric.heapArenas().forEach(x -> {
                System.out.println(x);
            });

            metric.directArenas().forEach(x -> {
                System.out.println(x);
            });

            Utils.sleepQuietly(5000, TimeUnit.MILLISECONDS);

            if(Thread.currentThread().isInterrupted()){
                System.out.println("monitor thread is interrupted,exit loop now...");
                break;
            }
        }



    }


}
