package com.gerwalex.monetize.ads;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.gerwalex.monetize.R;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd;
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback;

import java.util.Objects;

public abstract class FragmentInterstatialAd extends AdManagerInterstitialAdLoadCallback {
    public final MutableLiveData<Boolean> isTestDevice = new MutableLiveData<>();
    private final String adUnitId;
    private final Context context;
    private final Handler handler;
    private AdManagerInterstitialAd mInterstitialAd;

    public FragmentInterstatialAd(@NonNull Context context, @NonNull String adUnitId) {
        this.context = context.getApplicationContext();
        this.adUnitId = Objects.requireNonNull(adUnitId);
        handler = new Handler(Looper.getMainLooper());
        initializeInterstitialAd(getAdUnitId());
    }

    private void afterInterstitialAdLoaded() {
        mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                // Called when fullscreen content is dismissed.
                Log.d("gerwalex", "The ad was dismissed.");
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                // Called when fullscreen content failed to show.
                Log.d("gerwalex", "The ad failed to show. " + adError);
            }

            @Override
            public void onAdShowedFullScreenContent() {
                // Called when fullscreen content is shown.
                // Make sure to set your reference to null so you don't
                // show it a second time.
                mInterstitialAd = null;
                initializeInterstitialAd(adUnitId);
                Log.d("gerwalex", "The ad was shown.");
            }
        });
    }

    public abstract String getAdUnitId();

    private void initializeInterstitialAd(String adUnitId) {
        AdManagerAdRequest adRequest = new AdManagerAdRequest.Builder().build();
        isTestDevice.setValue(adRequest.isTestDevice(context));
        Log.d("gerwalex", "isTestDevice: " + isTestDevice.getValue());
        AdManagerInterstitialAd.load(context, adUnitId, adRequest, this);
    }

    @Override
    public void onAdFailedToLoad(@NonNull LoadAdError error) {
        // Handle the error
        Log.d("gerwalex", "InterstatialAd loading failed: " + error.getMessage());
        mInterstitialAd = null;
    }

    @Override
    public void onAdLoaded(@NonNull AdManagerInterstitialAd interstitialAd) {
        // The mAdManagerInterstitialAd reference will be null until
        // an ad is loaded.
        mInterstitialAd = interstitialAd;
        afterInterstitialAdLoaded();
    }

    public void show(Activity activity, long delay) {
        if (mInterstitialAd != null) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mInterstitialAd.show(activity);
                    activity.overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_left);
                }
            }, delay);
        }
    }
}
