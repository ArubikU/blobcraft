package dev.arubik.blobcraft.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import dev.arubik.blobcraft.DashboardHtml;
import dev.arubik.blobcraft.models.ChunkedUpload;
import dev.arubik.blobcraft.models.StoredFile;
import dev.arubik.blobcraft.services.ChunkedUploadService;
import dev.arubik.blobcraft.storage.FileStorage;

public class HttpServerWrapper {
    private HttpServer server;
    private final int port;
    private final String bindAddress;
    private final String accessKey;
    private final int maxThreads;
    private final boolean enableCors;
    private final boolean logRequests;
    private final FileStorage fileStorage;
    private final JavaPlugin plugin;
    private final Gson gson = new Gson();
    private final boolean enableDashboard;
    private final String dashboardPath;
    private final boolean dashboardAuth;
    private final ChunkedUploadService chunkedUploadService;
    private final int readTimeout;
    private final int writeTimeout;
    private final int idleTimeout;
    private final long maxRequestSize;
    private final int bufferSize;
    private final boolean rateLimitEnabled;
    private final int rateLimitRequests;
    private final int rateLimitWindow;
    private final boolean progressEnabled;
    private final long progressUpdateInterval;

    public HttpServerWrapper(int port, String bindAddress, String accessKey, int maxThreads,
                           boolean enableCors, boolean logRequests, FileStorage fileStorage, 
                           JavaPlugin plugin, boolean enableDashboard, String dashboardPath, 
                           boolean dashboardAuth, ChunkedUploadService chunkedUploadService,
                           int readTimeout, int writeTimeout, int idleTimeout, 
                           long maxRequestSize, int bufferSize, boolean rateLimitEnabled,
                           int rateLimitRequests, int rateLimitWindow, boolean progressEnabled,
                           long progressUpdateInterval) {
        this.port = port;
        this.bindAddress = bindAddress;
        this.accessKey = accessKey;
        this.maxThreads = maxThreads;
        this.enableCors = enableCors;
        this.logRequests = logRequests;
        this.fileStorage = fileStorage;
        this.plugin = plugin;
        this.enableDashboard = enableDashboard;
        this.dashboardPath = dashboardPath;
        this.dashboardAuth = dashboardAuth;
        this.chunkedUploadService = chunkedUploadService;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.idleTimeout = idleTimeout;
        this.maxRequestSize = maxRequestSize;
        this.bufferSize = bufferSize;
        this.rateLimitEnabled = rateLimitEnabled;
        this.rateLimitRequests = rateLimitRequests;
        this.rateLimitWindow = rateLimitWindow;
        this.progressEnabled = progressEnabled;
        this.progressUpdateInterval = progressUpdateInterval;
    }

    public void start() {
        try {
            InetSocketAddress address = new InetSocketAddress(bindAddress, port);
            server = HttpServer.create(address, 0);
            server.setExecutor(Executors.newFixedThreadPool(maxThreads));

            // Standard endpoints
            server.createContext("/upload", new UploadHandler());
            server.createContext("/blob/", new PrivateDownloadHandler());
            server.createContext("/public/", new PublicDownloadHandler());
            server.createContext("/list", new ListHandler());
            server.createContext("/delete/", new DeleteHandler());
            server.createContext("/metadata/", new MetadataHandler());
            server.createContext("/health", new HealthHandler());
            
            // Chunked upload endpoints
            if (chunkedUploadService != null) {
                server.createContext("/upload/init", new InitUploadHandler());
                server.createContext("/upload/chunk", new ChunkUploadHandler());
                server.createContext("/upload/progress/", new ProgressHandler());
                server.createContext("/upload/cancel/", new CancelUploadHandler());
                plugin.getLogger().info("Chunked upload endpoints registered");
            }
            
            // Dashboard endpoints
            if (enableDashboard) {
                server.createContext(dashboardPath, new DashboardHandler());
                server.createContext("/api/stats", new StatsHandler());
                plugin.getLogger().info("Dashboard enabled at: " + dashboardPath);
            }

            server.start();
            plugin.getLogger().info("HTTP server started on " + bindAddress + ":" + port);
            plugin.getLogger().info("Server configuration:");
            plugin.getLogger().info("- Read timeout: " + readTimeout + "ms");
            plugin.getLogger().info("- Write timeout: " + writeTimeout + "ms");
            plugin.getLogger().info("- Max request size: " + (maxRequestSize / 1024 / 1024) + "MB");
            plugin.getLogger().info("- Buffer size: " + bufferSize + " bytes");

        } catch (IOException e) {
            plugin.getLogger().severe("Error starting HTTP server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("HTTP server stopped");
        }
    }

    private class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 403, "{\"error\":\"Forbidden\"}");
                return;
            }

            try {
                InputStream input = exchange.getRequestBody();
                Headers headers = exchange.getRequestHeaders();
                String filename = headers.getFirst("X-Filename");
                if (filename == null || filename.isEmpty()) {
                    sendResponse(exchange, 400, "{\"error\":\"Filename header is required\"}");
                    return;
                }
                filename = URLDecoder.decode(filename, StandardCharsets.UTF_8.name());

                // Check content length
                String contentLengthStr = headers.getFirst("Content-Length");
                if (contentLengthStr != null) {
                    long contentLength = Long.parseLong(contentLengthStr);
                    if (contentLength > maxRequestSize) {
                        sendResponse(exchange, 413, "{\"error\":\"Request too large. Use chunked upload for large files.\"}");
                        return;
                    }
                }

                // Read data with buffer
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[bufferSize];
                int bytesRead;
                while ((bytesRead = input.read(data)) != -1) {
                    buffer.write(data, 0, bytesRead);
                }
                byte[] fileData = buffer.toByteArray();

                String publicHeader = headers.getFirst("X-Public");
                boolean isPublic = "true".equalsIgnoreCase(publicHeader);

                String ttlHeader = headers.getFirst("X-TTL");
                Long ttlSeconds = null;
                if (ttlHeader != null && !ttlHeader.trim().isEmpty()) {
                    try {
                        ttlSeconds = Long.parseLong(ttlHeader);
                    } catch (NumberFormatException e) {
                        // Ignore invalid TTL
                    }
                }

                // Extract metadata from headers
                Map<String, String> metadata = new HashMap<>();
                String tags = headers.getFirst("X-Tags");
                String category = headers.getFirst("X-Category");
                String uploader = headers.getFirst("X-Uploader");
                String description = headers.getFirst("X-Description");

                if (tags != null) metadata.put("tags", tags);
                if (category != null) metadata.put("category", category);
                if (uploader != null) metadata.put("uploader", uploader);
                if (description != null) metadata.put("description", description);

                // Extract custom metadata (X-Meta-* headers)
                for (String headerName : headers.keySet()) {
                    if (headerName.startsWith("X-Meta-")) {
                        String metaKey = headerName.substring(7); // Remove "X-Meta-" prefix
                        String metaValue = headers.getFirst(headerName);
                        if (metaValue != null) {
                            metadata.put(metaKey, metaValue);
                        }
                    }
                }

                // Get client IP and User-Agent
                String clientIp = getClientIp(exchange);
                String userAgent = headers.getFirst("User-Agent");

                StoredFile storedFile = fileStorage.storeFile(filename, fileData, isPublic, ttlSeconds, 
                    clientIp, userAgent, metadata);

                if (storedFile == null) {
                    sendResponse(exchange, 507, "{\"error\":\"Storage limit exceeded\"}");
                    return;
                }

                JsonObject response = new JsonObject();
                response.addProperty("id", storedFile.getId());
                response.addProperty("filename", storedFile.getFilename());
                response.addProperty("size", storedFile.getSize());
                response.addProperty("originalSize", storedFile.getOriginalSize());
                response.addProperty("uploadedAt", storedFile.getUploadedAt().toString());
                response.addProperty("public", storedFile.isPublic());
                response.addProperty("compressed", storedFile.isCompressed());
                response.addProperty("mimeType", storedFile.getMimeType());
                if (storedFile.getExpiresAt() != null) {
                    response.addProperty("expiresAt", storedFile.getExpiresAt().toString());
                }
                response.addProperty("url", storedFile.isPublic() ? 
                    "/public/" + storedFile.getId() : "/blob/" + storedFile.getId());
                
                sendResponse(exchange, 200, gson.toJson(response));

            } catch (Exception e) {
                plugin.getLogger().warning("File upload failed: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"File upload failed: " + e.getMessage() + "\"}");
            }
        }
    }

    private class PrivateDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleDownload(exchange, false);
        }
    }

    private class PublicDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleDownload(exchange, true);
        }
    }

    private void handleDownload(HttpExchange exchange, boolean isPublic) throws IOException {
        logRequest(exchange);
        setCorsHeaders(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String fileId = path.substring((isPublic ? "/public/" : "/blob/").length());

        try {
            StoredFile storedFile = fileStorage.getFile(fileId);
            if (storedFile == null) {
                sendResponse(exchange, 404, "{\"error\":\"File not found\"}");
                return;
            }

            if (!isPublic && !isAuthorized(exchange)) {
                sendResponse(exchange, 403, "{\"error\":\"Forbidden\"}");
                return;
            }

            if (isPublic && !storedFile.isPublic()) {
                sendResponse(exchange, 403, "{\"error\":\"File is not public\"}");
                return;
            }

            byte[] fileData = fileStorage.getFileData(fileId);
            if (fileData == null) {
                sendResponse(exchange, 404, "{\"error\":\"File data not found\"}");
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", storedFile.getMimeType());
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + storedFile.getFilename() + "\"");
            exchange.sendResponseHeaders(200, fileData.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileData);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("File download failed: " + e.getMessage());
            sendResponse(exchange, 500, "{\"error\":\"File download failed\"}");
        }
    }

    private class DeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"DELETE".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 403, "{\"error\":\"Forbidden\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String fileId = path.substring("/delete/".length());

            try {
                boolean success = fileStorage.deleteFile(fileId);
                if (success) {
                    JsonObject response = new JsonObject();
                    response.addProperty("success", true);
                    response.addProperty("message", "File deleted successfully");
                    sendResponse(exchange, 200, gson.toJson(response));
                } else {
                    sendResponse(exchange, 404, "{\"error\":\"File not found\"}");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to delete file: " + e.getMessage());
                sendResponse(exchange, 500, "{\"error\":\"Failed to delete file\"}");
            }
        }
    }

    private class MetadataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String fileId = path.substring("/metadata/".length());

            try {
                StoredFile storedFile = fileStorage.getFile(fileId);
                if (storedFile == null) {
                    sendResponse(exchange, 404, "{\"error\":\"File not found\"}");
                    return;
                }

                if (!storedFile.isPublic() && !isAuthorized(exchange)) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden\"}");
                    return;
                }

                JsonObject response = new JsonObject();
                response.addProperty("id", storedFile.getId());
                response.addProperty("filename", storedFile.getFilename());
                response.addProperty("size", storedFile.getSize());
                response.addProperty("originalSize", storedFile.getOriginalSize());
                response.addProperty("uploadedAt", storedFile.getUploadedAt().toString());
                response.addProperty("public", storedFile.isPublic());
                response.addProperty("compressed", storedFile.isCompressed());
                response.addProperty("mimeType", storedFile.getMimeType());
                response.addProperty("uploaderIp", storedFile.getUploaderIp());
                response.addProperty("uploaderAgent", storedFile.getUploaderAgent());
                if (storedFile.getExpiresAt() != null) {
                    response.addProperty("expiresAt", storedFile.getExpiresAt().toString());
                }

                // Add metadata
                JsonObject metadataJson = new JsonObject();
                if (storedFile.getMetadata() != null) {
                    for (Map.Entry<String, String> entry : storedFile.getMetadata().entrySet()) {
                        metadataJson.addProperty(entry.getKey(), entry.getValue());
                    }
                }
                response.add("metadata", metadataJson);

                sendResponse(exchange, 200, gson.toJson(response));

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get file metadata: " + e.getMessage());
                sendResponse(exchange, 500, "{\"error\":\"Failed to get file metadata\"}");
            }
        }
    }

    private class ListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            if (dashboardAuth && !isAuthorized(exchange)) {
                sendResponse(exchange, 403, "{\"error\":\"Forbidden\"}");
                return;
            }

            try {
                // Parse query parameters
                URI uri = exchange.getRequestURI();
                String query = uri.getQuery();
                Map<String, String> queryParams = new HashMap<>();
                if (query != null) {
                    String[] pairs = query.split("&");
                    for (String pair : pairs) {
                        int idx = pair.indexOf("=");
                        if (idx > 0) {
                            queryParams.put(pair.substring(0, idx), pair.substring(idx + 1));
                        }
                    }
                }

                // Extract parameters
                int page = 1;
                int pageSize = 10;
                String extensionFilter = queryParams.get("ext");
                String inputFilter = queryParams.getOrDefault("search", "");

                try {
                    page = Integer.parseInt(queryParams.getOrDefault("page", "1"));
                    pageSize = Integer.parseInt(queryParams.getOrDefault("pageSize", "10"));
                } catch (NumberFormatException e) {
                    // Use default values if parsing fails
                }

                List<StoredFile> files ;
                int totalFiles = 0;
                if(inputFilter.equals("")) {
                    files = fileStorage.listFiles(page, pageSize, extensionFilter);
                    totalFiles = fileStorage.getTotalFiles(extensionFilter);
                } else {
                    files = fileStorage.listFiles(page, pageSize, extensionFilter, inputFilter);
                    totalFiles = fileStorage.getTotalFiles(extensionFilter, inputFilter);
                }



                JsonObject response = new JsonObject();
                response.addProperty("page", page);
                response.addProperty("pageSize", pageSize);
                response.addProperty("total", totalFiles);

                JsonArray filesArray = new JsonArray();
                for (StoredFile file : files) {
                    JsonObject fileJson = new JsonObject();
                    fileJson.addProperty("id", file.getId());
                    fileJson.addProperty("filename", file.getFilename());
                    fileJson.addProperty("size", file.getSize());
                    fileJson.addProperty("originalSize", file.getOriginalSize());
                    fileJson.addProperty("uploadedAt", file.getUploadedAt().toString());
                    fileJson.addProperty("extension", file.getExtension());
                    fileJson.addProperty("public", file.isPublic());
                    fileJson.addProperty("compressed", file.isCompressed());
                    fileJson.addProperty("mimeType", file.getMimeType());
                    fileJson.addProperty("uploader", file.getUploader());
                    fileJson.addProperty("tags", file.getTags());
                    fileJson.addProperty("category", file.getCategory());
                    fileJson.addProperty("uploaderIp", file.getUploaderIp());
                    if (file.getExpiresAt() != null) {
                        fileJson.addProperty("expiresAt", file.getExpiresAt().toString());
                    }
                    fileJson.addProperty("url", file.isPublic() ? 
                        "/public/" + file.getId() : "/blob/" + file.getId());
                    filesArray.add(fileJson);
                }
                response.add("files", filesArray);

                sendResponse(exchange, 200, gson.toJson(response));

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to list files: " + e.getMessage());
                sendResponse(exchange, 500, "{\"error\":\"Failed to list files\"}");
            }
        }
    }

    private class InitUploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 403, "{\"error\":\"Forbidden\"}");
                return;
            }

            if (chunkedUploadService == null) {
                sendResponse(exchange, 503, "{\"error\":\"Chunked upload not available\"}");
                return;
            }

            try {
                Headers headers = exchange.getRequestHeaders();
                String filename = headers.getFirst("X-Filename");
                String totalSizeStr = headers.getFirst("X-Total-Size");

                if (filename == null || totalSizeStr == null) {
                    sendResponse(exchange, 400, "{\"error\":\"Missing required headers: X-Filename, X-Total-Size\"}");
                    return;
                }

                filename = URLDecoder.decode(filename, StandardCharsets.UTF_8.name());
                long totalSize;
                try {
                    totalSize = Long.parseLong(totalSizeStr);
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid total size: " + totalSizeStr + "\"}");
                    return;
                }

                boolean isPublic = "true".equalsIgnoreCase(headers.getFirst("X-Public"));

                String ttlHeader = headers.getFirst("X-TTL");
                Long ttlSeconds = null;
                if (ttlHeader != null && !ttlHeader.trim().isEmpty()) {
                    try {
                        ttlSeconds = Long.parseLong(ttlHeader);
                    } catch (NumberFormatException e) {
                        // Ignore invalid TTL
                    }
                }

                // Extract metadata
                Map<String, String> metadata = new HashMap<>();
                String tags = headers.getFirst("X-Tags");
                String category = headers.getFirst("X-Category");
                String uploader = headers.getFirst("X-Uploader");
                String description = headers.getFirst("X-Description");

                if (tags != null) metadata.put("tags", tags);
                if (category != null) metadata.put("category", category);
                if (uploader != null) metadata.put("uploader", uploader);
                if (description != null) metadata.put("description", description);

                String clientIp = getClientIp(exchange);
                String userAgent = headers.getFirst("User-Agent");

                ChunkedUpload upload = chunkedUploadService.initializeUpload(
                    filename, totalSize, isPublic, ttlSeconds, metadata, clientIp, userAgent);

                JsonObject response = new JsonObject();
                response.addProperty("uploadId", upload.getUploadId());
                response.addProperty("chunkSize", upload.getChunkSize());
                response.addProperty("totalChunks", upload.getTotalChunks());
                response.addProperty("expiresAt", upload.getExpiresAt().toString());

                sendResponse(exchange, 200, gson.toJson(response));

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid upload initialization: " + e.getMessage());
                sendResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to initialize upload: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"Failed to initialize upload: " + e.getMessage() + "\"}");
            }
        }
    }

    private class ChunkUploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            setCorsHeaders(exchange);

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 403, "{\"error\":\"Forbidden\"}");
                return;
            }

            if (chunkedUploadService == null) {
                sendResponse(exchange, 503, "{\"error\":\"Chunked upload not available\"}");
                return;
            }

            try {
                Headers headers = exchange.getRequestHeaders();
                String uploadId = headers.getFirst("X-Upload-Id");
                String chunkNumberStr = headers.getFirst("X-Chunk-Number");

                if (uploadId == null || chunkNumberStr == null) {
                    sendResponse(exchange, 400, "{\"error\":\"Missing required headers: X-Upload-Id, X-Chunk-Number\"}");
                    return;
                }

                int chunkNumber;
                try {
                    chunkNumber = Integer.parseInt(chunkNumberStr);
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid chunk number: " + chunkNumberStr + "\"}");
                    return;
                }

                // Read chunk data with buffer
                InputStream input = exchange.getRequestBody();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[bufferSize];
                int bytesRead;
                while ((bytesRead = input.read(data)) != -1) {
                    buffer.write(data, 0, bytesRead);
                }

                byte[] chunkData = buffer.toByteArray();

                boolean success = chunkedUploadService.uploadChunk(uploadId, chunkNumber, chunkData);

                if (success) {
                    ChunkedUpload upload = chunkedUploadService.getUpload(uploadId);
                    JsonObject response = new JsonObject();
                    response.addProperty("success", true);
                    response.addProperty("chunkNumber", chunkNumber);
                    response.addProperty("progress", upload != null ? upload.getProgress() : 0.0);
                    response.addProperty("completed", upload != null && upload.isCompleted());
                    if (upload != null && upload.isCompleted()) {
                        response.addProperty("fileId", upload.getFinalFileId());
                    }

                    sendResponse(exchange, 200, gson.toJson(response));
                } else {
                    sendResponse(exchange, 500, "{\"error\":\"Failed to upload chunk\"}");
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to upload chunk: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"Failed to upload chunk: " + e.getMessage() + "\"}");
            }
        }
    }

    private class ProgressHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            setCorsHeaders(exchange);

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            if (chunkedUploadService == null) {
                sendResponse(exchange, 503, "{\"error\":\"Chunked upload not available\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String uploadId = path.substring("/upload/progress/".length());

            ChunkedUpload upload = chunkedUploadService.getUpload(uploadId);
            if (upload == null) {
                sendResponse(exchange, 404, "{\"error\":\"Upload not found\"}");
                return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("uploadId", upload.getUploadId());
            response.addProperty("filename", upload.getFilename());
            response.addProperty("totalSize", upload.getTotalSize());
            response.addProperty("uploadedBytes", upload.getUploadedBytes());
            response.addProperty("progress", upload.getProgress());
            response.addProperty("totalChunks", upload.getTotalChunks());
            response.addProperty("uploadedChunks", upload.getChunks().size());
            response.addProperty("completed", upload.isCompleted());
            response.addProperty("createdAt", upload.getCreatedAt().toString());

            if (upload.isCompleted()) {
                response.addProperty("fileId", upload.getFinalFileId());
            }

            // Missing chunks for resumable upload
            List<Integer> missingChunks = chunkedUploadService.getMissingChunks(uploadId);
            JsonArray missingArray = new JsonArray();
            for (Integer chunk : missingChunks) {
                missingArray.add(chunk);
            }
            response.add("missingChunks", missingArray);

            sendResponse(exchange, 200, gson.toJson(response));
        }
    }

    private class CancelUploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            setCorsHeaders(exchange);

            if (!"DELETE".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 403, "{\"error\":\"Forbidden\"}");
                return;
            }

            if (chunkedUploadService == null) {
                sendResponse(exchange, 503, "{\"error\":\"Chunked upload not available\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String uploadId = path.substring("/upload/cancel/".length());

            boolean success = chunkedUploadService.cancelUpload(uploadId);

            JsonObject response = new JsonObject();
            response.addProperty("success", success);
            response.addProperty("message", success ? "Upload cancelled" : "Upload not found");

            sendResponse(exchange, success ? 200 : 404, gson.toJson(response));
        }
    }

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            setCorsHeaders(exchange);

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "healthy");
            response.addProperty("timestamp", java.time.Instant.now().toString());
            response.addProperty("files", fileStorage.getFileCount());
            response.addProperty("memoryUsed", fileStorage.getUsedMemory());
            response.addProperty("storageUsed", fileStorage.getUsedStorage());
            
            if (chunkedUploadService != null) {
                response.addProperty("activeUploads", chunkedUploadService.getActiveUploadsCount());
                response.addProperty("uploadingBytes", chunkedUploadService.getTotalUploadingBytes());
            }

            sendResponse(exchange, 200, gson.toJson(response));
        }
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            setCorsHeaders(exchange);

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            JsonObject stats = new JsonObject();
            stats.addProperty("totalFiles", fileStorage.getFileCount());
            stats.addProperty("usedMemory", fileStorage.getUsedMemory());
            stats.addProperty("usedStorage", fileStorage.getUsedStorage());
            stats.addProperty("maxMemory", fileStorage.getMaxRam());
            stats.addProperty("maxStorage", fileStorage.getMaxStorage());
            stats.addProperty("memoryUsagePercent", 
                (double) fileStorage.getUsedMemory() / fileStorage.getMaxRam() * 100);
            stats.addProperty("storageUsagePercent", 
                (double) fileStorage.getUsedStorage() / fileStorage.getMaxStorage() * 100);
            
            if (chunkedUploadService != null) {
                stats.addProperty("activeUploads", chunkedUploadService.getActiveUploadsCount());
                stats.addProperty("uploadingBytes", chunkedUploadService.getTotalUploadingBytes());
            }
            
            sendResponse(exchange, 200, gson.toJson(stats));
        }
    }

    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            setCorsHeaders(exchange);

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            if (dashboardAuth && !isAuthorized(exchange)) {
                String authInfo = "Basic realm=\"BlobCraft Dashboard\"";
                exchange.getResponseHeaders().set("WWW-Authenticate", authInfo);
                sendResponse(exchange, 401, "{\"error\":\"Authentication required\"}");
                return;
            }

            // Use the existing DashboardHtml class
            String html = DashboardHtml.getDashboardHtml();

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(html.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    // Utility methods
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.equals("Bearer " + accessKey);
        }
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            String credentials = new String(java.util.Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
            String[] parts = credentials.split(":", 2);
            if (parts.length == 2) {
                return parts[0].equals("admin") && parts[1].equals(accessKey);
            }
        }
        return false;
    }

    private void setCorsHeaders(HttpExchange exchange) {
        if (enableCors) {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Access-Control-Allow-Origin", "*");
            headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Filename, X-Public, X-TTL, X-Tags, X-Category, X-Uploader, X-Description, X-Upload-Id, X-Chunk-Number, X-Total-Size");
            headers.set("Access-Control-Allow-Credentials", "true");
        }
    }

    private void logRequest(HttpExchange exchange) {
        if (logRequests) {
            plugin.getLogger().info(exchange.getRequestMethod() + " " + exchange.getRequestURI() + 
                " from " + getClientIp(exchange));
        }
    }

    private String getClientIp(HttpExchange exchange) {
        String xForwardedFor = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = exchange.getRequestHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }
}
