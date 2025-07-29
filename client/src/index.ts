export interface BlobCraftFile {
  id: string
  filename: string
  size: number
  originalSize?: number
  uploadedAt: string
  expiresAt?: string
  extension?: string
  public: boolean
  compressed?: boolean
  url: string
  mimeType?: string
  uploaderIp?: string
  uploader?: string
  tags?: string
  category?: string
  metadata?: Record<string, string>
}

export interface FileMetadata {
  id: string
  filename: string
  extension: string
  size: number
  mimeType: string
  uploadedAt: string
  expiresAt?: string
  ttl?: number
  public: boolean
  compressed: boolean
  uploaderIp: string
  uploaderAgent: string
  metadata: Record<string, string>
  urls: {
    public?: string
    publicFull?: string
    private: string
    metadata: string
  }
  blobcraft: {
    name: string
    extension: string
    uploader: string
    tags: string
    category: string
    description: string
  }
}

export interface ListResponse {
  page: number
  pageSize: number
  total: number
  files: BlobCraftFile[]
}

export interface UploadResponse {
  id: string
  filename: string
  size: number
  originalSize?: number
  uploadedAt: string
  expiresAt?: string
  public: boolean
  compressed?: boolean
  url: string
  mimeType?: string
}

export interface DeleteResponse {
  success: boolean
  message: string
}

export interface ListParams {
  page?: number
  pageSize?: number
  ext?: string
  search?: string
}

export interface UploadOptions {
  isPublic?: boolean
  ttl?: number // TTL in seconds
  tags?: string
  category?: string
  uploader?: string
  description?: string
  metadata?: Record<string, string>
}

export interface HealthResponse {
  status: string
  timestamp: string
  totalFiles: number
  usedMemory: number
  usedStorage: number
  maxMemory: number
  maxStorage: number
  memoryUsagePercent: number
  storageUsagePercent: number
}

export interface ChunkedUploadInit {
  uploadId: string
  chunkSize: number
  totalChunks: number
  expiresAt: string
}

export interface ChunkUploadResponse {
  success: boolean
  chunkNumber: number
  progress: number
  completed: boolean
  fileId?: string
}

export interface UploadProgress {
  uploadId: string
  filename: string
  totalSize: number
  uploadedBytes: number
  progress: number
  totalChunks: number
  uploadedChunks: number
  completed: boolean
  createdAt: string
  fileId?: string
  missingChunks: number[]
}

export interface ChunkedUploadOptions extends UploadOptions {
  chunkSize?: number
  onProgress?: (progress: UploadProgress) => void
  onChunkUploaded?: (chunkNumber: number, progress: number) => void
  enableResume?: boolean
}

export class BlobCraftClient {
  protected baseUrl: string
  protected accessKey: string

  constructor(baseUrl: string, accessKey: string) {
    // Remove trailing slash from baseUrl
    this.baseUrl = baseUrl.replace(/\/$/, "")
    this.accessKey = accessKey
  }

  /**
   * Upload a file to BlobCraft
   */
  async uploadFile(file: Blob | Buffer, filename: string, options: UploadOptions = {}): Promise<UploadResponse> {
    const headers: Record<string, string> = {
      Authorization: `Bearer ${this.accessKey}`,
      "X-Filename": encodeURIComponent(filename),
      "X-Public": options.isPublic ? "true" : "false",
    }

    if (options.ttl) headers["X-TTL"] = options.ttl.toString()
    if (options.tags) headers["X-Tags"] = options.tags
    if (options.category) headers["X-Category"] = options.category
    if (options.uploader) headers["X-Uploader"] = options.uploader
    if (options.description) headers["X-Description"] = options.description

    // Add custom metadata as headers
    if (options.metadata) {
      for (const [key, value] of Object.entries(options.metadata)) {
        headers[`X-Meta-${key}`] = value
      }
    }

    let bodyToSend: BodyInit
    if (file instanceof Blob) {
      bodyToSend = file
    } else if (typeof Buffer !== "undefined" && file instanceof Buffer) {
      // Convert Buffer to Uint8Array for fetch compatibility
      bodyToSend = new Uint8Array(file)
    } else {
      throw new Error("Unsupported file type for upload")
    }

    const response = await fetch(`${this.baseUrl}/upload`, {
      method: "POST",
      headers,
      body: bodyToSend,
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Upload failed: ${response.status} ${errorText}`)
    }

    return response.json()
  }

  /**
   * Upload a file from a File input element (browser only)
   */
  async uploadFileFromInput(fileInput: File, options: UploadOptions = {}): Promise<UploadResponse> {
    return this.uploadFile(fileInput, fileInput.name, options)
  }

  /**
   * Upload text content as a file
   */
  async uploadText(content: string, filename: string, options: UploadOptions = {}): Promise<UploadResponse> {
    const blob = new Blob([content], { type: "text/plain" })
    return this.uploadFile(blob, filename, options)
  }

  /**
   * Upload JSON data as a file
   */
  async uploadJson(data: any, filename: string, options: UploadOptions = {}): Promise<UploadResponse> {
    const jsonString = JSON.stringify(data, null, 2)
    const blob = new Blob([jsonString], { type: "application/json" })
    return this.uploadFile(blob, filename, options)
  }

  /**
   * Upload a file with TTL (time to live)
   */
  async uploadFileWithTTL(
    file: Blob | Buffer,
    filename: string,
    ttlSeconds: number,
    isPublic = false,
  ): Promise<UploadResponse> {
    return this.uploadFile(file, filename, { isPublic, ttl: ttlSeconds })
  }

  /**
   * Upload a temporary file (1 hour TTL)
   */
  async uploadTempFile(file: Blob | Buffer, filename: string, isPublic = false): Promise<UploadResponse> {
    return this.uploadFileWithTTL(file, filename, 3600, isPublic)
  }

  /**
   * List files with optional pagination and filtering
   */
  async listFiles(params: ListParams = {}): Promise<ListResponse> {
    const queryParams = new URLSearchParams()

    if (params.page !== undefined) queryParams.set("page", params.page.toString())
    if (params.pageSize !== undefined) queryParams.set("pageSize", params.pageSize.toString())
    if (params.ext) queryParams.set("ext", params.ext)
    if (params.search) queryParams.set("search", params.search)

    const url = `${this.baseUrl}/list${queryParams.toString() ? "?" + queryParams.toString() : ""}`

    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${this.accessKey}` },
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`List failed: ${response.status} ${errorText}`)
    }

    return response.json()
  }

  /**
   * Download a file as a Blob
   */
  async downloadFile(id: string, isPublic = false): Promise<Blob> {
    const url = isPublic ? `${this.baseUrl}/public/${id}` : `${this.baseUrl}/blob/${id}`

    const headers: Record<string, string> = {}
    if (!isPublic) {
      headers.Authorization = `Bearer ${this.accessKey}`
    }

    const response = await fetch(url, { headers })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Download failed: ${response.status} ${errorText}`)
    }

    return response.blob()
  }

  /**
   * Download a file as text
   */
  async downloadFileAsText(id: string, isPublic = false): Promise<string> {
    const blob = await this.downloadFile(id, isPublic)
    return blob.text()
  }

  /**
   * Download a file as JSON
   */
  async downloadFileAsJson<T = any>(id: string, isPublic = false): Promise<T> {
    const text = await this.downloadFileAsText(id, isPublic)
    return JSON.parse(text)
  }

  /**
   * Download a file as ArrayBuffer
   */
  async downloadFileAsArrayBuffer(id: string, isPublic = false): Promise<ArrayBuffer> {
    const blob = await this.downloadFile(id, isPublic)
    return blob.arrayBuffer()
  }

  /**
   * Delete a file by ID
   */
  async deleteFile(id: string): Promise<DeleteResponse> {
    const response = await fetch(`${this.baseUrl}/delete/${id}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${this.accessKey}` },
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Delete failed: ${response.status} ${errorText}`)
    }

    return response.json()
  }

  /**
   * Get the download URL for a file
   */
  getDownloadUrl(id: string, isPublic = false): string {
    return isPublic ? `${this.baseUrl}/public/${id}` : `${this.baseUrl}/blob/${id}`
  }

  /**
   * Check server health and get statistics
   */
  async getHealth(): Promise<HealthResponse> {
    const response = await fetch(`${this.baseUrl}/api/stats`)

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Health check failed: ${response.status} ${errorText}`)
    }

    return response.json()
  }

  /**
   * Search files by filename pattern
   */
  async searchFiles(pattern: string, params: ListParams = {}): Promise<BlobCraftFile[]> {
    const searchParams = { ...params, search: pattern }
    const response = await this.listFiles(searchParams)
    return response.files
  }

  /**
   * Get files by extension
   */
  async getFilesByExtension(extension: string, params: ListParams = {}): Promise<ListResponse> {
    return this.listFiles({ ...params, ext: extension })
  }

  /**
   * Batch upload multiple files
   */
  async uploadMultipleFiles(
    files: Array<{ data: Blob | Buffer; filename: string; options?: UploadOptions }>,
  ): Promise<UploadResponse[]> {
    const uploadPromises = files.map((file) => this.uploadFile(file.data, file.filename, file.options || {}))

    return Promise.all(uploadPromises)
  }

  /**
   * Get detailed file metadata
   */
  async getFileMetadata(id: string): Promise<FileMetadata> {
    const response = await fetch(`${this.baseUrl}/metadata/${id}`)

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Get metadata failed: ${response.status} ${errorText}`)
    }

    return response.json()
  }

  /**
   * Get public URL for a file (only works for public files)
   */
  async getPublicUrl(id: string): Promise<string> {
    const metadata = await this.getFileMetadata(id)

    if (!metadata.public || !metadata.urls.publicFull) {
      throw new Error("File is not public or URL not available")
    }

    return metadata.urls.publicFull
  }

  /**
   * Upload file with rich metadata
   */
  async uploadFileWithMetadata(
    file: Blob | Buffer,
    filename: string,
    metadata: {
      isPublic?: boolean
      ttl?: number
      tags?: string[]
      category?: string
      uploader?: string
      description?: string
      customMetadata?: Record<string, string>
    },
  ): Promise<UploadResponse> {
    const options: UploadOptions = {
      isPublic: metadata.isPublic,
      ttl: metadata.ttl,
      tags: metadata.tags?.join(", "),
      category: metadata.category,
      uploader: metadata.uploader,
      description: metadata.description,
      metadata: metadata.customMetadata,
    }

    return this.uploadFile(file, filename, options)
  }

  /**
   * Upload a large file using chunked upload
   */
  async uploadFileChunked(
    file: Blob | Buffer,
    filename: string,
    options: ChunkedUploadOptions = {},
  ): Promise<UploadResponse> {
    const fileSize = file instanceof Blob ? file.size : file.length
    const chunkSize = options.chunkSize || 12 * 1024 * 1024 // 12MB default

    // Initialize chunked upload
    const initResponse = await this.initializeChunkedUpload(filename, fileSize, options)

    try {
      // Upload chunks
      const totalChunks = initResponse.totalChunks
      const actualChunkSize = initResponse.chunkSize

      for (let chunkNumber = 0; chunkNumber < totalChunks; chunkNumber++) {
        const start = chunkNumber * actualChunkSize
        const end = Math.min(start + actualChunkSize, fileSize)

        let chunkData: Blob
        if (file instanceof Blob) {
          chunkData = file.slice(start, end)
        } else {
          chunkData = new Blob([file.slice(start, end)])
        }

        const chunkResponse = await this.uploadChunk(initResponse.uploadId, chunkNumber, chunkData)

        if (options.onChunkUploaded) {
          options.onChunkUploaded(chunkNumber, chunkResponse.progress)
        }

        if (options.onProgress) {
          const progress = await this.getUploadProgress(initResponse.uploadId)
          options.onProgress(progress)
        }

        if (chunkResponse.completed && chunkResponse.fileId) {
          // Upload completed
          return {
            id: chunkResponse.fileId,
            filename: filename,
            size: fileSize,
            uploadedAt: new Date().toISOString(),
            public: options.isPublic || false,
            url: options.isPublic ? `/public/${chunkResponse.fileId}` : `/blob/${chunkResponse.fileId}`,
          }
        }
      }

      throw new Error("Upload completed but no file ID received")
    } catch (error) {
      // Cancel upload on error
      await this.cancelUpload(initResponse.uploadId).catch(() => {})
      throw error
    }
  }

  /**
   * Initialize a chunked upload
   */
  async initializeChunkedUpload(
    filename: string,
    totalSize: number,
    options: UploadOptions = {},
  ): Promise<ChunkedUploadInit> {
    const headers: Record<string, string> = {
      Authorization: `Bearer ${this.accessKey}`,
      "X-Filename": encodeURIComponent(filename),
      "X-Total-Size": totalSize.toString(),
      "X-Public": options.isPublic ? "true" : "false",
    }

    if (options.ttl) headers["X-TTL"] = options.ttl.toString()
    if (options.tags) headers["X-Tags"] = options.tags
    if (options.category) headers["X-Category"] = options.category
    if (options.uploader) headers["X-Uploader"] = options.uploader
    if (options.description) headers["X-Description"] = options.description

    if (options.metadata) {
      for (const [key, value] of Object.entries(options.metadata)) {
        headers[`X-Meta-${key}`] = value
      }
    }

    const response = await fetch(`${this.baseUrl}/upload/init`, {
      method: "POST",
      headers,
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Failed to initialize upload: ${response.status} ${errorText}`)
    }

    return response.json()
  }

  /**
   * Upload a single chunk
   */
  async uploadChunk(uploadId: string, chunkNumber: number, chunkData: Blob): Promise<ChunkUploadResponse> {
    const response = await fetch(`${this.baseUrl}/upload/chunk`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${this.accessKey}`,
        "X-Upload-Id": uploadId,
        "X-Chunk-Number": chunkNumber.toString(),
      },
      body: chunkData,
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Failed to upload chunk ${chunkNumber}: ${response.status} ${errorText}`)
    }

    return response.json()
  }

  /**
   * Get upload progress
   */
  async getUploadProgress(uploadId: string): Promise<UploadProgress> {
    const response = await fetch(`${this.baseUrl}/upload/progress/${uploadId}`)

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Failed to get progress: ${response.status} ${errorText}`)
    }

    return response.json()
  }

  /**
   * Cancel an upload
   */
  async cancelUpload(uploadId: string): Promise<{ success: boolean; message: string }> {
    const response = await fetch(`${this.baseUrl}/upload/cancel/${uploadId}`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${this.accessKey}`,
      },
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Failed to cancel upload: ${response.status} ${errorText}`)
    }

    return response.json()
  }

  /**
   * Resume a failed upload
   */
  async resumeUpload(
    uploadId: string,
    file: Blob | Buffer,
    options: ChunkedUploadOptions = {},
  ): Promise<UploadResponse> {
    const progress = await this.getUploadProgress(uploadId)

    if (progress.completed) {
      throw new Error("Upload already completed")
    }

    const fileSize = file instanceof Blob ? file.size : file.length
    const chunkSize = progress.totalChunks > 0 ? Math.ceil(fileSize / progress.totalChunks) : 8 * 1024 * 1024

    // Upload missing chunks
    for (const chunkNumber of progress.missingChunks) {
      const start = chunkNumber * chunkSize
      const end = Math.min(start + chunkSize, fileSize)

      let chunkData: Blob
      if (file instanceof Blob) {
        chunkData = file.slice(start, end)
      } else {
        chunkData = new Blob([file.slice(start, end)])
      }

      const chunkResponse = await this.uploadChunk(uploadId, chunkNumber, chunkData)

      if (options.onChunkUploaded) {
        options.onChunkUploaded(chunkNumber, chunkResponse.progress)
      }

      if (options.onProgress) {
        const updatedProgress = await this.getUploadProgress(uploadId)
        options.onProgress(updatedProgress)
      }

      if (chunkResponse.completed && chunkResponse.fileId) {
        return {
          id: chunkResponse.fileId,
          filename: progress.filename,
          size: fileSize,
          uploadedAt: new Date().toISOString(),
          public: false, // We don't know from progress, assume private
          url: `/blob/${chunkResponse.fileId}`,
        }
      }
    }

    throw new Error("Resume completed but upload not finalized")
  }

  /**
   * Smart upload - automatically chooses between regular and chunked upload
   */
  async uploadFileSmart(
    file: Blob | Buffer,
    filename: string,
    options: ChunkedUploadOptions = {},
  ): Promise<UploadResponse> {
    const fileSize = file instanceof Blob ? file.size : file.length
    const chunkThreshold = options.chunkSize || 50 * 1024 * 1024 // 50MB threshold

    if (fileSize > chunkThreshold) {
      return this.uploadFileChunked(file, filename, options)
    } else {
      return this.uploadFile(file, filename, options)
    }
  }
}

// Node.js specific utilities
export class BlobCraftNodeClient extends BlobCraftClient {
  /**
   * Upload a file from filesystem (Node.js only)
   */
  async uploadFileFromPath(filePath: string, filename?: string, options: UploadOptions = {}): Promise<UploadResponse> {
    const fs = await import("fs")
    const path = await import("path")

    const data = fs.readFileSync(filePath)
    const finalFilename = filename || path.basename(filePath)

    return this.uploadFile(data, finalFilename, options)
  }

  /**
   * Download a file to filesystem (Node.js only)
   */
  async downloadFileToPath(id: string, outputPath: string, isPublic = false): Promise<void> {
    const fs = await import("fs")

    const arrayBuffer = await this.downloadFileAsArrayBuffer(id, isPublic)
    const buffer = Buffer.from(arrayBuffer)

    fs.writeFileSync(outputPath, buffer)
  }
}

// Browser specific utilities
export class BlobCraftBrowserClient extends BlobCraftClient {
  /**
   * Trigger download in browser
   */
  async triggerDownload(id: string, filename?: string, isPublic = false): Promise<void> {
    const blob = await this.downloadFile(id, isPublic)

    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = filename || `file_${id}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  /**
   * Upload files from drag and drop
   */
  async uploadFromDragDrop(files: FileList, options: UploadOptions = {}): Promise<UploadResponse[]> {
    const fileArray = Array.from(files)
    const uploadData = fileArray.map((file) => ({
      data: file,
      filename: file.name,
      options,
    }))

    return this.uploadMultipleFiles(uploadData)
  }
}

// Usage examples and factory functions
export function createBlobCraftClient(baseUrl: string, accessKey: string): BlobCraftClient {
  if (typeof window !== "undefined") {
    return new BlobCraftBrowserClient(baseUrl, accessKey)
  } else {
    return new BlobCraftNodeClient(baseUrl, accessKey)
  }
}

// Default export
export default BlobCraftClient
