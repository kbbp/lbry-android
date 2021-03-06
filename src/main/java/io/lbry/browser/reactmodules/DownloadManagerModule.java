package io.lbry.browser.reactmodules;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import io.lbry.browser.MainActivity;
import io.lbry.browser.R;
import io.lbry.browser.receivers.NotificationDeletedReceiver;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Random;

public class DownloadManagerModule extends ReactContextBaseJavaModule {

    private Context context;

    private HashMap<Integer, NotificationCompat.Builder> builders = new HashMap<Integer, NotificationCompat.Builder>();

    private HashMap<String, Integer> downloadIdNotificationIdMap = new HashMap<String, Integer>();

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");    
    
    private static final int MAX_PROGRESS = 100;

    private static final String GROUP_DOWNLOADS = "io.lbry.browser.GROUP_DOWNLOADS";
    
    public static final String NOTIFICATION_ID_KEY = "io.lbry.browser.notificationId";
    
    public static final int GROUP_ID = 0;
    
    public static boolean groupCreated = false;
    
    public DownloadManagerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
    }

    private int generateNotificationId() {
        return new Random().nextInt();
    }

    @Override
    public String getName() {
        return "LbryDownloadManager";
    }
    
    private void createNotificationGroup() {
        if (!groupCreated) {
            Intent intent = new Intent(context, NotificationDeletedReceiver.class);
            intent.putExtra(NOTIFICATION_ID_KEY, GROUP_ID);
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, GROUP_ID, intent, 0);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
            builder.setContentTitle("Active downloads")
                   .setContentText("Active downloads")
                   .setSmallIcon(R.drawable.ic_file_download_black_24dp)
                   .setPriority(NotificationCompat.PRIORITY_LOW)
                   .setGroup(GROUP_DOWNLOADS)
                   .setGroupSummary(true)
                   .setDeleteIntent(pendingIntent);
            notificationManager.notify(GROUP_ID, builder.build());
        
            groupCreated = true;
        }
    }
    
    private PendingIntent getLaunchPendingIntent() {
        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent intent = PendingIntent.getActivity(context, 0, launchIntent, 0);
        return intent;
    }

    @ReactMethod
    public void startDownload(String id, String fileName) {
        createNotificationGroup();
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentIntent(getLaunchPendingIntent())
               .setContentTitle(String.format("Downloading %s...", fileName))
               .setGroup(GROUP_DOWNLOADS)
               .setPriority(NotificationCompat.PRIORITY_LOW)
               .setProgress(MAX_PROGRESS, 0, false)
               .setSmallIcon(R.drawable.ic_file_download_black_24dp);

        int notificationId = generateNotificationId();
        downloadIdNotificationIdMap.put(id, notificationId);

        builders.put(notificationId, builder);
        notificationManager.notify(notificationId, builder.build());
    }

    @ReactMethod
    public void updateDownload(String id, String fileName, double progress, double writtenBytes, double totalBytes) {
        if (!downloadIdNotificationIdMap.containsKey(id)) {
            return;
        }

        int notificationId = downloadIdNotificationIdMap.get(id);
        if (!builders.containsKey(notificationId)) {
            return;
        }

        createNotificationGroup();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder builder = builders.get(notificationId);
        builder.setContentIntent(getLaunchPendingIntent())
               .setContentText(String.format("%.0f%% (%s / %s)", progress, formatBytes(writtenBytes), formatBytes(totalBytes)))
               .setGroup(GROUP_DOWNLOADS)
               .setProgress(MAX_PROGRESS, new Double(progress).intValue(), false);
        notificationManager.notify(notificationId, builder.build());

        if (progress == MAX_PROGRESS) {
            builder.setContentTitle(String.format("Downloaded %s.", fileName));
            downloadIdNotificationIdMap.remove(id);
            builders.remove(notificationId);
        }
    }
    
    @ReactMethod
    public void stopDownload(String id, String filename) {
        if (!downloadIdNotificationIdMap.containsKey(id)) {
            return;
        }

        int notificationId = downloadIdNotificationIdMap.get(id);
        if (!builders.containsKey(notificationId)) {
            return;
        }
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder builder = builders.get(notificationId);
        notificationManager.cancel(notificationId);
        
        downloadIdNotificationIdMap.remove(id);
        builders.remove(notificationId);
        
        if (builders.values().size() == 0) {
            notificationManager.cancel(GROUP_ID);
            groupCreated = false;
        }
    }

    private String formatBytes(double bytes)
    {
        if (bytes < 1048576) { // < 1MB
            return String.format("%s KB", DECIMAL_FORMAT.format(bytes / 1024.0));
        }

        if (bytes < 1073741824) { // < 1GB
            return String.format("%s MB", DECIMAL_FORMAT.format(bytes / (1024.0 * 1024.0)));
        }

        return String.format("%s GB", DECIMAL_FORMAT.format(bytes / (1024.0 * 1024.0 * 1024.0)));
    }
}
