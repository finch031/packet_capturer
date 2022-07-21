package com.github.capture.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/13 16:05
 * @description
 */
public class BufferPoolTest {

    @Test
    public void test01(){
        PooledByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;

        ByteBuf byteBuf = allocator.buffer(128);
        byteBuf.writeInt(3);
        byteBuf.writeLong(System.currentTimeMillis());

        String msg = "你好China!";
        byteBuf.writeBytes(msg.getBytes());

        System.out.println(byteBuf.readInt());
        System.out.println(byteBuf.readLong());

        byte[] data = new byte[msg.getBytes().length];

        byteBuf.readBytes(data,0,data.length);
        System.out.println("data size=" + data.length);

        System.out.println(new String(data));

    }

}
