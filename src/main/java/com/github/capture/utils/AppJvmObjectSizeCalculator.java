package com.github.capture.utils;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022-03-31 16:44
 * @description
 */
public class AppJvmObjectSizeCalculator {
    private static final Logger LOG = LogManager.getLogger(AppJvmObjectSizeCalculator.class);

    private static final ObjectSizeCalculator objectSizeCalculator = new ObjectSizeCalculator(ObjectSizeCalculator.CurrentLayout.SPEC);

    private long totalUsedJvmObjectBytes;

    public AppJvmObjectSizeCalculator(){
        this.totalUsedJvmObjectBytes = 0;
    }

    public AppJvmObjectSizeCalculator(long totalUsedJvmObjectBytes){
        this.totalUsedJvmObjectBytes = totalUsedJvmObjectBytes;
    }

    public void calculate(Object object){
        long objectSize = objectSizeCalculator.calculateObjectSize(object);
        String objClassName = object.getClass().getCanonicalName();
        this.totalUsedJvmObjectBytes += objectSize;

        LOG.info("Object: " + objClassName + " used jvm bytes:" +
                Utils.formatBytes(objectSize) + ",total jvm bytes:" +
                Utils.formatBytes(totalUsedJvmObjectBytes));
    }
}
