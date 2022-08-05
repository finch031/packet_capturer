package com.github.capture.disruptor;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.model.CapturePacket;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;
import com.github.capture.sink.PacketSinkFactoryMaker;
import com.github.capture.source.EmptyPacketSource;
import com.github.capture.source.PacketSource;
import com.github.capture.source.Pcap4jPacketSource;
import com.github.capture.utils.Utils;
import com.google.common.util.concurrent.RateLimiter;
import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/22 10:13
 * @description
 */
public class DisruptorMain {
    private static final Logger LOG = LogManager.getLogger(DisruptorMain.class);

    public static void main(String[] args){
        AppConfiguration appConf = AppConfiguration.loadFromPropertiesResource("client.properties");

        String sinkTypeStr = appConf.getString("client.sink.type","console");
        PacketSink.SinkType sinkType = PacketSink.SinkType.valueOf(sinkTypeStr.toUpperCase());

        String captureSourceType = appConf.getString("client.capture.source.type","pcap4j");
        PacketEventFactory factory = new PacketEventFactory(captureSourceType);
        int ringBufferSize = appConf.getInteger("client.disruptor.ring.buffer.size",1024);

        WaitStrategy waitStrategy = new BlockingWaitStrategy();
        Disruptor<CapturePacket> disruptor = new Disruptor<>(
                factory,
                ringBufferSize,                 // 指定RingBuffer的大小
                Utils.disruptorNamedDaemonThreadFactory(),
                ProducerType.SINGLE,            // 单个生产者
                waitStrategy);                  // 消费者等待策略

        PacketSinkFactory packetSinkFactory = PacketSinkFactoryMaker.makeFactory(sinkType,appConf);
        int taskNumber = appConf.getInteger("client.task.parallel.number",4);
        PacketEventHandler[] handlers = new PacketEventHandler[taskNumber];
        for(int i = 0; i < taskNumber; i++){
            handlers[i] = new PacketEventHandler(appConf,packetSinkFactory,"packet_event_handler_" + i);
        }
        disruptor.handleEventsWith(handlers);
        disruptor.setDefaultExceptionHandler(new ExceptionHandler<CapturePacket>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, CapturePacket event) {
                LOG.error("event:{} exception",event,ex);
            }

            @Override
            public void handleOnStartException(Throwable ex) {
                LOG.error(ex);
            }

            @Override
            public void handleOnShutdownException(Throwable ex) {
                LOG.error(ex);
            }
        });

        disruptor.start();

        RingBuffer<CapturePacket> ringBuffer = disruptor.getRingBuffer();

        PacketSource<?> packetSource = null;
        switch (captureSourceType){
            case "pcap4j":
                packetSource = new Pcap4jPacketSource();
                break;
            case "none":
                packetSource = new EmptyPacketSource();
                break;
            default:
                System.err.println("not supported packet source of:" + captureSourceType);
                break;
        }

        double permitsPerSecond = appConf.getDouble("client.capture.max.speed",1000d);
        @SuppressWarnings("UnstableApiUsage")
        RateLimiter producerRateLimiter = RateLimiter.create(permitsPerSecond);

        PacketEventProducer producer = new PacketEventProducer(ringBuffer,packetSource,producerRateLimiter);

        Thread producerThread = new Thread(producer);
        producerThread.setName("producer_thread");
        producerThread.start();

        Thread monitorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    System.out.println("producer packet speed: " + producer.getMetricSpeed());

                    for (PacketEventHandler eventHandler : handlers) {
                        System.out.println("consumer[" + eventHandler.getHandlerName() + "] packet speed: " + eventHandler.getMetricSpeed());
                    }

                    System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
                    Utils.sleepQuietly(5, TimeUnit.SECONDS);
                }
            }
        });

        monitorThread.setDaemon(true);
        monitorThread.setName("monitor_thread");
        monitorThread.start();
    }
}