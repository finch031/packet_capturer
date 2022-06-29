package com.github.capture.utils;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/15 8:09
 * @description
 */
public class UtilsTest {

    //@Test
    public void test01(){
        float value = 10.0000F;
        BigDecimal bd = new BigDecimal(value);
        value = bd.setScale(2, RoundingMode.HALF_UP).floatValue();
        System.out.println(value);

        DecimalFormat df = new DecimalFormat("0.00");
        df.setRoundingMode(RoundingMode.HALF_UP);
        System.out.println(df.format(10.0000F));
    }

    @Test
    public void test2(){

        try{
            FileInputStream fis = new FileInputStream("D:\\tmp\\primary.idx");

            int size = fis.available();

            byte[] data = new byte[size];
            fis.read(data);

            ByteBuffer buffer = ByteBuffer.allocate(size);

            buffer.put(data);

            buffer.flip();

            while(buffer.hasRemaining()){
                // int value = buffer.getInt();
                // System.out.println(value);
                System.out.println(buffer.getInt());
            }

        }catch (IOException ioe){
            ioe.printStackTrace();
        }

    }

}
