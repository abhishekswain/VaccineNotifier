package com.abhishek.vaccinenotifier.activities;

import android.annotation.SuppressLint;
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
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.abhishek.vaccinenotifier.R;
import com.abhishek.vaccinenotifier.utils.SharedPrefUtil;
import com.abhishek.vaccinenotifier.workers.MyWorker;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    WebView mWebView;
    TextView checkStatus;
    TextView searchDetails;
    Button stopJobsButton;
    TextView moreInfo;

    public static String mpinValue;
    public static String distName;
    public static String interval;
    private static MainActivity instance;
    SharedPrefUtil spUtil;

    public static MainActivity getInstance() {
        return instance;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
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

                cancelAllWorkers();

                mpinValue = pinValue;
                if (null == pinValue || pinValue.toString().equals("")) {
                    mpinValue = MyWorker.defaultPin;
                }

                distName = districtName;
                interval = intervalValue;

                spUtil.addOrUpdateSharedPrefString(MyWorker.distIDKey, distID);
                spUtil.addOrUpdateSharedPrefString(MyWorker.emailIDKey, emailID);
                spUtil.addOrUpdateSharedPrefString(MyWorker.pinValueKey, mpinValue);
                spUtil.addOrUpdateSharedPrefString(MyWorker.only18PlusKey, only18Plus);
                spUtil.addOrUpdateSharedPrefString(MyWorker.intervalValueKey, intervalValue);
                spUtil.addOrUpdateSharedPrefString(MyWorker.districtNameKey, districtName);
                spUtil.addOrUpdateSharedPrefLong(MyWorker.notificationIDKey, 1);

                MyWorker.startOrrestartWorker(spUtil);

                updateJobCountAndVaccineStatus(workersCount(), null, MyWorker.VACCINE_STATUS.PENDING);
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
            updateJobCountAndVaccineStatus(workersCount(), null, MyWorker.VACCINE_STATUS.PENDING);
        } catch (Exception e) {
            e.printStackTrace();
            updateJobCountAndVaccineStatus(0, "Error!", MyWorker.VACCINE_STATUS.PENDING);
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
                    updateJobCountAndVaccineStatus(workersCount(), null, MyWorker.VACCINE_STATUS.PENDING);
                } catch (Exception e) {
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

    public void updateJobCountAndVaccineStatus(int count, String message, MyWorker.VACCINE_STATUS vStatus) {

        if (null != message) {
            checkStatus.setText("Jobs Running: " + message);
        } else {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    String text = "Jobs Running: " + count + " | Polled " + spUtil.getSharedPrefValueLong(MyWorker.tryCountKey) + " times.";

                    if (vStatus != MyWorker.VACCINE_STATUS.PENDING) {
                        String availabilityStatus = (vStatus == MyWorker.VACCINE_STATUS.AVAILABLE) ? "Available" : "Not Available";
                        availabilityStatus = (vStatus == MyWorker.VACCINE_STATUS.ERROR) ? "Error" : availabilityStatus;
                        text = "Jobs Running: " + count + " | Polled " + spUtil.getSharedPrefValueLong(MyWorker.tryCountKey) + " times.\nAvailability Status:" + availabilityStatus;
                    }
                    checkStatus.setText(text);

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
            text = MyWorker.defaultPin.equals(pin) ? "District: " + dist : "PIN: " + pin;
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
        MyWorker.numOfNotificationOnAvailability = 0;
        MyWorker.tryCount = 0;
        spUtil.clearAllPref();

        ((NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
    }
}