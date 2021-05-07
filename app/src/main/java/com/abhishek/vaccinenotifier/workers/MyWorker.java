package com.abhishek.vaccinenotifier.workers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.NetworkType;
import androidx.work.Operation;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.abhishek.vaccinenotifier.R;
import com.abhishek.vaccinenotifier.activities.MainActivity;
import com.abhishek.vaccinenotifier.activities.NotificationActivity;
import com.abhishek.vaccinenotifier.covidservices.CovidDataService;
import com.abhishek.vaccinenotifier.utils.SharedPrefUtil;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MyWorker extends Worker {

    private SharedPrefUtil spUtil;
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

    public static String workerTag = "vaccine";
    public static String defaultPin = "123";

    public static int maxIntervalForloopSec = 60;


    String notificationChannelId = "my_channel_id_01";
    String notificationChannelName = "My Notifications";
    NotificationCompat.Builder notificationBuilder;
    NotificationManager notificationManager;
    CovidDataService covidDataService;

    public enum VACCINE_STATUS {
        AVAILABLE,
        NOT_AVAILABLE,
        PENDING,
        ERROR
    }


    public MyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {

        super(context, workerParams);
        mContext = context;
        covidDataService = new CovidDataService(mContext);
        notificationBuilder = new NotificationCompat.Builder(mContext, notificationChannelId);
        notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        spUtil = new SharedPrefUtil(mContext, getApplicationContext().getPackageName());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @NonNull
    @Override
    public Result doWork() {

        try {
            setForegroundAsync(displayNotification("Vaccine availability information!", "Watch out this space for vaccine availability status", getInputData().getInt(notificationIDKey, 1)));

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

                if (null != MainActivity.getInstance()) {
                    MainActivity.getInstance().updateSearchDetails(spUtil.getSharedPrefValueString(pinValueKey), spUtil.getSharedPrefValueString(districtNameKey), spUtil.getSharedPrefValueString(intervalValueKey));
                }

                VACCINE_STATUS vaccineStatus = covidDataService.checkVaccineAvailability(distID, pinValue, only18Plus, emailID);
                if (vaccineStatus == VACCINE_STATUS.NOT_AVAILABLE || vaccineStatus == VACCINE_STATUS.ERROR) {
                    numOfNotificationOnAvailability = 0;
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

                if (null != MainActivity.getInstance()) {
                    if (vaccineStatus == VACCINE_STATUS.ERROR) {
                        MainActivity.getInstance().updateJobCountAndVaccineStatus(MainActivity.getInstance().workersCount(), null, spUtil.getSharedPrefValueBoolean(stateChnagedToAvailableKey) ? VACCINE_STATUS.AVAILABLE : VACCINE_STATUS.NOT_AVAILABLE);
                    }
                    MainActivity.getInstance().updateJobCountAndVaccineStatus(MainActivity.getInstance().workersCount(), null, spUtil.getSharedPrefValueBoolean(stateChnagedToAvailableKey) ? VACCINE_STATUS.AVAILABLE : VACCINE_STATUS.NOT_AVAILABLE);
                }

                if (loopCount > 1) {
                    for (int j = 0; j < intervalNum * 10; j++) {

                        if (isStopped()) {
                            notificationManager.cancelAll();
                            return Result.success();
                        }

                        Thread.sleep(100);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Result.retry();
        }

        startOrrestartWorker(spUtil);

        return Result.success();
    }


    private ForegroundInfo displayNotification(String title, String text, int id) {

        boolean isSilentUpdate = true;

        if ((spUtil.getSharedPrefValueLong(notAvailableCountKey) <= 1)) {
            isSilentUpdate = false;
        }

        if (spUtil.getSharedPrefValueBoolean(stateChnagedToAvailableKey)) {

            if (numOfNotificationOnAvailability >= numOfNotificationOnAvailabilityMax) {
                isSilentUpdate = true;
            } else {
                isSilentUpdate = false;
            }

            numOfNotificationOnAvailability++;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(notificationChannelId, notificationChannelName, NotificationManager.IMPORTANCE_HIGH);

            // Configure the notification channel.
            notificationChannel.setDescription("VaccineNotifier");
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});

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
                .setContentTitle(title + "  (Poll count:" + spUtil.getSharedPrefValueLong(tryCountKey) + ")")
                .setContentText(text)
                .setContentInfo("VaccineNotifier")
                .setOngoing(true)
                .setContentIntent(conPendingIntent);

        Notification notification = notificationBuilder.build();
        notificationManager.notify(/*notification id*/id, notification);

        return new ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }


    public static Operation startOrrestartWorker(SharedPrefUtil spUtil) {

        WorkManager.getInstance().cancelAllWork();
        WorkManager.getInstance().pruneWork();

        long intervalInLong = Long.parseLong(spUtil.getSharedPrefValueString(MyWorker.intervalValueKey));
        if (intervalInLong <= MyWorker.maxIntervalForloopSec) {
            intervalInLong = MyWorker.maxIntervalForloopSec;
        }


        //creating a data object
        //to pass the data with workRequest
        //we can put as many variables needed
        Data inputData = new Data.Builder()
                .putString(MyWorker.distIDKey, spUtil.getSharedPrefValueString(MyWorker.distIDKey))
                .putString(MyWorker.emailIDKey, spUtil.getSharedPrefValueString(MyWorker.emailIDKey))
                .putString(MyWorker.pinValueKey, spUtil.getSharedPrefValueString(MyWorker.pinValueKey))
                .putString(MyWorker.only18PlusKey, spUtil.getSharedPrefValueString(MyWorker.only18PlusKey))
                .putString(MyWorker.intervalValueKey, spUtil.getSharedPrefValueString(MyWorker.intervalValueKey))
                .putString(MyWorker.districtNameKey, spUtil.getSharedPrefValueString(MyWorker.districtNameKey))
                .putInt(MyWorker.notificationIDKey, (int) spUtil.getSharedPrefValueLong(MyWorker.notificationIDKey))
                .build();

        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();

        PeriodicWorkRequest periodicWorkRequest
                = new PeriodicWorkRequest.Builder(MyWorker.class, intervalInLong, TimeUnit.SECONDS)
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(workerTag)
                // setting a backoff on case the work needs to retry
                //PeriodicWorkRequest.MIN_BACKOFF_MILLIS
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                //.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build();

        return WorkManager.getInstance().enqueueUniquePeriodicWork(workerTag, ExistingPeriodicWorkPolicy.REPLACE, periodicWorkRequest);

    }
}