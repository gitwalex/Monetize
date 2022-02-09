package com.gerwalex.monetize.ads;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.transition.Fade;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.gerwalex.lib.R;
import com.gerwalex.lib.main.App;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;

public class AdViewWrapper extends FrameLayout {

    private final AdRequest adRequest;
    private final String adUnitId;
    private final AdaptiveBannerSize adaptiveBannerSize;
    private final Type bannerType;
    private final AdView mAdView;
    private final Observer<? super Boolean> withAdObserver = new Observer<Boolean>() {
        @Override
        public void onChanged(Boolean noAds) {
            fadeInOut(noAds);
        }
    };
    private volatile boolean adViewSizeIsSet;

    public AdViewWrapper(@NonNull Context context) {
        this(context, null);
    }

    public AdViewWrapper(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AdViewWrapper(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        adRequest = new AdRequest.Builder().build();
        App.isTestDevice = adRequest.isTestDevice(context);
        //        if (BuildConfig.DEBUG && !App.isTestDevice) {
        //            throw new UnsupportedOperationException("Device ist kein Testgerät. Debigging nicht möglich");
        //        }
        Log.d("gerwalex", "App.isTestDevice: " + App.isTestDevice);
        Resources res = getResources();
        setContentDescription(res.getString(R.string.adViewDescription));
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AdViewWrapper, 0, 0);
        try {
            int value = a.getInt(R.styleable.AdViewWrapper_bannerType, 0);
            bannerType = Type.values()[value];
            value = a.getInt(R.styleable.AdViewWrapper_adaptiveBannerSize, 0);
            adaptiveBannerSize = AdaptiveBannerSize.values()[value];
            adUnitId = a.getString(R.styleable.AdViewWrapper_adUnitId);
            if (adUnitId == null) {
                throw new IllegalArgumentException("Missing adUnitId!!");
            }
            Log.d("gerwalex", String.format("Loading AdViewType %1$s, adUnitId: %2$s ", bannerType.name(), adUnitId));
        } finally {
            a.recycle();
        }
        mAdView = new AdView(context);
        mAdView.setAdUnitId(adUnitId);
        addView(mAdView);
        mAdView.setAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                super.onAdFailedToLoad(error);
                Log.d("gerwalex",
                        String.format("AdMobUnitId: %1s, AdType: %2s, isTestDevice (%3s)", adUnitId, bannerType.name(),
                                App.isTestDevice));
                Log.d("gerwalex", "Ad loading failed: " + error);
                removeView(mAdView);
            }
        });
    }

    private void fadeInOut(Boolean fadeIn) {
        Transition transition = new Fade();
        transition.setDuration(getResources().getInteger(R.integer.fadeInOutDuration));
        transition.addTarget(AdViewWrapper.this);
        TransitionManager.beginDelayedTransition(AdViewWrapper.this, transition);
        setVisibility(fadeIn ? View.GONE : View.VISIBLE);
        Log.d("gerwalex", "AdViewWrapper:fadeInOut: (gone?) " + fadeIn);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            App.noAds.observeForever(withAdObserver);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!isInEditMode()) {
            App.noAds.removeObserver(withAdObserver);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != 0) {
            if (!adViewSizeIsSet) {
                AdSize adSize;
                if (bannerType == Type.AdaptiveBanner) {
                    float density = getResources().getDisplayMetrics().density;
                    Log.d("gerwalex", String.format("AdView measured width %1$d, height %2$d: ", w, h));
                    int size = (int) (w / density);
                    switch (adaptiveBannerSize) {
                        case Anchored:
                            adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(getContext(), size);
                            break;
                        case Inline:
                            adSize = AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(getContext(), size);
                            break;
                        case Interscroller:
                            adSize = AdSize.getCurrentOrientationInterscrollerAdSize(getContext(), size);
                            break;
                        default:
                            throw new IllegalArgumentException("AdaptiveBannerSize nicht bekannt");
                    }
                } else {
                    adSize = bannerType.getAdSize();
                }
                mAdView.setAdSize(adSize);
                mAdView.loadAd(adRequest);
                adViewSizeIsSet = true;
            }
        }
    }

    public enum AdaptiveBannerSize {
        Anchored, Inline, Interscroller
    }

    public enum Type {
        Banner {
            @Override
            public AdSize getAdSize() {
                return AdSize.BANNER;
            }
        }, LargeBanner {
            @Override
            public AdSize getAdSize() {
                return AdSize.LARGE_BANNER;
            }
        }, MediumRectangle {
            @Override
            public AdSize getAdSize() {
                return AdSize.MEDIUM_RECTANGLE;
            }
        }, FullSizeBanner {
            @Override
            public AdSize getAdSize() {
                return AdSize.FULL_BANNER;
            }
        }, Leaderboard {
            @Override
            public AdSize getAdSize() {
                return AdSize.LEADERBOARD;
            }
        }, AdaptiveBanner {
            @Override
            public AdSize getAdSize() {
                throw new NullPointerException("AdSize wird in onSizeChanged ermittelt");
            }
        };

        public abstract AdSize getAdSize();
    }
}
