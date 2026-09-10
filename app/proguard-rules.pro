# Keep the notification listener service; it is instantiated by the system.
-keep class com.mati.tradenotify.ingest.TelegramNotificationListener { *; }
-keep class * extends android.service.notification.NotificationListenerService { *; }
