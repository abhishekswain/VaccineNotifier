package com.abhishek.vaccinenotifier.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefUtil {

    SharedPreferences sharedPreferences;
    SharedPreferences.Editor myEdit;
    Context mContext;

    public SharedPrefUtil(Context context, String name) {

        this.mContext = context;
        sharedPreferences = mContext.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    public void addPUpdateSharedPrefLong(String key, long value) {

        myEdit = sharedPreferences.edit();
        myEdit.putLong(key, value);
        myEdit.commit();

    }

    public void addPUpdateSharedPrefString(String key, String value) {

        myEdit = sharedPreferences.edit();
        myEdit.putString(key, value);
        myEdit.commit();

    }

    public String getSharedPrefValueString(String key) {
        return sharedPreferences.getString(key, null);
    }

    public long getSharedPrefValueLong(String key) {

        return sharedPreferences.getLong(key, 0);
    }
}
