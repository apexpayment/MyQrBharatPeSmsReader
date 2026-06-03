# MyQR BharatPe SMS Reader Android App

Private Android companion app for your own merchant phone.
It reads only **new BharatPe payment received SMS/RCS notification text** and sends it to your Hostinger server.
OTP/login/password messages are blocked locally.

## What it tracks
Accepted example:

```text
Received Rs.100.37 from NAME on BharatPe QR. The funds are added to your BharatPe Account.
```

Rejected examples:

```text
Your OTP is 123456
Login code for BharatPe is 123456
```

## GitHub upload

1. Create a new GitHub repo.
2. Upload all files from this folder.
3. Open the repo in Android Studio.
4. Let Gradle sync complete.
5. Build APK: Android Studio > Build > Build APK(s).

## Server setup

Upload your v20 PHP gateway to Hostinger first. Required endpoint:

```text
https://your-domain.com/api/sms-push.php
```

Dashboard flow:

```text
Dashboard > Device Notification > Generate Pairing Code
```

After pairing, copy:

```text
sms_push_url
device_token
```

Paste them inside the Android app screen and tap **Save Server Settings**.

## Phone setup

1. Install APK on merchant phone.
2. Open app.
3. Paste SMS Push URL and Device Token.
4. Tap Save Server Settings.
5. Tap Allow SMS Receive.
6. Keep battery mode unrestricted for this app.
7. If BharatPe message comes in Google Messages/RCS, tap Open Notification Access for RCS and enable this app.

## Safe matching rule on server

Auto-success only when:

```text
amount found in BharatPe SMS
+ exact random amount match
+ pending order active
+ inside 5-minute expiry window
+ only one pending order has that exact amount
```

If amount missing, duplicate, late, or suspicious, server keeps it in manual review.

## Privacy

This app does not read old SMS inbox history.
It uses RECEIVE_SMS only for new incoming messages.
It forwards only messages that contain BharatPe brand + payment received words + amount + QR/account context.
