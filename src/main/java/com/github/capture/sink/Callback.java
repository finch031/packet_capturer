package com.github.capture.sink;

import java.util.concurrent.Future;

/**
 * @author yusheng
 * @version 0.0.1.20220614_alpha
 * @project packet_capturer
 * @datetime 2022/6/14 16:32
 * @description
 */
@FunctionalInterface
public interface Callback {
    void onCompletion(Future<SinkPacketMetadata> future);
}
