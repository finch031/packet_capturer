package com.github.capture.utils;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.SimpleTimeZone;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2021-09-09 15:05
 * @description this code is copy from org.apache.hadoop.util.JsonSerialization
 */
public class JsonSerialization<T> {
    private static final Logger LOG = LogManager.getLogger(JsonSerialization.class);

    private static final String UTF_8 = "UTF-8";
    private final Class<T> classType;
    private final ObjectMapper mapper;

    private static final ObjectWriter WRITER = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private static final ObjectReader MAP_READER = new ObjectMapper().readerFor(Map.class);

    /**
     * @return an ObjectWriter which pretty-prints its output
     */
    public static ObjectWriter writer() {
        return WRITER;
    }

    /**
     * @return an ObjectReader which returns simple Maps.
     */
    public static ObjectReader mapReader() {
        return MAP_READER;
    }

    /**
     * Create an instance bound to a specific type.
     * @param classType class to marshall
     * @param failOnUnknownProperties fail if an unknown property is encountered.
     * @param pretty generate pretty (indented) output?
     */
    public JsonSerialization(Class<T> classType, boolean failOnUnknownProperties, boolean pretty) {
        Utils.checkArgument(classType != null, "null classType");
        this.classType = classType;
        this.mapper = new ObjectMapper()
                // 自动加载classpath中所有Jackson Module
                .findAndRegisterModules()
                // 时区序列化为+08:00形式
                .configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, false)
                // 日期、时间序列化为字符串
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                // 持续时间序列化为字符串
                .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
                // 当出现Java类中未知的属性时不报错，而是忽略此JSON字段
                .configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false)
                // 枚举类型调用`toString`方法进行序列化
                .configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true)
                // 设置java.util.Date类型序列化格式
                .setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                // 设置Jackson使用的时区
                .setTimeZone(SimpleTimeZone.getTimeZone("GMT+8"));

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, pretty);
    }

    /**
     * Get the simple name of the class type to be marshalled.
     * @return the name of the class being marshalled
     */
    public String getName() {
        return classType.getSimpleName();
    }

    /**
     * Get the mapper of this class.
     * @return the mapper
     */
    public ObjectMapper getMapper() {
        return mapper;
    }

    /**
     * Convert from JSON.
     *
     * @param json input
     * @return the parsed JSON
     * @throws IOException IO problems
     * @throws JsonParseException If the input is not well-formatted
     * @throws JsonMappingException failure to map from the JSON to this class
     */
    @SuppressWarnings("unchecked")
    public synchronized T fromJson(String json) throws IOException, JsonParseException, JsonMappingException {
        if (json.isEmpty()) {
            throw new EOFException("No data");
        }
        try {
            return mapper.readValue(json, classType);
        } catch (IOException e) {
            LOG.error("Exception while parsing json : {}\n{}", e, json, e);
            throw e;
        }
    }

    /**
     * Read from an input stream.
     * @param stream stream to read from
     * @return the parsed entity
     * @throws IOException IO problems
     * @throws JsonParseException If the input is not well-formatted
     * @throws JsonMappingException failure to map from the JSON to this class
     */
    public synchronized T fromJsonStream(InputStream stream) throws IOException {
        return mapper.readValue(stream, classType);
    }

    /**
     * Load from a JSON text file.
     * @param jsonFile input file
     * @return the parsed JSON
     * @throws IOException IO problems
     * @throws JsonParseException If the input is not well-formatted
     * @throws JsonMappingException failure to map from the JSON to this class
     */
    @SuppressWarnings("unchecked")
    public synchronized T load(File jsonFile) throws IOException, JsonParseException, JsonMappingException {
        if (!jsonFile.isFile()) {
            throw new FileNotFoundException("Not a file: " + jsonFile);
        }
        if (jsonFile.length() == 0) {
            throw new EOFException("File is empty: " + jsonFile);
        }
        try {
            return mapper.readValue(jsonFile, classType);
        } catch (IOException e) {
            LOG.error("Exception while parsing json file {}", jsonFile, e);
            throw e;
        }
    }

    /**
     * Save to a local file. Any existing file is overwritten unless
     * the OS blocks that.
     * @param file file
     * @param instance instance
     * @throws IOException IO exception
     */
    public void save(File file, T instance) throws IOException {
        writeJsonAsBytes(instance, new FileOutputStream(file));
    }

    /**
     * Convert from a JSON file.
     * @param resource input file
     * @return the parsed JSON
     * @throws IOException IO problems
     * @throws JsonParseException If the input is not well-formatted
     * @throws JsonMappingException failure to map from the JSON to this class
     */
    @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
    public synchronized T fromResource(String resource) throws IOException, JsonParseException, JsonMappingException {
        try (InputStream resStream = this.getClass().getResourceAsStream(resource)) {
            if (resStream == null) {
                throw new FileNotFoundException(resource);
            }
            return mapper.readValue(resStream, classType);
        } catch (IOException e) {
            LOG.error("Exception while parsing json resource {}", resource, e);
            throw e;
        }
    }

    /**
     * clone by converting to JSON and back again.
     * This is much less efficient than any Java clone process.
     * @param instance instance to duplicate
     * @return a new instance
     * @throws IOException IO problems.
     */
    public T fromInstance(T instance) throws IOException {
        return fromJson(toJson(instance));
    }


    /**
     * Write the JSON as bytes, then close the file.
     * @param dataOutputStream an output stream that will always be closed
     * @throws IOException on any failure
     */
    private void writeJsonAsBytes(T instance, OutputStream dataOutputStream) throws IOException {
        try {
            dataOutputStream.write(toBytes(instance));
        } finally {
            dataOutputStream.close();
        }
    }

    /**
     * Convert JSON to bytes.
     * @param instance instance to convert
     * @return a byte array
     * @throws IOException IO problems
     */
    public byte[] toBytes(T instance) throws IOException {
        return mapper.writeValueAsBytes(instance);
    }

    /**
     * Deserialize from a byte array.
     * @param bytes byte array
     * @throws IOException IO problems
     * @throws EOFException not enough data
     */
    public T fromBytes(byte[] bytes) throws IOException {
        return fromJson(new String(bytes, 0, bytes.length, UTF_8));
    }

    /**
     * Convert an instance to a JSON string.
     * @param instance instance to convert
     * @return a JSON string description
     * @throws JsonProcessingException Json generation problems
     */
    public synchronized String toJson(T instance) throws JsonProcessingException {
        return mapper.writeValueAsString(instance);
    }

    /**
     * Convert an instance to a string form for output. This is a robust
     * operation which will convert any JSON-generating exceptions into
     * error text.
     * @param instance non-null instance
     * @return a JSON string
     */
    public String toString(T instance) {
        Utils.checkArgument(instance != null, "Null instance argument");
        try {
            return toJson(instance);
        } catch (JsonProcessingException e) {
            return "Failed to convert to a string: " + e;
        }
    }
}