package com.github.capture.task;

import com.github.capture.model.TcpPacketRecord;
import com.github.capture.parser.PacketParsable;
import com.github.capture.parser.PacketParser;
import com.github.capture.sink.Callback;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.SinkPacketMetadata;
import com.github.capture.utils.IdGenerator;
import com.github.capture.utils.Triple;
import com.github.capture.utils.Utils;
import com.github.capture.utils.XORShiftRandom;
import io.netty.buffer.ByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/14 19:18
 * @description
 */
public class PacketParallelTask implements Runnable, PacketParsable {
    private static final Logger LOG = LogManager.getLogger(PacketParallelTask.class);

    /**
     * 数据源.
     * */
    private ConcurrentLinkedQueue<ByteBuf> packetBuffer;

    /**
     * id生成器.
     * */
    private IdGenerator idGenerator;

    /**
     * 数据包输出器.
     * */
    private PacketSink sink;

    /**
     * 任务运行状态标志.
     * */
    private transient boolean isRunning = false;

    /**
     * 数据包任务的metric信息
     * v1-时间戳,v2-数据包处理速度,v3-线程ID
     * */
    private final Triple<Long,Float,Long> processMetric = new Triple<>(System.currentTimeMillis(),0F,0L);

    public PacketParallelTask(){
        this.idGenerator = new XORShiftRandom();
        this.isRunning = true;
    }

    public PacketParallelTask(ConcurrentLinkedQueue<ByteBuf> packetBuffer, PacketSink sink){
        this();
        this.packetBuffer = packetBuffer;
        this.sink = sink;
    }

    public void setPacketBuffer(ConcurrentLinkedQueue<ByteBuf> packetBuffer){
        this.packetBuffer = packetBuffer;
    }

    public void setSink(PacketSink sink){
        this.sink = sink;
    }

    public void setIdGenerator(IdGenerator idGenerator){
        this.idGenerator = idGenerator;
    }

    private void assertIsReady(){
        Utils.checkNotNull(packetBuffer);
        LOG.info("packet buffer is ready.");

        Utils.checkNotNull(sink);
        LOG.info("sink is ready, sink type is: " + sink.sinkType());

        this.processMetric.updateThird(Thread.currentThread().getId());
    }

    public void stop(){
        this.isRunning = false;
        try{
            sink.close();
        }catch (IOException ioe){
            ioe.printStackTrace();
        }
    }

    public final Triple<Long,Float,Long> processMetric(){
        return this.processMetric;
    }

    @Override
    public void run() {
        assertIsReady();
        // 打开输出器
        sink.open();

        float processSpeed = 0F;
        int processSpeedCounter = 0;
        long processSpeedStartTs = System.currentTimeMillis();
        long millisTakenNow = 0;
        ByteBuf packet = null;
        byte[] data = new byte[65535];
        while (isRunning){
            // 拉取数据包
            packet = packetBuffer.poll();

            if(packet != null){
                int size = packet.readInt();
                long timestamp = packet.readLong();
                if(size > 65535){
                    data = new byte[size];
                }
                packet.readBytes(data,0,size);

                /*
                // 原始数据包大小
                int size = Utils.readUnsignedIntLE(packet,0) - 8;
                // 数据包捕获时间
                long timestamp = Utils.readLong(packet,4);
                // 以太网包数据
                data = new byte[size];
                // 分离出数据包
                System.arraycopy(packet,12,data,0,size);
                */

                // 数据包解析
                // TcpPacketRecord tcpPacketRecord = parse(packet.array(),12,size,timestamp);
                TcpPacketRecord tcpPacketRecord = parse(data,0, size,timestamp);

                try{
                    // 解析记录输出.
                    sink.writeTo(tcpPacketRecord, new Callback() {
                        @Override
                        public void onCompletion(Future<SinkPacketMetadata> future) {
                        }
                    });

                    processSpeedCounter++;
                }catch (Exception ex){
                    ex.printStackTrace();
                }

                // 释放内存块
                packet.release();
            }

            millisTakenNow = System.currentTimeMillis() - processSpeedStartTs;
            if(millisTakenNow >= 1000){
                // 数据包处理速度计算
                processSpeed = Utils.numericFormat((1000F * processSpeedCounter) / millisTakenNow,2);
                this.processMetric.updateFirst(System.currentTimeMillis());
                this.processMetric.updateSecond(processSpeed);

                // reset.
                processSpeedStartTs = System.currentTimeMillis();
                processSpeedCounter = 0;
            }
        }
    }

    @Override
    public TcpPacketRecord parse(byte[] data,int offset, int len,long captureTs) {
        return PacketParser.parse(data,offset,len,String.valueOf(idGenerator.id()),captureTs);
    }
}
