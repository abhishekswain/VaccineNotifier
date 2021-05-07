package com.abhishek.vaccinenotifier.events;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.abhishek.vaccinenotifier.utils.SharedPrefUtil;
import com.abhishek.vaccinenotifier.workers.MyWorker;

public class BootCompleteReceiver extends BroadcastReceiver {

    SharedPrefUtil spUtil;

    @Override
    public void onReceive(Context context, Intent intent) {

        spUtil = new SharedPrefUtil(context, context.getApplicationContext().getPackageName());

        if (null != spUtil.getSharedPrefValueString(MyWorker.intervalValueKey)) {
            MyWorker.startOrrestartWorker(spUtil);
        }

    }

}
