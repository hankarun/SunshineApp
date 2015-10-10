package com.example.android.sunshine.app;


import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class WeatherListener extends WearableListenerService {
    private static final String TAG = WeatherListener.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;
    private static String condition;
    private static int temperature;
    private static int temperaturemin;
    private static int wID;
    private static boolean alreadyInitialize;
    private static String path;

    @Override
    public void onCreate() {
        super.onCreate();
    }


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .build();
        }

        if (!mGoogleApiClient.isConnected())
            mGoogleApiClient.connect();

        DataMap dataMap = DataMap.fromByteArray(messageEvent.getData());

        path = messageEvent.getPath();
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        DataMap config = putDataMapRequest.getDataMap();

        if (path.equals(Consts.PATH_WEATHER_INFO)) {

            if (dataMap.containsKey(Consts.KEY_WEATHER_CONDITION)) {
                condition = dataMap.getString(Consts.KEY_WEATHER_CONDITION);
            }

            if (dataMap.containsKey(Consts.KEY_WEATHER_TEMPERATUREMAX)) {
                temperature = dataMap.getInt(Consts.KEY_WEATHER_TEMPERATUREMAX);
            }

            if (dataMap.containsKey(Consts.KEY_WEATHER_TEMPERATUREMIN)) {
                temperaturemin = dataMap.getInt(Consts.KEY_WEATHER_TEMPERATUREMIN);
            }

            if (dataMap.containsKey(Consts.KEY_WEATHER_WID)) {
                wID = dataMap.getInt(Consts.KEY_WEATHER_WID);
            }

            config.putString(Consts.KEY_WEATHER_CONDITION, condition);
            config.putInt(Consts.KEY_WEATHER_TEMPERATUREMAX, temperature);
            config.putInt(Consts.KEY_WEATHER_TEMPERATUREMIN, temperaturemin);
            config.putInt(Consts.KEY_WEATHER_WID, wID);
        } else {
            if (!alreadyInitialize) {
                Wearable.NodeApi.getLocalNode(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetLocalNodeResult>() {
                    @Override
                    public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
                        Uri uri = new Uri.Builder()
                                .scheme("wear")
                                .path(path)
                                .authority(getLocalNodeResult.getNode().getId())
                                .build();

                        Wearable.DataApi.getDataItem(mGoogleApiClient, uri)
                                .setResultCallback(
                                        new ResultCallback<DataApi.DataItemResult>() {
                                            @Override
                                            public void onResult(DataApi.DataItemResult dataItemResult) {
                                                if (dataItemResult.getStatus().isSuccess() && dataItemResult.getDataItem() != null) {
                                                    fetchConfig(DataMapItem.fromDataItem(dataItemResult.getDataItem()).getDataMap());
                                                }

                                                alreadyInitialize = true;
                                            }
                                        }
                                );
                    }
                });

                while (!alreadyInitialize) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        }

        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataMapRequest.asPutDataRequest())
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        Log.d(TAG, "SaveConfig: " + dataItemResult.getStatus() + ", " + dataItemResult.getDataItem().getUri());

                        mGoogleApiClient.disconnect();
                    }
                });
    }

    protected void fetchConfig(DataMap config) {
        if (config.containsKey(Consts.KEY_WEATHER_CONDITION)) {
            condition = config.getString(Consts.KEY_WEATHER_CONDITION);
        }

        if (config.containsKey(Consts.KEY_WEATHER_TEMPERATUREMAX)) {
            temperature = config.getInt(Consts.KEY_WEATHER_TEMPERATUREMAX);
        }

        if (config.containsKey(Consts.KEY_WEATHER_TEMPERATUREMIN)) {
            temperaturemin = config.getInt(Consts.KEY_WEATHER_TEMPERATUREMIN);
        }

        if (config.containsKey(Consts.KEY_WEATHER_WID)) {
            wID = config.getInt(Consts.KEY_WEATHER_WID);
        }
    }
}
