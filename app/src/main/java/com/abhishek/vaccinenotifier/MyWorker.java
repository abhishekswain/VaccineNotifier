package com.abhishek.vaccinenotifier;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.ExecutionException;

public class MyWorker extends Worker {

    Context mContext;

    //a public static string that will be used as the key
    //for sending and receiving data

    public static final String distID = "distID";
    public static final String pinValue = "pinValue";
    public static final String only18Plus = "only18Plus";
    public static final String emailID = "emailID";
    public static final String notificationID = "notificationID";
    public static final String intervalValue = "intervalValue";
    public static final String districtName = "districtName";
    public static String districtNameValue ;
    public static String pinValueString ;
    public static String intervalValueString;
    public static int availableCount;
    public static int notAvailableCount;
    public static long tryCount;
    public static boolean stopFlag = false;

    String NOTIFICATION_CHANNEL_ID = "my_channel_id_01";
    NotificationCompat.Builder notificationBuilder;
    NotificationManager notificationManager;

    CovidDataService covidDataService;

    public MyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {

        super(context, workerParams);
        mContext = context;
        covidDataService = new CovidDataService(mContext);
        notificationBuilder = new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID);
        notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        displayNotification("Vaccine availability information!", "Watch out this space for vaccine availability status", getInputData().getInt(this.notificationID, 1), false);

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @NonNull
    @Override
    public Result doWork() {

        try {
            int intervalNum = Integer.valueOf(getInputData().getString(this.intervalValue));
            stopFlag = false;

            int loopCount = intervalNum>=900 ? 1: 900/intervalNum;
            for(int i=0;i<loopCount;i++) {

                if(stopFlag){

                    notificationManager.cancelAll();
                    return Result.success();
                }
                //getting the input data
                String distID = getInputData().getString(this.distID);
                String emailID = getInputData().getString(this.emailID);
                String pinValue = getInputData().getString(this.pinValue);
                String only18Plus = getInputData().getString(this.only18Plus);
                String intervalValue = getInputData().getString(this.intervalValue);
                int notificationId = getInputData().getInt(this.notificationID, 1);
                districtNameValue = getInputData().getString(this.districtName);
                pinValueString = pinValue;
                intervalValueString = intervalValue;

                tryCount++;

                MainActivity.getInstance().updateJobCount(MainActivity.getInstance().workersCount(),null);
                MainActivity.getInstance().updateSearchDetails(pinValueString,districtNameValue,intervalValueString);

                String dataFilePath = covidDataService.checkVaccineAvailability(distID, pinValue, only18Plus, emailID);
                if (dataFilePath != CovidDataService.notAvailable) {
                    notAvailableCount++;
                    boolean stopNotificationAlertOnNextMsg = notAvailableCount > 1;
                    displayNotification("Vaccine Available!", "Vaccine booking slot(s) available, Click to know more!", notificationId, stopNotificationAlertOnNextMsg);
                } else {
                    availableCount++;
                    boolean stopNotificationAlertOnNextMsg = availableCount > 2;
                    displayNotification("Vaccine slots are not available yet for your selection!", "This notification will be updated once available.", notificationId, stopNotificationAlertOnNextMsg);
                }

                if(loopCount > 1) {
                    Thread.sleep(intervalNum * 1000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Result.retry();
        }

        return Result.success();
    }


    private void displayNotification(String title, String text, int id, boolean stopNotificationAlertOnNextMsg) {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "My Notifications", NotificationManager.IMPORTANCE_HIGH);

            // Configure the notification channel.
            notificationChannel.setDescription("Channel description");

            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});

            notificationManager.createNotificationChannel(notificationChannel);
        }

        notificationBuilder =null;
        notificationBuilder = new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID);

        if(stopNotificationAlertOnNextMsg){

            notificationBuilder.setOnlyAlertOnce(true);
        }

        notificationBuilder.setAutoCancel(false)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(mContext.getResources(),
                        R.mipmap.ic_launcher))
                .setTicker("Hearty365")
                .setPriority(Notification.PRIORITY_MAX)
                .setContentTitle(title)
                .setContentText(text+"  ("+MyWorker.tryCount+")")
                .setContentInfo("Info");


        Intent notificationIntent = new Intent(mContext, NotificationView.class);
        PendingIntent conPendingIntent = PendingIntent.getActivity(mContext, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        notificationBuilder.setContentIntent(conPendingIntent);
        notificationBuilder.setOngoing(true);
        notificationManager.notify(/*notification id*/id, notificationBuilder.build());
    }
}