package com.abhishek.vaccinenotifier.activities;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.abhishek.vaccinenotifier.R;
import com.abhishek.vaccinenotifier.utils.SharedPrefUtil;
import com.abhishek.vaccinenotifier.workers.MyWorker;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    WebView mWebView;
    TextView checkStatus;
    TextView searchDetails;
    Button stopJobsButton;
    TextView moreInfo;
    String workerTag = "vaccine";
    String defaultPin = "123";
    public static String mpinValue;
    public static String distName;
    public static String interval;
    private static MainActivity instance;
    SharedPrefUtil spUtil;

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        instance = this;

        spUtil = new SharedPrefUtil(this, getApplicationContext().getPackageName());

        mWebView = (WebView) findViewById(R.id.vaccineWeb);
        mWebView.setInitialScale(0);

        searchDetails = (TextView) findViewById(R.id.searchDetails);
        checkStatus = (TextView) findViewById(R.id.vaccineStatus);
        stopJobsButton = (Button) findViewById(R.id.stopJobsButton);
        moreInfo = (TextView) findViewById(R.id.moreInfo);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);


        mWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void performClick(String distID, String emailID, String pinValue, String only18Plus, String intervalValue, String districtName) throws ExecutionException, InterruptedException {

                long intervalInLong = Long.valueOf(intervalValue);
                if (intervalInLong <= 1800) {
                    intervalInLong = 1800;
                }

                cancelAllWorkers();

                mpinValue = pinValue;
                if (null == pinValue || pinValue.toString().equals("")) {
                    mpinValue = defaultPin;
                }

                distName = districtName;
                interval = intervalValue;

                //creating a data object
                //to pass the data with workRequest
                //we can put as many variables needed
                Data inputData = new Data.Builder()
                        .putString(MyWorker.distIDKey, distID)
                        .putString(MyWorker.emailIDKey, emailID)
                        .putString(MyWorker.pinValueKey, mpinValue)
                        .putString(MyWorker.only18PlusKey, only18Plus)
                        .putString(MyWorker.intervalValueKey, intervalValue)
                        .putString(MyWorker.districtNameKey, districtName)
                        .putInt(MyWorker.notificationIDKey, 1)
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

                WorkManager.getInstance(getInstance()).enqueueUniquePeriodicWork(workerTag, ExistingPeriodicWorkPolicy.REPLACE, periodicWorkRequest);

                updateJobCount(workersCount(), null);
                updateSearchDetails(spUtil.getSharedPrefValueString(MyWorker.pinValueKey), spUtil.getSharedPrefValueString(MyWorker.districtNameKey), spUtil.getSharedPrefValueString(MyWorker.intervalValueKey));

            }
        }, "submitBtnAnd");


        mWebView.loadUrl("file:///android_asset/index.html");
        try {
            updateSearchDetails(spUtil.getSharedPrefValueString(MyWorker.pinValueKey), spUtil.getSharedPrefValueString(MyWorker.districtNameKey), spUtil.getSharedPrefValueString(MyWorker.intervalValueKey));
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            updateJobCount(workersCount(), null);
        } catch (ExecutionException e) {
            e.printStackTrace();
            updateJobCount(0, "Error!");
        } catch (InterruptedException e) {
            e.printStackTrace();
            updateJobCount(0, "Error!");
        }

        stopJobsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    cancelAllWorkers();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                searchDetails.setText("No search active");

                try {
                    updateJobCount(workersCount(), null);
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public int workersCount() throws ExecutionException, InterruptedException {
        int workerCount = 0;
        if (null != WorkManager.getInstance(getInstance()).getWorkInfosByTag(MyWorker.class.getName()).get()) {
            List<WorkInfo> workInfoList = WorkManager.getInstance(getInstance()).getWorkInfosByTag(MyWorker.class.getName()).get();
            for (WorkInfo workInfo : workInfoList) {
                WorkInfo.State state = workInfo.getState();
                workerCount++;
            }
        }
        return workerCount;
    }

    public void cancelAllWorkers() throws ExecutionException, InterruptedException {
        WorkManager.getInstance(getInstance()).cancelAllWork();
        WorkManager.getInstance(getInstance()).pruneWork();
        cleanData();
    }

    public void updateJobCount(int count, String message) {
        if (null != message) {
            checkStatus.setText("Jobs Running: " + message);
        } else {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    checkStatus.setText("Jobs Running: " + count + "\nI have checked for " + spUtil.getSharedPrefValueLong(MyWorker.tryCountKey) + " times since you've scheduled me.");

                }
            });

        }
    }

    public void updateSearchDetails(String pin, String dist, String interval) throws ExecutionException, InterruptedException {

        String text;
        if (null == pin) {
            if (workersCount() == 0) {
                text = "No search active";
            } else {
                String temp = (null == spUtil.getSharedPrefValueString(MyWorker.pinValueKey)) ? "District: " + spUtil.getSharedPrefValueString(MyWorker.districtNameKey) : "PIN: " + spUtil.getSharedPrefValueString(MyWorker.pinValueKey);
                temp = (null == spUtil.getSharedPrefValueString(MyWorker.pinValueKey) && null == spUtil.getSharedPrefValueString(MyWorker.districtNameKey) && null == spUtil.getSharedPrefValueString(MyWorker.intervalValueKey)) ? "'Updating..'" : temp;
                text = (null == spUtil.getSharedPrefValueString(MyWorker.intervalValueKey)) ? "Search active for " + temp + ", Interval: 'Updating..'" : "Search active for " + temp + ", Interval: " + interval + " seconds";
            }
        } else {
            text = defaultPin.equals(pin) ? "District: " + dist : "PIN: " + pin;
            text = "Search active for " + text + ", Interval: " + interval + " seconds";
        }

        String finalText = text;
        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                searchDetails.setText(finalText);
            }
        });
    }

    public void cleanData() throws ExecutionException, InterruptedException {

        mpinValue = null;
        distName = null;
        interval = null;
        MyWorker.availableCount = 0;
        MyWorker.notAvailableCount = 0;
        MyWorker.tryCount = 0;
        spUtil.addPUpdateSharedPrefString(MyWorker.pinValueKey, null);
        spUtil.addPUpdateSharedPrefString(MyWorker.districtNameKey, null);
        spUtil.addPUpdateSharedPrefString(MyWorker.intervalValueKey, null);

        ((NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
        spUtil.addPUpdateSharedPrefLong("tryCount", 0);
    }
}