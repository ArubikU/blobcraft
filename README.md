# 🗄️ BlobCraft

A powerful Minecraft plugin that transforms your server into a blob storage HTTP server, perfect for local testing, development, and file management.

## ✨ Features

- 🚀 **HTTP API** - RESTful API for file operations
- 📁 **File Management** - Upload, download, list, and delete files
- ⏰ **File Expiration** - Automatic cleanup with configurable TTL
- 🗜️ **Compression** - Automatic gzip compression for large files
- 🌐 **Web Dashboard** - Beautiful web interface for file management
- 🔒 **Security** - Access key authentication and public/private files
- 📊 **Monitoring** - Real-time statistics and health checks
- 🎯 **TypeScript Client** - Full-featured client library
- 📱 **Responsive Design** - Works on desktop and mobile devices

## 🚀 Quick Start

### 1. Installation

1. Download the latest `BlobCraft-1.0.0.jar` from releases
2. Place it in your `plugins/` folder
3. Start your Minecraft server
4. Configure the plugin in `plugins/BlobCraft/config.yml`

### 2. Building from Source

```bash
# Clone the repository
git clone https://github.com/ArubikU/blobcraft.git
cd blobcraft

# Build the plugin
./gradlew shadowJar

# The JAR file will be in build/libs/
```

### 3. Configuration

Edit `plugins/BlobCraft/config.yml`:

```yaml
# BlobCraft Configuration File
# File storage and HTTP server settings for Minecraft plugin

# Server Configuration
server:
  # Network settings
  port: 9090                    # HTTP server port
  bind-address: "0.0.0.0"      # Bind address (0.0.0.0 for all interfaces, 127.0.0.1 for localhost only)
  access-key: "your-secret-key-here" # API access key for authentication
  
  # Performance settings
  max-threads: 10               # Maximum HTTP server threads
  enable-cors: true             # Enable CORS headers for web browser access
  log-requests: true            # Log all HTTP requests
  
  # Timeout settings (in milliseconds)
  read-timeout: 30000           # 30 seconds
  write-timeout: 30000          # 30 seconds
  idle-timeout: 60000           # 60 seconds
  
  # Request limits
  max-request-size: 104857600   # 100MB for regular uploads
  
  # Rate limiting
  rate-limit:
    enabled: false              # Enable rate limiting
    requests: 100               # Max requests per window
    window: 60                  # Time window in seconds

# Storage Configuration
storage:
  # Memory and disk limits
  max-ram: 1073741824          # 1GB RAM limit (0 = unlimited)
  max-storage: 10737418240     # 10GB storage limit (0 = unlimited)
  
  # File settings
  max-file-size: 5368709120    # 5GB max file size
  compression:
    enabled: true              # Enable file compression
    threshold: 1024            # Compress files larger than 1KB
    level: 6                   # Compression level (1-9)
  
  # File expiration
  default-ttl: 0               # 30 days default TTL (0 = no expiration)
  cleanup-interval: 300        # Cleanup expired files every 5 minutes

# Chunked Upload Configuration
chunked-upload:
  enabled: true                # Enable chunked upload support
  chunk-size: 12582912         # 12MB chunk size
  max-chunks: 1000            # Maximum chunks per upload
  upload-timeout: 3600        # Upload timeout in seconds (1 hour)
  temp-dir: "temp/uploads"    # Temporary directory for chunks
  
  # Progress tracking
  progress:
    enabled: true             # Enable progress tracking
    update-interval: 1000     # Progress update interval in milliseconds
  
  # Maximum upload session duration in seconds
  max-session-duration: 3600
  
  # Cleanup interval for expired upload sessions in seconds
  cleanup-interval: 300
  
  # Maximum concurrent uploads per IP
  max-concurrent-per-ip: 3

# Dashboard Configuration
dashboard:
  enabled: true               # Enable web dashboard
  path: "/dashboard"          # Dashboard URL path
  require-auth: true          # Require authentication for dashboard
  
  # Dashboard features
  show-stats: true            # Show server statistics
  show-files: true            # Show file list
  allow-upload: true          # Allow file upload from dashboard
  allow-delete: true          # Allow file deletion from dashboard

# File Management
files:
  # Allowed file types (empty = all types allowed)
  allowed-extensions: []
  
  # Blocked file types
  blocked-extensions:
    - "exe"
    - "bat"
    - "cmd"
    - "scr"
    - "com"
    - "pif"
  
  # File naming
  preserve-names: true        # Preserve original filenames
  sanitize-names: true        # Sanitize filenames (remove special chars)
  
  # Metadata
  store-metadata: true        # Store file metadata
  store-uploader-info: true   # Store uploader IP and user agent
  
  # Default list limit (maximum files returned in one request)
  list-limit: 100
  
  # Maximum filename length
  max-filename-length: 255

# Security Configuration
security:
  # API Security
  require-auth: true          # Require authentication for API
  allow-public-files: true    # Allow public file access
  
  # Upload restrictions
  max-uploads-per-ip: 100     # Max uploads per IP per hour
  scan-uploads: false         # Scan uploads for malware (requires external tool)
  
  # CORS settings
  cors:
    enabled: true
    allowed-origins: ["*"]
    allowed-methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
    allowed-headers: ["Content-Type", "Authorization", "X-Filename", "X-Public", "X-TTL"]
  
  # Enable IP whitelist
  ip-whitelist-enabled: false
  
  # Allowed IP addresses (when whitelist is enabled)
  allowed-ips: []
  
  # Enable user agent filtering
  user-agent-filtering: false
  
  # Blocked user agents (regex patterns)
  blocked-user-agents: []

# Logging Configuration
logging:
  level: "INFO"               # Log level (DEBUG, INFO, WARN, ERROR)
  file-operations: true       # Log file operations
  http-requests: true         # Log HTTP requests
  errors: true                # Log errors
  performance: false          # Log performance metrics
  
  # Log upload details
  log-uploads: true
  
  # Log download details
  log-downloads: false
```

## 📡 API Reference

### Base URL
```
http://localhost:9090
```

### Authentication
All private endpoints require the `Authorization` header:
```
Authorization: Bearer your-secret-key
```

### Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `POST` | `/upload` | Upload files | ✅ |
| `GET` | `/list` | List files with pagination | ✅ |
| `GET` | `/blob/{id}` | Download private file | ✅ |
| `GET` | `/public/{id}` | Download public file | ❌ |
| `DELETE` | `/delete/{id}` | Delete file | ✅ |
| `GET` | `/health` | Server health check | ❌ |
| `GET` | `/api/stats` | Detailed statistics | ❌ |
| `GET` | `/dashboard` | Web dashboard | Optional |

### Upload Headers

| Header | Description | Example |
|--------|-------------|---------|
| `X-Filename` | File name | `document.pdf` |
| `X-Public` | Make file public | `true` or `false` |
| `X-TTL` | Expiration in seconds | `3600` |

### Examples

#### Upload a file
```bash
curl -X POST http://localhost:9090/upload \
  -H "Authorization: Bearer your-secret-key" \
  -H "X-Filename: example.txt" \
  -H "X-Public: true" \
  -H "X-TTL: 3600" \
  --data-binary @file.txt
```

#### List files
```bash
curl "http://localhost:9090/list?page=1&pageSize=10" \
  -H "Authorization: Bearer your-secret-key"
```

#### Download a file
```bash
# Public file
curl http://localhost:9090/public/abc123def456

# Private file
curl http://localhost:9090/blob/abc123def456 \
  -H "Authorization: Bearer your-secret-key"
```

#### Delete a file
```bash
curl -X DELETE http://localhost:9090/delete/abc123def456 \
  -H "Authorization: Bearer your-secret-key"
```

## 🎯 TypeScript Client

### Installation

```bash
cd client
npm install
npm run build
```

### Usage

```typescript
import { BlobCraftClient, createBlobCraftClient } from 'blobcraft-client';

// Create client
const client = new BlobCraftClient('http://localhost:9090', 'your-secret-key');

// Upload a file
const result = await client.uploadText('Hello World!', 'hello.txt', {
  isPublic: true,
  ttl: 3600 // 1 hour
});

// List files
const files = await client.listFiles({ page: 1, pageSize: 10 });

// Download file
const content = await client.downloadFileAsText(result.id, true);

// Delete file
await client.deleteFile(result.id);
```

### Browser Usage

```html
<!DOCTYPE html>
<html>
<head>
    <title>BlobCraft Client</title>
</head>
<body>
    <input type="file" id="fileInput" multiple>
    <button onclick="uploadFiles()">Upload</button>

    <script type="module">
        import { BlobCraftBrowserClient } from './dist/index.js';
        
        const client = new BlobCraftBrowserClient('http://localhost:9090', 'your-secret-key');
        
        window.uploadFiles = async function() {
            const files = document.getElementById('fileInput').files;
            const results = await client.uploadFromDragDrop(files, { isPublic: true });
            console.log('Uploaded:', results);
        };
    </script>
</body>
</html>
```

### Node.js Usage

```javascript
const { BlobCraftNodeClient } = require('blobcraft-client');

const client = new BlobCraftNodeClient('http://localhost:9090', 'your-secret-key');

async function example() {
    // Upload from file system
    const result = await client.uploadFileFromPath('./local-file.txt', 'uploaded.txt', {
        isPublic: false,
        ttl: 7200 // 2 hours
    });
    
    // Download to file system
    await client.downloadFileToPath(result.id, './downloaded.txt', false);
}
```

## 🌐 Web Dashboard

Access the web dashboard at: `http://localhost:9090/dashboard`

### Features

- 📊 **Real-time Statistics** - Memory, storage, and file count
- 📤 **Drag & Drop Upload** - Multi-file upload with progress
- 📁 **File Management** - View, download, delete files
- 🔍 **Search & Filter** - Find files by name or extension
- 📱 **Responsive Design** - Works on all devices
- 🗜️ **Compression Info** - Shows which files are compressed

### Screenshots

The dashboard provides:
- Server health monitoring
- File upload with drag & drop
- File browser with search and filtering
- Download and delete operations
- Real-time statistics

## 🎮 In-Game Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/blobcraft status` | Show server status | `blobcraft.admin` |
| `/blobcraft info` | Show configuration | `blobcraft.admin` |
| `/blobcraft stats` | Show storage statistics | `blobcraft.admin` |
| `/blobcraft reload` | Reload configuration | `blobcraft.admin` |

## ⚙️ Configuration Reference

### Storage Settings
- `max-ram`: Maximum RAM usage in bytes
- `max-storage`: Maximum storage usage in bytes

### Expiration Settings
- `enable-expiration`: Enable automatic file cleanup
- `default-ttl`: Default time-to-live in seconds
- `max-ttl`: Maximum allowed TTL
- `cleanup-interval`: How often to check for expired files

### Compression Settings
- `enable-compression`: Enable gzip compression
- `compression-level`: Compression level (1-9)
- `compress-threshold`: Minimum file size to compress

### Security Settings
- `access-key`: Secret key for API access
- `dashboard-auth`: Require auth for dashboard

## 🔧 Development

### Project Structure
```
blobcraft/
├── src/main/java/dev/arubik/blobcraft/
│   ├── Main.java                    # Plugin main class
│   ├── FileStorage.java             # File storage management
│   ├── HttpServerWrapper.java       # HTTP server implementation
│   ├── DashboardHtml.java          # Web dashboard HTML
│   └── models/
│       └── StoredFile.java         # File model
├── src/main/resources/
│   ├── plugin.yml                  # Plugin metadata
│   └── config.yml                  # Default configuration
├── client/                         # TypeScript client library
│   ├── src/index.ts               # Client implementation
│   ├── package.json               # NPM configuration
│   └── tsconfig.json              # TypeScript configuration
├── build.gradle.kts               # Build configuration
└── README.md                      # This file
```

### Building

```bash
# Build plugin
./gradlew shadowJar

# Build TypeScript client
cd client
npm run build

# Run tests
./gradlew test
cd client && npm test
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🐛 Issues & Support

- Report bugs: [GitHub Issues](https://github.com/ArubikU/blobcraft/issues)
- Feature requests: [GitHub Discussions](https://github.com/ArubikU/blobcraft/discussions)
- Documentation: [Wiki](https://github.com/ArubikU/blobcraft/wiki) (WIP)

## 🎯 Use Cases

- **Development Testing** - Local file storage for web applications
- **Minecraft Integration** - Store player data, world backups, logs
- **Prototyping** - Quick file storage solution for experiments
- **Education** - Learn about HTTP APIs and file storage
- **Automation** - Automated file uploads from scripts and tools

## 🚀 Roadmap

- [ ] File versioning system
- [ ] Webhook notifications
- [ ] External storage providers (S3, etc.)
- [ ] File sharing with custom permissions
- [ ] Bulk operations API
- [ ] Advanced search capabilities
- [ ] File metadata and tags
- [ ] Rate limiting and quotas
- [ ] Multi-user support
- [ ] Plugin integrations

---

Made with ❤️ for the Minecraft community