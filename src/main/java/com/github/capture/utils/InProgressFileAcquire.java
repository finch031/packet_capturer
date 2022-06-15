package com.github.capture.utils;

import java.io.File;
import java.util.Collection;
import java.util.Stack;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/6/15 11:20
 * @description
 */
public class InProgressFileAcquire {
    private final String inProgressFileDir;
    private final String fileNamePrefix;
    private final Stack<File> inProgressFiles = new Stack<>();

    public InProgressFileAcquire(String inProgressFileDir,String fileNamePrefix){
        this.inProgressFileDir = inProgressFileDir;
        this.fileNamePrefix = fileNamePrefix;

        Collection<File> tempCollection = Utils.listFiles(new File(inProgressFileDir),new String[] {"inprogress"},false);

        for (File inProgressFile : tempCollection) {
            inProgressFiles.push(inProgressFile);
        }
    }

    public String generateNewFile(){
        return inProgressFileDir + "/" + fileNamePrefix + "_" + Utils.now() + "_" + Thread.currentThread().getId() + ".inprogress";
    }

    public String acquire(){
        if(inProgressFiles.isEmpty()){
            return generateNewFile();
        }else{
            return inProgressFiles.pop().getAbsolutePath();
        }
    }

}
