package com.github.capture.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022-03-30 09:59
 * @description
 */
public class OsResourceMonitorTask implements Runnable{
    private static final Logger LOG = LogManager.getLogger(OsResourceMonitorTask.class);

    private final HeapMemoryMonitor jvmHeapMemoryMonitor;
    private final OsCpuStat osCpuStat;
    private final OsMemoryStat osMemoryStat;
    // private final SysInfoLinux sysInfoLinux;
    private final int intervalSeconds;

    private boolean running;

    public OsResourceMonitorTask(int intervalSeconds){
        this.jvmHeapMemoryMonitor = new HeapMemoryMonitor(0.6d);
        this.jvmHeapMemoryMonitor.registerListener(new HeapMemoryMonitor.Listener() {
            @Override
            public void memoryUsageAboveThreshold(long usedMemory, long maxMemory) {
                LOG.warn("jvm heap memory used: " + Utils.formatBytes(usedMemory) + " max memory:" + Utils.formatBytes(maxMemory));
            }
        });

        this.osCpuStat = new OsCpuStat(30);
        this.osMemoryStat = new OsMemoryStat();
        // this.sysInfoLinux = new SysInfoLinux();
        this.intervalSeconds = intervalSeconds;

        this.running = true;
    }

    @Override
    public void run() {
        if(!Utils.IS_LINUX_OS){
            LOG.info("UnSupport OS!");
            return;
        }

        double osCpuLoad;
        long osFreePhysicalMemorySize;
        long osTotalFreeMemorySize;
        // long osNetworkReadSize;
        // long osNetworkWriteSize;
        // long osStorageReadSize;
        // long osStorageWriteSize;

        long jvmHeapUsed;
        long jvmHeapCommitted;
        long jvmHeapMax;

        while(running){
            osCpuLoad = osCpuStat.getCpuLoad();
            // KB.
            osFreePhysicalMemorySize = osMemoryStat.getOsFreePhysicalMemorySize();
            osTotalFreeMemorySize = osMemoryStat.getOsTotalFreeMemorySize();

            // osNetworkReadSize = sysInfoLinux.getNetworkBytesRead();
            // osNetworkWriteSize = sysInfoLinux.getNetworkBytesWritten();
            // osStorageReadSize = sysInfoLinux.getStorageBytesRead();
            // osStorageWriteSize = sysInfoLinux.getStorageBytesWritten();

            jvmHeapMemoryMonitor.start();
            MemoryUsage memoryUsage = jvmHeapMemoryMonitor.getTenuredGenMemoryUsage();

            LOG.info("os cpu load:" + osCpuLoad);
            LOG.info("os free physical memory:" + Utils.formatBytes(osFreePhysicalMemorySize * 1024));
            LOG.info("os total free memory:" + Utils.formatBytes(osTotalFreeMemorySize));
            // LOG.info("os network read:" + Utils.formatBytes(osNetworkReadSize));
            // LOG.info("os network write:" + Utils.formatBytes(osNetworkWriteSize));
            // LOG.info("os storage read:" + Utils.formatBytes(osStorageReadSize));
            // LOG.info("os storage write:" + Utils.formatBytes(osStorageWriteSize));

            jvmHeapUsed = memoryUsage.getUsed();
            jvmHeapCommitted = memoryUsage.getCommitted();
            jvmHeapMax = memoryUsage.getMax();

            LOG.info("jvm heap memory usage: used=" + Utils.formatBytes(jvmHeapUsed) +
                    " committed=" + Utils.formatBytes(jvmHeapCommitted) +
                    " max=" + Utils.formatBytes(jvmHeapMax));

            Utils.sleepQuietly(this.intervalSeconds, TimeUnit.SECONDS);
        }
    }

    public void stop(){
        this.running = false;
    }
}
