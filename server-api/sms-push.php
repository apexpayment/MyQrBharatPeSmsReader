<?php
require_once dirname(__DIR__) . '/includes/init.php';
require_once dirname(__DIR__) . '/includes/device_helpers.php';
require_once dirname(__DIR__) . '/includes/payment_matcher.php';

function myqr_sms_time_to_sql($value): ?string
{
    $value = trim((string)$value);
    if ($value === '') return null;
    $ts = is_numeric($value) ? (strlen($value) >= 13 ? ((int)$value / 1000) : (int)$value) : strtotime($value);
    if ($ts === false || $ts <= 0) return null;
    return (new DateTimeImmutable('@' . (int)$ts))->setTimezone(new DateTimeZone('Asia/Kolkata'))->format('Y-m-d H:i:s');
}

function myqr_is_bharatpe_payment_sms(string $sender, string $body): bool
{
    $hay = strtolower($sender . ' ' . $body);

    // Never forward OTP/login/security messages.
    foreach (['otp', 'one time password', 'one-time password', 'verification code', 'login code', 'password', 'pin', 'do not share'] as $blocked) {
        if (str_contains($hay, $blocked)) return false;
    }

    // Accept only BharatPe receiving/payment-credit messages.
    $hasBrand = str_contains($hay, 'bharatpe') || str_contains($hay, 'bharat pe');
    $hasReceiveWord = preg_match('/\b(received|credited|added)\b/i', $body) === 1;
    $hasAmount = preg_match('/(?:rs\.?|inr|₹)\s*[0-9][0-9,]*(?:\.\d{1,2})?/i', $body) === 1;
    $hasMerchantContext = str_contains($hay, 'bharatpe qr') || str_contains($hay, 'bharatpe account') || str_contains($hay, 'paymentdashboard') || str_contains($hay, 'qr');

    return $hasBrand && $hasReceiveWord && $hasAmount && $hasMerchantContext;
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_response([
        'status' => true,
        'api' => 'sms-push',
        'message' => 'BharatPe SMS API is ready. Android app must send POST with x-device-token.',
        'method_required' => 'POST',
        'sms_push_url' => app_url('api/sms-push.php'),
        'required_header' => 'x-device-token',
        'sample_json' => [
            'sender' => 'BharatPe for Business',
            'body' => 'Received Rs.100.37 from CUSTOMER on BharatPe QR. The funds are added to your BharatPe Account.',
            'received_at' => round(microtime(true) * 1000),
            'source_type' => 'sms_broadcast'
        ]
    ]);
}

$device = auth_device_by_bearer();
$raw = read_raw_body();
$input = json_decode($raw, true);
if (!is_array($input)) json_response(['status' => false, 'message' => 'Invalid JSON'], 400);

$sender = mb_substr(trim((string)($input['sender'] ?? $input['from'] ?? '')), 0, 190);
$body = trim((string)($input['body'] ?? $input['message'] ?? $input['sms_body'] ?? ''));
$sourceType = mb_substr(trim((string)($input['source_type'] ?? 'sms_broadcast')), 0, 80);
$receivedRaw = trim((string)($input['received_at'] ?? $input['timestamp'] ?? ''));
$receivedAt = myqr_sms_time_to_sql($receivedRaw) ?: now_ist();

if ($body === '') json_response(['status' => false, 'message' => 'Empty SMS body'], 422);

$pdo = DB::pdo();
$pdo->prepare('UPDATE device_connections SET last_seen_at=?, updated_at=? WHERE id=?')->execute([now_ist(), now_ist(), (int)$device['id']]);

$isBharatPe = myqr_is_bharatpe_payment_sms($sender, $body);
$hashBase = (int)$device['merchant_id'] . '|' . (int)$device['id'] . '|' . $sender . '|' . $body . '|' . $receivedAt;
$rawHash = hash('sha256', $hashBase);
$notificationKey = 'sms:' . substr($rawHash, 0, 48);
$payloadJson = json_encode($input, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
if ($payloadJson !== false && mb_strlen($payloadJson) > 60000) $payloadJson = mb_substr($payloadJson, 0, 60000);
if ($payloadJson === false) $payloadJson = null;

$parsed = parse_payment_email('BharatPe SMS', $body, $sender);
$appPackage = $sourceType === 'rcs_notification' ? 'rcs.google.messages.bharatpe' : 'sms.bharatpe';
$appName = $sourceType === 'rcs_notification' ? 'BharatPe RCS Notification' : 'BharatPe SMS';

try {
    $ins = $pdo->prepare('INSERT INTO device_notification_events
        (merchant_id, device_id, app_package, app_name, notification_key, notification_title, notification_text, notification_big_text, notification_text_lines, notification_sub_text, notification_summary_text, notification_ticker_text, raw_payload_json, raw_body_hash, payload_field_count, parsed_amount, parsed_utr, parsed_source, received_at, posted_at, status, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
    $ins->execute([
        (int)$device['merchant_id'],
        (int)$device['id'],
        $appPackage,
        $appName,
        $notificationKey,
        mb_substr($sender ?: 'BharatPe', 0, 255),
        mb_substr($body, 0, 65000),
        null,
        null,
        null,
        null,
        null,
        $payloadJson,
        $rawHash,
        2,
        $parsed['amount'],
        $parsed['utr'],
        'device_bharatpe_sms',
        $receivedAt,
        $receivedAt,
        $isBharatPe ? 'new' : 'ignored_non_bharatpe_sms',
        now_ist(),
    ]);
    $deviceEventId = (int)$pdo->lastInsertId();
} catch (PDOException $e) {
    if (($e->errorInfo[1] ?? 0) == 1062) {
        json_response(['status' => true, 'message' => 'duplicate_sms_ignored']);
    }
    throw $e;
}

if (!$isBharatPe) {
    json_response([
        'status' => true,
        'message' => 'ignored_non_bharatpe_sms',
        'hint' => 'Only BharatPe payment received SMS is accepted. OTP/login/personal SMS are ignored.',
        'debug_url' => app_url('device_payload.php?id=' . $deviceEventId)
    ]);
}

if (empty($parsed['amount'])) {
    $pdo->prepare('UPDATE device_notification_events SET status="manual_review_sms_missing_amount" WHERE id=?')->execute([$deviceEventId]);
    json_response([
        'status' => true,
        'message' => 'manual_review_sms_missing_amount',
        'hint' => 'BharatPe SMS detected but amount not parsed. Auto-success blocked.',
        'debug_url' => app_url('device_payload.php?id=' . $deviceEventId)
    ]);
}

$messageId = 'sms:' . $rawHash;
try {
    $ev = $pdo->prepare('INSERT INTO payment_email_events (merchant_id, gmail_message_id, sender_email, subject, snippet, raw_body_hash, parsed_amount, parsed_utr, parsed_payer, parsed_source, received_at, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "new", ?)');
    $ev->execute([
        (int)$device['merchant_id'],
        $messageId,
        $sender ?: 'bharatpe_sms',
        'BharatPe SMS',
        mb_substr($body, 0, 600),
        $rawHash,
        $parsed['amount'],
        $parsed['utr'],
        $parsed['payer'],
        'device_bharatpe_sms',
        $receivedAt,
        now_ist()
    ]);
    $emailEventId = (int)$pdo->lastInsertId();
    $match = match_email_event($emailEventId);

    $matchedOrderId = null;
    $ms = $pdo->prepare('SELECT matched_order_id FROM payment_email_events WHERE id=? LIMIT 1');
    $ms->execute([$emailEventId]);
    $matchedOrderId = $ms->fetchColumn() ?: null;

    $pdo->prepare('UPDATE device_notification_events SET matched_email_event_id=?, matched_order_id=?, status=? WHERE id=?')
        ->execute([$emailEventId, $matchedOrderId, $match, $deviceEventId]);

    json_response([
        'status' => true,
        'message' => $match,
        'parsed_amount' => $parsed['amount'],
        'parsed_utr' => $parsed['utr'],
        'matched_order_id' => $matchedOrderId,
        'debug_url' => app_url('device_payload.php?id=' . $deviceEventId),
        'preview' => mb_substr($body, 0, 180)
    ]);
} catch (PDOException $e) {
    if (($e->errorInfo[1] ?? 0) == 1062) {
        $pdo->prepare('UPDATE device_notification_events SET status="duplicate_sms_event" WHERE id=?')->execute([$deviceEventId]);
        json_response(['status' => true, 'message' => 'duplicate_sms_event', 'debug_url' => app_url('device_payload.php?id=' . $deviceEventId)]);
    }
    throw $e;
}
