<?php
/**
 * GenMemo Initialization
 * Include this file at the top of every page
 */

define('GENMEMO', true);

// Load configuration
require_once __DIR__ . '/config.php';

// Load database
require_once __DIR__ . '/db.php';

// Load authentication
require_once __DIR__ . '/auth.php';

// Load helper functions
require_once __DIR__ . '/functions.php';

// Load R2 storage
require_once __DIR__ . '/R2Storage.php';
