package dev.arubik.blobcraft;

public class DashboardHtml {
    
    public static String getDashboardHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BlobCraft Dashboard</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            color: #333;
        }
        
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }
        
        .header {
            background: rgba(255, 255, 255, 0.95);
            backdrop-filter: blur(10px);
            border-radius: 15px;
            padding: 30px;
            margin-bottom: 30px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
            text-align: center;
        }
        
        .header h1 {
            color: #4a5568;
            font-size: 2.5rem;
            margin-bottom: 10px;
            background: linear-gradient(135deg, #667eea, #764ba2);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }
        
        .header p {
            color: #718096;
            font-size: 1.1rem;
        }
        
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        
        .stat-card {
            background: rgba(255, 255, 255, 0.95);
            backdrop-filter: blur(10px);
            border-radius: 15px;
            padding: 25px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
            transition: transform 0.3s ease;
        }
        
        .stat-card:hover {
            transform: translateY(-5px);
        }
        
        .stat-card h3 {
            color: #4a5568;
            font-size: 0.9rem;
            text-transform: uppercase;
            letter-spacing: 1px;
            margin-bottom: 10px;
        }
        
        .stat-card .value {
            font-size: 2rem;
            font-weight: bold;
            color: #2d3748;
            margin-bottom: 5px;
        }
        
        .stat-card .label {
            color: #718096;
            font-size: 0.9rem;
        }
        
        .progress-bar {
            width: 100%;
            height: 8px;
            background: #e2e8f0;
            border-radius: 4px;
            margin-top: 10px;
            overflow: hidden;
        }
        
        .progress-fill {
            height: 100%;
            background: linear-gradient(90deg, #48bb78, #38a169);
            border-radius: 4px;
            transition: width 0.3s ease;
        }
        
        .progress-fill.warning {
            background: linear-gradient(90deg, #ed8936, #dd6b20);
        }
        
        .progress-fill.danger {
            background: linear-gradient(90deg, #f56565, #e53e3e);
        }
        
        .main-content {
            background: rgba(255, 255, 255, 0.95);
            backdrop-filter: blur(10px);
            border-radius: 15px;
            padding: 30px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
        }
        
        .upload-section {
            margin-bottom: 40px;
            padding-bottom: 30px;
            border-bottom: 1px solid #e2e8f0;
        }
        
        .upload-area {
            border: 2px dashed #cbd5e0;
            border-radius: 10px;
            padding: 40px;
            text-align: center;
            transition: all 0.3s ease;
            cursor: pointer;
        }
        
        .upload-area:hover, .upload-area.dragover {
            border-color: #667eea;
            background: rgba(102, 126, 234, 0.05);
        }
        
        .upload-area input[type="file"] {
            display: none;
        }
        
        .upload-options {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin-top: 20px;
        }
        
        .upload-option {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        
        .upload-option input, .upload-option select {
            margin: 0;
            padding: 8px;
            border: 1px solid #cbd5e0;
            border-radius: 6px;
            font-size: 0.9rem;
        }
        
        .upload-option input[type="checkbox"] {
            width: auto;
            padding: 0;
        }
        
        .btn {
            background: linear-gradient(135deg, #667eea, #764ba2);
            color: white;
            border: none;
            padding: 12px 24px;
            border-radius: 8px;
            cursor: pointer;
            font-size: 1rem;
            transition: all 0.3s ease;
            text-decoration: none;
            display: inline-block;
        }
        
        .btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
        }
        
        .btn-danger {
            background: linear-gradient(135deg, #f56565, #e53e3e);
        }
        
        .btn-danger:hover {
            box-shadow: 0 4px 12px rgba(245, 101, 101, 0.4);
        }
        
        .btn-info {
            background: linear-gradient(135deg, #4299e1, #3182ce);
        }
        
        .btn-info:hover {
            box-shadow: 0 4px 12px rgba(66, 153, 225, 0.4);
        }
        
        .files-section h2 {
            color: #4a5568;
            margin-bottom: 20px;
            font-size: 1.5rem;
        }
        
        .files-controls {
            display: flex;
            gap: 15px;
            margin-bottom: 20px;
            flex-wrap: wrap;
            align-items: center;
        }
        
        .files-controls input, .files-controls select {
            padding: 8px 12px;
            border: 1px solid #cbd5e0;
            border-radius: 6px;
            font-size: 0.9rem;
        }
        
        .files-table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 20px;
        }
        
        .files-table th,
        .files-table td {
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid #e2e8f0;
        }
        
        .files-table th {
            background: #f7fafc;
            font-weight: 600;
            color: #4a5568;
        }
        
        .files-table tr:hover {
            background: #f7fafc;
        }
        
        .file-actions {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
        }
        
        .file-actions button {
            padding: 4px 8px;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 0.8rem;
            transition: all 0.2s ease;
        }
        
        .btn-download {
            background: #48bb78;
            color: white;
        }
        
        .btn-delete {
            background: #f56565;
            color: white;
        }
        
        .btn-metadata {
            background: #4299e1;
            color: white;
        }
        
        .btn-copy {
            background: #805ad5;
            color: white;
        }
        
        .file-size {
            font-family: monospace;
            color: #718096;
        }
        
        .file-status {
            padding: 2px 8px;
            border-radius: 12px;
            font-size: 0.8rem;
            font-weight: 500;
        }
        
        .status-public {
            background: #c6f6d5;
            color: #22543d;
        }
        
        .status-private {
            background: #fed7d7;
            color: #742a2a;
        }
        
        .status-compressed {
            background: #bee3f8;
            color: #2a4365;
        }
        
        .loading {
            text-align: center;
            padding: 40px;
            color: #718096;
        }
        
        .error {
            background: #fed7d7;
            color: #742a2a;
            padding: 12px;
            border-radius: 6px;
            margin: 10px 0;
        }
        
        .success {
            background: #c6f6d5;
            color: #22543d;
            padding: 12px;
            border-radius: 6px;
            margin: 10px 0;
        }
        
        .pagination {
            display: flex;
            justify-content: center;
            gap: 10px;
            margin-top: 20px;
        }
        
        .pagination button {
            padding: 8px 12px;
            border: 1px solid #cbd5e0;
            background: white;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.2s ease;
        }
        
        .pagination button:hover:not(:disabled) {
            background: #f7fafc;
        }
        
        .pagination button:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }
        
        .pagination button.active {
            background: #667eea;
            color: white;
            border-color: #667eea;
        }
        
        /* Modal styles */
        .modal {
            display: none;
            position: fixed;
            z-index: 1000;
            left: 0;
            top: 0;
            width: 100%;
            height: 100%;
            background-color: rgba(0,0,0,0.5);
        }
        
        .modal-content {
            background-color: #fefefe;
            margin: 5% auto;
            padding: 20px;
            border-radius: 10px;
            width: 90%;
            max-width: 600px;
            max-height: 80vh;
            overflow-y: auto;
        }
        
        .close {
            color: #aaa;
            float: right;
            font-size: 28px;
            font-weight: bold;
            cursor: pointer;
        }
        
        .close:hover {
            color: black;
        }
        
        .metadata-grid {
            display: grid;
            grid-template-columns: 1fr 2fr;
            gap: 10px;
            margin: 15px 0;
        }
        
        .metadata-key {
            font-weight: bold;
            color: #4a5568;
        }
        
        .metadata-value {
            color: #718096;
            word-break: break-all;
        }
        
        .copy-button {
            background: #805ad5;
            color: white;
            border: none;
            padding: 5px 10px;
            border-radius: 4px;
            cursor: pointer;
            font-size: 0.8rem;
            margin-left: 10px;
        }
        
        @media (max-width: 768px) {
            .container {
                padding: 10px;
            }
            
            .header h1 {
                font-size: 2rem;
            }
            
            .stats-grid {
                grid-template-columns: 1fr;
            }
            
            .files-controls {
                flex-direction: column;
                align-items: stretch;
            }
            
            .files-table {
                font-size: 0.9rem;
            }
            
            .files-table th,
            .files-table td {
                padding: 8px;
            }
            
            .upload-options {
                grid-template-columns: 1fr;
            }
            
            .metadata-grid {
                grid-template-columns: 1fr;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🗄️ BlobCraft Dashboard</h1>
            <p>Minecraft Blob Storage Management</p>
        </div>
        
        <div class="stats-grid">
            <div class="stat-card">
                <h3>Total Files</h3>
                <div class="value" id="totalFiles">-</div>
                <div class="label">stored files</div>
            </div>
            
            <div class="stat-card">
                <h3>Memory Usage</h3>
                <div class="value" id="memoryUsage">-</div>
                <div class="label">of available RAM</div>
                <div class="progress-bar">
                    <div class="progress-fill" id="memoryProgress"></div>
                </div>
            </div>
            
            <div class="stat-card">
                <h3>Storage Usage</h3>
                <div class="value" id="storageUsage">-</div>
                <div class="label">of available space</div>
                <div class="progress-bar">
                    <div class="progress-fill" id="storageProgress"></div>
                </div>
            </div>
            
            <div class="stat-card">
                <h3>Server Status</h3>
                <div class="value" id="serverStatus">🟢</div>
                <div class="label">operational</div>
            </div>
        </div>
        
        <div class="main-content">
            <div class="upload-section">
                <h2>📤 Upload Files</h2>
                <div class="upload-area" onclick="document.getElementById('fileInput').click()">
                    <input type="file" id="fileInput" multiple>
                    <div>
                        <h3>Drop files here or click to browse</h3>
                        <p>Support for multiple files, automatic compression, and expiration</p>
                    </div>
                </div>
                
                <div class="upload-options">
                    <div class="upload-option">
                        <input type="checkbox" id="publicFile">
                        <label for="publicFile">Make files public</label>
                    </div>
                    <div class="upload-option">
                        <label for="ttlInput">TTL (seconds):</label>
                        <input type="number" id="ttlInput" placeholder="3600" min="1" max="604800">
                    </div>
                    <div class="upload-option">
                        <label for="tagsInput">Tags:</label>
                        <input type="text" id="tagsInput" placeholder="tag1, tag2, tag3">
                    </div>
                    <div class="upload-option">
                        <label for="categoryInput">Category:</label>
                        <select id="categoryInput">
                            <option value="general">General</option>
                            <option value="images">Images</option>
                            <option value="documents">Documents</option>
                            <option value="videos">Videos</option>
                            <option value="audio">Audio</option>
                            <option value="archives">Archives</option>
                        </select>
                    </div>
                    <div class="upload-option">
                        <label for="uploaderInput">Uploader:</label>
                        <input type="text" id="uploaderInput" placeholder="Your name">
                    </div>
                    <div class="upload-option">
                        <label for="descriptionInput">Description:</label>
                        <input type="text" id="descriptionInput" placeholder="File description">
                    </div>
                </div>
                
                <div id="uploadStatus"></div>
            </div>
            
            <div class="files-section">
                <h2>📁 File Management</h2>
                
                <div class="files-controls">
                    <input type="text" id="searchInput" placeholder="Search files...">
                    <select id="extensionFilter">
                        <option value="">All extensions</option>
                    </select>
                    <button class="btn" onclick="loadFiles()">🔄 Refresh</button>
                </div>
                
                <div id="filesContainer">
                    <div class="loading">Loading files...</div>
                </div>
                
                <div class="pagination" id="pagination"></div>
            </div>
        </div>
    </div>

    <!-- Metadata Modal -->
    <div id="metadataModal" class="modal">
        <div class="modal-content">
            <span class="close">&times;</span>
            <h2>📋 File Metadata</h2>
            <div id="metadataContent"></div>
        </div>
    </div>

    <script>
        let currentPage = 1;
        let totalPages = 1;
        const pageSize = 10;
        
        // Initialize dashboard
        document.addEventListener('DOMContentLoaded', function() {
            loadStats();
            loadFiles();
            setupEventListeners();
            
            // Refresh stats every 30 seconds
            setInterval(loadStats, 30000);
        });
        
        function setupEventListeners() {
            // File upload
            const fileInput = document.getElementById('fileInput');
            fileInput.addEventListener('change', handleFileUpload);
            
            // Drag and drop
            const uploadArea = document.querySelector('.upload-area');
            uploadArea.addEventListener('dragover', handleDragOver);
            uploadArea.addEventListener('dragleave', handleDragLeave);
            uploadArea.addEventListener('drop', handleDrop);
            
            // Search and filter
            document.getElementById('searchInput').addEventListener('input', debounce(loadFiles, 500));
            document.getElementById('extensionFilter').addEventListener('change', loadFiles);
            
            // Modal
            const modal = document.getElementById('metadataModal');
            const closeBtn = document.querySelector('.close');
            closeBtn.onclick = function() {
                modal.style.display = 'none';
            }
            window.onclick = function(event) {
                if (event.target == modal) {
                    modal.style.display = 'none';
                }
            }
        }
        
        async function loadStats() {
            try {
                const response = await fetch('/api/stats');
                const stats = await response.json();
                
                document.getElementById('totalFiles').textContent = stats.totalFiles;
                
                const memoryPercent = stats.memoryUsagePercent.toFixed(1);
                document.getElementById('memoryUsage').textContent = memoryPercent + '%';
                updateProgressBar('memoryProgress', stats.memoryUsagePercent);
                
                const storagePercent = stats.storageUsagePercent.toFixed(1);
                document.getElementById('storageUsage').textContent = storagePercent + '%';
                updateProgressBar('storageProgress', stats.storageUsagePercent);
                
            } catch (error) {
                console.error('Failed to load stats:', error);
                document.getElementById('serverStatus').textContent = '🔴';
                document.querySelector('#serverStatus').nextElementSibling.textContent = 'error';
            }
        }
        
        function updateProgressBar(id, percent) {
            const progressBar = document.getElementById(id);
            progressBar.style.width = percent + '%';
            
            progressBar.className = 'progress-fill';
            if (percent > 80) {
                progressBar.classList.add('danger');
            } else if (percent > 60) {
                progressBar.classList.add('warning');
            }
        }
        
        async function loadFiles() {
            const search = document.getElementById('searchInput').value;
            const extension = document.getElementById('extensionFilter').value;
            
            try {
                const params = new URLSearchParams({
                    page: currentPage,
                    pageSize: pageSize
                });
                
                if (extension) params.set('ext', extension);
                if (search) params.set('search', search);
                
                const response = await fetch(`/list?${params}`);
                const data = await response.json();
                
                displayFiles(data.files);
                updatePagination(data.page, Math.ceil(data.total / data.pageSize), data.total);
                updateExtensionFilter(data.files);
                
            } catch (error) {
                document.getElementById('filesContainer').innerHTML = 
                    '<div class="error">Failed to load files: ' + error.message + '</div>';
            }
        }
        
        function displayFiles(files) {
            const container = document.getElementById('filesContainer');
            
            if (files.length === 0) {
                container.innerHTML = '<div class="loading">No files found</div>';
                return;
            }
            
            const table = `
                <table class="files-table">
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Size</th>
                            <th>Type</th>
                            <th>Status</th>
                            <th>Uploader</th>
                            <th>Uploaded</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${files.map(file => `
                            <tr>
                                <td title="${file.filename}">${truncateText(file.filename, 30)}</td>
                                <td class="file-size">${formatFileSize(file.originalSize)}</td>
                                <td>${file.extension || 'unknown'}</td>
                                <td>
                                    <span class="file-status ${file.public ? 'status-public' : 'status-private'}">
                                        ${file.public ? 'Public' : 'Private'}
                                    </span>
                                    ${file.compressed ? '<span class="file-status status-compressed">Compressed</span>' : ''}
                                </td>
                                <td>${file.uploaderIp || 'anonymous'}</td>
                                <td>${formatDate(file.uploadedAt)}</td>
                                <td class="file-actions">
                                    <button class="btn-download" onclick="downloadFile('${file.id}', '${file.filename}', ${file.public})">
                                        📥 Download
                                    </button>
                                    <button class="btn-metadata" onclick="showMetadata('${file.id}')">
                                        📋 Info
                                    </button>
                                    ${file.public ? `<button class="btn-copy" onclick="copyPublicUrl('${file.id}')">🔗 Copy URL</button>` : ''}
                                    <button class="btn-delete" onclick="deleteFile('${file.id}', '${file.filename}')">
                                        🗑️ Delete
                                    </button>
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            `;
            
            container.innerHTML = table;
        }
        
        function updatePagination(current, total, totalFiles) {
            currentPage = current;
            totalPages = total;
            
            const pagination = document.getElementById('pagination');
            
            if (total <= 1) {
                pagination.innerHTML = '';
                return;
            }
            
            let html = '';
            
            // Previous button
            html += `<button ${current === 1 ? 'disabled' : ''} onclick="changePage(${current - 1})">← Previous</button>`;
            
            // Page numbers
            for (let i = Math.max(1, current - 2); i <= Math.min(total, current + 2); i++) {
                html += `<button class="${i === current ? 'active' : ''}" onclick="changePage(${i})">${i}</button>`;
            }
            
            // Next button
            html += `<button ${current === total ? 'disabled' : ''} onclick="changePage(${current + 1})">Next →</button>`;
            
            pagination.innerHTML = html;
        }
        
        function changePage(page) {
            if (page >= 1 && page <= totalPages) {
                currentPage = page;
                loadFiles();
            }
        }
        
        function updateExtensionFilter(files) {
            const filter = document.getElementById('extensionFilter');
            const currentValue = filter.value;
            const extensions = [...new Set(files.map(f => f.extension).filter(Boolean))].sort();
            
            filter.innerHTML = '<option value="">All extensions</option>' +
                extensions.map(ext => `<option value="${ext}">${ext}</option>`).join('');
            
            filter.value = currentValue;
        }
        
        async function handleFileUpload() {
            const files = document.getElementById('fileInput').files;
            if (files.length === 0) return;
            
            await uploadFiles(Array.from(files));
        }
        
        async function uploadFiles(files) {
            const isPublic = document.getElementById('publicFile').checked;
            const ttl = document.getElementById('ttlInput').value;
            const tags = document.getElementById('tagsInput').value;
            const category = document.getElementById('categoryInput').value;
            const uploader = document.getElementById('uploaderInput').value;
            const description = document.getElementById('descriptionInput').value;
            const statusDiv = document.getElementById('uploadStatus');
            
            statusDiv.innerHTML = '<div class="loading">Uploading files...</div>';
            
            try {
                const results = [];
                
                for (const file of files) {
                    const headers = {
                        'X-Filename': file.name,
                        'X-Public': isPublic ? 'true' : 'false'
                    };
                    
                    if (ttl) headers['X-TTL'] = ttl;
                    if (tags) headers['X-Tags'] = tags;
                    if (category) headers['X-Category'] = category;
                    if (uploader) headers['X-Uploader'] = uploader;
                    if (description) headers['X-Description'] = description;
                    
                    const response = await fetch('/upload', {
                        method: 'POST',
                        headers: headers,
                        body: file
                    });
                    
                    if (response.ok) {
                        const result = await response.json();
                        results.push(result);
                    } else {
                        throw new Error(`Failed to upload ${file.name}: ${response.statusText}`);
                    }
                }
                
                statusDiv.innerHTML = `<div class="success">Successfully uploaded ${results.length} file(s)</div>`;
                loadFiles();
                loadStats();
                
                // Clear form
                document.getElementById('fileInput').value = '';
                document.getElementById('tagsInput').value = '';
                document.getElementById('uploaderInput').value = '';
                document.getElementById('descriptionInput').value = '';
                
            } catch (error) {
                statusDiv.innerHTML = `<div class="error">Upload failed: ${error.message}</div>`;
            }
        }
        
        async function downloadFile(id, filename, isPublic) {
            try {
                const url = isPublic ? `/public/${id}` : `/blob/${id}`;
                const response = await fetch(url);
                
                if (!response.ok) throw new Error('Download failed');
                
                const blob = await response.blob();
                const downloadUrl = URL.createObjectURL(blob);
                
                const a = document.createElement('a');
                a.href = downloadUrl;
                a.download = filename;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(downloadUrl);
                
            } catch (error) {
                alert('Download failed: ' + error.message);
            }
        }
        
        async function showMetadata(id) {
            try {
                const response = await fetch(`/metadata/${id}`);
                if (!response.ok) throw new Error('Failed to load metadata');
                
                const metadata = await response.json();
                const modal = document.getElementById('metadataModal');
                const content = document.getElementById('metadataContent');
                
                content.innerHTML = `
                    <div class="metadata-grid">
                        <div class="metadata-key">ID:</div>
                        <div class="metadata-value">${metadata.id}</div>
                        
                        <div class="metadata-key">Filename:</div>
                        <div class="metadata-value">${metadata.filename}</div>
                        
                        <div class="metadata-key">Extension:</div>
                        <div class="metadata-value">${metadata.extension || 'none'}</div>
                        
                        <div class="metadata-key">Size:</div>
                        <div class="metadata-value">${formatFileSize(metadata.size)}</div>
                        
                        <div class="metadata-key">MIME Type:</div>
                        <div class="metadata-value">${metadata.mimeType}</div>
                        
                        <div class="metadata-key">Uploaded:</div>
                        <div class="metadata-value">${formatDate(metadata.uploadedAt)}</div>
                        
                        <div class="metadata-key">Public:</div>
                        <div class="metadata-value">${metadata.public ? 'Yes' : 'No'}</div>
                        
                        <div class="metadata-key">Compressed:</div>
                        <div class="metadata-value">${metadata.compressed ? 'Yes' : 'No'}</div>
                        
                        <div class="metadata-key">Uploader IP:</div>
                        <div class="metadata-value">${metadata.uploaderIp}</div>
                        
                        <div class="metadata-key">User Agent:</div>
                        <div class="metadata-value">${metadata.uploaderAgent || 'Unknown'}</div>
                        
                        ${metadata.expiresAt ? `
                        <div class="metadata-key">Expires:</div>
                        <div class="metadata-value">${formatDate(metadata.expiresAt)} (TTL: ${metadata.ttl}s)</div>
                        ` : ''}
                    </div>
                    
                    <h3>🗄️ BlobCraft Info</h3>
                    <div class="metadata-grid">
                        <div class="metadata-key">Name:</div>
                        <div class="metadata-value">${metadata.filename || 'none'}</div>
                        
                        <div class="metadata-key">Extension:</div>
                        <div class="metadata-value">${metadata.extension || 'none'}</div>
                        
                        <div class="metadata-key">Uploader:</div>
                        <div class="metadata-value">${metadata.uploaderIp || 'none'}</div>
                        
                        <div class="metadata-key">Tags:</div>
                        <div class="metadata-value">${metadata.tags || 'none'}</div>
                        
                        <div class="metadata-key">Category:</div>
                        <div class="metadata-value">${metadata.category || 'none'}</div>
                        
                        <div class="metadata-key">Description:</div>
                        <div class="metadata-value">${metadata.description || 'none'}</div>

                        <div class="metadata-key">Raw:</div>
                        <div class="metadata-value">${JSON.stringify(metadata || {})}</div>
                    </div>
                    
                    <h3>🔗 URLs</h3>
                    <div class="metadata-grid">
                        <div class="metadata-key">Private URL:</div>
                        <div class="metadata-value">
                            ${window.location.origin}/blob/${metadata.id}
                            <button class="copy-button" onclick="copyToClipboard(window.location.origin + '/blob/' + metadata.id)"">Copy</button>
                        </div>
                        
                        <div class="metadata-key">Metadata URL:</div>
                        <div class="metadata-value">
                            ${window.location.origin}/meta/${metadata.id}
                            <button class="copy-button" onclick="copyToClipboard(window.location.origin + '/meta/' + metadata.id)">Copy</button>
                        </div>
                        
                        ${metadata.public ? `
                        <div class="metadata-key">Public URL:</div>
                        <div class="metadata-value">
                            ${window.location.origin}/public/${metadata.id}
                            <button class="copy-button" onclick="copyToClipboard('${window.location.origin}/public/${metadata.id}')">Copy</button>
                        </div>
                        
                        ` : ''}
                    </div>
                    
                    ${Object.keys(metadata.metadata).length > 0 ? `
                    <h3>📝 Custom Metadata</h3>
                    <div class="metadata-grid">
                        ${Object.entries(metadata.metadata).map(([key, value]) => `
                            <div class="metadata-key">${key}:</div>
                            <div class="metadata-value">${value}</div>
                        `).join('')}
                    </div>
                    ` : ''}
                `;
                
                modal.style.display = 'block';
                
            } catch (error) {
                alert('Failed to load metadata: ' + error.message);
            }
        }
        
        async function copyPublicUrl(id) {
            try {
                const response = await fetch(`/metadata/${id}`);
                if (!response.ok) throw new Error('Failed to load metadata');
                
                const metadata = await response.json();
                if (metadata.urls.publicFull) {
                    await copyToClipboard(metadata.urls.publicFull);
                    alert('Public URL copied to clipboard!');
                } else {
                    alert('File is not public');
                }
            } catch (error) {
                alert('Failed to copy URL: ' + error.message);
            }
        }
        
        async function copyToClipboard(text) {
            try {
                await navigator.clipboard.writeText(text);
            } catch (err) {
                // Fallback for older browsers
                const textArea = document.createElement('textarea');
                textArea.value = text;
                document.body.appendChild(textArea);
                textArea.select();
                document.execCommand('copy');
                document.body.removeChild(textArea);
            }
        }
        
        async function deleteFile(id, filename) {
            if (!confirm(`Are you sure you want to delete "${filename}"?`)) return;
            
            try {
                const response = await fetch(`/delete/${id}`, {
                    method: 'DELETE'
                });
                
                if (response.ok) {
                    loadFiles();
                    loadStats();
                } else {
                    throw new Error('Delete failed');
                }
                
            } catch (error) {
                alert('Delete failed: ' + error.message);
            }
        }
        
        // Drag and drop handlers
        function handleDragOver(e) {
            e.preventDefault();
            e.currentTarget.classList.add('dragover');
        }
        
        function handleDragLeave(e) {
            e.currentTarget.classList.remove('dragover');
        }
        
        function handleDrop(e) {
            e.preventDefault();
            e.currentTarget.classList.remove('dragover');
            
            const files = Array.from(e.dataTransfer.files);
            uploadFiles(files);
        }
        
        // Utility functions

        function formatFileSize(bytes) {
            if (bytes === 0) return '0 B';
            const k = 1024;
            const sizes = ['B', 'KB', 'MB', 'GB'];
            const i = bytes < k ? 0 : Math.floor(Math.log(bytes) / Math.log(k));
            if (i === 0) return bytes + ' B';
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        }
        
        function formatDate(dateString) {
            return new Date(dateString).toLocaleString();
        }
        
        function truncateText(text, maxLength) {
            return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
        }
        
        function debounce(func, wait) {
            let timeout;
            return function executedFunction(...args) {
                const later = () => {
                    clearTimeout(timeout);
                    func(...args);
                };
                clearTimeout(timeout);
                timeout = setTimeout(later, wait);
            };
        }
    </script>
</body>
</html>
        """;
    }
}
