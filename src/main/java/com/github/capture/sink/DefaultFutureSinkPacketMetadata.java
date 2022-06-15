package com.github.capture.sink;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author yusheng
 * @version 0.0.1.20220614_alpha
 * @project packet_capturer
 * @datetime 2022/6/14 17:34
 * @description
 */
public class DefaultFutureSinkPacketMetadata implements Future<SinkPacketMetadata> {
    private final SinkPacketMetadata metadata;

    public DefaultFutureSinkPacketMetadata(SinkPacketMetadata metadata){
        this.metadata = metadata;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public SinkPacketMetadata get() throws InterruptedException, ExecutionException {
        return metadata;
    }

    @Override
    public SinkPacketMetadata get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return metadata;
    }
}
