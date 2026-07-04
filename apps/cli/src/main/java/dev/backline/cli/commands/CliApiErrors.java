package dev.backline.cli.commands;

import dev.backline.cli.client.ApiClientException;

import java.io.IOException;

final class CliApiErrors {

    private CliApiErrors() {
    }

    static int print(String apiUrl, ApiClientException e) {
        System.err.println("API error (" + e.httpStatus() + "): " + e.getMessage());
        return 1;
    }

    static int print(String apiUrl, IOException e) {
        String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        System.err.println("Cannot reach API at " + apiUrl + ": " + detail);
        return 1;
    }

    static int printInterrupted() {
        Thread.currentThread().interrupt();
        System.err.println("Interrupted");
        return 1;
    }
}
