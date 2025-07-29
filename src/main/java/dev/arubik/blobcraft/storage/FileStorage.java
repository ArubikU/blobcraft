package dev.arubik.blobcraft.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.arubik.blobcraft.models.StoredFile;

public class FileStorage {
    
    private final long maxRam;
    private final long maxStorage;
    
    // File index - only metadata, not actual file data
    private final Map<String, FileIndex> fileIndex;
    
    // LRU cache for frequently accessed files
    private final Map<String, CachedFile> fileCache;
    private final int maxCacheSize = 100; // Maximum files to keep in memory
    
    private long usedStorage;
    private long usedMemory;
    
    // Expiration settings
    private final boolean enableExpiration;
    private final long defaultTtl;
    private final long maxTtl;
    private final ScheduledExecutorService cleanupExecutor;
    
    // Compression settings
    private final boolean enableCompression;
    private final int compressionLevel;
    private final long compressThreshold;
    
    // File persistence
    private final Path storageDirectory;
    private final Path indexFile;
    private final Gson gson = new Gson();
    
    private final JavaPlugin plugin;
    
    // Inner classes for indexing
    private static class FileIndex {
        public String id;
        public String filename;
        public long size;
        public long originalSize;
        public boolean isPublic;
        public boolean isCompressed;
        public String mimeType;
        public Instant uploadedAt;
        public Instant expiresAt;
        public String uploaderIp;
        public String uploaderAgent;
        public Map<String, String> metadata;
        public String diskPath; // Path to file on disk
        
        public FileIndex() {}
        
        public FileIndex(StoredFile file, String diskPath) {
            this.id = file.getId();
            this.filename = file.getFilename();
            this.size = file.getSize();
            this.originalSize = file.getOriginalSize();
            this.isPublic = file.isPublic();
            this.isCompressed = file.isCompressed();
            this.mimeType = file.getMimeType();
            this.uploadedAt = file.getUploadedAt();
            this.expiresAt = file.getExpiresAt();
            this.uploaderIp = file.getUploaderIp();
            this.uploaderAgent = file.getUploaderAgent();
            this.metadata = file.getMetadata();
            this.diskPath = diskPath;
        }
        
        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
        
        public StoredFile toStoredFile(byte[] data) {
            return new StoredFile(id, filename, data, isPublic, uploadedAt, expiresAt, 
                isCompressed, originalSize, uploaderIp, uploaderAgent, metadata);
        }

        public JsonElement toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            json.addProperty("filename", filename);
            json.addProperty("size", size);
            json.addProperty("originalSize", originalSize);
            json.addProperty("isPublic", isPublic);
            json.addProperty("isCompressed", isCompressed);
            json.addProperty("mimeType", mimeType);
            json.addProperty("uploadedAt", uploadedAt.toString());
            if (expiresAt != null) {
                json.addProperty("expiresAt", expiresAt.toString());
            }
            json.addProperty("uploaderIp", uploaderIp);
            json.addProperty("uploaderAgent", uploaderAgent);
            if (metadata != null) {
                JsonObject metaJson = new JsonObject();
                for (Map.Entry<String, String> entry : metadata.entrySet()) {
                    metaJson.addProperty(entry.getKey(), entry.getValue());
                }
                json.add("metadata", metaJson);
            }
            json.addProperty("diskPath", diskPath);
            return json;
        }

        public static FileIndex fromJson(JsonObject json) {
            FileIndex index = new FileIndex();
            index.id = json.get("id").getAsString();
            index.filename = json.get("filename").getAsString();
            index.size = json.get("size").getAsLong();
            index.originalSize = json.get("originalSize").getAsLong();
            index.isPublic = json.get("isPublic").getAsBoolean();
            index.isCompressed = json.get("isCompressed").getAsBoolean();
            index.mimeType = json.get("mimeType").getAsString();
            index.uploadedAt = Instant.parse(json.get("uploadedAt").getAsString());
            if (json.has("expiresAt") && !json.get("expiresAt").isJsonNull()) {
                index.expiresAt = Instant.parse(json.get("expiresAt").getAsString());
            }
            index.uploaderIp = json.get("uploaderIp").getAsString();
            index.uploaderAgent = json.get("uploaderAgent").getAsString();
            
            if (json.has("metadata")) {
                JsonObject metaJson = json.getAsJsonObject("metadata");
                index.metadata = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : metaJson.entrySet()) {
                    index.metadata.put(entry.getKey(), entry.getValue().getAsString());
                }
            } else {
                index.metadata = new HashMap<>();
            }
            
            index.diskPath = json.get("diskPath").getAsString();
            return index;
        }
    }
    
    private static class CachedFile {
        public final byte[] data;
        public final long lastAccessed;
        
        public CachedFile(byte[] data) {
            this.data = data;
            this.lastAccessed = System.currentTimeMillis();
        }
    }
    
    public FileStorage(long maxRam, long maxStorage, boolean enableExpiration, 
                      long defaultTtl, long maxTtl, long cleanupInterval,
                      boolean enableCompression, int compressionLevel, 
                      long compressThreshold, JavaPlugin plugin) {
        this.maxRam = maxRam;
        this.maxStorage = maxStorage;
        this.fileIndex = new ConcurrentHashMap<>();
        this.fileCache = new ConcurrentHashMap<>();
        this.usedStorage = 0;
        this.usedMemory = 0;
        
        this.enableExpiration = enableExpiration;
        this.defaultTtl = defaultTtl;
        this.maxTtl = maxTtl;
        this.plugin = plugin;
        
        this.enableCompression = enableCompression;
        this.compressionLevel = Math.max(1, Math.min(9, compressionLevel));
        this.compressThreshold = compressThreshold;
        
        // Initialize storage directory
        this.storageDirectory = Paths.get(plugin.getDataFolder().getAbsolutePath(), "storage");
        this.indexFile = storageDirectory.resolve("file_index.json");
        
        try {
            Files.createDirectories(storageDirectory);
            loadFileIndex();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create storage directory: " + e.getMessage());
        }
        
        // Start cleanup task if expiration is enabled
        if (enableExpiration && cleanupInterval > 0) {
            this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
            this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredFiles, 
                cleanupInterval, 
                cleanupInterval, 
                TimeUnit.SECONDS
            );
        } else {
            this.cleanupExecutor = null;
        }
        
        // Start cache cleanup task
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
            this::cleanupCache, 5, 5, TimeUnit.MINUTES
        );
        
        plugin.getLogger().info("FileStorage initialized:");
        plugin.getLogger().info("- Storage directory: " + storageDirectory.toString());
        plugin.getLogger().info("- Max RAM: " + (maxRam / 1024 / 1024) + "MB");
        plugin.getLogger().info("- Max Storage: " + (maxStorage / 1024 / 1024 / 1024) + "GB");
        plugin.getLogger().info("- Compression: " + (enableCompression ? "enabled" : "disabled"));
        plugin.getLogger().info("- Expiration: " + (enableExpiration ? "enabled" : "disabled"));
        plugin.getLogger().info("- Files indexed: " + fileIndex.size());
    }
    
    private void loadFileIndex() {
        try {
            if (!Files.exists(indexFile)) {
                plugin.getLogger().info("No existing index file found, scanning storage directory...");
                scanStorageDirectory();
                return;
            }
            
            String indexContent = Files.readString(indexFile);
            JsonObject indexJson = gson.fromJson(indexContent, JsonObject.class);
            
            JsonArray filesArray = indexJson.getAsJsonArray("files");
            long totalStorage = 0;
            
            for (int i = 0; i < filesArray.size(); i++) {
                JsonObject fileJson = filesArray.get(i).getAsJsonObject();
                FileIndex index = FileIndex.fromJson(fileJson);
                
                // Verify file still exists on disk
                Path filePath = storageDirectory.resolve(index.diskPath);
                if (Files.exists(filePath)) {
                    fileIndex.put(index.id, index);
                    totalStorage += index.size;
                } else {
                    plugin.getLogger().warning("File missing from disk: " + index.diskPath);
                }
            }
            
            this.usedStorage = totalStorage;
            plugin.getLogger().info("Loaded " + fileIndex.size() + " files from index (" + 
                (usedStorage / 1024 / 1024) + "MB total)");
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load file index: " + e.getMessage());
            scanStorageDirectory();
        }
    }
    
    private void scanStorageDirectory() {
        try {
            if (!Files.exists(storageDirectory)) {
                return;
            }
            
            plugin.getLogger().info("Scanning storage directory for files...");
            long totalStorage = 0;
            int fileCount = 0;
            
            Files.walk(storageDirectory)
                .filter(Files::isRegularFile)
                .filter(path -> !path.equals(indexFile))
                .filter(path -> !path.getFileName().toString().endsWith(".meta"))
                .forEach(filePath -> {
                    try {
                        FileIndex index = createIndexFromDiskFile(filePath);
                        if (index != null) {
                            fileIndex.put(index.id, index);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to index file " + filePath + ": " + e.getMessage());
                    }
                });
            
            for (FileIndex index : fileIndex.values()) {
                totalStorage += index.size;
                fileCount++;
            }
            
            this.usedStorage = totalStorage;
            plugin.getLogger().info("Scanned " + fileCount + " files (" + (totalStorage / 1024 / 1024) + "MB total)");
            saveFileIndex();
            
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to scan storage directory: " + e.getMessage());
        }
    }
    
    private FileIndex createIndexFromDiskFile(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        
        // Extract ID from filename (format: {id}_{originalname})
        int underscoreIndex = fileName.indexOf('_');
        if (underscoreIndex == -1) {
            return null; // Invalid filename format
        }
        
        String id = fileName.substring(0, underscoreIndex);
        String originalName = fileName.substring(underscoreIndex + 1);
        
        // Read file size
        long size = Files.size(filePath);
        
        // Try to read metadata file
        Path metaPath = filePath.resolveSibling(fileName + ".meta");
        Map<String, String> metadata = new HashMap<>();
        boolean isPublic = false;
        boolean isCompressed = false;
        long originalSize = size;
        Instant uploadedAt = Instant.now();
        Instant expiresAt = null;
        String uploaderIp = "unknown";
        String uploaderAgent = "unknown";
        String mimeType = "application/octet-stream";
        
        if (Files.exists(metaPath)) {
            List<String> metaLines = Files.readAllLines(metaPath);
            for (String line : metaLines) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    
                    switch (key) {
                        case "public":
                            isPublic = Boolean.parseBoolean(value);
                            break;
                        case "compressed":
                            isCompressed = Boolean.parseBoolean(value);
                            break;
                        case "originalSize":
                            originalSize = Long.parseLong(value);
                            break;
                        case "uploadedAt":
                            uploadedAt = Instant.parse(value);
                            break;
                        case "expiresAt":
                            if (!"null".equals(value)) {
                                expiresAt = Instant.parse(value);
                            }
                            break;
                        case "uploaderIp":
                            uploaderIp = value;
                            break;
                        case "uploaderAgent":
                            uploaderAgent = value;
                            break;
                        case "mimeType":
                            mimeType = value;
                            break;
                        default:
                            metadata.put(key, value);
                            break;
                    }
                }
            }
        }
        
        FileIndex index = new FileIndex();
        index.id = id;
        index.filename = originalName;
        index.size = size;
        index.originalSize = originalSize;
        index.isPublic = isPublic;
        index.isCompressed = isCompressed;
        index.mimeType = mimeType;
        index.uploadedAt = uploadedAt;
        index.expiresAt = expiresAt;
        index.uploaderIp = uploaderIp;
        index.uploaderAgent = uploaderAgent;
        index.metadata = metadata;
        index.diskPath = fileName;
        
        return index;
    }
    
    private void saveFileIndex() {
        try {
            JsonObject indexJson = new JsonObject();
            JsonArray filesArray = new JsonArray();
            
            for (FileIndex index : fileIndex.values()) {
                filesArray.add(index.toJson());
            }
            
            indexJson.add("files", filesArray);
            indexJson.addProperty("lastUpdated", Instant.now().toString());
            indexJson.addProperty("totalFiles", fileIndex.size());
            indexJson.addProperty("totalStorage", usedStorage);
            
            Files.writeString(indexFile, gson.toJson(indexJson), 
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save file index: " + e.getMessage());
        }
    }
    
    public synchronized StoredFile storeFile(String filename, byte[] data, boolean isPublic, 
                                           Long ttlSeconds, String uploaderIp, String uploaderAgent,
                                           Map<String, String> metadata) {
        // Calculate expiration
        Instant expiresAt = null;
        if (enableExpiration && ttlSeconds != null && ttlSeconds > 0) {
            long actualTtl = Math.min(ttlSeconds, maxTtl);
            expiresAt = Instant.now().plusSeconds(actualTtl);
        } else if (enableExpiration && defaultTtl > 0) {
            expiresAt = Instant.now().plusSeconds(defaultTtl);
        }
        
        // Compress if enabled and file is large enough
        byte[] finalData = data;
        boolean compressed = false;
        long originalSize = data.length;
        
if (enableCompression && data.length >= compressThreshold) {
    long freeMemory = Runtime.getRuntime().freeMemory();
    long totalMemory = Runtime.getRuntime().totalMemory();
    long maxMemory = Runtime.getRuntime().maxMemory();
    long usedMemory = totalMemory - freeMemory;
    long availableMemory = maxMemory - usedMemory;

    // Require at least 2x the size of the original data
    if (availableMemory >= (long) data.length * 2) {
        try {
            finalData = compressData(data);
            compressed = true;
            plugin.getLogger().info("Compressed file " + filename + " from " +
                    originalSize + " to " + finalData.length + " bytes");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to compress file " + filename + ": " + e.getMessage());
            finalData = data;
        }
    } else {
        plugin.getLogger().warning("Skipping compression of file " + filename + 
            " due to insufficient memory (needed: " + (data.length * 2 / 1024 / 1024) +
            "MB, available: " + (availableMemory / 1024 / 1024) + "MB)");
        finalData = data;
    }
}

        long fileSize = finalData.length;
        
        // Check storage limits
        if (maxStorage > 0 && usedStorage + fileSize > maxStorage) {
            plugin.getLogger().warning("Storage limit exceeded for file: " + filename);
            return null; // Storage limit exceeded
        }
        
        // Generate unique ID
        String id = generateUniqueId();
        
        // Create stored file
        StoredFile storedFile = new StoredFile(id, filename, finalData, isPublic, 
            Instant.now(), expiresAt, compressed, originalSize, uploaderIp, uploaderAgent, metadata);
        
        // Save to disk
        String diskPath = id + "_" + filename;
        try {
            saveFileToDisk(storedFile, finalData, diskPath);
            System.gc(); // Suggest garbage collection after large file operations
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save file to disk: " + e.getMessage());
            return null;
        }
        
        // Create index entry
        FileIndex index = new FileIndex(storedFile, diskPath);
        fileIndex.put(id, index);
        usedStorage += fileSize;
        
        // Add to cache
        fileCache.put(id, new CachedFile(finalData));
        usedMemory += fileSize;
        
        // Save updated index
        saveFileIndex();
        
        plugin.getLogger().info("Stored file: " + filename + " (ID: " + id + ", Size: " + fileSize + " bytes)");
        
        return storedFile;
    }
    
    private void saveFileToDisk(StoredFile storedFile, byte[] data, String diskPath) throws IOException {
        // Save file data
        Path filePath = storageDirectory.resolve(diskPath);
        Files.write(filePath, data, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        
        // Save metadata
        Path metaPath = storageDirectory.resolve(diskPath + ".meta");
        List<String> metaLines = new ArrayList<>();
        metaLines.add("public=" + storedFile.isPublic());
        metaLines.add("compressed=" + storedFile.isCompressed());
        metaLines.add("originalSize=" + storedFile.getOriginalSize());
        metaLines.add("uploadedAt=" + storedFile.getUploadedAt().toString());
        metaLines.add("expiresAt=" + (storedFile.getExpiresAt() != null ? storedFile.getExpiresAt().toString() : "null"));
        metaLines.add("uploaderIp=" + storedFile.getUploaderIp());
        metaLines.add("uploaderAgent=" + (storedFile.getUploaderAgent() != null ? storedFile.getUploaderAgent() : "unknown"));
        metaLines.add("mimeType=" + storedFile.getMimeType());
        
        // Add custom metadata
        if (storedFile.getMetadata() != null) {
            for (Map.Entry<String, String> entry : storedFile.getMetadata().entrySet()) {
                metaLines.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        
        Files.write(metaPath, metaLines, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }
    
    public StoredFile getFile(String id) {
        FileIndex index = fileIndex.get(id);
        if (index == null) {
            return null;
        }
        
        if (index.isExpired()) {
            deleteFile(id); // Auto-cleanup expired file
            return null;
        }
        
        // Check cache first
        CachedFile cached = fileCache.get(id);
        if (cached != null) {
            byte[] data = cached.data;
            if (index.isCompressed) {
                try {
                    data = decompressData(data);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to decompress cached file " + id + ": " + e.getMessage());
                    return null;
                }
            }
            return index.toStoredFile(data);
        }
        
        // Load from disk
        try {
            Path filePath = storageDirectory.resolve(index.diskPath);
            if (!Files.exists(filePath)) {
                plugin.getLogger().warning("File missing from disk: " + index.diskPath);
                fileIndex.remove(id);
                saveFileIndex();
                return null;
            }
            
            byte[] data = Files.readAllBytes(filePath);
            
            // Add to cache if we have memory available
            if (usedMemory + data.length <= maxRam) {
                fileCache.put(id, new CachedFile(data));
                usedMemory += data.length;
            }
            
            return index.toStoredFile(data);
            
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load file from disk: " + e.getMessage());
            return null;
        }
    }
    
    public byte[] getFileData(String id) throws IOException {
        FileIndex index = fileIndex.get(id);
        if (index == null) {
            return null;
        }
        
        if (index.isExpired()) {
            deleteFile(id);
            return null;
        }
        
        // Check cache first
        CachedFile cached = fileCache.get(id);
        if (cached != null) {
            byte[] data = cached.data;
            if (index.isCompressed) {
                return decompressData(data);
            }
            return data;
        }
        
        // Load from disk
        Path filePath = storageDirectory.resolve(index.diskPath);
        if (!Files.exists(filePath)) {
            plugin.getLogger().warning("File missing from disk: " + index.diskPath);
            fileIndex.remove(id);
            saveFileIndex();
            return null;
        }
        
        byte[] data = Files.readAllBytes(filePath);
        
        // Add to cache if we have memory available
        if (usedMemory + data.length <= maxRam) {
            fileCache.put(id, new CachedFile(data));
            usedMemory += data.length;
        }
        
        if (index.isCompressed) {
            return decompressData(data);
        }
        
        return data;
    }
    
    public synchronized boolean deleteFile(String id) {
        FileIndex index = fileIndex.remove(id);
        if (index != null) {
            // Delete from disk
            try {
                Path filePath = storageDirectory.resolve(index.diskPath);
                Path metaPath = storageDirectory.resolve(index.diskPath + ".meta");
                
                Files.deleteIfExists(filePath);
                Files.deleteIfExists(metaPath);
                
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to delete file from disk: " + e.getMessage());
            }
            
            // Remove from cache
            CachedFile cached = fileCache.remove(id);
            if (cached != null) {
                usedMemory -= cached.data.length;
            }
            
            usedStorage -= index.size;
            saveFileIndex();
            
            plugin.getLogger().info("Deleted file: " + index.filename + " (ID: " + id + ")");
            return true;
        }
        return false;
    }
    
    public List<StoredFile> listFiles(int page, int pageSize, String extensionFilter) {
        List<FileIndex> allIndices = new ArrayList<>();
        
        // Filter out expired files and apply extension filter
        for (FileIndex index : fileIndex.values()) {
            if (index.isExpired()) {
                deleteFile(index.id); // Auto-cleanup
                continue;
            }
            
            if (extensionFilter != null && !extensionFilter.trim().isEmpty()) {
                String extension = getFileExtension(index.filename);
                if (!extensionFilter.equalsIgnoreCase(extension)) {
                    continue;
                }
            }
            
            allIndices.add(index);
        }
        
        // Sort by upload time (newest first)
        allIndices.sort((a, b) -> b.uploadedAt.compareTo(a.uploadedAt));
        
        // Apply pagination
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, allIndices.size());
        
        if (start >= allIndices.size()) {
            return new ArrayList<>();
        }
        
        List<StoredFile> result = new ArrayList<>();
        for (int i = start; i < end; i++) {
            FileIndex index = allIndices.get(i);
            // Create StoredFile without loading actual data (for listing purposes)
            StoredFile file = index.toStoredFile(new byte[0]); // Empty data for listing
            result.add(file);
        }
        
        return result;
    }
    public List<StoredFile> listFiles(int page, int pageSize, String extensionFilter, String inputFilter) {
        List<StoredFile> files = listFiles(page, pageSize, extensionFilter);
        
        if (inputFilter == null || inputFilter.trim().isEmpty()) {
            return files; // No filter applied
        }
        
        String filter = inputFilter.toLowerCase();
        List<StoredFile> filteredFiles = new ArrayList<>();
        
        for (StoredFile file : files) {
            if (file.getFilename().toLowerCase().contains(filter) || 
                file.getId().toLowerCase().contains(filter)) {
                filteredFiles.add(file);
            }
        }
        
        return filteredFiles;
    }
    public int getTotalFiles(String extensionFilter) {
        int count = 0;
        for (FileIndex index : fileIndex.values()) {
            if (index.isExpired()) {
                deleteFile(index.id); // Auto-cleanup
                continue;
            }
            
            if (extensionFilter == null || extensionFilter.trim().isEmpty()) {
                count++;
            } else {
                String extension = getFileExtension(index.filename);
                if (extensionFilter.equalsIgnoreCase(extension)) {
                    count++;
                }
            }
        }
        return count;
    }
    public int getTotalFiles(String extensionFilter, String inputFilter) {
        if (inputFilter == null || inputFilter.trim().isEmpty()) {
            return getTotalFiles(extensionFilter); // No filter applied
        }
        
        int count = 0;
        String filter = inputFilter.toLowerCase();
        
        for (FileIndex index : fileIndex.values()) {
            if (index.isExpired()) {
                deleteFile(index.id); // Auto-cleanup
                continue;
            }
            
            if (extensionFilter != null && !extensionFilter.trim().isEmpty()) {
                String extension = getFileExtension(index.filename);
                if (!extensionFilter.equalsIgnoreCase(extension)) {
                    continue;
                }
            }
            
            if (index.filename.toLowerCase().contains(filter) || index.id.toLowerCase().contains(filter)) {
                count++;
            }
        }
        
        return count;
    }
    public void cleanupExpiredFiles() {
        List<String> expiredIds = new ArrayList<>();
        
        for (FileIndex index : fileIndex.values()) {
            if (index.isExpired()) {
                expiredIds.add(index.id);
            }
        }
        
        if (!expiredIds.isEmpty()) {
            plugin.getLogger().info("Cleaning up " + expiredIds.size() + " expired files");
            for (String id : expiredIds) {
                deleteFile(id);
            }
        }
    }
    
    private void cleanupCache() {
        if (fileCache.size() <= maxCacheSize && usedMemory <= maxRam) {
            return;
        }
        
        // Sort by last accessed time and remove oldest entries
        List<Map.Entry<String, CachedFile>> entries = new ArrayList<>(fileCache.entrySet());
        entries.sort((a, b) -> Long.compare(a.getValue().lastAccessed, b.getValue().lastAccessed));
        
        int toRemove = Math.max(0, fileCache.size() - maxCacheSize);
        if (usedMemory > maxRam) {
            toRemove = Math.max(toRemove, fileCache.size() / 2); // Remove half if over memory limit
        }
        
        for (int i = 0; i < toRemove && i < entries.size(); i++) {
            Map.Entry<String, CachedFile> entry = entries.get(i);
            fileCache.remove(entry.getKey());
            usedMemory -= entry.getValue().data.length;
        }
        
        if (toRemove > 0) {
            plugin.getLogger().info("Cleaned up " + toRemove + " files from cache");
        }
    }
    
private byte[] compressData(byte[] data) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    // You can customize compression level with Deflater
    try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
        int chunkSize = 8192;
        for (int offset = 0; offset < data.length; offset += chunkSize) {
            int len = Math.min(chunkSize, data.length - offset);
            gzos.write(data, offset, len);
        }
    }
    return baos.toByteArray();
}


private byte[] decompressData(byte[] compressedData) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try (GZIPInputStream gzis = new GZIPInputStream(bais)) {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = gzis.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
    }

    return baos.toByteArray();
}

    
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) return "";
        return filename.substring(lastDot + 1).toLowerCase();
    }
    
    public int getFileCount() {
        return fileIndex.size();
    }
    
    public long getUsedMemory() {
        return usedMemory;
    }
    
    public long getUsedStorage() {
        return usedStorage;
    }
    
    public long getMaxRam() {
        return maxRam;
    }
    
    public long getMaxStorage() {
        return maxStorage;
    }
    
    public synchronized void clear() {
        fileIndex.clear();
        fileCache.clear();
        usedMemory = 0;
        usedStorage = 0;
        saveFileIndex();
    }
    
    public void shutdown() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
        }
        saveFileIndex();
        plugin.getLogger().info("FileStorage shutdown completed");
    }
    
    private String generateUniqueId() {
        String id;
        do {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        } while (fileIndex.containsKey(id));
        return id;
    }
}
