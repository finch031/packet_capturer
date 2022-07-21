package com.github.capture.sink.redis;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 14:41
 * @description
 */
public class RedisPacketSinkFactory implements PacketSinkFactory {
    private final JedisPool jedisPool;

    public RedisPacketSinkFactory(AppConfiguration appConf){
        String redisHost = appConf.getString("client.redis.host","");
        int redisPort = appConf.getInteger("client.redis.port",6379);
        String redisPassword = appConf.getString("client.redis.password","");
        int redisDB = appConf.getInteger("client.redis.db",1);
        int taskParallelNumber = appConf.getInteger("client.task.parallel.number",4);

        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(taskParallelNumber);
        jedisPoolConfig.setMaxIdle(1);

        jedisPool = new JedisPool(jedisPoolConfig,redisHost,redisPort,3000,redisPassword,redisDB);
    }

    @Override
    public PacketSink getPacketSink(AppConfiguration appConf) {
        return new RedisSink(jedisPool.getResource());
    }
}
