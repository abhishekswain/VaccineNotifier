package com.abhishek.vaccinenotifier.workers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.abhishek.vaccinenotifier.R;
import com.abhishek.vaccinenotifier.activities.MainActivity;
import com.abhishek.vaccinenotifier.activities.NotificationActivity;
import com.abhishek.vaccinenotifier.covidservices.CovidDataService;
import com.abhishek.vaccinenotifier.utils.SharedPrefUtil;

import java.util.Objects;

public class MyWorker extends Worker {

    Context mContext;

    public static final String distIDKey = "distID";
    public static final String pinValueKey = "pinValue";
    public static final String only18PlusKey = "only18Plus";
    public static final String emailIDKey = "emailID";
    public static final String notificationIDKey = "notificationID";
    public static final String intervalValueKey = "intervalValue";
    public static final String districtNameKey = "districtName";
    public static String tryCountKey = "tryCount";
    public static String notAvailableCountKey = "notAvailableCount";
    public static String availableCountKey = "availableCount";
    public static String stateChnagedToAvailableKey = "stateChnagedToAvailable";

    public static long availableCount;
    public static long notAvailableCount;
    public static long tryCount;
    public static int numOfNotificationOnAvailability;
    private static final int numOfNotificationOnAvailabilityMax = 2;

    public static int maxIntervalForloopSec = 1800;


    String notificationChannelId = "my_channel_id_01";
    String notificationChannelName = "My Notifications";
    NotificationCompat.Builder notificationBuilder;
    NotificationManager notificationManager;
    CovidDataService covidDataService;
    SharedPrefUtil spUtil;

    public MyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {

        super(context, workerParams);
        mContext = context;
        covidDataService = new CovidDataService(mContext);
        notificationBuilder = new NotificationCompat.Builder(mContext, notificationChannelId);
        notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        spUtil = new SharedPrefUtil(mContext, getApplicationContext().getPackageName());
        displayNotification("Vaccine availability information!", "Watch out this space for vaccine availability status", getInputData().getInt(notificationIDKey, 1));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @NonNull
    @Override
    public Result doWork() {

        try {
            int intervalNum = Integer.parseInt(Objects.requireNonNull(getInputData().getString(intervalValueKey)));

            if (0 != spUtil.getSharedPrefValueLong(tryCountKey)) {
                tryCount = spUtil.getSharedPrefValueLong(tryCountKey);
            }

            if (0 != spUtil.getSharedPrefValueLong(availableCountKey)) {
                availableCount = spUtil.getSharedPrefValueLong(availableCountKey);
            }

            if (0 != spUtil.getSharedPrefValueLong(notAvailableCountKey)) {
                notAvailableCount = spUtil.getSharedPrefValueLong(notAvailableCountKey);
            }

            //getting the input data
            String distID = getInputData().getString(distIDKey);
            String districtName = getInputData().getString(districtNameKey);
            String emailID = getInputData().getString(emailIDKey);
            String pinValue = getInputData().getString(pinValueKey);
            String only18Plus = getInputData().getString(only18PlusKey);
            String intervalValue = getInputData().getString(intervalValueKey);
            int notificationId = getInputData().getInt(notificationIDKey, 1);


            int loopCount = intervalNum >= maxIntervalForloopSec ? 1 : maxIntervalForloopSec / intervalNum;
            for (int i = 0; i < loopCount; i++) {

                tryCount++;
                spUtil.addOrUpdateSharedPrefLong(tryCountKey, tryCount);

                if (isStopped()) {

                    notificationManager.cancelAll();
                    return Result.success();
                }

                spUtil.addOrUpdateSharedPrefString(pinValueKey, pinValue);
                spUtil.addOrUpdateSharedPrefString(intervalValueKey, intervalValue);
                spUtil.addOrUpdateSharedPrefString(districtNameKey, districtName);

                MainActivity.getInstance().updateJobCount(MainActivity.getInstance().workersCount(), null);
                MainActivity.getInstance().updateSearchDetails(spUtil.getSharedPrefValueString(pinValueKey), spUtil.getSharedPrefValueString(districtNameKey), spUtil.getSharedPrefValueString(intervalValueKey));

                if (loopCount > 1) {
                    for (int j = 0; j < intervalNum * 10; j++) {

                        if (isStopped()) {
                            notificationManager.cancelAll();
                            return Result.success();
                        }

                        Thread.sleep(100);
                    }
                }

                String dataFilePath = covidDataService.checkVaccineAvailability(distID, pinValue, only18Plus, emailID);
                if (dataFilePath.equals(CovidDataService.notAvailable)) {

                    notAvailableCount++;
                    spUtil.addOrUpdateSharedPrefLong(notAvailableCountKey, notAvailableCount);
                    spUtil.addOrUpdateSharedPrefBoolean(stateChnagedToAvailableKey, false);
                    setForegroundAsync(displayNotification("Vaccine slots are not available yet for your selection!", "This notification will be updated once available.", notificationId));

                } else {

                    availableCount++;
                    spUtil.addOrUpdateSharedPrefLong(availableCountKey, availableCount);
                    spUtil.addOrUpdateSharedPrefBoolean(stateChnagedToAvailableKey, true);
                    setForegroundAsync(displayNotification("Vaccine Available!", "Vaccine booking slot(s) available, Click to know more!", notificationId));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Result.retry();
        }

        return Result.success();
    }


    private ForegroundInfo displayNotification(String title, String text, int id) {

        boolean isSilentUpdate = false;

        if ((spUtil.getSharedPrefValueLong(notAvailableCountKey) > 1)) {
            isSilentUpdate = true;
            numOfNotificationOnAvailability = 0;
        }

        if (spUtil.getSharedPrefValueBoolean(stateChnagedToAvailableKey)) {
            if (numOfNotificationOnAvailability < numOfNotificationOnAvailabilityMax) {
                isSilentUpdate = false;
            } else {
                isSilentUpdate = true;
            }

            numOfNotificationOnAvailability++;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(notificationChannelId, notificationChannelName, NotificationManager.IMPORTANCE_HIGH);

            // Configure the notification channel.
            notificationChannel.setDescription("VaccineNotifier");
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setVibrationPattern(new long[]{1000});

            notificationManager.createNotificationChannel(notificationChannel);
        }

        Intent notificationIntent = new Intent(mContext, NotificationActivity.class);
        PendingIntent conPendingIntent = PendingIntent.getActivity(mContext, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        notificationBuilder.setAutoCancel(false)
                .setSilent(isSilentUpdate)
                //.setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(mContext.getResources(),
                        R.mipmap.ic_launcher))
                .setTicker("VaccineNotifier")
                .setPriority(Notification.PRIORITY_MAX)
                .setContentTitle(title)
                .setContentText(text + "  (" + spUtil.getSharedPrefValueLong(tryCountKey) + ")")
                .setContentInfo("VaccineNotifier")
                .setOngoing(true)
                .setContentIntent(conPendingIntent);

        Notification notification = notificationBuilder.build();
        notificationManager.notify(/*notification id*/id, notification);

        return new ForegroundInfo(id, notification);
    }
}