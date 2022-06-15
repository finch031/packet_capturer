package com.github.capture.conf;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class AppConfiguration implements Serializable,Cloneable {
    /*
     * 标识Java类的序列化版本,若不显示定义则该类变量的值将由JVM根据类的相关信息计算
     * 而修改后的类的计算结果与修改前的类的计算结果往往不同，从而造成对象的反序列化因为
     * 不兼容而失败。可通过jdk的bin下的serialver工具生成:serialver AppConfiguration
     * */
    private static final long serialVersionUID = 1L;

    // Stores the concrete key/value pairs of this configuration object.
    protected final HashMap<String, Object> confData;

    // Creates a new empty configuration.
    public AppConfiguration(){
        this.confData = new HashMap<>();
    }

    // Creates a new configuration from a map.
    public AppConfiguration(Map<String,Object> configMap){
        this.confData = new HashMap<>();
        if(configMap != null && !configMap.isEmpty()){
            this.confData.putAll(configMap);
        }
    }

    /**
     * Creates a new configuration with the copy of the given configuration.
     *
     * @param other The configuration to copy the entries from.
     */
    public AppConfiguration(AppConfiguration other) {
        this.confData = new HashMap<>(other.confData);
    }

    /**
     * load configuration from the properties resource.
     * @param resourceName properties resource name must be ends with .properties
     * @return AppConfiguration.
     * */
    public static AppConfiguration loadFromPropertiesResource(String resourceName){
        AppConfiguration appConfiguration = new AppConfiguration();

        try{
            Properties props = loadProperties(resourceName);
            props.forEach((k,v) -> {
                // set the k,v as string.
                appConfiguration.setString(k.toString(),v.toString());
            });
        }catch (IOException ioe){
            ioe.printStackTrace();
        }

        return appConfiguration;
    }

    /**
     * load configuration from the file with default parser.
     * @param configFile config file.
     * */
    public static AppConfiguration loadFromFile(File configFile){
        Map<String,Object> configMap = new HashMap<>();
        loadConfigMapFromFile(configFile,configMap,new DefaultStrLineParser());

        return new AppConfiguration(configMap);
    }

    /**
     * load configuration from the file.
     * @param configFile config file.
     * @param parser parser the file string lines to config map.
     * */
    public static AppConfiguration loadFromFile(File configFile,StrLineParser parser){
        Map<String,Object> configMap = new HashMap<>();
        loadConfigMapFromFile(configFile,configMap,parser);

        return new AppConfiguration(configMap);
    }






    // --------------------------------------------------------------------------------------------

    /**
     * Adds the given key/value pair to the configuration object. The class can be retrieved by invoking
     * {@link #getClass(String, Class, ClassLoader)} if it is in the scope of the class loader on the caller.
     *
     * @param key The key of the pair to be added
     * @param klazz The value of the pair to be added
     * @see #getClass(String, Class, ClassLoader)
     */
    public void setClass(String key, Class<?> klazz) {
        setValueInternal(key, klazz.getName());
    }

    /**
     * Returns the class associated with the given key as a string.
     *
     * @param <T> The type of the class to return.

     * @param key The key pointing to the associated value
     * @param defaultValue The optional default value returned if no entry exists
     * @param classLoader The class loader used to resolve the class.
     *
     * @return The value associated with the given key, or the default value, if to entry for the key exists.
     */
    @SuppressWarnings("unchecked")
    public <T> Class<T> getClass(String key, Class<? extends T> defaultValue, ClassLoader classLoader) throws ClassNotFoundException {
        Optional<Object> o = getRawValue(key);
        if (!o.isPresent()) {
            return (Class<T>) defaultValue;
        }

        if (o.get().getClass() == String.class) {
            return (Class<T>) Class.forName((String) o.get(), true, classLoader);
        }

        throw new IllegalArgumentException("Configuration cannot evaluate object of class " + o.get().getClass() + " as a class name");
    }

    // --------------------------------------------------------------------------------------------

    /**
     * Adds the given key/value pair to the configuration object.
     *
     * @param key
     *        the key of the key/value pair to be added
     * @param value
     *        the value of the key/value pair to be added
     */
    public void setString(String key, String value) {
        setValueInternal(key, value);
    }

    /**
     * Adds the given key/value pair to the configuration object.
     *
     * @param key
     *        the key of the key/value pair to be added
     * @param value
     *        the value of the key/value pair to be added
     */
    public void setInteger(String key, int value) {
        setValueInternal(key, value);
    }

    /**
     * Adds the given key/value pair to the configuration object.
     *
     * @param key
     *        the key of the key/value pair to be added
     * @param value
     *        the value of the key/value pair to be added
     */
    public void setLong(String key, long value) {
        setValueInternal(key, value);
    }

    /**
     * Adds the given key/value pair to the configuration object.
     *
     * @param key
     *        the key of the key/value pair to be added
     * @param value
     *        the value of the key/value pair to be added
     */
    public void setBoolean(String key, boolean value) {
        setValueInternal(key, value);
    }

    /**
     * Adds the given key/value pair to the configuration object.
     *
     * @param key
     *        the key of the key/value pair to be added
     * @param value
     *        the value of the key/value pair to be added
     */
    public void setFloat(String key, float value) {
        setValueInternal(key, value);
    }

    /**
     * Adds the given key/value pair to the configuration object.
     *
     * @param key
     *        the key of the key/value pair to be added
     * @param value
     *        the value of the key/value pair to be added
     */
    public void setDouble(String key, double value) {
        setValueInternal(key, value);
    }

    /**
     * Adds the given key/value pair to the configuration object.
     *
     * @param key
     *        the key of the key/value pair to be added
     * @param value
     *        the value of the key/value pair to be added
     */
    public void setList(String key, List<?> value) {
        setValueInternal(key, value);
    }

    /**
     * Adds the given key/value pair to the configuration object.
     *
     * @param key
     *        the key of the key/value pair to be added
     * @param value
     *        the value of the key/value pair to be added
     */
    public void setMap(String key, Map<?,?> value) {
        setValueInternal(key, value);
    }

    /**
     * Returns the value associated with the given key as a string.
     *
     * @param key
     *        the key pointing to the associated value
     * @param defaultValue
     *        the default value which is returned in case there is no value associated with the given key
     * @return the (default) value associated with the given key
     */
    public String getString(String key, String defaultValue) {
        return getRawValue(key)
                .map(AppConfiguration::convertToString)
                .orElse(defaultValue);
    }

    /**
     * Returns the value associated with the given key as an integer.
     *
     * @param key
     *        the key pointing to the associated value
     * @param defaultValue
     *        the default value which is returned in case there is no value associated with the given key
     * @return the (default) value associated with the given key
     */
    public int getInteger(String key, int defaultValue) {
        return getRawValue(key)
                .map(AppConfiguration::convertToInt)
                .orElse(defaultValue);
    }

    /**
     * Returns the value associated with the given key as a long.
     *
     * @param key
     *        the key pointing to the associated value
     * @param defaultValue
     *        the default value which is returned in case there is no value associated with the given key
     * @return the (default) value associated with the given key
     */
    public long getLong(String key, long defaultValue) {
        return getRawValue(key)
                .map(AppConfiguration::convertToLong)
                .orElse(defaultValue);
    }

    /**
     * Returns the value associated with the given key as a boolean.
     *
     * @param key
     *        the key pointing to the associated value
     * @param defaultValue
     *        the default value which is returned in case there is no value associated with the given key
     * @return the (default) value associated with the given key
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return getRawValue(key)
                .map(AppConfiguration::convertToBoolean)
                .orElse(defaultValue);
    }

    /**
     * Returns the value associated with the given key as a float.
     *
     * @param key
     *        the key pointing to the associated value
     * @param defaultValue
     *        the default value which is returned in case there is no value associated with the given key
     * @return the (default) value associated with the given key
     */
    public float getFloat(String key, float defaultValue) {
        return getRawValue(key)
                .map(AppConfiguration::convertToFloat)
                .orElse(defaultValue);
    }

    /**
     * Returns the value associated with the given key as a double.
     *
     * @param key
     *        the key pointing to the associated value
     * @param defaultValue
     *        the default value which is returned in case there is no value associated with the given key
     * @return the (default) value associated with the given key
     */
    public double getDouble(String key, double defaultValue) {
        return getRawValue(key)
                .map(AppConfiguration::convertToDouble)
                .orElse(defaultValue);
    }

    /**
     * Returns the value associated with the given key as a double.
     *
     * @param key
     *        the key pointing to the associated value
     * @param defaultValue
     *        the default value which is returned in case there is no value associated with the given key
     * @return the (default) value associated with the given key
     */
    public String getListAsString(String key, String defaultValue) {
        return getRawValue(key)
                .map(AppConfiguration::convertToString)
                .orElse(defaultValue);
    }

    /**
     * Returns the value associated with the given key as a double.
     *
     * @param key
     *        the key pointing to the associated value
     * @param defaultValue
     *        the default value which is returned in case there is no value associated with the given key
     * @return the (default) value associated with the given key
     */
    public String getMapAsString(String key, String defaultValue) {
        return getRawValue(key)
                .map(AppConfiguration::convertToString)
                .orElse(defaultValue);
    }


    // --------------------------------------------------------------------------------------------

    /**
     * Removes given config option from the configuration.
     *
     * @param key key of the config to remove
     * @return true is config has been removed, false otherwise
     */
    public boolean removeConfig(String key){
        synchronized (this.confData){
            // try the current key
            Object oldValue = this.confData.remove(key);
            return oldValue != null;
        }
    }

    private <T> void setValueInternal(String key, T value) {
        if (key == null) {
            throw new NullPointerException("Key must not be null.");
        }
        if (value == null) {
            throw new NullPointerException("Value must not be null.");
        }

        synchronized (this.confData) {
            this.confData.put(key, value);
        }
    }

    private Optional<Object> getRawValue(String key) {
        if (key == null) {
            throw new NullPointerException("Key must not be null.");
        }

        synchronized (this.confData) {
            return Optional.ofNullable(this.confData.get(key));
        }
    }

    private static String convertToString(Object o) {
        if (o.getClass() == String.class) {
            return (String) o;
        }else if (o instanceof List) {
            return ((List<?>) o).stream()
                    .map(e -> escapeWithSingleQuote(convertToString(e), ";"))
                    .collect(Collectors.joining(";"));
        } else if (o instanceof Map) {
            return ((Map<?, ?>) o).entrySet().stream()
                    .map(e -> {
                        String escapedKey = escapeWithSingleQuote(e.getKey().toString(), ":");
                        String escapedValue = escapeWithSingleQuote(e.getValue().toString(), ":");

                        return escapeWithSingleQuote(escapedKey + ":" + escapedValue, ",");
                    })
                    .collect(Collectors.joining(","));
        }
        return o.toString();
    }

    private static Integer convertToInt(Object o) {
        if (o.getClass() == Integer.class) {
            return (Integer) o;
        } else if (o.getClass() == Long.class) {
            long value = (Long) o;
            if (value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE) {
                return (int) value;
            } else {
                throw new IllegalArgumentException(String.format(
                        "Configuration value %s overflows/underflows the integer type.",
                        value));
            }
        }

        return Integer.parseInt(o.toString());
    }

    private static Long convertToLong(Object o) {
        if (o.getClass() == Long.class) {
            return (Long) o;
        } else if (o.getClass() == Integer.class) {
            return ((Integer) o).longValue();
        }

        return Long.parseLong(o.toString());
    }

    private static Boolean convertToBoolean(Object o) {
        if (o.getClass() == Boolean.class) {
            return (Boolean) o;
        }

        switch (o.toString().toUpperCase()) {
            case "TRUE":
                return true;
            case "FALSE":
                return false;
            default:
                throw new IllegalArgumentException(String.format(
                        "Unrecognized option for boolean: %s. Expected either true or false(case insensitive)",
                        o));
        }
    }

    private static Float convertToFloat(Object o) {
        if (o.getClass() == Float.class) {
            return (Float) o;
        } else if (o.getClass() == Double.class) {
            double value = ((Double) o);
            if (value == 0.0
                    || (value >= Float.MIN_VALUE && value <= Float.MAX_VALUE)
                    || (value >= -Float.MAX_VALUE && value <= -Float.MIN_VALUE)) {
                return (float) value;
            } else {
                throw new IllegalArgumentException(String.format(
                        "Configuration value %s overflows/underflows the float type.",
                        value));
            }
        }

        return Float.parseFloat(o.toString());
    }

    private static Double convertToDouble(Object o) {
        if (o.getClass() == Double.class) {
            return (Double) o;
        } else if (o.getClass() == Float.class) {
            return ((Float) o).doubleValue();
        }

        return Double.parseDouble(o.toString());
    }


// --------------------------------------------------------------------------------------------

    /**
     * Returns the keys of all key/value pairs stored inside this
     * configuration object.
     *
     * @return the keys of all key/value pairs stored inside this configuration object
     */
    public Set<String> keySet() {
        synchronized (this.confData) {
            return new HashSet<>(this.confData.keySet());
        }
    }

    /**
     * Adds all entries in this {@code Configuration} to the given {@link Properties}.
     */
    public void addAllToProperties(Properties props) {
        synchronized (this.confData) {
            for (Map.Entry<String, Object> entry : this.confData.entrySet()) {
                props.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public void addAll(AppConfiguration other) {
        synchronized (this.confData) {
            synchronized (other.confData) {
                this.confData.putAll(other.confData);
            }
        }
    }

    /**
     * Adds all entries from the given configuration into this configuration. The keys
     * are prepended with the given prefix.
     *
     * @param other
     *        The configuration whose entries are added to this configuration.
     * @param prefix
     *        The prefix to prepend.
     */
    public void addAll(AppConfiguration other, String prefix) {
        final StringBuilder bld = new StringBuilder();
        bld.append(prefix);
        final int pl = bld.length();

        synchronized (this.confData) {
            synchronized (other.confData) {
                for (Map.Entry<String, Object> entry : other.confData.entrySet()) {
                    bld.setLength(pl);
                    bld.append(entry.getKey());
                    this.confData.put(bld.toString(), entry.getValue());
                }
            }
        }
    }

    /**
     * Checks whether there is an entry with the specified key.
     *
     * @param key key of entry
     * @return true if the key is stored, false otherwise
     */
    public boolean containsKey(String key){
        synchronized (this.confData){
            return this.confData.containsKey(key);
        }
    }

    public <T> AppConfiguration set(String key, T value) {
        setValueInternal(key, value);
        return this;
    }

    public Map<String, String> toMap() {
        synchronized (this.confData){
            Map<String, String> ret = new HashMap<>(this.confData.size());
            for (Map.Entry<String, Object> entry : confData.entrySet()) {
                ret.put(entry.getKey(), convertToString(entry.getValue()));
            }
            return ret;
        }
    }

    // --------------------------------------------------------------------------------------------

    /**
     * Escapes the given string with single quotes, if the input string contains a double quote or any of the
     * given {@code charsToEscape}. Any single quotes in the input string will be escaped by doubling.
     *
     * <p>Given that the escapeChar is (;)
     *
     * <p>Examples:
     * <ul>
     *     <li>A,B,C,D => A,B,C,D</li>
     *     <li>A'B'C'D => 'A''B''C''D'</li>
     *     <li>A;BCD => 'A;BCD'</li>
     *     <li>AB"C"D => 'AB"C"D'</li>
     *     <li>AB'"D:B => 'AB''"D:B'</li>
     * </ul>
     *
     * @param string a string which needs to be escaped
     * @param charsToEscape escape chars for the escape conditions
     * @return escaped string by single quote
     */
    private static String escapeWithSingleQuote(String string, String... charsToEscape) {
        boolean escape = Arrays.stream(charsToEscape).anyMatch(string::contains)
                || string.contains("\"") || string.contains("'");

        if (escape) {
            return "'" + string.replaceAll("'", "''") + "'";
        }

        return string;
    }

    public static Properties loadProperties(String resourceName) throws IOException {
        return loadProperties(resourceName, (ClassLoader)null);
    }

    public static Properties loadProperties(String resourceName, ClassLoader classLoader) throws IOException {
        if (resourceName == null) {
            throw new IllegalArgumentException("Resource name must not be null");
        }

        if(!resourceName.endsWith(".properties")){
            throw new IllegalArgumentException("Resource name must be ends with .properties");
        }

        ClassLoader classLoaderToUse = classLoader;
        if (classLoader == null) {
            classLoaderToUse = getDefaultClassLoader();
        }

        Enumeration<URL> urls = classLoaderToUse != null ? classLoaderToUse.getResources(resourceName) : ClassLoader.getSystemResources(resourceName);
        Properties props = new Properties();

        while(urls.hasMoreElements()) {
            URL url = (URL)urls.nextElement();
            URLConnection con = url.openConnection();
            con.setUseCaches(con.getClass().getSimpleName().startsWith("JNLP"));
            InputStream is = con.getInputStream();
            Throwable var1 = null;

            try {
                if (resourceName.endsWith(".properties")) {
                    props.load(is);
                }
            } catch (Throwable var2) {
                var1 = var2;
                throw var2;
            } finally {
                if (is != null) {
                    if (var1 != null) {
                        try {
                            is.close();
                        } catch (Throwable var3) {
                            var1.addSuppressed(var3);
                        }
                    } else {
                        is.close();
                    }
                }
            }
        }

        return props;
    }

    private static ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread().getContextClassLoader();
        } catch (Throwable var1) {
            //
        }

        if (cl == null) {
            cl = AppConfiguration.class.getClassLoader();
            if (cl == null) {
                try {
                    cl = ClassLoader.getSystemClassLoader();
                } catch (Throwable var2) {
                    //
                }
            }
        }

        return cl;
    }

    private static List<String> readAllLines(File file){
        List<String> fileLines = Collections.emptyList();
        try{
            fileLines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        }catch (IOException ioe){
            ioe.printStackTrace();
        }

        return fileLines;
    }

    private static void loadConfigMapFromFile(File file,Map<String,Object> configMap,StrLineParser lineParser){
        List<String> fileLines = readAllLines(file);
        fileLines.forEach(line -> {
            lineParser.parseAndSet(line,configMap);
        });
    }

    // --------------------------------------------------------------------------------------------

    public interface StrLineParser{
        /**
         * parse a string line and set item to the configuration map.
         * @param line the string line.
         * @param configMap configuration item to store.
         * */
        void parseAndSet(String line,Map<String,Object> configMap);
    }

    public static class DefaultStrLineParser implements StrLineParser{
        @Override
        public void parseAndSet(String line, Map<String, Object> configMap) {
            String[] tmpArr = line.trim().split("[\\s+=:]");
            if(tmpArr.length >= 2){
                configMap.put(tmpArr[0],tmpArr[1]);
            }
        }
    }

    // --------------------------------------------------------------------------------------------

    @Override
    public int hashCode() {
        int hash = 0;
        for (String s : this.confData.keySet()) {
            hash ^= s.hashCode();
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof AppConfiguration) {
            Map<String, Object> otherConf = ((AppConfiguration) obj).confData;

            for (Map.Entry<String, Object> e : this.confData.entrySet()) {
                Object thisVal = e.getValue();
                Object otherVal = otherConf.get(e.getKey());

                if (!thisVal.getClass().equals(byte[].class)) {
                    if (!thisVal.equals(otherVal)) {
                        return false;
                    }
                } else if (otherVal.getClass().equals(byte[].class)) {
                    if (!Arrays.equals((byte[]) thisVal, (byte[]) otherVal)) {
                        return false;
                    }
                } else {
                    return false;
                }
            }

            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public String toString() {
        return this.confData.toString();
    }

    @Override
    protected AppConfiguration clone() throws CloneNotSupportedException {
        AppConfiguration config = new AppConfiguration();
        config.addAll(this);
        return config;
    }
}