package com.abhishek.vaccinenotifier.utils;

import android.os.Build;

import androidx.annotation.RequiresApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class JSONTOHTML {

    /**
     * Get the JSON data formated in HTML
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static String getHtmlData(String strJsonData) throws JSONException {
        return jsonToHtml(new JSONArray(strJsonData));
    }

    /**
     * convert json Data to structured Html text
     *
     * @param obj
     * @return string
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private static String jsonToHtml(Object obj) {
        StringBuilder html = new StringBuilder();

        try {
            if (obj instanceof JSONObject) {
                JSONObject jsonObject = (JSONObject) obj;

                List<String> list = new ArrayList<String>();
                jsonObject.keys().forEachRemaining(list::add);
                Object[] keys = list.toArray();

                html.append("<div class=\"json_object\">");

                if (keys.length > 0) {
                    for (Object key : keys) {
                        // print the key and open a DIV
                        html.append("<div><span class=\"json_key\">")
                                .append(key).append("</span> : ");

                        Object val = jsonObject.get(key.toString());
                        // recursive call
                        html.append(jsonToHtml(val));
                        // close the div
                        html.append("</div>");
                    }
                }

                html.append("</div>");

            } else if (obj instanceof JSONArray) {
                JSONArray array = (JSONArray) obj;
                for (int i = 0; i < array.length(); i++) {
                    // recursive call
                    html.append(jsonToHtml(array.get(i)));
                }
            } else {
                // print the value
                html.append(obj);
            }
        } catch (JSONException e) {
            return e.getLocalizedMessage();
        }

        return html.toString();
    }
}
