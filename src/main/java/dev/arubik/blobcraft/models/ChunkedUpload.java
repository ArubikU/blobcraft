package dev.arubik.blobcraft.models;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkedUpload {
    private final String uploadId;
    private final String filename;
    private final long totalSize;
    private final int totalChunks;
    private final int chunkSize;
    private final boolean isPublic;
    private final Long ttlSeconds;
    private final Map<String, String> metadata;
    private final String uploaderIp;
    private final String uploaderAgent;
    private final Instant createdAt;
    private final Instant expiresAt;
    
    // Progress tracking
    private final Map<Integer, ChunkInfo> chunks;
    private final AtomicLong uploadedBytes;
    private volatile boolean completed;
    private volatile String finalFileId;
    
    public ChunkedUpload(String uploadId, String filename, long totalSize, 
                        int chunkSize, boolean isPublic, Long ttlSeconds,
                        Map<String, String> metadata, String uploaderIp, 
                        String uploaderAgent) {
        this.uploadId = uploadId;
        this.filename = filename;
        this.totalSize = totalSize;
        this.chunkSize = chunkSize;
        this.totalChunks = (int) Math.ceil((double) totalSize / chunkSize);
        this.isPublic = isPublic;
        this.ttlSeconds = ttlSeconds;
        this.metadata = metadata != null ? new ConcurrentHashMap<>(metadata) : new ConcurrentHashMap<>();
        this.uploaderIp = uploaderIp;
        this.uploaderAgent = uploaderAgent;
        this.createdAt = Instant.now();
        this.expiresAt = Instant.now().plusSeconds(3600); // 1 hour to complete upload
        
        this.chunks = new ConcurrentHashMap<>();
        this.uploadedBytes = new AtomicLong(0);
        this.completed = false;
    }
    
    public static class ChunkInfo {
        private final int chunkNumber;
        private final long size;
        private final String checksum;
        private final Instant uploadedAt;
        
        public ChunkInfo(int chunkNumber, long size, String checksum) {
            this.chunkNumber = chunkNumber;
            this.size = size;
            this.checksum = checksum;
            this.uploadedAt = Instant.now();
        }
        
        // Getters
        public int getChunkNumber() { return chunkNumber; }
        public long getSize() { return size; }
        public String getChecksum() { return checksum; }
        public Instant getUploadedAt() { return uploadedAt; }
    }
    
    public synchronized void addChunk(int chunkNumber, long size, String checksum) {
        if (!chunks.containsKey(chunkNumber)) {
            chunks.put(chunkNumber, new ChunkInfo(chunkNumber, size, checksum));
            uploadedBytes.addAndGet(size);
        }
    }
    
    public boolean isComplete() {
        return chunks.size() == totalChunks && !completed;
    }
    
    public void markCompleted(String finalFileId) {
        this.completed = true;
        this.finalFileId = finalFileId;
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    public double getProgress() {
        return totalSize > 0 ? (double) uploadedBytes.get() / totalSize * 100.0 : 0.0;
    }
    
    public boolean hasChunk(int chunkNumber) {
        return chunks.containsKey(chunkNumber);
    }
    
    public int getMissingChunksCount() {
        return totalChunks - chunks.size();
    }
    
    // Getters
    public String getUploadId() { return uploadId; }
    public String getFilename() { return filename; }
    public long getTotalSize() { return totalSize; }
    public int getTotalChunks() { return totalChunks; }
    public int getChunkSize() { return chunkSize; }
    public boolean isPublic() { return isPublic; }
    public Long getTtlSeconds() { return ttlSeconds; }
    public Map<String, String> getMetadata() { return new ConcurrentHashMap<>(metadata); }
    public String getUploaderIp() { return uploaderIp; }
    public String getUploaderAgent() { return uploaderAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Map<Integer, ChunkInfo> getChunks() { return new ConcurrentHashMap<>(chunks); }
    public long getUploadedBytes() { return uploadedBytes.get(); }
    public boolean isCompleted() { return completed; }
    public String getFinalFileId() { return finalFileId; }
}
