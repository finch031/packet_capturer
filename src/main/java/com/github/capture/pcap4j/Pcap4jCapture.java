package com.github.capture.pcap4j;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.model.Pcap4jCapturePacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pcap4j.core.*;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.namednumber.DataLinkType;
import org.pcap4j.util.NifSelector;

import java.io.IOException;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/8/4 20:46
 * @description
 */
public final class Pcap4jCapture {
    private static final Logger LOG = LogManager.getLogger(Pcap4jCapture.class);
    private static final int timeoutMillis = 200;
    private static final int bufferSize = 64 * 1024 * 1024;
    private static PcapHandle pcapHandle;

    private Pcap4jCapture(){
        AppConfiguration appConf = AppConfiguration.loadFromPropertiesResource("client.properties");
        String[] args = {};
        try {
            pcapHandle = initPcapHandle(appConf, args);
        }catch (PcapNativeException | NotOpenException ex){
            ex.printStackTrace();
        }

        if(pcapHandle == null){
            System.err.println("PcapHandle初始化失败!");
            System.exit(1);
        }
    }

    public Pcap4jCapturePacket getNextPacket() {
        try{
            Packet packet = pcapHandle.getNextPacket();
            if(packet != null && pcapHandle.getDlt().value() == 1){
                return new Pcap4jCapturePacket(pcapHandle.getTimestamp().getTime(),packet.getRawData());
            }
        }catch (NotOpenException ne){
            ne.printStackTrace();
        }
        return null;
    }

    /**
     * 初始化PcapHandle
     * @param appConf 应用参数配置.
     * @param args 外部传参.
     * */
    private PcapHandle initPcapHandle(AppConfiguration appConf, String[] args) throws PcapNativeException, NotOpenException {
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

        // String pcapDirection = appConf.getString("client.packet.direction","INOUT");
        // Setting direction is not implemented on this platform
        // handle.setDirection(PcapHandle.PcapDirection.valueOf(pcapDirection));

        handle.setDlt(DataLinkType.EN10MB);

        return handle;
    }

    public static Pcap4jCapture getInstance(){
        return Pcap4jCaptureHolder.INSTANCE.getInstance();
    }

    private enum Pcap4jCaptureHolder{
        INSTANCE;
        private final Pcap4jCapture instance;
        Pcap4jCaptureHolder(){
            this.instance = new Pcap4jCapture();
        }

        private Pcap4jCapture getInstance(){
            return this.instance;
        }
    }
}
