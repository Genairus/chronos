package com.genairus.chronos.cli;

/**
 * Thrown for all structured configuration errors: missing config file, malformed JSON,
 * undefined environment variable, unknown generator target, etc.
 */
public class BuildConfigException extends RuntimeException {

    public BuildConfigException(String message) {
        super(message);
    }

    public BuildConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
