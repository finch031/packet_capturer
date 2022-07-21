package com.github.capture.sink.es;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.SSLContexts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.*;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.*;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.rest.RestStatus;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static java.util.Collections.unmodifiableList;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_REPLICAS;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_SHARDS;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2021-06-28 15:07
 * @description ElasticSearch 7x rest client.
 */
public class Es7xRestClient {
    private static final Logger LOG = LogManager.getLogger(Es7xRestClient.class);

    private static final String TRUSTSTORE_PATH = "truststore.path";
    private static final String TRUSTSTORE_PASSWORD = "truststore.password";
    private static final String CLIENT_SOCKET_TIMEOUT = "client.socket.timeout";
    private static final String CLIENT_PATH_PREFIX = "client.path.prefix";

    private static List<HttpHost> clusterHosts;

    /**
     * A client for the running ElasticSearch cluster
     */
    private static RestClient client;

    /**
     * RestClient settings.
     * */
    private static Settings clientSettings;

    /**
     * @param cluster system property with a comma delimited list of [host:port]
     *                to which to send REST requests
     * @param schema HttpHost schema: http or https.
     * @param user auth user.
     * @param password auth password.
     * @param settings rest client settings.
     * */
    public void initClient(String cluster,String schema,String user, String password,Settings settings) throws IOException {
        String[] stringUrls = cluster.split(",");
        List<HttpHost> hosts = new ArrayList<>(stringUrls.length);
        for (String stringUrl : stringUrls) {
            int portSeparator = stringUrl.lastIndexOf(':');
            if (portSeparator < 0) {
                throw new IllegalArgumentException("Illegal cluster url [" + stringUrl + "]");
            }
            String host = stringUrl.substring(0, portSeparator);
            int port = Integer.valueOf(stringUrl.substring(portSeparator + 1));
            hosts.add(new HttpHost(host, port,schema));
        }

        clusterHosts = unmodifiableList(hosts);
        LOG.info("initializing REST clients against {}", clusterHosts);

        if(settings == null){
            clientSettings = restClientSettings();
        }else{
            clientSettings = settings;
        }

        CredentialsProvider credentialsProvider;
        if((user == null || user.trim().isEmpty()) && (password == null || password.trim().isEmpty())){
            credentialsProvider = null;
            LOG.warn("no auth user and password!");
        }else{
            credentialsProvider = httpCredentialsProvider(user,password);
        }

        client = buildClient(clientSettings, clusterHosts.toArray(new HttpHost[clusterHosts.size()]),credentialsProvider);
        LOG.info("rest client build success!");
    }

    /**
     * build low level ElasticSearch  rest client.
     * */
    private RestClient buildClient(Settings settings, HttpHost[] hosts,CredentialsProvider credentialsProvider) throws IOException {
        RestClientBuilder builder = RestClient.builder(hosts);
        configureClient(builder, settings,credentialsProvider);
        builder.setStrictDeprecationMode(true);
        return builder.build();
    }

    /**
     * close rest client.
     **/
    public void closeClient() throws IOException {
        try {
            IOUtils.close(client);
            LOG.info("rest client close success!");
        } finally {
            client = null;
            clusterHosts = null;
        }
    }

    private static void configureClient(RestClientBuilder builder, Settings settings,CredentialsProvider credentialsProvider) throws IOException{
        // Header[] defaultHeaders = new Header[]{new BasicHeader("http.max_content_length", "500mb")};

        final String socketTimeoutString = settings.get(CLIENT_SOCKET_TIMEOUT);
        final TimeValue socketTimeout = TimeValue.parseTimeValue(socketTimeoutString == null ? "60s" : socketTimeoutString, CLIENT_SOCKET_TIMEOUT);

        builder.setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
            @Override
            public RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder builder) {
                // 连接超时(默认1s)
                builder.setConnectTimeout(10 * 1000);
                // 套接字超时(默认30s)
                builder.setSocketTimeout(Math.toIntExact(socketTimeout.getMillis()));
                builder.setConnectionRequestTimeout(30 * 1000);
                return builder;
            }
        });

        builder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
            @Override
            public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpAsyncClientBuilder) {
                httpAsyncClientBuilder
                        .setDefaultIOReactorConfig(
                                IOReactorConfig.custom()
                                        .setIoThreadCount(30)
                                        .setConnectTimeout(10 * 1000)
                                        .setSoTimeout(10 * 1000)
                                        .build());
                //.setDefaultCredentialsProvider(credentialsProvider);

                if(credentialsProvider != null){
                    httpAsyncClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                }

                return httpAsyncClientBuilder;
            }
        });

        builder.setFailureListener(new RestClient.FailureListener(){
            public void onFailure(HttpHost host) {
            }
        });

        String keystorePath = settings.get(TRUSTSTORE_PATH);
        if (keystorePath != null) {
            final String keystorePass = settings.get(TRUSTSTORE_PASSWORD);
            if (keystorePass == null) {
                throw new IllegalStateException(TRUSTSTORE_PATH + " is provided but not " + TRUSTSTORE_PASSWORD);
            }
            Path path = PathUtils.get(keystorePath);
            if (!Files.exists(path)) {
                throw new IllegalStateException(TRUSTSTORE_PATH + " is set but points to a non-existing file");
            }
            try {
                final String keyStoreType = keystorePath.endsWith(".p12") ? "PKCS12" : "jks";
                KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                try (InputStream is = Files.newInputStream(path)) {
                    keyStore.load(is, keystorePass.toCharArray());
                }
                SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(keyStore, null).build();
                SSLIOSessionStrategy sessionStrategy = new SSLIOSessionStrategy(sslcontext);
                builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setSSLStrategy(sessionStrategy));
            } catch (KeyStoreException | NoSuchAlgorithmException | KeyManagementException | CertificateException e) {
                throw new RuntimeException("Error setting up ssl", e);
            }
        }

        Map<String, String> headers = ThreadContext.buildDefaultHeaders(settings);
        Header[] defaultHeaders = new Header[headers.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            defaultHeaders[i++] = new BasicHeader(entry.getKey(), entry.getValue());
        }
        builder.setDefaultHeaders(defaultHeaders);

        if (settings.hasValue(CLIENT_PATH_PREFIX)) {
            builder.setPathPrefix(settings.get(CLIENT_PATH_PREFIX));
        }
    }

    /**
     * 用户认证对象
     * */
    private static CredentialsProvider httpCredentialsProvider(String userName, String password){
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        UsernamePasswordCredentials usernamePasswordCredentials = new UsernamePasswordCredentials(userName,password);
        credentialsProvider.setCredentials(AuthScope.ANY,usernamePasswordCredentials);
        return credentialsProvider;
    }

    /**
     * Used to obtain settings for the REST client that is used to send REST requests.
     */
    private Settings restClientSettings() {
        Settings.Builder builder = Settings.builder();
        if (System.getProperty("es.rest.client_path_prefix") != null) {
            builder.put(CLIENT_PATH_PREFIX, System.getProperty("es.rest.client_path_prefix"));
        }
        return builder.build();
    }

    /**
     *  RestClient.
     * */
    public RestClient getRestClient(){
        return client;
    }

    /**
     *  RestClient settings.
     * */
    public Settings getClientSettings(){
        return clientSettings;
    }

    /**
     * Convert the entity from a {@link Response} into a map of maps.
     */
    public static Map<String, Object> entityAsMap(Response response) throws IOException {
        XContentType xContentType = XContentType.fromMediaTypeOrFormat(response.getEntity().getContentType().getValue());
        // EMPTY and THROW are fine here because `.map` doesn't use named x content or deprecation
        try (XContentParser parser = xContentType.xContent().createParser(
                NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                response.getEntity().getContent())) {
            return parser.map();
        }
    }

    /**
     * perform get request.
     * */
    public Response performGetRequest(String endPoint, Map<String,String> params){
        Response response = null;
        try{
            Request request = new Request("GET",endPoint);
            if(params != null){
                request.addParameters(params);
            }
            response = client.performRequest(request);
        }catch (IOException ioe){
            ioe.printStackTrace();
        }
        return response;
    }

    /**
     * perform async get request.
     * */
    public void performAsyncGetRequest(String endPoint,
                                       ResponseListener responseListener,
                                       Map<String,String> params){
        Request request = new Request("GET",endPoint);
        if(params != null){
            request.addParameters(params);
        }
        client.performRequestAsync(request,responseListener);
    }


    /**
     * perform json post request.
     * */
    public Response performJsonPostRequest(String jsonString,String endPoint,Map<String,String> params){
        Request request = new Request("POST",endPoint);
        if(params != null){
            request.addParameters(params);
        }
        request.setJsonEntity(jsonString);
        Response response = null;
        try{
            response = client.performRequest(request);
        }catch (IOException ioe){
            ioe.printStackTrace();
        }

        return response;
    }

    /**
     * perform async json post request.
     * */
    public void performAsyncJsonPostRequest(String jsonString,
                                            String endPoint,
                                            ResponseListener responseListener,
                                            Map<String,String> params){
        Request request = new Request("POST",endPoint);
        if(params != null){
            request.addParameters(params);
        }
        request.setJsonEntity(jsonString);
        client.performRequestAsync(request,responseListener);
    }

    /**
     * perform async json batch post request.
     * */
    public void performAsyncBatchJsonPostRequest(String[] documents,
                                                 String endPoint,
                                                 Map<String,String> params){
        final CountDownLatch latch = new CountDownLatch(documents.length);
        for (String document : documents) {
            performAsyncJsonPostRequest(document,endPoint,new ResponseListener() {
                @Override
                public void onSuccess(Response response) {
                    latch.countDown();
                }

                @Override
                public void onFailure(Exception exception) {
                    String errorMsg = stackTrace(exception);
                    LOG.error(errorMsg);
                    latch.countDown();
                }
            },params);
        }

        try {
            latch.await();
        }catch (InterruptedException ie){
            ie.printStackTrace();
        }
    }

    /**
     * empty response listener.
     * */
    public static ResponseListener emptyResponseListener(){
        return new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
            }

            @Override
            public void onFailure(Exception exception) {
            }
        };
    }

    /**
     *  default response listener.
     *
     * */
    public static ResponseListener defaultResponseListener(){
        return new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                try{
                    InputStream inputStream = response.getEntity().getContent();
                    List<String> lines = readAllLines(inputStream, Charset.forName("utf-8"));
                    for (String line : lines) {
                        System.out.println(line);
                    }
                }catch (IOException ioe){
                    ioe.printStackTrace();
                }
            }
            @Override
            public void onFailure(Exception exception) {
                exception.printStackTrace();
            }
        };
    }

    /**
     * alias exists.
     * */
    public boolean aliasExists(String alias) throws IOException {
        Request request = new Request("HEAD", "/_alias/" + alias);
        Response response = client.performRequest(request);
        return RestStatus.OK.getStatus() == response.getStatusLine().getStatusCode();
    }

    /**
     * alias exists.
     * */
    public boolean aliasExists(String index, String alias) throws IOException {
        Request request = new Request("HEAD", "/" + index + "/_alias/" + alias);
        Response response = client.performRequest(request);
        return RestStatus.OK.getStatus() == response.getStatusLine().getStatusCode();
    }

    /**
     * check index exists.
     * */
    public boolean indexExists(String index){
        Request request = new Request("HEAD", "/" + index);
        Response response = null;
        try{
            response = client.performRequest(request);
        }catch (IOException ioe){
            ioe.printStackTrace();
        }

        if(response != null){
            return RestStatus.OK.getStatus() == response.getStatusLine().getStatusCode();
        }else{
            return false;
        }
    }

    /**
     * open index.
     * */
    public void openIndex(String index){
        Request request = new Request("POST", "/" + index + "/_open");
        Response response = null;
        try{
            response = client.performRequest(request);
        }catch (IOException ioe){
            ioe.printStackTrace();
        }

        if(response != null){
            String responseMsg = response.getStatusLine().toString();
            System.out.println(responseMsg);
            LOG.info(responseMsg);
        }
    }

    /**
     * close index.
     * */
    public void closeIndex(String index){
        Request request = new Request("POST", "/" + index + "/_close");
        Response response = null;
        try{
            response = client.performRequest(request);
        }catch (IOException ioe){
            ioe.printStackTrace();
        }

        if(response != null){
            String responseMsg = response.getStatusLine().toString();
            System.out.println(responseMsg);
            LOG.info(responseMsg);
        }
    }

    /**
     *  get index settings as map.
     * */
    public Map<String, Object> getIndexSettingsAsMap(String index) throws IOException {
        Request request = new Request("GET", "/" + index + "/_settings");
        request.addParameter("flat_settings", "true");
        Response response = client.performRequest(request);
        try (InputStream is = response.getEntity().getContent()) {
            return XContentHelper.convertToMap(XContentType.JSON.xContent(), is, true);
        }
    }

    /**
     *  get index settings as json.
     * */
    public String getIndexSettingsAsJson(String index) throws IOException {
        Request request = new Request("GET", "/" + index + "/_settings");
        request.addParameter("flat_settings", "true");
        Response response = client.performRequest(request);
        JSONObject jsonObject = readInputStreamAsJson(response.getEntity().getContent());
        return jsonObject.toJSONString();
    }

    /**
     * update index settings.
     * */
    private void updateIndexSettings(String index, Settings settings) throws IOException {
        Request request = new Request("PUT", "/" + index + "/_settings");
        request.setJsonEntity(Strings.toString(settings));
        client.performRequest(request);
    }

    /**
     * update index settings.
     * Can't update non dynamic settings
     * */
    public void updateIndexSettings(String index, Settings.Builder settings) throws IOException {
        updateIndexSettings(index, settings.build());
    }

    /**
     * delete index.
     * */
    public void deleteIndex(String index) throws IOException {
        Request request = new Request("DELETE", "/" + index);
        client.performRequest(request);
    }

    /**
     * create index.
     * */
    public void createIndex(String index,String alias,int numberOfShards,int numberOfReplicas,String mapping) throws IOException{
        Settings.Builder builder = Settings.builder();
        builder.put(SETTING_NUMBER_OF_SHARDS,numberOfShards);
        builder.put(SETTING_NUMBER_OF_REPLICAS, numberOfReplicas);

        createIndex(index,builder.build(),mapping,alias);
    }

    /**
     * create index.
     *
     *   "aliases": {
     *     "alias_1": {},
     *     "alias_2": {
     *       "filter": {
     *         "term": { "user.id": "kimchy" }
     *       },
     *       "routing": "shard-1"
     *     }
     *   }
     *
     *   "mappings": {
     *     "properties": {
     *       "msgid":{"type":"keyword"},
     *       "tm_c":{"type":"date"},
     *       "bat_s":{"type":"short"},
     *       "sp":{"type":"scaled_float","scaling_factor": 10},
     *       "lng":{"type":"double"},
     *       "lat":{"type":"double"}
     *     }
     *   }
     *
     * */
    public void createIndex(String index,Settings settings,String mapping,String aliases) throws IOException{
        Request request = new Request("PUT", "/" + index);
        String entity = "{\"settings\": " + Strings.toString(settings);
        if (mapping != null && !mapping.trim().isEmpty()) {
            entity += ",\"mappings\" : {" + mapping + "}";
        }
        if (aliases != null && !aliases.trim().isEmpty()) {
            entity += ",\"aliases\": {" + aliases + "}";
        }
        entity += "}";

        System.out.println(entity);
        request.setJsonEntity(entity);
        Response response = client.performRequest(request);
        JSONObject jsonObject = readInputStreamAsJson(response.getEntity().getContent());
        System.out.println(jsonObject.toJSONString());
    }

    /**
     * index flush.
     * */
    public void flush(String index, boolean force) throws IOException {
        LOG.info("flushing index {} force={}", index, force);
        Request flushRequest = new Request("POST", "/" + index + "/_flush");
        flushRequest.addParameter("force", Boolean.toString(force));
        flushRequest.addParameter("wait_if_ongoing", "true");
        Response response = client.performRequest(flushRequest);
        System.out.println(response.getStatusLine().toString());
    }

    /**
     * print ElasticSearch Cluster Node status.
     * */
    public void printEsClusterNodes() throws IOException{
        Response response = performGetRequest("_nodes",null);
        JSONObject jsonObject = readInputStreamAsJson(response.getEntity().getContent());
        System.out.println(jsonObject.toJSONString());

        /*
        Map<String,Object> responseMap = entityAsMap(response);
        for (Map.Entry<String, Object> entry : responseMap.entrySet()) {
            String key = entry.getKey();
            if("nodes".equals(key)){
                String value = entry.getValue().toString();
                System.out.println(value);
                // parse error: not a valid json.
                // JSONObject jsonObject = JSON.parseObject(value);
            }
        }
        */
    }

    /**
     * print ElasticSearch _cat
     * */
    public void printEsCats(String catsEndPoint) throws IOException{
        String endPoint = "_cat";
        if(catsEndPoint != null && !catsEndPoint.trim().isEmpty()){
            endPoint = catsEndPoint;
        }
        Response response = performGetRequest(endPoint,null);
        String text = readInputStreamAsString(response.getEntity().getContent());
        System.out.println(text);
    }

    /**
     * read input stream as a json string
     */
    private static JSONObject readInputStreamAsJson(InputStream inputStream) throws IOException {
        String jsonStr = readInputStreamAsString(inputStream);
        JSONObject jsonObject = new JSONObject();
        if(!jsonStr.isEmpty()){
            jsonObject = JSON.parseObject(jsonStr);
        }
        return jsonObject;
    }

    /**
     * read input stream as a string
     */
    public static String readInputStreamAsString(InputStream inputStream) throws IOException {
        int totalBytes = inputStream.available();
        String text = "";
        if(totalBytes > 0){
            byte[] buff = new byte[totalBytes];
            // read once.
            int realReadBytes = inputStream.read(buff);
            if(realReadBytes != -1){
                text = new String(buff);
            }
        }
        return text;
    }

    /**
     * read all lines from input stream.
     * */
    private static List<String> readAllLines(InputStream inputStream, Charset charset) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset));
        try {
            List<String> result = new ArrayList<>();
            for (;;) {
                String line = reader.readLine();
                if (line == null){
                    break;
                }
                result.add(line);
            }
            return result;
        }
        finally {
            reader.close();
        }
    }

    /**
     * Get the stack trace from an exception as a string
     */
    private static String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
