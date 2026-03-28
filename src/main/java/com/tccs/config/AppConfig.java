package com.tccs.config;

import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private final Properties props = new Properties();

    public AppConfig() {
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String get(String key) {
        return System.getenv(key.toUpperCase().replace('.', '_')) != null ?
                System.getenv(key.toUpperCase().replace('.', '_')) : props.getProperty(key);
    }

    public String get(String key, String defaultValue) {
        String val = get(key);
        return val != null ? val : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String val = get(key);
        return val != null ? Integer.parseInt(val) : defaultValue;
    }

    public double getDouble(String key, double defaultValue) {
        String val = get(key);
        return val != null ? Double.parseDouble(val) : defaultValue;
    }
}
