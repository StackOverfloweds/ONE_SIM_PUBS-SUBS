package KDC.NAKT;

public class HashRoot {

    public static String computeRootHash(String key, String topic, int num) throws Exception {
        String keyTopic = Hash.hmacSha1(key, topic.getBytes());
        byte[] numericToByte = Integer.toString(num).getBytes();
        return Hash.hmacSha1(keyTopic, numericToByte);
    }
}