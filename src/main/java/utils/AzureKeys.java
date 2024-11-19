package utils;

public class AzureKeys {

    public static synchronized String getKey(String key) {
        try {
            return System.getenv(key);
        } catch (Exception e) {
            return AzureProperties.getProperties().getProperty(key);
        }
    }

}
