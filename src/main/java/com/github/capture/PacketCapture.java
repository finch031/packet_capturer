package com.github.capture;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.utils.Tuple;
import com.github.capture.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pcap4j.core.*;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.namednumber.DataLinkType;
import org.pcap4j.util.NifSelector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
    private ConcurrentLinkedQueue<byte[]> bufferPackets;

    /**
     * 抓包metric信息.
     * v1-时间戳,v2-抓包速度
     * */
    private final Tuple<Long,Float> captureMetric = new Tuple<>(System.currentTimeMillis(),0F);

    PacketCapture(){
        this.isRunning = true;
    }

    PacketCapture(AppConfiguration appConf,String[] cmdArgs,ConcurrentLinkedQueue<byte[]> bufferPackets, int capturePauseSleepMillis){
        this();
        this.appConf = appConf;
        this.cmdArgs = cmdArgs;
        this.bufferPackets = bufferPackets;
        this.capturePauseSleepMillis = capturePauseSleepMillis;
    }

    public static class Builder{
        private AppConfiguration appConf;
        private String[] cmdArgs;
        private int capturePauseSleepMillis = 1000;
        private ConcurrentLinkedQueue<byte[]> bufferPackets;

        public Builder(){}

        public Builder setAppConf(AppConfiguration appConf){
            this.appConf = appConf;
            return this;
        }

        public Builder setCmdArgs(String[] cmdArgs){
            this.cmdArgs = cmdArgs;
            return this;
        }

        public Builder setBufferPackets(ConcurrentLinkedQueue<byte[]> bufferPackets){
            this.bufferPackets = bufferPackets;
            return this;
        }

        public Builder setCapturePauseSleepMillis(int capturePauseSleepMillis){
            this.capturePauseSleepMillis = capturePauseSleepMillis;
            return this;
        }

        public PacketCapture build(){
            return new PacketCapture(appConf,cmdArgs,bufferPackets,capturePauseSleepMillis);
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

        PcapHandle handle =
                new PcapHandle.Builder(nif.getName())
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

                    if(dlt == 1){
                        byte[] rawData = packet.getRawData();

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

    private void writeBufferLoop(AppConfiguration appConf){
        AtomicLong counter = new AtomicLong(0);
        String outputPath = appConf.getString("client.packet.output.path","./packet");
        String fileNamePrefix = appConf.getString("client.packet.output.filename","tcp_capture");

        Collection<File> inProgressFiles = Utils.listFiles(new File(outputPath),new String[] {"inprogress"},false);

        String currentWriteFileName;
        boolean initIsInProgressFile = false;
        if(inProgressFiles.isEmpty()){
            currentWriteFileName = outputPath + "/" + fileNamePrefix + "_" + Utils.now() + ".inprogress";
        }else{
            Optional<File> latestFile = inProgressFiles.stream().max(new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    return Long.compare(o1.lastModified(), o2.lastModified());
                }
            });
            currentWriteFileName = latestFile.get().getAbsolutePath();
            initIsInProgressFile = true;
        }

        FileChannel fileChannel;
        int writeBatchSize = appConf.getInteger("client.packet.write.batch.size",100);
        int fileSwitchMB = appConf.getInteger("client.packet.outout.file.switch.mb",512);
        try{
            FileOutputStream fos = new FileOutputStream(currentWriteFileName,initIsInProgressFile);
            fileChannel = fos.getChannel();

            int hasWritten = 0;
            while(isRunning){
                byte[] data = bufferPackets.poll();
                if(data != null){
                    try{
                        fileChannel.write(ByteBuffer.wrap(data));
                        hasWritten++;
                        counter.incrementAndGet();
                    }catch (IOException ioe){
                        LOG.error(ioe);
                    }

                    if(hasWritten == writeBatchSize){
                        try{
                            fileChannel.force(true);

                            if(fileChannel.size() > fileSwitchMB * 1024L * 1024L){
                                fos.close();
                                File currentFile = new File(currentWriteFileName);
                                File destFile = new File(currentWriteFileName.replace(".inprogress",".packet"));
                                boolean renameSuccess = currentFile.renameTo(destFile);
                                if(renameSuccess){
                                    currentWriteFileName = outputPath + "/" + fileNamePrefix + "_" + Utils.now() + ".inprogress";
                                    fos = new FileOutputStream(currentWriteFileName);
                                    fileChannel = fos.getChannel();
                                    LOG.info(" write file " + destFile.getAbsolutePath() + " packet number=" + counter.get());
                                    counter.set(0);
                                    LOG.info("start write new file: " + currentWriteFileName);
                                }
                            }

                            hasWritten = 0;
                        }catch (IOException ioe){
                            ioe.printStackTrace();
                        }
                    }
                }
            }
        }catch (FileNotFoundException fileNotFoundException){
            fileNotFoundException.printStackTrace();
        }
    }

    public static void main(String[] args){
        AppConfiguration appConf = AppConfiguration.loadFromPropertiesResource("client.properties");

        /*
        Builder packetCaptureBuilder = new PacketCapture.Builder();
        packetCaptureBuilder.setAppConf(appConf);
        packetCaptureBuilder.setCmdArgs(args);
        packetCaptureBuilder.setCapturePauseSleepMillis(200);
        packetCaptureBuilder.setBufferPackets(new ConcurrentLinkedQueue<>());

        PacketCapture packetCapture = packetCaptureBuilder.build();
        packetCapture.start();
        */

        /*
        PcapHandle pcapHandle = null;
        try{
            pcapHandle = initPcapHandle(appConf,args);
        }catch (PcapNativeException | NotOpenException ex){
            ex.printStackTrace();
        }

        if(pcapHandle == null){
            LOG.error("pcap handle init failed!");
            System.exit(1);
        }

        PacketCapture capture = new PacketCapture();

        Thread writeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                capture.writeBufferLoop(appConf);
            }
        });
        writeThread.setDaemon(true);
        writeThread.start();

        capture.captureLoop(pcapHandle);
        */

    }
}