package com.github.capture.model;

import java.util.Arrays;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/8/5 21:50
 * @description
 */
public class Pcap4jCapturePacket implements CapturePacket{
    private long ts;
    private byte[] data;

    public Pcap4jCapturePacket(){}

    public Pcap4jCapturePacket(long ts, byte[] data){
        this.ts = ts;
        this.data = data;
    }

    @Override
    public long captureTimeStamp() {
        return ts;
    }

    @Override
    public byte[] data() {
        return data;
    }

    @Override
    public void setCaptureTimeStamp(long ts) {
        this.ts = ts;
    }

    @Override
    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "Pcap4jCapturePacket{" +
                "ts=" + ts +
                ", data=" + Arrays.toString(data) +
                '}';
    }
}
