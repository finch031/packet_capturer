package com.github.capture.model;

import lombok.Data;
import java.io.Serializable;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022-01-04 14:22
 * @description
 */
@Data
public class TcpPacketRecord implements Serializable {
    // 消息ID
    private String messageID;

    // 消息接收时间戳
    private long messageTs;

    // 报文捕获时间戳
    private long messageCaptureTs;

    // 以太网类型(IPv4/IPv6/ARP/RARP/PPP/MPLS等)
    private short etherType;

    // 以太网链路层源mac地址
    private String ethSrcAddress;

    // 以太网链路层目的mac地址
    private String ethDstAddress;

    // IP版本
    private byte ipVersion;

    private byte ipIhl;

    private byte ipTos;

    private int ipTotalLen;

    private int ipIdentification;

    private boolean ipReservedFlag;

    private boolean ipDontFragmentFlag;

    private boolean ipMoreFragmentFlag;

    private short ipFragmentOffset;

    private byte ipTtl;

    private byte ipProtocol;

    private short ipHeaderChecksum;

    // IP源地址
    private String ipSrcAddress;

    // IP目的地址
    private String ipDstAddress;

    private int tcpSrcPort;
    private int tcpDstPort;

    private long tcpSeqNumber;
    private long tcpAcknowledgmentNumber;

    private byte tcpDataOffset;
    private byte tcpReserved;

    private boolean tcpSyn;
    private boolean tcpAck;
    private boolean tcpFin;
    private boolean tcpPsh;
    private boolean tcpRst;
    private boolean tcpUrg;

    private short tcpChecksum;
    private int tcpWindow;

    private int tcpUrgentPointer;
}