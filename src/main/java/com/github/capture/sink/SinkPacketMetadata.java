package com.github.capture.sink;

/**
 * @author yusheng
 * @version 0.0.1.20220614_alpha
 * @project packet_capturer
 * @datetime 2022/6/14 16:32
 * @description
 */
public final class SinkPacketMetadata {
    private boolean success;
    private long millisTaken;
    private Exception exception;

    public SinkPacketMetadata(){
        this(false,-1L,null);
    }

    public SinkPacketMetadata(boolean success,long millisTaken,Exception exception){
        this.success = success;
        this.millisTaken = millisTaken;
        this.exception = exception;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setMillisTaken(long millisTaken) {
        this.millisTaken = millisTaken;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getMillisTaken() {
        return millisTaken;
    }

    public Exception getException() {
        return exception;
    }

    @Override
    public String toString() {
        return "success=" + success + ",millis taken=" + millisTaken + ",exception=" + (exception == null ? "" : exception.getMessage());
    }
}
