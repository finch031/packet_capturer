package com.github.capture.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.capture.model.TcpPacketRecord;
import org.junit.jupiter.api.Test;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/24 11:19
 * @description
 */
public class JsonSerializationTest {
    private static final JsonSerialization<TcpPacketRecord> jsonSerialization = new JsonSerialization<>(TcpPacketRecord.class,true,false);

    @Test
    public void test01(){
        TcpPacketRecord record = new TcpPacketRecord();
        record.setEtherType((short) 12);
        record.setEthSrcAddress("2");
        record.setIpFragmentOffset((short) 43);
        record.setIpIhl((byte) 1);
        record.setTcpSyn(true);
        record.setEthDstAddress("22");
        record.setTcpWindow(123);
        record.setTcpUrg(false);
        record.setIpDstAddress("xxs");


        try{
            String result = jsonSerialization.toJson(record);
            System.out.println(result);
        }catch (JsonProcessingException jpe){
            jpe.printStackTrace();
        }

    }

}
