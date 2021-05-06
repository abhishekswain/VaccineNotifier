package com.abhishek.vaccinenotifier.covidservices;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.abhishek.vaccinenotifier.utils.GMailSender;
import com.abhishek.vaccinenotifier.utils.JSONTOHTML;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class CovidDataService {

    public static final String notAvailable = "Not Available";
    String baseURL = "https://cdn-api.co-vin.in/api/v2/appointment/sessions/public/";
    String directoryPath;
    JSONArray available;
    String htmlFileName = "index.html";
    String filePath;

    Context mContext;

    public CovidDataService(Context mContext) {

        this.mContext = mContext;
        directoryPath = mContext.getFilesDir().getAbsolutePath().toString() + File.separator + "vaccinereport";
        filePath = directoryPath + File.separator + htmlFileName;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public String checkVaccineAvailability(String distID, String pinValue, String only18Plus, String emailID) {

        available = new JSONArray();
        boolean isOnly18Plus = Boolean.valueOf(only18Plus);

        try {

            Date date = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

            String byPin = "calendarByPin?pincode=" + pinValue;
            String byDistrict = "calendarByDistrict?district_id=" + distID;

            String finalURL = (String.valueOf(pinValue).length() == 6) ? (baseURL + byPin) : (baseURL + byDistrict);

            // convert date to calendar
            Calendar c = Calendar.getInstance();
            c.setTime(date);

            String result = null;

            URL url = new URL(finalURL + "&date=" + formatter.format(c.getTime()));
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            result = inputStreamToString(in);
            JSONArray centersArray = new JSONObject(result).getJSONArray("centers");

            // manipulate date
            c.add(Calendar.DATE, +7);

            url = new URL(finalURL + "&date=" + formatter.format(c.getTime()));
            urlConnection = (HttpURLConnection) url.openConnection();

            in = new BufferedInputStream(urlConnection.getInputStream());
            result = inputStreamToString(in);
            for (int i = 0; i < new JSONObject(result).getJSONArray("centers").length(); i++) {
                centersArray.put(new JSONObject(result).getJSONArray("centers").getJSONObject(i));
            }

            c.add(Calendar.DATE, +7);

            url = new URL(finalURL + "&date=" + formatter.format(c.getTime()));
            urlConnection = (HttpURLConnection) url.openConnection();

            in = new BufferedInputStream(urlConnection.getInputStream());
            result = inputStreamToString(in);


            for (int i = 0; i < new JSONObject(result).getJSONArray("centers").length(); i++) {
                centersArray.put(new JSONObject(result).getJSONArray("centers").getJSONObject(i));
            }


            JSONArray jsonArray = centersArray;

            for (int i = 0; i < jsonArray.length(); i++) {

                JSONArray sessions = jsonArray.getJSONObject(i).getJSONArray("sessions");
                JSONArray newSessions = new JSONArray();

                for (int j = 0; j < sessions.length(); j++) {

                    if (sessions.getJSONObject(j).getInt("available_capacity") != 0) {
                        if (isOnly18Plus) {
                            if (sessions.getJSONObject(j).getInt("min_age_limit") == 18) {
                                newSessions.put(sessions.getJSONObject(j));
                            }
                        } else {
                            newSessions.put(sessions.getJSONObject(j));
                        }

                    }

                    if ((j == sessions.length() - 1) && newSessions.length() > 0) {
                        (jsonArray.getJSONObject(i)).remove("sessions");
                        (jsonArray.getJSONObject(i)).put("sessions", newSessions);
                        available.put(jsonArray.getJSONObject(i));
                    }

                }
                newSessions = null;
                sessions = null;
            }
            if (available.length() > 0) {
                sendEmail(available, "jikun56@gmail.com", emailID, "abcd123");
            }

            boolean isSuccess = false;

            if (available.length() > 0) {
                isSuccess = writeAvailabilityHtmlFileToDirectory(available, directoryPath, htmlFileName);
                if (isSuccess) {
                    return filePath;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        deleteFile(directoryPath, htmlFileName);
        writeFileOnInternalStorage(directoryPath, htmlFileName, " <html><head></head><style>.json_object { margin:10px; padding-left:10px; border-left:1px solid #ccc}.json_key { font-weight: bold; }" +
                "</style>" + "<div><b>No vaccine slots available yet for you selection. Your notification content will be updated when vaccine slots are made available.<b></div>" + "</head></html>");
        return notAvailable;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private boolean writeAvailabilityHtmlFileToDirectory(JSONArray available, String dir, String fileName) throws JSONException {

        boolean isSuccess = writeFileOnInternalStorage(dir, fileName, " <html><head></head><style>.json_object { margin:10px; padding-left:10px; border-left:1px solid #ccc}.json_key { font-weight: bold; }" +
                "</style>" + JSONTOHTML.getHtmlData(available.toString()) + "</head></html>");

        return isSuccess;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void sendEmail(JSONArray available, String from, String to, String password) {

        try {

            GMailSender sender = new GMailSender(from, password);
            sender.sendMail("Vaccine Slot Available",
                    " <html><head></head><style>.json_object { margin:10px; padding-left:10px; border-left:1px solid #ccc}.json_key { font-weight: bold; }" +
                            "</style>" + JSONTOHTML.getHtmlData(available.toString()) + "</head></html>",
                    from,
                    to);

        } catch (Exception e) {
            Log.e("SendMail", e.getMessage(), e);
            Toast.makeText(mContext, "Email Notification Failed!", Toast.LENGTH_SHORT).show();
        }
    }

    private String inputStreamToString(InputStream is) {
        String rLine = "";
        StringBuilder sb = new StringBuilder();

        InputStreamReader isr = new InputStreamReader(is);

        BufferedReader rd = new BufferedReader(isr);

        try {
            while ((rLine = rd.readLine()) != null) {
                sb.append(rLine);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public boolean writeFileOnInternalStorage(String directory, String sFileName, String sBody) {
        File dir = new File(directory);
        if (!dir.exists()) {
            dir.mkdir();
        }

        try {
            File afile = new File(dir, sFileName);
            FileWriter writer = new FileWriter(afile);
            writer.append(sBody);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean deleteFile(String dir, String fileName) {

        boolean deleted = false;
        File myFile = new File(dir, fileName);
        if (myFile.exists()) {
            deleted = myFile.delete();
        }

        return deleted;
    }

}