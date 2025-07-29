package dev.arubik.blobcraft;

import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.arubik.blobcraft.models.StoredFile;
import dev.arubik.blobcraft.server.HttpServerWrapper;
import dev.arubik.blobcraft.services.ChunkedUploadService;
import dev.arubik.blobcraft.storage.FileStorage;

public class Main extends JavaPlugin {
    private FileStorage fileStorage;
    private HttpServerWrapper httpServer;
    private ChunkedUploadService chunkedUploadService;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        
        // Load configuration
        FileConfiguration config = getConfig();
        
        // Log configuration loading
        getLogger().info("Loading BlobCraft configuration...");
        
        try {
            // Storage configuration
            long maxRam = config.getLong("storage.max-ram", 1073741824L); // 1GB default
            long maxStorage = config.getLong("storage.max-storage", 10737418240L); // 10GB default
            boolean enableExpiration = config.getBoolean("storage.enable-expiration", true);
            long defaultTtl = config.getLong("storage.default-ttl", 86400L); // 24 hours
            long maxTtl = config.getLong("storage.max-ttl", 604800L); // 7 days
            long cleanupInterval = config.getLong("storage.cleanup-interval", 3600L); // 1 hour
            
            // Compression settings
            boolean enableCompression = config.getBoolean("storage.compression.enabled", true);
            int compressionLevel = config.getInt("storage.compression.level", 6);
            long compressThreshold = config.getLong("storage.compression.threshold", 1024L);
            
            // Initialize file storage with correct parameters
            fileStorage = new FileStorage(
                maxRam,
                maxStorage,
                enableExpiration,
                defaultTtl,
                maxTtl,
                cleanupInterval,
                enableCompression,
                compressionLevel,
                compressThreshold,
                this
            );
            
            // Chunked upload configuration
            boolean chunkedUploadEnabled = config.getBoolean("chunked-upload.enabled", true);
            if (chunkedUploadEnabled) {
                int chunkSize = config.getInt("chunked-upload.chunk-size", 12582912); // 8MB
                String tempUploadPath = config.getString("chunked-upload.temp-dir", "temp/uploads");
                long maxFileSize = config.getLong("chunked-upload.max-file-size", 5368709120L); // 5GB
                boolean enableStreamingCompression = config.getBoolean("chunked-upload.streaming-compression", false);
                
                chunkedUploadService = new ChunkedUploadService(
                    fileStorage,
                    this,
                    tempUploadPath,
                    chunkSize,
                    maxFileSize,
                    enableStreamingCompression
                );
                
                getLogger().info("Chunked upload service initialized:");
                getLogger().info("- Chunk size: " + (chunkSize / 1024 / 1024) + "MB");
                getLogger().info("- Max file size: " + (maxFileSize / 1024 / 1024) + "MB");
                getLogger().info("- Temp directory: " + tempUploadPath);
            }
            
            // Server configuration
            int port = config.getInt("server.port", 8080);
            String bindAddress = config.getString("server.bind-address", "0.0.0.0");
            String accessKey = config.getString("server.access-key", "your-secret-key-here");
            int maxThreads = config.getInt("server.max-threads", 10);
            boolean enableCors = config.getBoolean("server.enable-cors", true);
            boolean logRequests = config.getBoolean("server.log-requests", true);
            
            // Timeout settings
            int readTimeout = config.getInt("server.read-timeout", 30000);
            int writeTimeout = config.getInt("server.write-timeout", 30000);
            int idleTimeout = config.getInt("server.idle-timeout", 60000);
            
            // Request limits
            long maxRequestSize = config.getLong("server.max-request-size", 104857600L); // 100MB
            int bufferSize = config.getInt("server.buffer-size", 8192);
            
            // Rate limiting
            boolean rateLimitEnabled = config.getBoolean("server.rate-limit.enabled", false);
            int rateLimitRequests = config.getInt("server.rate-limit.requests", 100);
            int rateLimitWindow = config.getInt("server.rate-limit.window", 60);
            
            // Dashboard settings
            boolean enableDashboard = config.getBoolean("dashboard.enabled", true);
            String dashboardPath = config.getString("dashboard.path", "/dashboard");
            boolean dashboardAuth = config.getBoolean("dashboard.require-auth", true);
            
            // Progress settings
            boolean progressEnabled = config.getBoolean("chunked-upload.progress.enabled", true);
            long progressUpdateInterval = config.getLong("chunked-upload.progress.update-interval", 1000L);
            
            // Initialize HTTP server with correct parameters
            httpServer = new HttpServerWrapper(
                port,
                bindAddress,
                accessKey,
                maxThreads,
                enableCors,
                logRequests,
                fileStorage,
                this,
                enableDashboard,
                dashboardPath,
                dashboardAuth,
                chunkedUploadService,
                readTimeout,
                writeTimeout,
                idleTimeout,
                maxRequestSize,
                bufferSize,
                rateLimitEnabled,
                rateLimitRequests,
                rateLimitWindow,
                progressEnabled,
                progressUpdateInterval
            );
            
            // Start HTTP server
            httpServer.start();
            
            // Log configuration details
            getLogger().info("BlobCraft has been enabled successfully!");
            getLogger().info("Configuration loaded:");
            getLogger().info("- Server: " + bindAddress + ":" + port);
            getLogger().info("- Max RAM: " + (maxRam / 1024 / 1024) + "MB");
            getLogger().info("- Max Storage: " + (maxStorage / 1024 / 1024 / 1024) + "GB");
            getLogger().info("- Compression: " + (enableCompression ? "enabled (level " + compressionLevel + ")" : "disabled"));
            getLogger().info("- Dashboard: " + (enableDashboard ? "enabled at " + dashboardPath : "disabled"));
            getLogger().info("- Chunked Upload: " + (chunkedUploadEnabled ? "enabled" : "disabled"));
            
            // Validate access key
            if ("your-secret-key-here".equals(accessKey)) {
                getLogger().warning("WARNING: You are using the default access key! Please change it in config.yml");
            }
            
            getLogger().info("HTTP server is running on http://" + bindAddress + ":" + port);
            if (enableDashboard) {
                getLogger().info("Dashboard available at: http://" + bindAddress + ":" + port + dashboardPath);
            }
            
        } catch (Exception e) {
            getLogger().severe("Failed to initialize BlobCraft: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down BlobCraft...");
        
        // Stop HTTP server
        if (httpServer != null) {
            httpServer.stop();
        }
        
        // Stop chunked upload service
        if (chunkedUploadService != null) {
            chunkedUploadService.shutdown();
        }
        
        // Stop file storage
        if (fileStorage != null) {
            fileStorage.shutdown();
        }
        
        getLogger().info("BlobCraft has been disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("blobcraft")) {
            return false;
        }

        if (!sender.hasPermission("blobcraft.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                handleStatusCommand(sender);
                break;
                
            case "stats":
                handleStatsCommand(sender);
                break;
                
            case "list":
                handleListCommand(sender, args);
                break;
                
            case "delete":
                handleDeleteCommand(sender, args);
                break;
                
            case "info":
                handleInfoCommand(sender, args);
                break;
                
            case "cleanup":
                handleCleanupCommand(sender);
                break;
                
            case "reload":
                handleReloadCommand(sender);
                break;
                
            case "uploads":
                handleUploadsCommand(sender);
                break;
                
            default:
                sendHelpMessage(sender);
                break;
        }

        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== BlobCraft Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/blobcraft status" + ChatColor.WHITE + " - Show server status");
        sender.sendMessage(ChatColor.YELLOW + "/blobcraft stats" + ChatColor.WHITE + " - Show storage statistics");
        sender.sendMessage(ChatColor.YELLOW + "/blobcraft list [page]" + ChatColor.WHITE + " - List stored files");
        sender.sendMessage(ChatColor.YELLOW + "/blobcraft delete <id>" + ChatColor.WHITE + " - Delete a file");
        sender.sendMessage(ChatColor.YELLOW + "/blobcraft info <id>" + ChatColor.WHITE + " - Show file information");
        sender.sendMessage(ChatColor.YELLOW + "/blobcraft cleanup" + ChatColor.WHITE + " - Run cleanup manually");
        sender.sendMessage(ChatColor.YELLOW + "/blobcraft uploads" + ChatColor.WHITE + " - Show active uploads");
        sender.sendMessage(ChatColor.YELLOW + "/blobcraft reload" + ChatColor.WHITE + " - Reload configuration");
    }

    private void handleStatusCommand(CommandSender sender) {
        try {
            sender.sendMessage(ChatColor.GOLD + "=== BlobCraft Status ===");
            sender.sendMessage(ChatColor.GREEN + "✓ Plugin: " + ChatColor.WHITE + "Running");
            sender.sendMessage(ChatColor.GREEN + "✓ HTTP Server: " + ChatColor.WHITE + "Active");
            sender.sendMessage(ChatColor.GREEN + "✓ File Storage: " + ChatColor.WHITE + "Operational");
            
            if (chunkedUploadService != null) {
                sender.sendMessage(ChatColor.GREEN + "✓ Chunked Upload: " + ChatColor.WHITE + "Available");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "⚠ Chunked Upload: " + ChatColor.WHITE + "Disabled");
            }
            
            FileConfiguration config = getConfig();
            String bindAddress = config.getString("server.bind-address", "0.0.0.0");
            int port = config.getInt("server.port", 8080);
            sender.sendMessage(ChatColor.BLUE + "Server URL: " + ChatColor.WHITE + "http://" + bindAddress + ":" + port);
            
            if (config.getBoolean("dashboard.enabled", true)) {
                String dashboardPath = config.getString("dashboard.path", "/dashboard");
                sender.sendMessage(ChatColor.BLUE + "Dashboard: " + ChatColor.WHITE + "http://" + bindAddress + ":" + port + dashboardPath);
            }
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error getting status: " + e.getMessage());
        }
    }

    private void handleStatsCommand(CommandSender sender) {
        try {
            sender.sendMessage(ChatColor.GOLD + "=== Storage Statistics ===");
            sender.sendMessage(ChatColor.BLUE + "Files: " + ChatColor.WHITE + fileStorage.getFileCount());
            sender.sendMessage(ChatColor.BLUE + "Memory Used: " + ChatColor.WHITE + formatBytes(fileStorage.getUsedMemory()) + " / " + formatBytes(fileStorage.getMaxRam()));
            sender.sendMessage(ChatColor.BLUE + "Storage Used: " + ChatColor.WHITE + formatBytes(fileStorage.getUsedStorage()) + " / " + formatBytes(fileStorage.getMaxStorage()));
            
            double memoryPercent = (double) fileStorage.getUsedMemory() / fileStorage.getMaxRam() * 100;
            double storagePercent = (double) fileStorage.getUsedStorage() / fileStorage.getMaxStorage() * 100;
            
            sender.sendMessage(ChatColor.BLUE + "Memory Usage: " + ChatColor.WHITE + String.format("%.1f%%", memoryPercent));
            sender.sendMessage(ChatColor.BLUE + "Storage Usage: " + ChatColor.WHITE + String.format("%.1f%%", storagePercent));
            
            if (chunkedUploadService != null) {
                sender.sendMessage(ChatColor.GOLD + "=== Upload Statistics ===");
                sender.sendMessage(ChatColor.BLUE + "Active Uploads: " + ChatColor.WHITE + chunkedUploadService.getActiveUploadsCount());
                sender.sendMessage(ChatColor.BLUE + "Uploading Bytes: " + ChatColor.WHITE + formatBytes(chunkedUploadService.getTotalUploadingBytes()));
            }
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error getting statistics: " + e.getMessage());
        }
    }

    private void handleListCommand(CommandSender sender, String[] args) {
        try {
            int page = 1;
            if (args.length > 1) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid page number: " + args[1]);
                    return;
                }
            }
            
            List<StoredFile> files = fileStorage.listFiles(page, 10, null);
            int totalFiles = fileStorage.getTotalFiles(null);
            int totalPages = (int) Math.ceil((double) totalFiles / 10);
            
            sender.sendMessage(ChatColor.GOLD + "=== Files (Page " + page + "/" + totalPages + ") ===");
            
            if (files.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No files found.");
                return;
            }
            
            for (StoredFile file : files) {
                String status = file.isPublic() ? ChatColor.GREEN + "Public" : ChatColor.RED + "Private";
                String compressed = file.isCompressed() ? ChatColor.BLUE + " [Compressed]" : "";
                sender.sendMessage(ChatColor.WHITE + file.getId() + " - " + file.getFilename() + 
                    " (" + formatBytes(file.getSize()) + ") " + status + compressed);
            }
            
            if (page < totalPages) {
                sender.sendMessage(ChatColor.GRAY + "Use /blobcraft list " + (page + 1) + " for next page");
            }
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error listing files: " + e.getMessage());
        }
    }

    private void handleDeleteCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /blobcraft delete <file-id>");
            return;
        }
        
        String fileId = args[1];
        
        try {
            StoredFile file = fileStorage.getFile(fileId);
            if (file == null) {
                sender.sendMessage(ChatColor.RED + "File not found: " + fileId);
                return;
            }
            
            boolean success = fileStorage.deleteFile(fileId);
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "File deleted successfully: " + file.getFilename());
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to delete file: " + fileId);
            }
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error deleting file: " + e.getMessage());
        }
    }

    private void handleInfoCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /blobcraft info <file-id>");
            return;
        }
        
        String fileId = args[1];
        
        try {
            StoredFile file = fileStorage.getFile(fileId);
            if (file == null) {
                sender.sendMessage(ChatColor.RED + "File not found: " + fileId);
                return;
            }
            
            sender.sendMessage(ChatColor.GOLD + "=== File Information ===");
            sender.sendMessage(ChatColor.BLUE + "ID: " + ChatColor.WHITE + file.getId());
            sender.sendMessage(ChatColor.BLUE + "Filename: " + ChatColor.WHITE + file.getFilename());
            sender.sendMessage(ChatColor.BLUE + "Size: " + ChatColor.WHITE + formatBytes(file.getSize()));
            sender.sendMessage(ChatColor.BLUE + "Original Size: " + ChatColor.WHITE + formatBytes(file.getOriginalSize()));
            sender.sendMessage(ChatColor.BLUE + "MIME Type: " + ChatColor.WHITE + file.getMimeType());
            sender.sendMessage(ChatColor.BLUE + "Uploaded: " + ChatColor.WHITE + file.getUploadedAt());
            sender.sendMessage(ChatColor.BLUE + "Public: " + ChatColor.WHITE + (file.isPublic() ? "Yes" : "No"));
            sender.sendMessage(ChatColor.BLUE + "Compressed: " + ChatColor.WHITE + (file.isCompressed() ? "Yes" : "No"));
            sender.sendMessage(ChatColor.BLUE + "Uploader IP: " + ChatColor.WHITE + file.getUploaderIp());
            
            if (file.getExpiresAt() != null) {
                sender.sendMessage(ChatColor.BLUE + "Expires: " + ChatColor.WHITE + file.getExpiresAt());
            }
            
            if (file.getMetadata() != null && !file.getMetadata().isEmpty()) {
                sender.sendMessage(ChatColor.GOLD + "=== Metadata ===");
                for (Map.Entry<String, String> entry : file.getMetadata().entrySet()) {
                    sender.sendMessage(ChatColor.BLUE + entry.getKey() + ": " + ChatColor.WHITE + entry.getValue());
                }
            }
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error getting file info: " + e.getMessage());
        }
    }

    private void handleCleanupCommand(CommandSender sender) {
        try {
            sender.sendMessage(ChatColor.YELLOW + "Running cleanup...");
            // Call the cleanup method that exists in FileStorage
            fileStorage.cleanupExpiredFiles();
            sender.sendMessage(ChatColor.GREEN + "Cleanup completed successfully.");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error during cleanup: " + e.getMessage());
        }
    }

    private void handleUploadsCommand(CommandSender sender) {
        if (chunkedUploadService == null) {
            sender.sendMessage(ChatColor.RED + "Chunked upload service is not available.");
            return;
        }
        
        try {
            int activeCount = chunkedUploadService.getActiveUploadsCount();
            long uploadingBytes = chunkedUploadService.getTotalUploadingBytes();
            
            sender.sendMessage(ChatColor.GOLD + "=== Active Uploads ===");
            sender.sendMessage(ChatColor.BLUE + "Active Uploads: " + ChatColor.WHITE + activeCount);
            sender.sendMessage(ChatColor.BLUE + "Total Uploading: " + ChatColor.WHITE + formatBytes(uploadingBytes));
            
            if (activeCount == 0) {
                sender.sendMessage(ChatColor.YELLOW + "No active uploads.");
            }
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error getting active uploads: " + e.getMessage());
        }
    }

    private void handleReloadCommand(CommandSender sender) {
        try {
            sender.sendMessage(ChatColor.YELLOW + "Reloading configuration...");
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
            sender.sendMessage(ChatColor.YELLOW + "Note: Some changes may require a server restart to take effect.");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading configuration: " + e.getMessage());
        }
    }

    private String formatBytes(long bytes) {
        if (bytes == 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.1f %s", size, units[unitIndex]);
    }

    // Getters for other classes
    public FileStorage getFileStorage() {
        return fileStorage;
    }

    public ChunkedUploadService getChunkedUploadService() {
        return chunkedUploadService;
    }
}
