<?php
/**
 * Cloudflare R2 Storage Handler
 * Uses AWS S3-compatible API
 */

if (!defined('GENMEMO')) {
    die('Direct access not allowed');
}

class R2Storage {
    private string $accountId;
    private string $accessKeyId;
    private string $secretAccessKey;
    private string $bucketName;
    private string $endpoint;
    private string $publicUrl;
    private string $region = 'auto';

    public function __construct() {
        $this->accountId = R2_ACCOUNT_ID;
        $this->accessKeyId = R2_ACCESS_KEY_ID;
        $this->secretAccessKey = R2_SECRET_ACCESS_KEY;
        $this->bucketName = R2_BUCKET_NAME;
        $this->endpoint = "https://{$this->accountId}.r2.cloudflarestorage.com";
        $this->publicUrl = R2_PUBLIC_URL;
    }

    /**
     * Upload a file to R2
     *
     * @param string $localPath Path to local file
     * @param string $key Storage key (path in R2)
     * @param string $contentType MIME type
     * @return array ['success' => bool, 'url' => string|null, 'error' => string|null]
     */
    public function upload(string $localPath, string $key, string $contentType): array {
        if (!file_exists($localPath)) {
            return ['success' => false, 'url' => null, 'error' => 'File not found'];
        }

        $fileContent = file_get_contents($localPath);
        $fileSize = strlen($fileContent);

        $date = gmdate('Ymd\THis\Z');
        $dateShort = gmdate('Ymd');

        $host = "{$this->accountId}.r2.cloudflarestorage.com";
        $uri = "/{$this->bucketName}/{$key}";

        // Create canonical request
        $contentHash = hash('sha256', $fileContent);

        $headers = [
            'content-length' => $fileSize,
            'content-type' => $contentType,
            'host' => $host,
            'x-amz-content-sha256' => $contentHash,
            'x-amz-date' => $date,
        ];

        ksort($headers);

        $signedHeaders = implode(';', array_keys($headers));
        $canonicalHeaders = '';
        foreach ($headers as $k => $v) {
            $canonicalHeaders .= strtolower($k) . ':' . trim($v) . "\n";
        }

        $canonicalRequest = "PUT\n{$uri}\n\n{$canonicalHeaders}\n{$signedHeaders}\n{$contentHash}";
        $canonicalRequestHash = hash('sha256', $canonicalRequest);

        // Create string to sign
        $credentialScope = "{$dateShort}/{$this->region}/s3/aws4_request";
        $stringToSign = "AWS4-HMAC-SHA256\n{$date}\n{$credentialScope}\n{$canonicalRequestHash}";

        // Calculate signature
        $kDate = hash_hmac('sha256', $dateShort, 'AWS4' . $this->secretAccessKey, true);
        $kRegion = hash_hmac('sha256', $this->region, $kDate, true);
        $kService = hash_hmac('sha256', 's3', $kRegion, true);
        $kSigning = hash_hmac('sha256', 'aws4_request', $kService, true);
        $signature = hash_hmac('sha256', $stringToSign, $kSigning);

        // Build authorization header
        $authorization = "AWS4-HMAC-SHA256 Credential={$this->accessKeyId}/{$credentialScope}, SignedHeaders={$signedHeaders}, Signature={$signature}";

        // Make request
        $ch = curl_init();
        curl_setopt_array($ch, [
            CURLOPT_URL => $this->endpoint . $uri,
            CURLOPT_CUSTOMREQUEST => 'PUT',
            CURLOPT_POSTFIELDS => $fileContent,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_HTTPHEADER => [
                "Authorization: {$authorization}",
                "Content-Type: {$contentType}",
                "Content-Length: {$fileSize}",
                "Host: {$host}",
                "x-amz-content-sha256: {$contentHash}",
                "x-amz-date: {$date}",
            ],
        ]);

        $response = curl_exec($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $error = curl_error($ch);
        curl_close($ch);

        if ($httpCode >= 200 && $httpCode < 300) {
            return [
                'success' => true,
                'url' => rtrim($this->publicUrl, '/') . '/' . $key,
                'key' => $key,
                'error' => null
            ];
        }

        return [
            'success' => false,
            'url' => null,
            'error' => $error ?: "HTTP {$httpCode}: {$response}"
        ];
    }

    /**
     * Upload from $_FILES array
     *
     * @param array $file $_FILES['fieldname']
     * @param string $folder Folder in R2 (e.g., 'packages/uuid')
     * @return array
     */
    public function uploadFromForm(array $file, string $folder): array {
        if ($file['error'] !== UPLOAD_ERR_OK) {
            $errors = [
                UPLOAD_ERR_INI_SIZE => 'File too large (php.ini limit)',
                UPLOAD_ERR_FORM_SIZE => 'File too large (form limit)',
                UPLOAD_ERR_PARTIAL => 'File partially uploaded',
                UPLOAD_ERR_NO_FILE => 'No file uploaded',
                UPLOAD_ERR_NO_TMP_DIR => 'No temp directory',
                UPLOAD_ERR_CANT_WRITE => 'Cannot write to disk',
            ];
            return [
                'success' => false,
                'url' => null,
                'error' => $errors[$file['error']] ?? 'Unknown upload error'
            ];
        }

        // Validate file size
        if ($file['size'] > UPLOAD_MAX_SIZE) {
            return [
                'success' => false,
                'url' => null,
                'error' => 'File too large (max ' . formatFileSize(UPLOAD_MAX_SIZE) . ')'
            ];
        }

        // Validate file type
        $mimeType = mime_content_type($file['tmp_name']);
        $isImage = in_array($mimeType, ALLOWED_IMAGE_TYPES);
        $isAudio = in_array($mimeType, ALLOWED_AUDIO_TYPES);

        if (!$isImage && !$isAudio) {
            return [
                'success' => false,
                'url' => null,
                'error' => 'File type not allowed: ' . $mimeType
            ];
        }

        // Generate unique filename
        $ext = pathinfo($file['name'], PATHINFO_EXTENSION);
        $filename = randomString(16) . '.' . strtolower($ext);
        $key = trim($folder, '/') . '/' . $filename;

        return $this->upload($file['tmp_name'], $key, $mimeType);
    }

    /**
     * Delete a file from R2
     *
     * @param string $key Storage key
     * @return array
     */
    public function delete(string $key): array {
        $date = gmdate('Ymd\THis\Z');
        $dateShort = gmdate('Ymd');

        $host = "{$this->accountId}.r2.cloudflarestorage.com";
        $uri = "/{$this->bucketName}/{$key}";

        $contentHash = hash('sha256', '');

        $headers = [
            'host' => $host,
            'x-amz-content-sha256' => $contentHash,
            'x-amz-date' => $date,
        ];

        ksort($headers);

        $signedHeaders = implode(';', array_keys($headers));
        $canonicalHeaders = '';
        foreach ($headers as $k => $v) {
            $canonicalHeaders .= strtolower($k) . ':' . trim($v) . "\n";
        }

        $canonicalRequest = "DELETE\n{$uri}\n\n{$canonicalHeaders}\n{$signedHeaders}\n{$contentHash}";
        $canonicalRequestHash = hash('sha256', $canonicalRequest);

        $credentialScope = "{$dateShort}/{$this->region}/s3/aws4_request";
        $stringToSign = "AWS4-HMAC-SHA256\n{$date}\n{$credentialScope}\n{$canonicalRequestHash}";

        $kDate = hash_hmac('sha256', $dateShort, 'AWS4' . $this->secretAccessKey, true);
        $kRegion = hash_hmac('sha256', $this->region, $kDate, true);
        $kService = hash_hmac('sha256', 's3', $kRegion, true);
        $kSigning = hash_hmac('sha256', 'aws4_request', $kService, true);
        $signature = hash_hmac('sha256', $stringToSign, $kSigning);

        $authorization = "AWS4-HMAC-SHA256 Credential={$this->accessKeyId}/{$credentialScope}, SignedHeaders={$signedHeaders}, Signature={$signature}";

        $ch = curl_init();
        curl_setopt_array($ch, [
            CURLOPT_URL => $this->endpoint . $uri,
            CURLOPT_CUSTOMREQUEST => 'DELETE',
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_HTTPHEADER => [
                "Authorization: {$authorization}",
                "Host: {$host}",
                "x-amz-content-sha256: {$contentHash}",
                "x-amz-date: {$date}",
            ],
        ]);

        $response = curl_exec($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        return [
            'success' => $httpCode >= 200 && $httpCode < 300,
            'error' => $httpCode >= 300 ? "HTTP {$httpCode}" : null
        ];
    }

    /**
     * Get public URL for a key
     */
    public function getPublicUrl(string $key): string {
        return rtrim($this->publicUrl, '/') . '/' . $key;
    }
}

/**
 * Helper function to get R2 instance
 */
function r2(): R2Storage {
    static $instance = null;
    if ($instance === null) {
        $instance = new R2Storage();
    }
    return $instance;
}
