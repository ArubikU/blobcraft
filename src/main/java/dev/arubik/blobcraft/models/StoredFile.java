package dev.arubik.blobcraft.models;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

public class StoredFile {
    private final String id;
    private final String filename;
    private final String extension;
    private final byte[] data;
    private final long size;
    private final boolean isPublic;
    private final Instant uploadedAt;
    private final Instant expiresAt;
    private final boolean compressed;
    private final long originalSize;
    private final Map<String, String> metadata;
    private final String uploaderIp;
    private final String uploaderAgent;

    public StoredFile(String id, String filename, byte[] data, boolean isPublic,
                      Instant uploadedAt, Instant expiresAt, boolean compressed, 
                      long originalSize, String uploaderIp, String uploaderAgent,
                      Map<String, String> metadata) {
        this.id = id;
        this.filename = filename;
        this.data = data;
        this.isPublic = isPublic;
        this.uploadedAt = uploadedAt;
        this.expiresAt = expiresAt;
        this.compressed = compressed;
        this.originalSize = originalSize;
        this.uploaderIp = uploaderIp;
        this.uploaderAgent = uploaderAgent;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.size = data.length;
        this.extension = extractExtension(filename);
    }

    private String extractExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    // Getters
    public String getId() { return id; }
    public String getFilename() { return filename; }
    public String getExtension() { return extension; }
    public byte[] getData() { return data; }
    public long getSize() { return size; }
    public boolean isPublic() { return isPublic; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isCompressed() { return compressed; }
    public long getOriginalSize() { return originalSize; }
    public String getUploaderIp() { return uploaderIp; }
    public String getUploaderAgent() { return uploaderAgent; }
    public Map<String, String> getMetadata() { return new HashMap<>(metadata); }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public long getTimeToLive() {
        if (expiresAt == null) return -1;
        return expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
    }

    public String getMimeType() {
        return getMimeTypeFromExtension(extension);
    }

    // Metadata helper methods
    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public String getMetadata(String key) {
        return metadata.get(key);
    }

    public String getMetadata(String key, String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    // Common metadata getters
    public String getTags() {
        return getMetadata("tags", "");
    }

    public String getDescription() {
        return getMetadata("description", "");
    }

    public String getCategory() {
        return getMetadata("category", "general");
    }

    public String getUploader() {
        return getMetadata("uploader", "anonymous");
    }

    private String getMimeTypeFromExtension(String ext) {
        if (ext == null || ext.isEmpty()) {
            return "application/octet-stream";
        }
        
        switch (ext.toLowerCase()) {
            case "txt": return "text/plain";
            case "html": case "htm": return "text/html";
            case "css": return "text/css";
            case "js": return "application/javascript";
            case "json": return "application/json";
            case "xml": return "application/xml";
            case "pdf": return "application/pdf";
            case "zip": return "application/zip";
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "svg": return "image/svg+xml";
            case "mp3": return "audio/mpeg";
            case "mp4": return "video/mp4";
            case "wav": return "audio/wav";
            case "avi": return "video/avi";
            case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls": return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default: return "application/octet-stream";
        }
    }

    @Override
    public String toString() {
        return String.format("StoredFile{id='%s', filename='%s', size=%d, public=%s, compressed=%s, uploader='%s', expires=%s}",
                id, filename, size, isPublic, compressed, getUploader(), expiresAt);
    }
}
