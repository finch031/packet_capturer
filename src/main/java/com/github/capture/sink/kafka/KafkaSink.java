package com.github.capture.sink.kafka;

import com.github.capture.model.TcpPacketRecord;
import com.github.capture.sink.Callback;
import com.github.capture.sink.PacketSink;
import com.github.capture.utils.JsonSerialization;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/24 11:00
 * @description
 */
public class KafkaSink implements PacketSink {
    private static final Logger LOG = LogManager.getLogger(KafkaSink.class);

    private final KafkaProducer<String, String> producer;
    private final String topic;
    private static final JsonSerialization<TcpPacketRecord> jsonSerialization = new JsonSerialization<>(TcpPacketRecord.class,true,false);

    public KafkaSink(Properties props, String topic){
        this.producer = new KafkaProducer<>(props);
        this.topic = topic;
        LOG.info(String.format("成功初始化topic:%s上的KafkaProducer",topic));
    }

    @Override
    public SinkType sinkType() {
        return SinkType.KAFKA_SINK;
    }

    @Override
    public void writeTo(TcpPacketRecord record, Callback callback) throws Exception {
        String messageKey = record.getMessageID();
        String messageValue = jsonSerialization.toJson(record);

        // 1.初始化事务，前提条件是手动指定了transactionId
        // producer.initTransactions();

        try {
            // 2.开始事务
            // producer.beginTransaction();

            producer.send(new ProducerRecord<>(topic, messageKey, messageValue)).get();

            // 3.为消费者提供事务内的消费位移提交操作
            /*
             * This method should be used when you need to batch consumed and produced messages
             * together, typically in a consume-transform-produce pattern.
             * */
            // producer.sendOffsetsToTransaction();

            // 4.提交事务
            // producer.commitTransaction();

        } catch (ProducerFencedException | OutOfOrderSequenceException | AuthorizationException e) {
            // We can't recover from these exceptions, so our only option is to close the producer and exit.
            producer.close();
        }catch (KafkaException e){
            // For all other exceptions, just abort the transaction and try again.
            // 5.终止事务，类似于回滚.
            // producer.abortTransaction();
        }catch (InterruptedException | ExecutionException e){
            e.printStackTrace();
        }

        record = null;
    }

    @Override
    public void gatherAndWriteTo(TcpPacketRecord record) throws Exception {

    }

    @Override
    public void open() {

    }

    @Override
    public void close() throws IOException {
        this.producer.close();
    }
}
