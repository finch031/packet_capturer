package com.github.capture.sink.es;

import com.github.capture.conf.AppConfiguration;
import com.github.capture.sink.PacketSink;
import com.github.capture.sink.PacketSinkFactory;
import org.elasticsearch.common.settings.Settings;

import java.io.IOException;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2022/7/21 14:34
 * @description
 */
public class EsPacketSinkFactory implements PacketSinkFactory {
    private final Es7xRestHighLevelClient highLevelClient;
    private final String index;

    public EsPacketSinkFactory(AppConfiguration appConf, boolean createIndex){
        Es7xRestClient es7xRestClient = new Es7xRestClient();
        String cluster = appConf.getString("client.es.cluster","");
        String schema = "http";
        String user = appConf.getString("client.es.user","");
        String password = appConf.getString("client.es.password","");
        index = appConf.getString("client.es.write.index","");

        try{
            es7xRestClient.initClient(cluster,schema,user,password,null);
            es7xRestClient.printEsClusterNodes();

            if(createIndex){
                Settings indexSettings = Settings.builder()
                        .put("number_of_replicas","1")
                        .put("index.number_of_shards","5")
                        .build();
                es7xRestClient.createIndex(index,indexSettings,"","");
            }
        }catch (IOException ioe){
            ioe.printStackTrace();
        }

        highLevelClient = new Es7xRestHighLevelClient(es7xRestClient.getRestClient());
    }

    @Override
    public PacketSink getPacketSink(AppConfiguration appConf) {
        return new EsSink(highLevelClient,index);
    }
}