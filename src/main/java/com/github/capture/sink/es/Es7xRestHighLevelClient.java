package com.github.capture.sink.es;

import com.github.capture.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.*;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_REPLICAS;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_SHARDS;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2021-06-28 15:02
 * @description ElasticSearch 7x rest high level client.
 * @see "https://github.com/elastic/elasticsearch/blob/7.6/client/rest-high-level/src/test/java/org/elasticsearch/client/IndicesClientIT.java"
 */
public class Es7xRestHighLevelClient extends RestHighLevelClient{
    private static final Logger LOG = LogManager.getLogger(Es7xRestHighLevelClient.class);

    public Es7xRestHighLevelClient(RestClientBuilder restClientBuilder){
        super(restClientBuilder);
    }

    public Es7xRestHighLevelClient(RestClient restClient){
        super(restClient, RestClient::close, Collections.emptyList());
    }

    /**
     * create index.
     * */
    public CreateIndexResponse createIndex(String indexName,String aliasName,int numberOfShards,int numberOfReplicas) throws IOException{
        Objects.requireNonNull(indexName,"indexName must not be null");

        CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
        if(!isNullOrEmpty(aliasName)){
            Alias alias = new Alias(aliasName);
            createIndexRequest.alias(alias);
        }

        Settings.Builder builder = Settings.builder();
        builder.put(SETTING_NUMBER_OF_SHARDS,numberOfShards);
        builder.put(SETTING_NUMBER_OF_REPLICAS, numberOfReplicas);
        createIndexRequest.settings(builder);

        /*
        XContentBuilder mappingBuilder = JsonXContent.contentBuilder();
        mappingBuilder.startObject().startObject("properties")
                .startObject("vin")
                .field("type","keyword")
                .field("index",true)
                .endObject()

                .startObject("sendtime")
                .field("type","date")
                .field("format","yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis")
                .endObject()

                .startObject("vehicle_status")
                .field("type","integer")
                .field("index",true)
                .endObject()

                .startObject("speed")
                .field("type","scaled_float")
                .field("scaling_factor",100)
                .field("index",true)
                .endObject()

                .startObject("accu_mile")
                .field("type","double")
                .field("index",true)
                .endObject()

                .startObject("soc")
                .field("type","integer")
                .field("index",true)
                .endObject()

                .startObject("longitude")
                .field("type","double")
                .field("index",true)
                .endObject()

                .startObject("total_current")
                .field("type","float")
                .field("index",true)
                .endObject()

                .startObject("mer_bat_voltage")
                .field("type","scaled_float")
                .field("scaling_factor",100)
                .field("index",true)
                .endObject()

                .startObject("each_temp_probe_detect")
                .field("type","scaled_float")
                .field("scaling_factor",100)
                .field("index",true)
                .endObject()

                .startObject("dm_status")
                .field("type","integer")
                .field("index",true)
                .endObject()

                .endObject().endObject();

        createIndexRequest.mapping(mappingBuilder);
        */

        return this.indices().create(createIndexRequest, RequestOptions.DEFAULT);
    }

    /**
     * create index.
     * */
    public CreateIndexResponse createIndex(String indexName,String aliasName,Settings.Builder builder,XContentBuilder mappingBuilder) throws IOException{
        Objects.requireNonNull(indexName,"indexName must not be null");

        CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
        if(!isNullOrEmpty(aliasName)){
            Alias alias = new Alias(aliasName);
            createIndexRequest.alias(alias);
        }

        if(builder != null){
            createIndexRequest.settings(builder);
        }

        if(mappingBuilder != null){
            createIndexRequest.mapping(mappingBuilder);
        }

        return this.indices().create(createIndexRequest, RequestOptions.DEFAULT);
    }

    /**
     * create index template.
     * */
    public AcknowledgedResponse createIndexTemplate(String indexTemplateName,
                                                    List<String> indexPatterns,
                                                    int order,
                                                    String aliasName,
                                                    Settings.Builder builder,
                                                    XContentBuilder mappingBuilder) throws IOException{
        PutIndexTemplateRequest putIndexTemplateRequest = new PutIndexTemplateRequest(indexTemplateName);
        if(indexPatterns != null){
            putIndexTemplateRequest.patterns(indexPatterns);
        }

        if(!isNullOrEmpty(aliasName)){
            Alias alias = new Alias(aliasName);
            putIndexTemplateRequest.alias(alias);
        }

        if(builder != null){
            putIndexTemplateRequest.settings(builder);
        }

        if(mappingBuilder != null){
            putIndexTemplateRequest.mapping(mappingBuilder);
        }

        putIndexTemplateRequest.order(order);

        return this.indices().putTemplate(putIndexTemplateRequest,RequestOptions.DEFAULT);
    }

    /**
     * delete index template.
     * */
    public AcknowledgedResponse deleteIndexTemplate(String indexTemplateName) throws IOException{
        DeleteIndexTemplateRequest deleteIndexTemplateRequest = new DeleteIndexTemplateRequest(indexTemplateName);
        return this.indices().deleteTemplate(deleteIndexTemplateRequest,RequestOptions.DEFAULT);
    }

    /**
     * delete index.
     * */
    public AcknowledgedResponse deleteIndex(String indexName) throws IOException{
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
        return this.indices().delete(deleteIndexRequest,RequestOptions.DEFAULT);
    }

    /**
     * index exists.
     * */
    public boolean indexExists(String indexName) throws IOException{
        GetIndexRequest getIndexRequest = new GetIndexRequest(indexName);
        return this.indices().exists(getIndexRequest,RequestOptions.DEFAULT);
    }

    /**
     * get index.
     * */
    public GetIndexResponse getIndex(String... indices) throws IOException{
        GetIndexRequest getIndexRequest = new GetIndexRequest(indices);
        return this.indices().get(getIndexRequest,RequestOptions.DEFAULT);
    }

    /**
     * index search
     * */
    public SearchResponse search(SearchRequest searchRequest) throws IOException{
        return this.search(searchRequest,RequestOptions.DEFAULT);
    }

    /**
     * index search search async.
     * */
    public void searchAsync(SearchRequest searchRequest,ActionListener<SearchResponse> actionListener){
        if(actionListener != null){
            this.searchAsync(searchRequest,actionListener);
        }else{
            this.searchAsync(searchRequest, RequestOptions.DEFAULT, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse searchResponse) {
                    long took = searchResponse.getTook().getMillis();
                    int status = searchResponse.status().getStatus();
                    long totalHits = searchResponse.getHits().getTotalHits().value;
                    System.out.println("status:" + status + ",total hits:" + totalHits + ",took:" + took);
                }

                @Override
                public void onFailure(Exception e) {
                    e.printStackTrace();
                }
            });
        }


    }

    /**
     * is null or empty string.
     * */
    private static boolean isNullOrEmpty(String str){
        return str == null || str.trim().isEmpty();
    }

    /**
     * create index request from map.
     * */
    public IndexRequest indexRequestFromMap(String index, String type, String id, Map<String,Object> doc){
        IndexRequest indexRequest = new IndexRequest(index,type,id).source(doc);
        System.out.println("id: " + indexRequest.routing());
        return indexRequest;
    }

    /**
     * create index request from map.
     * */
    public IndexRequest indexRequestFromMap(String index, Map<String,Object> doc){
        IndexRequest indexRequest = new IndexRequest(index).source(doc);
        // System.out.println("id: " + indexRequest.routing());
        return indexRequest;
    }

    /**
     * create bulk index request.
     * */
    public BulkRequest bulkIndexRequest(List<IndexRequest> docs){
        BulkRequest bulkRequest = new BulkRequest();
        for (IndexRequest doc : docs) {
            bulkRequest.add(doc);
        }
        return bulkRequest;
    }

    /**
     * perform bulk index request.
     * */
    public int bulkDocIndex(BulkRequest bulkRequest){
        int retCode = 0;
        try{
            this.bulk(bulkRequest,RequestOptions.DEFAULT);
        }catch (IOException ioe){
            retCode = -1;
            String errorMsg = Utils.stackTrace(ioe);
            LOG.error(errorMsg);
        }
        return retCode;
    }
}