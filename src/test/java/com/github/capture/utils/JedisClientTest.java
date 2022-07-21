package com.github.capture.utils;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/12 19:44
 * @description
 */
public class JedisClientTest {

    // @Test
    public void test01(){
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        JedisPool jedisPool = new JedisPool(jedisPoolConfig,"node4",6379,3000,"xxs_2022!",3);
        Jedis jedis = jedisPool.getResource();

        jedis.set("a","a--x");

        jedis.close();
        jedisPool.close();
    }

}
