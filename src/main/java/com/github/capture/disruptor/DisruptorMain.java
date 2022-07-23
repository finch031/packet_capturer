package com.github.capture.disruptor;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.model.TcpPacketRecord;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;
import com.github.capture.sink.PacketSinkFactoryMaker;
import com.github.capture.utils.Utils;
import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
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

        PacketEventFactory factory = new PacketEventFactory();
        int ringBufferSize = appConf.getInteger("client.disruptor.ring.buffer.size",1024);

        WaitStrategy waitStrategy = new BlockingWaitStrategy();
        Disruptor<TcpPacketRecord> disruptor = new Disruptor<>(
                factory,
                ringBufferSize,                 // 指定RingBuffer的大小
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,            // 单个生产者
                waitStrategy);                  // 消费者等待策略


        PacketSinkFactory packetSinkFactory = PacketSinkFactoryMaker.makeFactory(sinkType,appConf);
        int taskNumber = appConf.getInteger("client.task.parallel.number",4);
        PacketEventHandler[] handlers = new PacketEventHandler[taskNumber];
        for(int i = 0; i < taskNumber; i++){
            handlers[i] = new PacketEventHandler(appConf,packetSinkFactory,"packet_event_handler_" + i);
        }
        disruptor.handleEventsWith(handlers);
        disruptor.setDefaultExceptionHandler(new ExceptionHandler<TcpPacketRecord>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, TcpPacketRecord event) {
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

        RingBuffer<TcpPacketRecord> ringBuffer = disruptor.getRingBuffer();
        PacketEventProducer producer = new PacketEventProducer(appConf,args,ringBuffer);

        Thread producerThread = new Thread(producer);
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
        monitorThread.start();
    }
}