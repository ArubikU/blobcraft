package dev.arubik.blobcraft.services;

import dev.arubik.blobcraft.models.ChunkedUpload;
import dev.arubik.blobcraft.models.StoredFile;
import dev.arubik.blobcraft.storage.FileStorage;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChunkedUploadService {
    
    private final Map<String, ChunkedUpload> activeUploads;
    private final Path tempUploadDir;
    private final FileStorage fileStorage;
    private final JavaPlugin plugin;
    private final ScheduledExecutorService cleanupExecutor;
    private final int chunkSize;
    private final long maxFileSize;
    private final boolean enableStreamingCompression;
    
    public ChunkedUploadService(FileStorage fileStorage, JavaPlugin plugin, 
                               String tempUploadPath, int chunkSize, long maxFileSize,
                               boolean enableStreamingCompression) {
        this.activeUploads = new ConcurrentHashMap<>();
        this.fileStorage = fileStorage;
        this.plugin = plugin;
        this.chunkSize = chunkSize;
        this.maxFileSize = maxFileSize;
        this.enableStreamingCompression = enableStreamingCompression;
        
        // Create temp directory
        this.tempUploadDir = Paths.get(plugin.getDataFolder().getAbsolutePath(), tempUploadPath);
        try {
            Files.createDirectories(tempUploadDir);
            plugin.getLogger().info("Chunked upload temp directory: " + tempUploadDir.toString());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create temp upload directory: " + e.getMessage());
        }
        
        // Start cleanup task
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredUploads, 5, 30, TimeUnit.MINUTES);
        
        plugin.getLogger().info("ChunkedUploadService initialized:");
        plugin.getLogger().info("- Chunk size: " + (chunkSize / 1024 / 1024) + "MB");
        plugin.getLogger().info("- Max file size: " + (maxFileSize / 1024 / 1024) + "MB");
        plugin.getLogger().info("- Temp directory: " + tempUploadDir.toString());
    }
    
    /**
     * Initialize a new chunked upload
     */
    public ChunkedUpload initializeUpload(String filename, long totalSize, boolean isPublic,
                                        Long ttlSeconds, Map<String, String> metadata,
                                        String uploaderIp, String uploaderAgent) {
        
        if (totalSize > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size: " + 
                (maxFileSize / 1024 / 1024) + "MB");
        }
        
        if (totalSize <= 0) {
            throw new IllegalArgumentException("Invalid file size: " + totalSize);
        }
        
        String uploadId = generateUploadId();
        
        ChunkedUpload upload = new ChunkedUpload(uploadId, filename, totalSize, chunkSize,
                isPublic, ttlSeconds, metadata, uploaderIp, uploaderAgent);
        
        activeUploads.put(uploadId, upload);
        
        // Create temp directory for this upload
        try {
            Path uploadPath = tempUploadDir.resolve(uploadId);
            Files.createDirectories(uploadPath);
            plugin.getLogger().info("Created upload directory: " + uploadPath.toString());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create upload directory for " + uploadId + ": " + e.getMessage());
            activeUploads.remove(uploadId);
            throw new RuntimeException("Failed to create upload directory", e);
        }
        
        plugin.getLogger().info("Initialized chunked upload: " + uploadId + " for file: " + filename + 
            " (" + (totalSize / 1024 / 1024) + "MB, " + upload.getTotalChunks() + " chunks)");
        return upload;
    }
    
    /**
     * Upload a chunk of data
     */
    public synchronized boolean uploadChunk(String uploadId, int chunkNumber, byte[] chunkData) {
        ChunkedUpload upload = activeUploads.get(uploadId);
        if (upload == null) {
            plugin.getLogger().warning("Upload not found: " + uploadId);
            return false;
        }
        
        if (upload.isExpired()) {
            plugin.getLogger().warning("Upload expired: " + uploadId);
            activeUploads.remove(uploadId);
            cleanupUploadFiles(uploadId);
            return false;
        }
        
        if (chunkNumber < 0 || chunkNumber >= upload.getTotalChunks()) {
            plugin.getLogger().warning("Invalid chunk number " + chunkNumber + " for upload " + uploadId + 
                " (expected 0-" + (upload.getTotalChunks() - 1) + ")");
            return false;
        }
        
        if (upload.hasChunk(chunkNumber)) {
            plugin.getLogger().info("Chunk already uploaded: " + chunkNumber + " for upload: " + uploadId);
            return true; // Already have this chunk
        }
        
        if (chunkData == null || chunkData.length == 0) {
            plugin.getLogger().warning("Empty chunk data for chunk " + chunkNumber + " in upload " + uploadId);
            return false;
        }
        
        try {
            // Save chunk to temp file
            Path uploadPath = tempUploadDir.resolve(uploadId);
            Path chunkPath = uploadPath.resolve("chunk_" + String.format("%06d", chunkNumber));
            
            Files.write(chunkPath, chunkData, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            
            // Calculate checksum
            String checksum = calculateChecksum(chunkData);
            
            // Add chunk to upload
            upload.addChunk(chunkNumber, chunkData.length, checksum);
            
            plugin.getLogger().info("Uploaded chunk " + chunkNumber + "/" + upload.getTotalChunks() + 
                " for " + uploadId + " (" + String.format("%.1f", upload.getProgress()) + "%, " + 
                chunkData.length + " bytes)");
            
            // Check if upload is complete
            if (upload.isComplete()) {
                plugin.getLogger().info("Upload complete, finalizing: " + uploadId);
                return finalizeUpload(uploadId);
            }
            
            return true;
            
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save chunk " + chunkNumber + " for upload " + uploadId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Finalize upload by combining all chunks
     */
    private boolean finalizeUpload(String uploadId) {
        ChunkedUpload upload = activeUploads.get(uploadId);
        if (upload == null) {
            plugin.getLogger().warning("Upload not found during finalization: " + uploadId);
            return false;
        }
        
        plugin.getLogger().info("Finalizing upload: " + uploadId + " (" + upload.getFilename() + ")");
        
        try {
            Path uploadPath = tempUploadDir.resolve(uploadId);
            
            if (!Files.exists(uploadPath)) {
                plugin.getLogger().severe("Upload directory missing: " + uploadPath);
                return false;
            }
            
            // Combine all chunks using streaming to handle large files
            ByteArrayOutputStream combinedStream = new ByteArrayOutputStream();
            long totalBytesRead = 0;
            
            for (int i = 0; i < upload.getTotalChunks(); i++) {
                Path chunkPath = uploadPath.resolve("chunk_" + String.format("%06d", i));
                
                if (!Files.exists(chunkPath)) {
                    plugin.getLogger().severe("Missing chunk " + i + " for upload " + uploadId + 
                        " at path: " + chunkPath);
                    return false;
                }
                
                try {
                    byte[] chunkData = Files.readAllBytes(chunkPath);
                    combinedStream.write(chunkData);
                    totalBytesRead += chunkData.length;
                    
                    if (i % 10 == 0 || i == upload.getTotalChunks() - 1) {
                        plugin.getLogger().info("Combined chunk " + i + "/" + upload.getTotalChunks() + 
                            " (" + (totalBytesRead / 1024 / 1024) + "MB)");
                    }
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to read chunk " + i + " for upload " + uploadId + ": " + e.getMessage());
                    return false;
                }
            }
            
            byte[] finalData = combinedStream.toByteArray();
            
            // Verify file size
            if (finalData.length != upload.getTotalSize()) {
                plugin.getLogger().severe("File size mismatch for upload " + uploadId + 
                    ". Expected: " + upload.getTotalSize() + ", Got: " + finalData.length);
                return false;
            }
            
            plugin.getLogger().info("Successfully combined " + upload.getTotalChunks() + " chunks into " + 
                (finalData.length / 1024 / 1024) + "MB file for upload " + uploadId);
            
            // Store the final file
            StoredFile storedFile = fileStorage.storeFile(
                upload.getFilename(),
                finalData,
                upload.isPublic(),
                upload.getTtlSeconds(),
                upload.getUploaderIp(),
                upload.getUploaderAgent(),
                upload.getMetadata()
            );
            
            if (storedFile != null) {
                upload.markCompleted(storedFile.getId());
                plugin.getLogger().info("Successfully finalized upload " + uploadId + 
                    " as file " + storedFile.getId() + " (" + storedFile.getFilename() + ")");
                
                // Cleanup temp files
                cleanupUploadFiles(uploadId);
                
                return true;
            } else {
                plugin.getLogger().severe("Failed to store final file for upload " + uploadId + 
                    " - storage limit may be exceeded");
                return false;
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to finalize upload " + uploadId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get upload progress
     */
    public ChunkedUpload getUpload(String uploadId) {
        ChunkedUpload upload = activeUploads.get(uploadId);
        if (upload != null && upload.isExpired()) {
            activeUploads.remove(uploadId);
            cleanupUploadFiles(uploadId);
            return null;
        }
        return upload;
    }
    
    /**
     * Cancel an upload
     */
    public boolean cancelUpload(String uploadId) {
        ChunkedUpload upload = activeUploads.remove(uploadId);
        if (upload != null) {
            cleanupUploadFiles(uploadId);
            plugin.getLogger().info("Cancelled upload: " + uploadId + " (" + upload.getFilename() + ")");
            return true;
        }
        return false;
    }
    
    /**
     * Get missing chunks for resumable upload
     */
    public List<Integer> getMissingChunks(String uploadId) {
        ChunkedUpload upload = activeUploads.get(uploadId);
        if (upload == null) return Collections.emptyList();
        
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < upload.getTotalChunks(); i++) {
            if (!upload.hasChunk(i)) {
                missing.add(i);
            }
        }
        return missing;
    }
    
    /**
     * Get list of active uploads (for admin purposes)
     */
    public List<ChunkedUpload> getActiveUploads() {
        return new ArrayList<>(activeUploads.values());
    }
    
    private void cleanupExpiredUploads() {
        List<String> expiredUploads = new ArrayList<>();
        
        for (Map.Entry<String, ChunkedUpload> entry : activeUploads.entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredUploads.add(entry.getKey());
            }
        }
        
        for (String uploadId : expiredUploads) {
            ChunkedUpload upload = activeUploads.remove(uploadId);
            cleanupUploadFiles(uploadId);
            if (upload != null) {
                plugin.getLogger().info("Cleaned up expired upload: " + uploadId + " (" + upload.getFilename() + ")");
            }
        }
        
        if (!expiredUploads.isEmpty()) {
            plugin.getLogger().info("Cleaned up " + expiredUploads.size() + " expired uploads");
        }
    }
    
    private void cleanupUploadFiles(String uploadId) {
        try {
            Path uploadPath = tempUploadDir.resolve(uploadId);
            if (Files.exists(uploadPath)) {
                // Delete all files in the upload directory
                Files.walk(uploadPath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            plugin.getLogger().warning("Failed to delete: " + path + " - " + e.getMessage());
                        }
                    });
                plugin.getLogger().info("Cleaned up temp files for upload: " + uploadId);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to cleanup upload files for " + uploadId + ": " + e.getMessage());
        }
    }
    
    private String generateUploadId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to calculate checksum: " + e.getMessage());
            return "unknown";
        }
    }
    
    public void shutdown() {
        plugin.getLogger().info("Shutting down ChunkedUploadService...");
        
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
            }
        }
        
        // Cleanup all active uploads
        plugin.getLogger().info("Cleaning up " + activeUploads.size() + " active uploads...");
        for (String uploadId : activeUploads.keySet()) {
            cleanupUploadFiles(uploadId);
        }
        activeUploads.clear();
        
        plugin.getLogger().info("ChunkedUploadService shutdown completed");
    }
    
    // Statistics
    public int getActiveUploadsCount() {
        return activeUploads.size();
    }
    
    public long getTotalUploadingBytes() {
        return activeUploads.values().stream()
            .mapToLong(ChunkedUpload::getUploadedBytes)
            .sum();
    }
}
