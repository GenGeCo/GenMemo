<?php
/**
 * API Endpoint for Android App
 * GET /api/list-packages.php
 * GET /api/list-packages.php?search=keyword
 * GET /api/list-packages.php?page=2&limit=20
 *
 * Returns list of public packages for the app to browse
 */

require_once __DIR__ . '/../includes/init.php';

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET');

$search = trim($_GET['search'] ?? '');
$page = max(1, (int)($_GET['page'] ?? 1));
$limit = min(50, max(1, (int)($_GET['limit'] ?? 20)));
$offset = ($page - 1) * $limit;

// Build query
$whereClause = "WHERE p.status = 'published' AND p.is_public = 1";
$params = [];

if ($search) {
    $whereClause .= " AND (p.name LIKE ? OR p.description LIKE ? OR p.topic LIKE ?)";
    $searchTerm = "%$search%";
    $params = [$searchTerm, $searchTerm, $searchTerm];
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
            p.created_at,
            u.username as author
        FROM packages p
        JOIN users u ON p.user_id = u.id
        $whereClause
        ORDER BY p.download_count DESC, p.created_at DESC
        LIMIT $limit OFFSET $offset";

$packages = db()->fetchAll($sql, $params);

// Format response
$result = [
    'success' => true,
    'data' => [
        'packages' => array_map(function($pkg) {
            return [
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
