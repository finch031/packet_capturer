package com.github.capture.sink.redis;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;
import redis.clients.jedis.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 14:41
 * @description
 */
public class RedisPacketSinkFactory implements PacketSinkFactory {
    // private final JedisPool jedisPool;

    // jedis集群
    // private final JedisCluster jedisCluster;

    // private final ShardedJedisPool shardedJedisPool;

    private final Set<HostAndPort> hostAndPortSet = new HashSet<>();

    public RedisPacketSinkFactory(AppConfiguration appConf){
        String redisHost = appConf.getString("client.redis.host","");
        int redisPort = appConf.getInteger("client.redis.port",6379);
        String redisPassword = appConf.getString("client.redis.password","");
        int redisDB = appConf.getInteger("client.redis.db",1);
        int taskParallelNumber = appConf.getInteger("client.task.parallel.number",4);

        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(taskParallelNumber);
        jedisPoolConfig.setMaxIdle(-1);

        // cluster模式
        String clusterNodes = appConf.getString("client.redis.cluster.nodes","");
        String[] nodes = clusterNodes.split(",");
        // Set<HostAndPort> hostAndPortSet = new HashSet<>();
        List<JedisShardInfo> shards = new ArrayList<>();
        for (String node : nodes) {
            String[] tmpNode = node.split(":");
            String host = tmpNode[0];
            String port = tmpNode[1];
            hostAndPortSet.add(new HostAndPort(host,Integer.parseInt(port)));
            JedisShardInfo shardInfo = new JedisShardInfo(host,Integer.parseInt(port));
            shardInfo.setPassword(redisPassword);
            shardInfo.setConnectionTimeout(3000);
            shards.add(shardInfo);
        }

        // jedisCluster = new JedisCluster(hostAndPortSet,3000,1000,30,redisPassword,jedisPoolConfig);
        // jedisPool = new JedisPool(jedisPoolConfig,redisHost,redisPort,3000,redisPassword,redisDB);
        // shardedJedisPool = new ShardedJedisPool(jedisPoolConfig,shards);
    }

    @Override
    public PacketSink getPacketSink(AppConfiguration appConf) {
        int recordTtl = appConf.getInteger("client.redis.ttl",300);

        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(4);
        jedisPoolConfig.setMaxIdle(-1);
        JedisCluster jedisCluster = new JedisCluster(hostAndPortSet,3000,1000,30,"xxs_2022!",jedisPoolConfig);

        return new RedisSink(jedisCluster,recordTtl);
        // return new RedisSink(shardedJedisPool.getResource(),recordTtl);
        // return new RedisSink(jedisPool.getResource(),recordTtl);
    }
}