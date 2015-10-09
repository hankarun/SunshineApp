package com.example.android.sunshine.app.wear;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.example.android.sunshine.app.R;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class WatchDataService extends WearableListenerService{
    public static final  String KEY_WEATHER_CONDITION   = "Condition";
    public static final  String KEY_WEATHER_SUNRISE     = "Sunrise";
    public static final  String KEY_WEATHER_SUNSET      = "Sunset";
    public static final  String KEY_WEATHER_TEMPERATURE = "Temperature";
    public static final  String PATH_WEATHER_INFO       = "/WeatherWatchFace/WeatherInfo";
    public static final  String PATH_SERVICE_REQUIRE    = "/WeatherService/Require";
    private static final String TAG                     = "WeatherService";

    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };

    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_SHORT_DESC = 1;
    private static final int INDEX_MAX_TEMP = 2;
    private static final int INDEX_MIN_TEMP = 3;


    private GoogleApiClient mGoogleApiClient;
    private String mPeerId;

    @Override
    public int onStartCommand(Intent intent,int flags,int startId)
    {
        if ( intent != null )
        {
                mPeerId = intent.getStringExtra( "PeerId" );
                Log.d("Activity " + mPeerId, "M");
                startTask();

        }
        return super.onStartCommand( intent, flags, startId );
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent )
    {
        super.onMessageReceived(messageEvent);
        mPeerId = messageEvent.getSourceNodeId();
        Log.d("Activity " + mPeerId, "MessageReceived: " + messageEvent.getPath());
        if ( messageEvent.getPath().equals( PATH_SERVICE_REQUIRE ) )
        {
            startTask();
        }
    }

    private void startTask() {
        Log.d(TAG, "Start Weather AsyncTask");
        mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(Wearable.API).build();
        execute();
    }

    private void execute(){
        if ( !mGoogleApiClient.isConnected() )
        { mGoogleApiClient.connect(); }

        DataMap config = new DataMap();

        String location = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location, System.currentTimeMillis());
        Cursor data = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return;
        }
        if (!data.moveToFirst()) {
            data.close();
            return;
        }

        // Extract the weather data from the Cursor
        int weatherId = data.getInt(INDEX_WEATHER_ID);
        int weatherArtResourceId = Utility.getIconResourceForWeatherCondition(weatherId);
        String description = data.getString(INDEX_SHORT_DESC);
        double maxTemp = data.getDouble(INDEX_MAX_TEMP);
        double minTemp = data.getDouble(INDEX_MIN_TEMP);
        String formattedMaxTemperature = Utility.formatTemperature(this, maxTemp);
        String formattedMinTemperature = Utility.formatTemperature(this, minTemp);
        data.close();
        //real
        config.putInt( KEY_WEATHER_TEMPERATURE, (int) maxTemp );
        config.putString( KEY_WEATHER_CONDITION, description );
        config.putInt(KEY_WEATHER_SUNSET, weatherId);
        config.putInt( KEY_WEATHER_SUNRISE, (int) minTemp );
        Log.d("app sunset",weatherId+"");

        //test
        //Random random = new Random();
        //config.putInt("Temperature",random.nextInt(100));
        //config.putString("Condition", new String[]{"clear","rain","snow","thunder","cloudy"}[random.nextInt
        // (4)]);

        Wearable.MessageApi.sendMessage( mGoogleApiClient, mPeerId, PATH_WEATHER_INFO, config.toByteArray() )
                .setResultCallback(
                        new ResultCallback<MessageApi.SendMessageResult>()
                        {
                            @Override
                            public void onResult( MessageApi.SendMessageResult sendMessageResult )
                            {
                                Log.d( TAG, "SendUpdateMessage: " + sendMessageResult.getStatus() );
                            }
                        }
                );
    }

}
