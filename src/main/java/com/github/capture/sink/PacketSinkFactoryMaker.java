package com.github.capture.sink;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.sink.ch.ClickHousePacketSinkFactory;
import com.github.capture.sink.console.ConsolePacketSinkFactory;
import com.github.capture.sink.es.EsPacketSinkFactory;
import com.github.capture.sink.file.CsvPacketSinkFactory;
import com.github.capture.sink.jdbc.JDBCPacketSinkFactory;
import com.github.capture.sink.kafka.KafkaPacketSinkFactory;
import com.github.capture.sink.redis.RedisPacketSinkFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 15:09
 * @description
 *          抽象工厂模式
 */
public class PacketSinkFactoryMaker {
    private static final Logger LOG = LogManager.getLogger(PacketSinkFactoryMaker.class);

    public static PacketSinkFactory makeFactory(PacketSink.SinkType sinkType, AppConfiguration appConf) {
        PacketSinkFactory factory = null;
        switch (sinkType){
            case CSV_SINK:
                factory = new CsvPacketSinkFactory();

                break;
            case MYSQL_SINK:
                factory = new JDBCPacketSinkFactory(appConf);

                break;
            case KAFKA_SINK:
                factory = new KafkaPacketSinkFactory();

                break;
            case CLICKHOUSE_SINK:
                factory = new ClickHousePacketSinkFactory();

                break;
            case ES_SINK:
                factory = new EsPacketSinkFactory(appConf,false);

                break;
            case REDIS_SINK:
                factory = new RedisPacketSinkFactory(appConf);

                break;
            case CONSOLE_SINK:
                factory = new ConsolePacketSinkFactory();

                break;
            default:
                LOG.error("unsupported sink type {}", sinkType);
                System.exit(1);
        }

        return factory;
    }

}
