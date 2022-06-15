package com.github.capture.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/15 8:09
 * @description
 */
public class UtilsTest {

    @Test
    public void test01(){
        float value = 10.0000F;
        BigDecimal bd = new BigDecimal(value);
        value = bd.setScale(2, RoundingMode.HALF_UP).floatValue();
        System.out.println(value);

        DecimalFormat df = new DecimalFormat("0.00");
        df.setRoundingMode(RoundingMode.HALF_UP);
        System.out.println(df.format(10.0000F));

    }

}
