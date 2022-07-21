package com.github.capture.sink.kafka;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 14:30
 * @description
 */
public class KafkaPacketSinkFactory implements PacketSinkFactory {

    public KafkaPacketSinkFactory(){
    }

    @Override
    public PacketSink getPacketSink(AppConfiguration appConf) {
        String bootstrapServers = appConf.getString("client.kafka.bootstrap.servers","");
        Properties kafkaProducerProps =  new Properties();
        kafkaProducerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        kafkaProducerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProducerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        String topic = appConf.getString("client.kafka.topic","");

        return new KafkaSink(kafkaProducerProps,topic);
    }
}
