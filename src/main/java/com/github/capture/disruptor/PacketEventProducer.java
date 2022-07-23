package com.github.capture.disruptor;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.model.TcpPacketRecord;
import com.github.capture.parser.PacketParser;
import com.github.capture.utils.IdGenerator;
import com.github.capture.utils.Snowflake;
import com.lmax.disruptor.RingBuffer;
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
 * @datetime 2022/7/22 10:25
 * @description
 */
public class PacketEventProducer implements Runnable{
    private static final Logger LOG = LogManager.getLogger(PacketEventProducer.class);
    private static final int timeoutMillis = 200;
    private static final int bufferSize = 64 * 1024 * 1024;

    private transient boolean isRunning;
    private PcapHandle pcapHandle;
    private final RingBuffer<TcpPacketRecord> ringBuffer;
    private static final IdGenerator idGenerator = new Snowflake(6,10);
    private float metricSpeed = 0f;

    private PcapHandle initPcapHandle(AppConfiguration appConf, String[] args)throws PcapNativeException, NotOpenException {
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

    public PacketEventProducer(AppConfiguration appConf, String[] args, RingBuffer<TcpPacketRecord> ringBuffer){
        this.ringBuffer = ringBuffer;
        try{
            this.pcapHandle = initPcapHandle(appConf,args);
        }catch (Exception ex){
            ex.printStackTrace();
        }
        this.isRunning = true;
    }

    public void stop(){
        this.isRunning = false;
    }

    public float getMetricSpeed() {
        return metricSpeed;
    }

    @Override
    public void run() {
        long parseMillis = 0;
        long recordMillisTaken = 0;
        Packet packet;

        long metricStartNano = 0;
        while(isRunning){
            metricStartNano = System.nanoTime();

            try{
                packet = pcapHandle.getNextPacket();
                // is Ethernet packet
                if(packet != null){
                    int dlt = pcapHandle.getDlt().value();
                    long timestamp = pcapHandle.getTimestamp().getTime();

                    // Ethernet
                    if(dlt == 1){
                        byte[] data = packet.getRawData();
                        long startTs = System.currentTimeMillis();
                        // 数据包解析
                        TcpPacketRecord record = PacketParser.parse(data, 0, data.length, String.valueOf(idGenerator.id()), timestamp);
                        parseMillis = System.currentTimeMillis() - startTs;
                        if(parseMillis >= 5){
                            System.out.println("packet parse millis: " + parseMillis);
                        }

                        long sequence = ringBuffer.next();
                        if(sequence % 10000 == 0){
                            System.out.println("sequence=" + sequence);
                        }

                        TcpPacketRecord event = ringBuffer.get(sequence);

                        event.setMessageID(record.getMessageID());
                        event.setMessageCaptureTs(record.getMessageCaptureTs());
                        event.setMessageTs(record.getMessageTs());

                        event.setEtherType(record.getEtherType());
                        event.setEthDstAddress(record.getEthDstAddress());
                        event.setEthSrcAddress(record.getEthSrcAddress());

                        event.setIpSrcAddress(record.getIpSrcAddress());
                        event.setIpDstAddress(record.getIpDstAddress());
                        event.setIpMoreFragmentFlag(record.isIpMoreFragmentFlag());
                        event.setIpTtl(record.getIpTtl());
                        event.setIpProtocol(record.getIpProtocol());
                        event.setIpFragmentOffset(record.getIpFragmentOffset());
                        event.setIpIhl(record.getIpIhl());
                        event.setIpHeaderChecksum(record.getIpHeaderChecksum());
                        event.setIpIdentification(record.getIpIdentification());
                        event.setIpReservedFlag(record.isIpReservedFlag());
                        event.setIpDontFragmentFlag(record.isIpDontFragmentFlag());
                        event.setIpTos(record.getIpTos());
                        event.setIpTotalLen(record.getIpTotalLen());
                        event.setIpVersion(record.getIpVersion());

                        event.setTcpPsh(record.isTcpPsh());
                        event.setTcpSyn(record.isTcpSyn());
                        event.setTcpFin(record.isTcpFin());
                        event.setTcpAck(record.isTcpAck());
                        event.setTcpRst(record.isTcpRst());
                        event.setTcpUrg(record.isTcpUrg());
                        event.setTcpDataOffset(record.getTcpDataOffset());
                        event.setTcpUrgentPointer(record.getTcpUrgentPointer());
                        event.setTcpReserved(record.getTcpReserved());
                        event.setTcpSrcPort(record.getTcpSrcPort());
                        event.setTcpDstPort(record.getTcpDstPort());
                        event.setTcpWindow(record.getTcpWindow());
                        event.setTcpChecksum(record.getTcpChecksum());
                        event.setTcpSeqNumber(record.getTcpSeqNumber());
                        event.setTcpAcknowledgmentNumber(record.getTcpAcknowledgmentNumber());

                        ringBuffer.publish(sequence);

                        recordMillisTaken = System.currentTimeMillis() - startTs;
                        if(recordMillisTaken >= 10){
                            System.out.println("producer record millis taken: " + recordMillisTaken);
                        }

                        metricSpeed = 1_000_000_000f / (System.nanoTime() - metricStartNano);
                    }
                }
            }catch (NotOpenException ex){
                ex.printStackTrace();
            }
        }
    }

}
