package com.github.capture.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Utility class for getting CPU usage (in percentage)
 *
 * CPU information is obtained from /proc/stat, so only Linux systems will support this class.
 * Calculation procedure is taken from:
 * https://github.com/Leo-G/DevopsWiki/wiki/How-Linux-CPU-Usage-Time-and-Percentage-is-calculated
 *
 * Assumes frequent calls at regular intervals to {@link #getCpuLoad() getCpuLoad}. The length of
 * time over which cpu load is calculated can be adjusted with parameter
 * {@code numCpuStatsToCollect} in the class constructor and how often {@link #getCpuLoad()
 * getCpuLoad} is called.
 * Example: if {@link #getCpuLoad() getCpuLoad} is called every second and {@code
 * numCpuStatsToCollect} is set to 60 then each call to {@link #getCpuLoad() getCpuLoad} returns
 * the cpu load over the last minute.
 */
public class OsCpuStat {
    private static final String CPU_STAT_FILE = "/proc/stat";
    private final Deque<CpuStats> collectedCpuStats;

    public static class CpuStats {
        private final long sysUpTime;
        private final long timeCpuIdle;

        public CpuStats(final long sysUpTime, final long timeCpuIdle) {
            this.sysUpTime = sysUpTime;
            this.timeCpuIdle = timeCpuIdle;
        }

        public long getSysUpTime() {
            return this.sysUpTime;
        }

        public long getTimeCpuIdle() {
            return this.timeCpuIdle;
        }
    }

    public OsCpuStat(int numCpuStatsToCollect){
        if (numCpuStatsToCollect <= 0) {
            numCpuStatsToCollect = 1;
        }

        this.collectedCpuStats = new ArrayDeque<>(numCpuStatsToCollect);

        final CpuStats cpuStats = getCpuStats();
        if (cpuStats != null) {
            for (int i = 0; i < numCpuStatsToCollect; i++) {
                this.collectedCpuStats.push(cpuStats);
            }
        }
    }

    /**
     * Collects a new cpu stat data point and calculates cpu load with it and the oldest one
     * collected which is then deleted.
     *
     * @return percentage of CPU usage. -1 if there are no cpu stats.
     */
    public double getCpuLoad() {
        if (this.collectedCpuStats.isEmpty()) {
            return -1;
        }

        final CpuStats newestCpuStats = getCpuStats();
        if (newestCpuStats == null) {
            return -1;
        }

        final CpuStats oldestCpuStats = this.collectedCpuStats.pollLast();
        if(oldestCpuStats == null){
            return -1;
        }
        this.collectedCpuStats.push(newestCpuStats);

        return calcCpuLoad(oldestCpuStats, newestCpuStats);
    }

    private double calcCpuLoad(final CpuStats startCpuStats, final CpuStats endCpuStats) {
        final long startSysUpTime = startCpuStats.getSysUpTime();
        final long startTimeCpuIdle = startCpuStats.getTimeCpuIdle();
        final long endSysUpTime = endCpuStats.getSysUpTime();
        final long endTimeCpuIdle = endCpuStats.getTimeCpuIdle();

        if (endSysUpTime == startSysUpTime) {
            // logger.error("Failed to calculate cpu load: division by zero");
            System.err.println("Failed to calculate cpu load: division by zero");
            return -1.0;
        }

        final double percentageCpuIdle = (100.0 * (endTimeCpuIdle - startTimeCpuIdle)) / (endSysUpTime - startSysUpTime);
        return 100.0 - percentageCpuIdle;
    }

    private CpuStats getCpuStats() {
        if (!Files.isRegularFile(Paths.get(CPU_STAT_FILE))) {
            // Mac doesn't use proc pseudo files for example.
            return null;
        }

        final String cpuLine = getCpuLineFromStatFile();
        if (cpuLine == null) {
            return null;
        }

        return getCpuStatsFromLine(cpuLine);
    }

    private String getCpuLineFromStatFile() {
        BufferedReader br = null;
        try {
            br = Files.newBufferedReader(Paths.get(CPU_STAT_FILE), StandardCharsets.UTF_8);
            String line;
            while ((line = br.readLine()) != null) {
                // looking for a line starting with "cpu<space>" which aggregates the values in all of
                // the other "cpuN" lines.
                if (line.startsWith("cpu ")) {
                    return line;
                }
            }
        } catch (final IOException e) {
            final String errMsg = "Failed to read cpu stat file: " + CPU_STAT_FILE;
            // logger.error(errMsg, e);
            System.err.println(errMsg);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (final IOException e) {
                    final String errMsg = "Failed to close cpu stat file: " + CPU_STAT_FILE;
                    // logger.error(errMsg, e);
                    System.err.println(errMsg);
                }
            }
        }
        return null;
    }

    /**
     * Parses cpu usage information from /proc/stat file.
     * Example of line expected with the meanings of the values below:
     * cpu  4705  356  584    3699   23    23     0       0     0          0
     * ---- user nice system idle iowait  irq  softirq steal guest guest_nice
     * this is a real machine cpu stat:
     * cpu  2995604475 6591 264771114 12946668946 38934579 0 11551473 149170 0 0
     * cpu0 87150410 361 7997375 411096480 1379774 0 94687 4543 0 0
     * cpu1 117787652 488 11041222 377344050 1544120 0 85410 5217 0 0
     * cpu2 121013786 142 10500167 374967523 1478977 0 76052 4977 0 0
     * cpu3 113146861 178 9845443 383655912 1397825 0 72958 4673 0 0
     * cpu4 112140017 229 9350418 385381655 1318901 0 68752 4414 0 0
     * cpu5 105596130 185 8937297 392398389 1298305 0 66928 4368 0 0
     * cpu6 101146841 309 8674537 397169313 1280173 0 66000 4290 0 0
     * cpu7 101016458 163 8399649 397686120 1219653 0 64137 4212 0 0
     * cpu8 98334759 289 8225314 400576079 1195443 0 62862 4199 0 0
     * cpu9 95916059 122 8096483 403143025 1188643 0 62487 4110 0 0
     * ...
     *
     * Method visible within the package for testing purposes.
     *
     * @param line the text containing cpu usage statistics
     * @return CpuStats object. null if there is an error.
     */
    private CpuStats getCpuStatsFromLine(final String line) {
        try {
            final String[] cpuInfo = line.split("\\s+");
            final long user = Long.parseLong(cpuInfo[1]);
            final long nice = Long.parseLong(cpuInfo[2]);
            final long system = Long.parseLong(cpuInfo[3]);
            final long idle = Long.parseLong(cpuInfo[4]);
            final long iowait = Long.parseLong(cpuInfo[5]);
            final long irq = Long.parseLong(cpuInfo[6]);
            final long softirq = Long.parseLong(cpuInfo[7]);
            final long steal = Long.parseLong(cpuInfo[8]);

            // guest and guest_nice are counted on user and nice respectively, so don't add them
            final long totalCpuTime = user + nice + system + idle + iowait + irq + softirq + steal;

            final long idleCpuTime = idle + iowait;

            return new CpuStats(totalCpuTime, idleCpuTime);
        } catch (final NumberFormatException | ArrayIndexOutOfBoundsException e) {
            final String errMsg = "Failed to parse cpu stats from line: " + line;
            // logger.error(errMsg, e);
            System.out.println(errMsg);
        }
        return null;
    }
}