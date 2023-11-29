package org.example;

import org.apache.http.NameValuePair;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final int serverPort;
    private final List<String> validPaths;
    private final ExecutorService executor = Executors.newFixedThreadPool(64);

    public Server(int serverPort, List<String> validPaths) {
        this.serverPort = serverPort;
        this.validPaths = validPaths;
    }

    public void start() throws IOException {
        try (var serverSocket = new ServerSocket(this.serverPort)) {
            while (true) {
                final var socket = serverSocket.accept();
                {
                    executor.submit(() -> {
                        try {
                            handleConnection(socket);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        }
    }


    private void handleConnection(Socket socket) throws IOException {
        try (
                final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final var out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            handleRequest(in, out);
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    private void handleRequest(BufferedReader in, BufferedOutputStream out) throws IOException {
        int httpParts = 3;
        final var requestLine = in.readLine();
        final var parts = requestLine.split(" ");

        // Функциональность по обработке параметров из Query

        Request request = new Request(requestLine);

        System.out.println("User request: " + request.getRequest());
        System.out.println("Requested path: " + request.getPath());

        List<NameValuePair> nameValuePairList = request.getNameValueParams();

        System.out.println("Webpage query parameters values:");
        for (NameValuePair nameValuePair : nameValuePairList) {
            System.out.println(nameValuePair);
        }

        System.out.println("Parameter value: " + request.getQueryParam("param1")); // value = first;
        System.out.println("Parameter value: " + request.getQueryParam("param3")); // value = null;

        System.out.println("HTTP method: " + request.getHttpMethod());
        System.out.println("HTTP version: " + request.getHttpVersion());

        if (parts.length != httpParts) {
            return;
        }

        final var path = parts[1];
        if (!validPaths.contains(path)) {
            sendPageNotFoundResponse(out);
            return;
        }

        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);

        if (path.equals("/classic.html")) {
            final var template = Files.readString(filePath);
            final var content = template.replace("{time}", LocalDateTime.now().toString()).getBytes();
            sendOkResponse(out, mimeType, content);
            out.write(content);
            out.flush();
            return;
        }

        final var length = Files.size(filePath);
        sendOkResponse(out, mimeType, String.valueOf(length).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }

    private void sendPageNotFoundResponse(BufferedOutputStream out) throws IOException {
        final var response = """
                HTTP/1.1 404 Not Found\r
                Content-Length: 0\r
                Connection: close\r
                \r
                """;
        out.write(response.getBytes());
        out.flush();
    }

    private void sendOkResponse(BufferedOutputStream out, String mimeType, byte[] content) throws IOException {
        final var length = Integer.toString(content.length);
        final var response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(response.getBytes());
        out.flush();
    }
}