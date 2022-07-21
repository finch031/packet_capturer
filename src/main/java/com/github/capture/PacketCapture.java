package com.github.capture;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.utils.Tuple;
import com.github.capture.utils.Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pcap4j.core.*;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.namednumber.DataLinkType;
import org.pcap4j.util.NifSelector;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public final class PacketCapture extends Thread{
    private static final Logger LOG = LogManager.getLogger(PacketCapture.class);
    private static final int timeoutMillis = 200;
    private static final int bufferSize = 64 * 1024 * 1024;

    /**
     * 应用参数配置.
     * */
    private AppConfiguration appConf;

    /**
     * 外部传参.
     * */
    private String[] cmdArgs;

    /**
     * 启停标志.
     * */
    private boolean isRunning = false;

    /**
     * 暂停抓包.
     * */
    private boolean pause = true;

    /**
     * 暂停抓包睡眠时长,默认1秒.
     * */
    private int capturePauseSleepMillis = 1000;

    /**
     * 数据包缓存.
     * */
    private ConcurrentLinkedQueue<ByteBuf> bufferPackets;

    private PooledByteBufAllocator bufAllocator;

    /**
     * 抓包metric信息.
     * v1-时间戳,v2-抓包速度
     * */
    private final Tuple<Long,Float> captureMetric = new Tuple<>(System.currentTimeMillis(),0F);

    PacketCapture(){
        this.isRunning = true;
    }

    PacketCapture(AppConfiguration appConf,String[] cmdArgs,ConcurrentLinkedQueue<ByteBuf> bufferPackets, PooledByteBufAllocator bufAllocator, int capturePauseSleepMillis){
        this();
        this.appConf = appConf;
        this.cmdArgs = cmdArgs;
        this.bufferPackets = bufferPackets;
        this.bufAllocator = bufAllocator;
        this.capturePauseSleepMillis = capturePauseSleepMillis;
    }

    public static class Builder{
        private AppConfiguration appConf;
        private String[] cmdArgs;
        private int capturePauseSleepMillis = 1000;
        private ConcurrentLinkedQueue<ByteBuf> bufferPackets;
        private PooledByteBufAllocator bufAllocator;

        public Builder(){}

        public Builder setAppConf(AppConfiguration appConf){
            this.appConf = appConf;
            return this;
        }

        public Builder setCmdArgs(String[] cmdArgs){
            this.cmdArgs = cmdArgs;
            return this;
        }

        public Builder setBufferPackets(ConcurrentLinkedQueue<ByteBuf> bufferPackets){
            this.bufferPackets = bufferPackets;
            return this;
        }

        public Builder setBufferPool(PooledByteBufAllocator bufAllocator){
            this.bufAllocator = bufAllocator;
            return this;
        }

        public Builder setCapturePauseSleepMillis(int capturePauseSleepMillis){
            this.capturePauseSleepMillis = capturePauseSleepMillis;
            return this;
        }

        public PacketCapture build(){
            return new PacketCapture(appConf,cmdArgs,bufferPackets,bufAllocator,capturePauseSleepMillis);
        }
    }

    /**
     * 初始化PcapHandle
     * @param appConf 应用参数配置.
     * @param args 外部传参.
     * */
    private PcapHandle initPcapHandle(AppConfiguration appConf, String[] args) throws PcapNativeException,NotOpenException{
        String NIF_NAME = null;

        if(args.length == 1){
            NIF_NAME = args[0];
        }

        PcapNetworkInterface nif = null;
        if (NIF_NAME != null) {
            nif = Pcaps.getDevByName(NIF_NAME);
        } else {
            try {
                nif = new NifSelector().selectNetworkInterface();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (nif == null) {
            return null;
        }

        System.out.println(nif.getName() + " (" + nif.getDescription() + ")");
        for (PcapAddress addr : nif.getAddresses()) {
            if (addr.getAddress() != null) {
                System.out.println("IP address: " + addr.getAddress());
            }
        }
        System.out.println();

        PcapHandle handle = new PcapHandle.Builder(nif.getName())
                        .snaplen(65535)
                        .promiscuousMode(PcapNetworkInterface.PromiscuousMode.PROMISCUOUS)
                        .timeoutMillis(timeoutMillis)
                        .bufferSize(bufferSize)
                        .build();

        LOG.info("pcap handle is open:" + handle.isOpen());

        String packetBpfExpression = appConf.getString("client.packet.bpf.expression","tcp");
        if(packetBpfExpression != null && !packetBpfExpression.isEmpty()){
            System.out.println("bpf expression: " + packetBpfExpression);
            handle.setFilter(packetBpfExpression, BpfProgram.BpfCompileMode.OPTIMIZE);
        }

        String pcapDirection = appConf.getString("client.packet.direction","INOUT");
        // Setting direction is not implemented on this platform
        // handle.setDirection(PcapHandle.PcapDirection.valueOf(pcapDirection));

        handle.setDlt(DataLinkType.EN10MB);

        return handle;
    }

    /**
     * 循环抓包
     * @param pcapHandle PcapHandle抓包句柄
     * */
    private void captureLoop(PcapHandle pcapHandle){
        startCapture();
        LOG.info("开始捕获...");

        float captureSpeed = 0F;
        int captureSpeedCounter = 0;
        long captureSpeedStartTs = System.currentTimeMillis();
        long millisTakenNow = 0;
        while (isRunning){
            // 暂停抓包.
            if(pause){
               Utils.sleepQuietly(capturePauseSleepMillis, TimeUnit.MILLISECONDS);
               continue;
            }

            Packet packet = null;
            try{
                packet = pcapHandle.getNextPacket();
                // is Ethernet packet
                if(packet != null){
                    int dlt = pcapHandle.getDlt().value();
                    long timestamp = pcapHandle.getTimestamp().getTime();

                    // Ethernet
                    if(dlt == 1){
                        byte[] rawData = packet.getRawData();

                        // 申请内存块
                        ByteBuf byteBuf = bufAllocator.buffer(rawData.length + 12);

                        // 写入原始数据长度
                        byteBuf.writeInt(rawData.length);
                        // 写入原始数据抓包时间
                        byteBuf.writeLong(timestamp);
                        // 写入原始数据
                        byteBuf.writeBytes(rawData);

                        bufferPackets.add(byteBuf);

                        /*
                        // 4 + 8 + raw_data_len
                        byte[] data = new byte[4 + 8 + rawData.length];

                        // write 4 byte length.
                        Utils.writeUnsignedIntLE(data,0,rawData.length + 8);

                        // write 8 byte timestamp.
                        Utils.writeLong(data,4,timestamp);

                        // write raw data.
                        System.arraycopy(rawData,0,data,12,rawData.length);

                        // buffer packets.
                        bufferPackets.add(data);
                        */

                        captureSpeedCounter++;
                    }
                }
            }catch (NotOpenException ex){
                ex.printStackTrace();
            }

            millisTakenNow = System.currentTimeMillis() - captureSpeedStartTs;
            if(millisTakenNow >= 1000){
                // 抓包速度计算
                captureSpeed = Utils.numericFormat((1000F * captureSpeedCounter) / millisTakenNow,2);
                this.captureMetric.updateV1(System.currentTimeMillis());
                this.captureMetric.updateV2(captureSpeed);

                // reset.
                captureSpeedStartTs = System.currentTimeMillis();
                captureSpeedCounter = 0;
            }
        }
    }

    /**
     * 开始抓包
     * */
    public void startCapture(){
        this.pause = false;
    }

    /**
     * 暂停抓包
     * */
    public void pauseCapture(){
        this.pause = true;
    }

    /**
     * 停止运行
     * */
    public void stopRunning(){
        this.isRunning = false;
    }

    @Override
    public void run() {
        PcapHandle pcapHandle = null;
        try{
            pcapHandle = initPcapHandle(appConf,cmdArgs);
        }catch (PcapNativeException | NotOpenException ex){
            ex.printStackTrace();
        }

        if(pcapHandle != null){
            captureLoop(pcapHandle);
        }else {
            LOG.error("PcapHandle抓包句柄初始化失败!");
        }
    }

    public final Tuple<Long,Float> captureMetric(){
        return this.captureMetric;
    }
}