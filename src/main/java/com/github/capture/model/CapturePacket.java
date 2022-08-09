package com.github.capture.model;

import java.io.Serializable;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/8/5 21:45
 * @description
 */
public interface CapturePacket extends Serializable {

    class EmptyCapturePacket implements CapturePacket{
        @Override
        public long captureTimeStamp() {
            return 0;
        }

        @Override
        public byte[] data() {
            return new byte[0];
        }

        @Override
        public void setCaptureTimeStamp(long ts) {
        }

        @Override
        public void setData(byte[] data) {
        }

        @Override
        public String toString() {
            return "EmptyCapturePacket{}";
        }
    }

    static CapturePacket emptyPacket(){
        return new EmptyCapturePacket();
    }

    long captureTimeStamp();

    byte[] data();

    void setCaptureTimeStamp(long ts);

    void setData(byte[] data);
}
