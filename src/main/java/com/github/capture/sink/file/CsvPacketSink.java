package com.github.capture.sink.file;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.model.TcpPacketRecord;
import com.github.capture.sink.Callback;
import com.github.capture.sink.PacketSink;
import com.github.capture.utils.InProgressFileAcquire;
import com.github.capture.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/14 21:30
 * @description
 */
public class CsvPacketSink implements PacketSink {
    private static final Logger LOG = LogManager.getLogger(CsvPacketSink.class);

    private FileWriter fileWriter;
    private final int writeBatchSize;
    private final int fileSwitchMB;

    private String currentWriteFileName;
    private static InProgressFileAcquire inProgressFileAcquire;

    private final List<String> writeBuffer = new ArrayList<>();

    private final CsvLineConverter csvLineConverter;

    public CsvPacketSink(AppConfiguration appConf,CsvLineConverter csvLineConverter){
        this.writeBatchSize = appConf.getInteger("client.packet.write.batch.size",100);
        this.fileSwitchMB = appConf.getInteger("client.packet.output.file.switch.mb",512);

        String outputPath = appConf.getString("client.packet.output.path","./packet");
        String fileNamePrefix = appConf.getString("client.packet.output.filename","tcp_capture");
        inProgressFileAcquire = new InProgressFileAcquire(outputPath,fileNamePrefix);
        this.csvLineConverter = csvLineConverter;
    }

    public interface CsvLineConverter{
        String header();
        String toLine(TcpPacketRecord record);
    }

    @Override
    public SinkType sinkType() {
        return SinkType.CSV_SINK;
    }

    @Override
    public void writeTo(TcpPacketRecord record, Callback callback) throws Exception {
        try{
            String sb = csvLineConverter.toLine(record);
            writeBuffer.add(sb);

            if(writeBuffer.size() >= writeBatchSize){
                for (String s : writeBuffer) {
                    fileWriter.write(s);
                    fileWriter.write("\n");
                }
                fileWriter.flush();
                writeBuffer.clear();
            }

            // 当前写入的文件大小超过阈值,开始切换新文件
            if(Utils.fileSize(this.currentWriteFileName) > fileSwitchMB * 1024L * 1024L){
                fileWriter.flush();
                fileWriter.close();

                File currentFile = new File(currentWriteFileName);
                File destFile = new File(currentWriteFileName.replace(".inprogress",".csv"));
                boolean renameSuccess = currentFile.renameTo(destFile);
                if(renameSuccess){
                    currentWriteFileName = inProgressFileAcquire.generateNewFile();
                    FileOutputStream fos = new FileOutputStream(currentWriteFileName,true);
                    fileWriter = new FileWriter(fos.getFD());
                    String header = csvLineConverter.header();
                    fileWriter.write(header);
                    fileWriter.write("\n");
                    fileWriter.flush();
                    LOG.info("start write new file: " + currentWriteFileName);
                }
            }
        }catch (IOException ioe){
            LOG.error(ioe);
        }
    }

    @Override
    public void gatherAndWriteTo(TcpPacketRecord record) throws Exception {
    }

    @Override
    public void open() {
        this.currentWriteFileName = inProgressFileAcquire.acquire();
        LOG.info("open file:" + currentWriteFileName + " ,init size:" + Utils.fileSize(currentWriteFileName));

        try{
            FileOutputStream fos = new FileOutputStream(currentWriteFileName,true);
            fileWriter = new FileWriter(fos.getFD());

            String header = csvLineConverter.header();
            fileWriter.write(header);
            fileWriter.write("\n");
            fileWriter.flush();
        }catch (IOException ioe){
            ioe.printStackTrace();
        }
    }

    @Override
    public void close() {
        LOG.info("close csv packet sink of: " + currentWriteFileName);
        try{
            for (String s : writeBuffer) {
                fileWriter.write(s);
                fileWriter.write("\n");
            }
            writeBuffer.clear();

            fileWriter.flush();
            fileWriter.close();
            fileWriter = null;
        }catch (IOException ioe){
            ioe.printStackTrace();
        }
    }
}