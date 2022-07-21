package com.github.capture.parser;

import com.github.capture.model.TcpPacketRecord;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/14 20:04
 * @description
 */
public interface PacketParsable {
    /**
     * @param data 原始数据包.
     * @param offset 字节偏移量.
     * @param len 字节长度.
     * @param captureTs 原始数据包捕获时间
     * */
    TcpPacketRecord parse(byte[] data,int offset, int len,long captureTs) throws Exception;
}