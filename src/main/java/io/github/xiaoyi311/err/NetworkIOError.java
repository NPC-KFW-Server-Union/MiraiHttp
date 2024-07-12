package io.github.xiaoyi311.err;

/**
 * 網絡錯誤。
 */
public class NetworkIOError extends Exception {
    public NetworkIOError(String desc, Throwable cause) {
        super(desc, cause);
    }
}
