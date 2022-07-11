package com.github.capture.utils;

import java.io.File;
import java.io.FileFilter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public final class Utils {
    public static final boolean IS_LINUX_OS = System.getProperty("os.name") != null && System.getProperty("os.name").startsWith("Linux");

    // prints up to 2 decimal digits. used for human readable printing
    private static final DecimalFormat TWO_DIGIT_FORMAT = new DecimalFormat("0.##");
    private static final String[] BYTE_SCALE_SUFFIXES = new String[] {"B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"};

    /**
     * Size of long in bytes
     */
    public static final int SIZEOF_LONG = Long.SIZE / Byte.SIZE;

    private static final int DB_CONN_TRY_TIMES = 3;


    private Utils(){
        // no instance.
    }

    /**
     * Write an unsigned integer in little-endian format to a byte array
     * at a given offset.
     *
     * @param buffer The byte array to write to
     * @param offset The position in buffer to write to
     * @param value The value to write
     */
    public static void writeUnsignedIntLE(byte[] buffer, int offset, int value) {
        // 0000 0000 0000 0000 0000 0000 1110 1001

        // 无符号整型强转为byte,结果就是只保留低字节的8位，也就是4字节的value的最低一个字节
        buffer[offset] = (byte) value;

        // 无符号整型向右移动8位，结果就是原先的整型低位第2个字节移动到低位第一个字节位置
        // 再进行强转，最终结果就是保留原始整型第二个字节
        buffer[offset + 1] = (byte) (value >>> 8);
        buffer[offset + 2] = (byte) (value >>> 16);
        buffer[offset + 3]   = (byte) (value >>> 24);
    }

    /**
     * Read an unsigned integer stored in little-endian format from a byte array
     * at a given offset.
     *
     * @param buffer The byte array to read from
     * @param offset The position in buffer to read from
     * @return The integer read (MUST BE TREATED WITH SPECIAL CARE TO AVOID SIGNEDNESS)
     */
    public static int readUnsignedIntLE(byte[] buffer, int offset) {
        return (buffer[offset] << 0 & 0xff)                 // 读第一个字节
                | ((buffer[offset + 1] & 0xff) << 8)        // 读第二个字节
                | ((buffer[offset + 2] & 0xff) << 16)       // 读第三个字节
                | ((buffer[offset + 3] & 0xff) << 24);      // 读第四个字节
    }

    public static void writeLong(byte[] buffer, int offset, long value){
        for(int i = offset + 7; i > offset; i--) {
            buffer[i] = (byte) value;
            value >>>= 8;
        }
        buffer[offset] = (byte) value;
    }

    public static long readLong(byte[] buffer,int offset){
        long l = 0;
        for(int i = offset; i < offset + SIZEOF_LONG; i++) {
            l <<= 8;
            l ^= buffer[i] & 0xFF;
        }
        return l;
    }

    public static String now(){
        LocalDateTime now = LocalDateTime.now();
        return now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    /**
     * Finds files within a given directory (and optionally its subdirectories)
     * which match an array of extensions.
     * @param directory  the directory to search in
     * @param extensions an array of extensions, ex. {"java","xml"}. If this
     *                   parameter is {@code null}, all files are returned.
     * @param recursive  if true all subdirectories are searched as well
     * @return an collection of java.io.File with the matching files
     */
    public static Collection<File> listFiles(File directory, String[] extensions, boolean recursive) {
        IOFileFilter filter;
        String[] suffixes = toSuffixes(extensions);
        filter = new IOFileFilter() {
            @Override
            public boolean accept(File file) {
                String name = file.getName();
                for (String suffix : suffixes) {
                    if (name.toLowerCase().endsWith(suffix)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean accept(File dir, String name) {
                for (String suffix : suffixes) {
                    if (name.toLowerCase().endsWith(suffix.toLowerCase())) {
                        return true;
                    }
                }
                return false;
            }
        };

        Collection<File> files = new java.util.LinkedList<>();
        innerListFiles(files,directory,filter,recursive);
        return files;
    }

    /**
     * Finds files within a given directory (and optionally its
     * subdirectories). All files found are filtered by an IOFileFilter.
     *
     * @param files the collection of files found.
     * @param directory the directory to search in.
     * @param filter the filter to apply to files and directories.
     * @param includeSubDirectories indicates if will include the subdirectories themselves
     */
    private static void innerListFiles(Collection<File> files, File directory, IOFileFilter filter, boolean includeSubDirectories) {
        File[] found = directory.listFiles((FileFilter) filter);

        // File[] found = directory.listFiles();

        if (found != null) {
            for (File file : found) {
                if (file.isDirectory()) {
                    if (includeSubDirectories) {
                        files.add(file);
                    }
                    innerListFiles(files, file, filter, includeSubDirectories);
                } else {
                    files.add(file);
                }
            }
        }
    }

    /**
     * Converts an array of file extensions to suffixes for use
     * with IOFileFilters.
     *
     * @param extensions  an array of extensions. Format: {"java", "xml"}
     * @return an array of suffixes. Format: {".java", ".xml"}
     */
    private static String[] toSuffixes(String[] extensions) {
        String[] suffixes = new String[extensions.length];
        for (int i = 0; i < extensions.length; i++) {
            suffixes[i] = "." + extensions[i];
        }
        return suffixes;
    }

    public static void sleepQuietly(int timeout, TimeUnit timeUnit){
        try{
            timeUnit.sleep(timeout);
        }catch (InterruptedException ie){
            // ignore.
        }
    }

    public static String timestampToDateTime(long ts){
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        return localDateTime.format(formatter);
    }

    public static float numericFormat(float value, int newScale){
        BigDecimal bd = new BigDecimal(value);
        return bd.setScale(newScale, RoundingMode.HALF_UP).floatValue();
    }

    public static <T> T checkNotNull(T reference) {
        if (reference == null) {
            throw new NullPointerException();
        } else {
            return reference;
        }
    }

    public static void checkArgument(boolean expression, Object errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
    }

    public static long fileSize(String filePath){
        return new File(filePath).length();
    }

    /**
     * formats a byte number as a human readable String ("3.2 MB")
     * @param bytes some size in bytes
     */
    public static String formatBytes(long bytes) {
        if (bytes < 0) {
            return String.valueOf(bytes);
        }
        double asDouble = (double) bytes;
        int ordinal = (int) Math.floor(Math.log(asDouble) / Math.log(1024.0));
        double scale = Math.pow(1024.0, ordinal);
        double scaled = asDouble / scale;
        String formatted = TWO_DIGIT_FORMAT.format(scaled);
        try {
            return formatted + " " + BYTE_SCALE_SUFFIXES[ordinal];
        } catch (IndexOutOfBoundsException e) {
            //huge number?
            return String.valueOf(asDouble);
        }
    }

    /**
     * get db connection.
     * @param driver driver.
     * @param url url.
     * @param user user.
     * @param password password.
     * */
    public static Connection getConnection(String driver, String url, String user, String password){
        Connection conn = null;
        Random random = new Random();

        try {
            Class.forName(driver);
        } catch (Exception ex) {
            String errorMsg = stackTrace(ex);
            System.err.println(errorMsg);
            throw new IllegalStateException("driver missing!");
        }

        boolean got = false;
        int times = 0;
        while (!got && times < DB_CONN_TRY_TIMES) {
            times++;
            try {
                conn = DriverManager.getConnection(url, user, password);
                got = true;
            } catch (Exception ex) {
                String errorMsg = stackTrace(ex);
                System.err.println(errorMsg);
                System.out.println("db connect, tried times:" + times);

                try {
                    int rt = random.nextInt(10);
                    Thread.sleep(rt * 1000);
                } catch (InterruptedException ie) {
                    // ignore.
                }
            }
        }

        if (null == conn) {
            throw new IllegalStateException("Can not connect to the data source.");
        }

        return conn;
    }

    /**
     * Get the stack trace from an exception as a string
     */
    public static String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * close db connection resources.
     * @param conn db connection.
     * @param statement PreparedStatement
     * */
    public static void closeResources(Connection conn, PreparedStatement statement) {
        try {
            if (statement != null && !statement.isClosed()) {
                statement.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




}
