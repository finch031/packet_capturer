package com.github.capture.utils;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/15 9:23
 * @description
 */
public interface IdGenerator {
    int id(int bits);

    long id();
}
