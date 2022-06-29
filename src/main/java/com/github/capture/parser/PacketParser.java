package com.github.capture.parser;

import com.github.capture.model.TcpPacketRecord;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.IllegalRawDataException;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.namednumber.EtherType;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/14 18:00
 * @description
 */
public final class PacketParser {
    /**
     * 原始数据包解析
     * @param data 原始数据.
     * @param messageID 消息ID.
     * @param captureTs 原始数据包捕获时间
     * */
    public static TcpPacketRecord parse(byte[] data,String messageID,long captureTs){
        TcpPacketRecord packetRecord = null;
        long parseStartTs = System.currentTimeMillis();

        try{
            EthernetPacket ethernetPacket = EthernetPacket.newPacket(data, 0, data.length);

            if(ethernetPacket.getHeader().getType() == EtherType.IPV4){
                packetRecord = new TcpPacketRecord();

                EthernetPacket.EthernetHeader ethernetHeader = ethernetPacket.getHeader();
                EtherType etherType = ethernetHeader.getType();

                packetRecord.setMessageID(messageID);
                packetRecord.setMessageTs(System.currentTimeMillis());
                packetRecord.setMessageCaptureTs(captureTs);

                packetRecord.setEtherType(etherType.value());

                String ethSrcAddress = ethernetHeader.getSrcAddr().toString();
                packetRecord.setEthSrcAddress(ethSrcAddress);

                String ethDstAddress = ethernetHeader.getDstAddr().toString();
                packetRecord.setEthDstAddress(ethDstAddress);

                switch (etherType.value()){
                    case (short) 0x0800:
                        // EtherType.IPV4;
                        IpV4Packet ipV4Packet = ethernetPacket.getPayload().get(IpV4Packet.class);

                        ipV4PacketParse(ipV4Packet,packetRecord);

                        TcpPacket tcpPacket = ethernetPacket.getPayload().get(TcpPacket.class);
                        tcpPacketParse(tcpPacket,packetRecord);

                        break;
                    case (short) 0x86dd:
                        // EtherType.IPV6

                        break;
                    case (short) 0x0806:
                        // EtherType.ARP


                        break;
                    case (short) 0x8035:
                        // EtherType.RARP

                        break;
                    case (short) 0x8100:
                        // EtherType.DOT1Q_VLAN_TAGGED_FRAMES

                        break;
                    case (short) 0x880b:
                        // EtherType.PPP


                        break;
                    case (short) 0x8847:
                        // EtherType.MPLS

                        break;
                    case (short) 0x809b:
                        // EtherType.APPLETALK


                        break;
                    case (short) 0x8863:
                        // EtherType.PPPOE_DISCOVERY_STAGE

                        break;
                    case (short) 0x8864:
                        // EtherType.PPPOE_SESSION_STAGE

                        break;
                    default:
                        break;
                }
            }else{
                System.out.println("packet is not a ipv4.");
            }
        }catch (IllegalRawDataException IRE){
            IRE.printStackTrace();
        }

        long millisTaken = System.currentTimeMillis() - parseStartTs;
        if(millisTaken > 30){
            System.out.println("- - - - - - - - - - -" + "millis taken=" + millisTaken + "- - - - - - - - - - - - -");
        }

        return packetRecord;
    }

    private static void ipV4PacketParse(IpV4Packet ipV4Packet, TcpPacketRecord packetRecord){
        IpV4Packet.IpV4Header ipV4Header = ipV4Packet.getHeader();

        byte ipVersion = ipV4Header.getVersion().value();
        packetRecord.setIpVersion(ipVersion);

        byte ipIhl = ipV4Header.getIhl();
        packetRecord.setIpIhl(ipIhl);

        byte tos = ipV4Header.getTos().value();
        packetRecord.setIpTos(tos);

        int ipTotalLen = ipV4Header.getTotalLengthAsInt();
        packetRecord.setIpTotalLen(ipTotalLen);

        int ipIdentification = ipV4Header.getIdentificationAsInt();
        packetRecord.setIpIdentification(ipIdentification);

        boolean ipReservedFlag = ipV4Header.getReservedFlag();
        packetRecord.setIpReservedFlag(ipReservedFlag);

        boolean ipDontFragmentFlag = ipV4Header.getDontFragmentFlag();
        packetRecord.setIpDontFragmentFlag(ipDontFragmentFlag);

        boolean ipMoreFragmentFlag = ipV4Header.getMoreFragmentFlag();
        packetRecord.setIpMoreFragmentFlag(ipMoreFragmentFlag);

        short ipFragmentOffset = ipV4Header.getFragmentOffset();
        packetRecord.setIpFragmentOffset(ipFragmentOffset);

        byte ipTtl = ipV4Header.getTtl();
        packetRecord.setIpTtl(ipTtl);

        byte ipProtocol = ipV4Header.getProtocol().value();
        packetRecord.setIpProtocol(ipProtocol);

        short ipHeaderChecksum = ipV4Header.getHeaderChecksum();
        packetRecord.setIpHeaderChecksum(ipHeaderChecksum);


        String ipSrcAddress = ipV4Header.getSrcAddr().getHostAddress();
        packetRecord.setIpSrcAddress(ipSrcAddress);

        // this is very slow.
        // String ipDstAddress = ipV4Header.getDstAddr().getCanonicalHostName();

        String ipDstAddress = ipV4Header.getDstAddr().getHostAddress();
        packetRecord.setIpDstAddress(ipDstAddress);

    }

    private static void tcpPacketParse(TcpPacket tcpPacket,TcpPacketRecord packetRecord){
        TcpPacket.TcpHeader tcpHeader = tcpPacket.getHeader();

        int tcpSrcPort = tcpHeader.getSrcPort().valueAsInt();
        packetRecord.setTcpSrcPort(tcpSrcPort);

        int tcpDstPort = tcpHeader.getDstPort().valueAsInt();
        packetRecord.setTcpDstPort(tcpDstPort);

        long tcpSeqNumber = tcpHeader.getSequenceNumberAsLong();
        packetRecord.setTcpSeqNumber(tcpSeqNumber);

        long tcpAcknowledgmentNumber = tcpHeader.getAcknowledgmentNumberAsLong();
        packetRecord.setTcpAcknowledgmentNumber(tcpAcknowledgmentNumber);

        packetRecord.setTcpDataOffset(tcpHeader.getDataOffset());

        packetRecord.setTcpReserved(tcpHeader.getReserved());

        packetRecord.setTcpSyn(tcpHeader.getSyn());

        packetRecord.setTcpAck(tcpHeader.getAck());

        packetRecord.setTcpFin(tcpHeader.getFin());

        packetRecord.setTcpPsh(tcpHeader.getPsh());

        packetRecord.setTcpRst(tcpHeader.getRst());

        packetRecord.setTcpUrg(tcpHeader.getUrg());

        packetRecord.setTcpChecksum(tcpHeader.getChecksum());

        packetRecord.setTcpWindow(tcpHeader.getWindowAsInt());

        packetRecord.setTcpUrgentPointer(tcpHeader.getUrgentPointerAsInt());
    }
}
