<?php
/**
 * API Endpoint for Android App
 * GET /api/list-packages.php                    - Public packages (store)
 * GET /api/list-packages.php?mine=1             - User's own packages (requires auth)
 * GET /api/list-packages.php?search=keyword     - Search public packages
 * GET /api/list-packages.php?page=2&limit=20    - Pagination
 *
 * Returns list of packages for the app to browse
 */

require_once __DIR__ . '/../includes/init.php';

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$search = trim($_GET['search'] ?? '');
$page = max(1, (int)($_GET['page'] ?? 1));
$limit = min(50, max(1, (int)($_GET['limit'] ?? 20)));
$offset = ($page - 1) * $limit;
$mine = isset($_GET['mine']) && $_GET['mine'] == '1';

// Check if requesting user's own packages
$userId = null;
if ($mine) {
    $userId = authenticateForPackages();
    if (!$userId) {
        echo json_encode(['success' => false, 'error' => 'Autenticazione richiesta per vedere i tuoi pacchetti']);
        exit;
    }
}

// Build query
if ($mine && $userId) {
    // User's own packages (all statuses)
    $whereClause = "WHERE p.user_id = ?";
    $params = [$userId];
} else {
    // Public packages only
    $whereClause = "WHERE p.status = 'published' AND p.is_public = 1";
    $params = [];
}

if ($search) {
    $whereClause .= " AND (p.name LIKE ? OR p.description LIKE ? OR p.topic LIKE ?)";
    $searchTerm = "%$search%";
    $params = array_merge($params, [$searchTerm, $searchTerm, $searchTerm]);
}

// Get total count
$countSql = "SELECT COUNT(*) as total FROM packages p $whereClause";
$totalResult = db()->fetch($countSql, $params);
$total = $totalResult['total'] ?? 0;
$totalPages = ceil($total / $limit);

// Get packages
$sql = "SELECT
            p.uuid as code,
            p.name,
            p.description,
            p.topic,
            p.question_types,
            p.answer_types,
            p.total_questions,
            p.download_count,
            p.status,
            p.is_public,
            p.created_at,
            u.username as author
        FROM packages p
        JOIN users u ON p.user_id = u.id
        $whereClause
        ORDER BY p.created_at DESC
        LIMIT $limit OFFSET $offset";

$packages = db()->fetchAll($sql, $params);

// Format response
$result = [
    'success' => true,
    'data' => [
        'packages' => array_map(function($pkg) use ($mine) {
            $item = [
                'code' => $pkg['code'],
                'name' => $pkg['name'],
                'description' => $pkg['description'],
                'topic' => $pkg['topic'],
                'question_types' => json_decode($pkg['question_types'], true),
                'answer_types' => json_decode($pkg['answer_types'], true),
                'total_questions' => (int) $pkg['total_questions'],
                'download_count' => (int) $pkg['download_count'],
                'author' => $pkg['author'],
                'created_at' => $pkg['created_at']
            ];
            // Include status info for user's own packages
            if ($mine) {
                $item['status'] = $pkg['status'];
                $item['is_public'] = (bool) $pkg['is_public'];
            }
            return $item;
        }, $packages),
        'pagination' => [
            'current_page' => $page,
            'total_pages' => $totalPages,
            'total_items' => (int) $total,
            'items_per_page' => $limit
        ]
    ]
];

echo json_encode($result, JSON_UNESCAPED_UNICODE);

/**
 * Authenticate user from token (for viewing own packages)
 */
function authenticateForPackages(): ?int {
    $token = '';

    // Check Authorization header
    $authHeader = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (preg_match('/Bearer\s+(.+)$/i', $authHeader, $matches)) {
        $token = $matches[1];
    }

    // Fallback to GET param
    if (empty($token)) {
        $token = $_GET['token'] ?? '';
    }

    if (empty($token)) {
        return null;
    }

    $tokenHash = hash('sha256', $token);

    $appToken = db()->fetch(
        "SELECT user_id FROM app_tokens WHERE token = ? AND expires_at > NOW()",
        [$tokenHash]
    );

    return $appToken ? (int) $appToken['user_id'] : null;
}
