package com.github.capture.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for getting system memory information
 * Note: This check is designed for Linux only.
 */
public class OsMemoryStat {
    // This file is used by Linux. It doesn't exist on Mac for example.
    private static final String MEM_INFO_FILE = "/proc/meminfo";

    private static final Set<String> MEM_KEYS = new HashSet<String>(){
        {
            add("MemFree");
            add("Buffers");
            add("Cached");
            add("SwapFree");
        }
    };

    private static final Set<String> MEM_AVAILABLE_KEYS = new HashSet<String>(){
        {
            add("MemFree");
            add("Active(file)");
            add("Inactive(file)");
            add("SReclaimable");
        }
    };

    /**
     * Includes OS cache and free swap.
     *
     * @return the total free memory size of the OS. 0 if there is an error or the OS doesn't support
     * this memory check.
     */
    public long getOsTotalFreeMemorySize() {
        return getAggregatedFreeMemorySize(MEM_KEYS);
    }

    /**
     * @return the free physical memory size of the OS. 0 if there is an error or the OS doesn't
     * support this memory check.
     */
    public long getOsFreePhysicalMemorySize() {
        return getAggregatedFreeMemorySize(MEM_AVAILABLE_KEYS);
    }

    /**
     * get free memory by keys.
     * */
    private long getAggregatedFreeMemorySize(final Set<String> memKeysToCombine) {
        if (!Files.isRegularFile(Paths.get(MEM_INFO_FILE))) {
            // Mac doesn't support /proc/meminfo for example.
            return 0;
        }

        final List<String> lines;
        // The file /proc/meminfo is assumed to contain only ASCII characters.
        // The assumption is that the file is not too big. So it is simpler to read the whole file
        // into memory.
        try {
            lines = Files.readAllLines(Paths.get(MEM_INFO_FILE), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            final String errMsg = "Failed to open mem info file: " + MEM_INFO_FILE;
            // logger.error(errMsg, e);
            System.err.println(errMsg);
            return 0;
        }
        return getOsTotalFreeMemorySizeFromStrings(lines, memKeysToCombine);
    }

    /**
     * @param lines text lines from the procinfo file
     * @return the total size of free memory in kB. 0 if there is an error.
     */
    private long getOsTotalFreeMemorySizeFromStrings(final List<String> lines, final Set<String> memKeysToCombine) {
        long totalFree = 0;
        int count = 0;

        for (final String line : lines) {
            for (final String keyName : memKeysToCombine) {
                if (line.startsWith(keyName)) {
                    count++;
                    final long size = parseMemoryLine(line);
                    totalFree += size;
                }
            }
        }

        final int length = memKeysToCombine.size();
        if (count != length) {
            final String errMsg = String.format("Expect %d keys in the meminfo file. Got %d. content: %s", length, count, lines);
            // logger.error(errMsg);
            System.out.println(errMsg);
            totalFree = 0;
        }
        return totalFree;
    }

    /**
     * Example file: $ cat /proc/meminfo
     * MemTotal:       65894008 kB
     * MemFree:        59400536 kB
     * Buffers:          409348 kB
     * Cached:          4290236 kB
     * SwapCached:            0 kB
     *
     * this is a real machine meminfo:
     * [windlink@dn53 0820]$ cat /proc/meminfo
     * MemTotal:       131826292 kB
     * MemFree:         1239404 kB
     * MemAvailable:   68001624 kB
     * Buffers:               0 kB
     * Cached:         62013572 kB
     * SwapCached:            0 kB
     * Active:         75221100 kB
     * Inactive:       44379808 kB
     * Active(anon):   58100212 kB
     * Inactive(anon):   318892 kB
     * Active(file):   17120888 kB
     * Inactive(file): 44060916 kB
     * Unevictable:           0 kB
     * Mlocked:               0 kB
     * SwapTotal:             0 kB
     * SwapFree:              0 kB
     * Dirty:            396104 kB
     * Writeback:             0 kB
     * AnonPages:      57587892 kB
     * Mapped:           119460 kB
     * Shmem:            831224 kB
     * Slab:            7374988 kB
     * SReclaimable:    6370204 kB
     * SUnreclaim:      1004784 kB
     * KernelStack:       40240 kB
     * PageTables:       152136 kB
     * NFS_Unstable:          0 kB
     * Bounce:                0 kB
     * WritebackTmp:          0 kB
     * CommitLimit:    65913144 kB
     * Committed_AS:   77997448 kB
     * VmallocTotal:   34359738367 kB
     * VmallocUsed:      352040 kB
     * VmallocChunk:   34359383040 kB
     * HardwareCorrupted:     0 kB
     * AnonHugePages:   6111232 kB
     * CmaTotal:              0 kB
     * CmaFree:               0 kB
     * HugePages_Total:       0
     * HugePages_Free:        0
     * HugePages_Rsvd:        0
     * HugePages_Surp:        0
     * Hugepagesize:       2048 kB
     * DirectMap4k:      337408 kB
     * DirectMap2M:    16439296 kB
     * DirectMap1G:    119537664 kB
     *
     *
     * Make the method package private to make unit testing easier. Otherwise it can be made private.
     *
     * @param line the text for a memory usage statistics we are interested in
     * @return size of the memory. unit kB. 0 if there is an error.
     */
    private long parseMemoryLine(final String line) {
        final int idx1 = line.indexOf(":");
        final int idx2 = line.lastIndexOf("kB");
        final String sizeString = line.substring(idx1 + 1, idx2 - 1).trim();
        try {
            return Long.parseLong(sizeString);
        } catch (final NumberFormatException e) {
            final String err = "Failed to parse the meminfo file. Line: " + line;
            // logger.error(err);
            System.err.println(err);
            return 0;
        }
    }
}
