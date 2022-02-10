package com.gerwalex.monetize.billing;

import static com.android.billingclient.api.BillingClient.SkuType.INAPP;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.gerwalex.monetize.databinding.BillingFragmentBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class BillingFragment extends Fragment
        implements PurchasesUpdatedListener, BillingClientStateListener, ConsumeResponseListener {

    private static final long START_DELAY = 1000L;
    private BillingClient billingClient;
    private Boolean billingClientConnected;
    private BillingFragmentBinding binding;
    /**
     * Verzögerung für Abruf Produkte und Prüfung, ob Aufbau Connection erfolgreich war.
     */
    private long retryDelay = START_DELAY;

    /**
     * In onResume werden regelmaessig die gekauften Produkte ermittel. Hier ist dann zu pruefen, ob ein gekauftes
     * Produkt noch verfuegbar sien kann.
     *
     * @param list Liste der gueltigen Produkte. Nicht null, kann aber leer sein.
     */
    @WorkerThread
    protected abstract void checkForInvalidProducts(@NonNull List<Purchase> list);

    /**
     * Kennzeichnet bei Google Play einen Kauf als verbraucht.
     *
     * @param purchaseToken token
     */
    @WorkerThread
    public void consumePurchase(@NonNull String purchaseToken) {
        if (billingClient.isReady()) {
            retryDelay = START_DELAY;
            ConsumeParams consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build();
            billingClient.consumeAsync(consumeParams, this);
        } else {
            // Not ready: retry delayed
            if (billingClientConnected != null && !billingClientConnected) {
                billingClientConnected = null;
                billingClient.startConnection(this);
            }
            if (retryDelay < TimeUnit.MINUTES.toMillis(2)) {
                Log.d("gerwalex",
                        String.format("ConsumePurchase: BillingClient not ready. Wait for %1$d Millis. ", retryDelay));
                requireView().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        consumePurchase(purchaseToken);
                    }
                }, retryDelay);
                retryDelay *= 2;
            } else {
                Log.e("gerwalex", "ConsumePurchase: Could not connect to Google Play, Giving up. ");
                onPurchaseConsumed(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE, purchaseToken);
                retryDelay = START_DELAY;
            }
        }
    }

    @WorkerThread
    protected void handlePurchase(@NonNull Purchase purchase) {
        switch (purchase.getPurchaseState()) {
            case Purchase.PurchaseState.PENDING:
                onPurchaseUpdated(BillingClient.BillingResponseCode.OK, purchase);
                break;
            case Purchase.PurchaseState.UNSPECIFIED_STATE:
                onPurchaseUpdated(BillingClient.BillingResponseCode.OK, purchase);
                break;
            case Purchase.PurchaseState.PURCHASED:
                // Verify the purchase.
                if (verifyValidSignature(purchase.getOriginalJson(), purchase.getSignature())) {
                    if (!purchase.isAcknowledged()) {
                        AcknowledgePurchaseParams acknowledgePurchaseParams =
                                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken())
                                        .build();
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams,
                                new AcknowledgePurchaseResponseListener() {
                                    @Override
                                    public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                            // Handle the success of the consume operation.
                                            Log.d("gerwalex",
                                                    String.format("Acknowledged, Purchase: %1$s ", purchase.getSkus()));
                                            onPurchaseUpdated(BillingClient.BillingResponseCode.OK, purchase);
                                        }
                                    }
                                });
                    }
                } else {
                    Log.d("gerwalex", "Signatur not valid: " + purchase.getSkus());
                }
        }
    }

    /**
     * Start eines Kaufs. Zum testen  muss zwingend ein Release erstellt werden. Wird der Build_Type
     * 'inAppPurchaseTest' ausgewaehlt, wird der Produktid eijn 'temp_' voraangestellt.
     *
     * @param produkt produkt
     */
    @UiThread
    public void initiatePurchase(@NonNull Product produkt) {
        List<String> skuList = new ArrayList<>();
        skuList.add(produkt.produktid);
        SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
        params.setSkusList(skuList).setType(produkt.skuType);
        billingClient.querySkuDetailsAsync(params.build(), new SkuDetailsResponseListener() {
            @Override
            public void onSkuDetailsResponse(@NonNull BillingResult billingResult, List<SkuDetails> skuDetailsList) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    if (skuDetailsList != null && skuDetailsList.size() > 0) {
                        BillingFlowParams flowParams =
                                BillingFlowParams.newBuilder().setSkuDetails(skuDetailsList.get(0)).build();
                        BillingResult launchBillingFlowResult =
                                billingClient.launchBillingFlow(requireActivity(), flowParams);
                        int result = launchBillingFlowResult.getResponseCode();
                        if (result != BillingClient.BillingResponseCode.OK) {
                            onPurchasesError(result);
                        }
                    } else {
                        //try to add item/product id "purchase" inside managed product in google play console
                        Log.d("gerwalex",
                                String.format("Purchase Item %1$s not Found, skuType %2$s " + produkt.produktid,
                                        produkt.skuType));
                    }
                } else {
                    Log.d("gerwalex", " Error " + billingResult.getDebugMessage());
                }
            }
        });
    }

    @Override
    public void onBillingServiceDisconnected() {
        billingClientConnected = false;
    }

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            Log.d("gerwalex", "BillingSetup ok.");
            billingClientConnected = true;
        } else {
            Log.d("gerwalex", "BillingSetup failed: " + billingResult);
            billingClientConnected = false;
        }
    }

    @Override
    public void onConsumeResponse(@NonNull BillingResult billingResult, @NonNull String purchaseToken) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            // Handle the success of the consume operation.
            Log.d("gerwalex", "Purchase Consumed");
            onPurchaseConsumed(billingResult.getResponseCode(), purchaseToken);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(new Runnable() {
            @Override
            public void run() {
                billingClient = BillingClient.newBuilder(requireContext()).enablePendingPurchases()
                        .setListener(BillingFragment.this).build();
                billingClient.startConnection(BillingFragment.this);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BillingFragmentBinding.inflate(inflater);
        return binding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        billingClient.endConnection();
    }

    @WorkerThread
    protected abstract void onPurchaseConsumed(@BillingClient.BillingResponseCode int billingResponseCode,
                                               @NonNull String purchaseToken);

    @WorkerThread
    protected abstract void onPurchaseUpdated(@BillingClient.BillingResponseCode int billingResponseCode,
                                              @NonNull Purchase purchase);

    @WorkerThread
    @CallSuper
    protected void onPurchasesError(@BillingClient.BillingResponseCode int billingResponseCode) {
        switch (billingResponseCode) {
            case BillingClient.BillingResponseCode.USER_CANCELED:
                Log.d("gerwalex", "BillingResponse USER_CANCELED ");
                break;
            case BillingClient.BillingResponseCode.ERROR:
                Log.d("gerwalex", "BillingResponse ERROR ");
                break;
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED:
                Log.d("gerwalex", "BillingResponse ITEM_ALREADY_OWNED ");
                break;
            case BillingClient.BillingResponseCode.ITEM_NOT_OWNED:
                Log.d("gerwalex", "BillingResponse ITEM_NOT_OWNED ");
                break;
            case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE:
                Log.d("gerwalex", "BillingResponse ITEM_UNAVAILABLE ");
                break;
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED:
                Log.d("gerwalex", "BillingResponse SERVICE_DISCONNECTED ");
                break;
            case BillingClient.BillingResponseCode.SERVICE_TIMEOUT:
                Log.d("gerwalex", "BillingResponse SERVICE_TIMEOUT ");
                break;
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE:
                Log.d("gerwalex", "BillingResponse SERVICE_UNAVAILABLE ");
                break;
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE:
                Log.d("gerwalex", "BillingResponse BILLING_UNAVAILABLE ");
                break;
            case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED:
                Log.d("gerwalex", "BillingResponse FEATURE_NOT_SUPPORTED ");
                break;
            case BillingClient.BillingResponseCode.DEVELOPER_ERROR:
                Log.d("gerwalex", "BillingResponse DEVELOPER_ERROR ");
                break;
            default:
                Log.d("gerwalex", String.format("BillingResponse UnKnown (%1d)", billingResponseCode));
        }
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
        if (list != null && list.size() > 0) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (Purchase purchase : list) {
                        int result = billingResult.getResponseCode();
                        if (result == BillingClient.BillingResponseCode.OK) {
                            // Ok: Kauf erfolgreich durchgeführt
                            handlePurchase(purchase);
                        } else {
                            onPurchasesError(result);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        new Thread(new Runnable() {
            @Override
            public void run() {
                queryPurchases();
            }
        });
    }

    /**
     * Ermittel alle gekauften Produkte der App. Ist der BillingClient nicht ready wird gewartet. Ist der
     * BillingClient nicht connected wird eine neue Connection gestartet.
     */
    @WorkerThread
    protected void queryPurchases() {
        if (billingClient.isReady()) {
            retryDelay = START_DELAY;
            billingClient.queryPurchasesAsync(INAPP, new PurchasesResponseListener() {
                @Override
                public void onQueryPurchasesResponse(@NonNull BillingResult billingResult,
                                                     @NonNull List<Purchase> list) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.d("gerwalex", "queryPurchases ok.");
                        if (list.size() > 0) {
                            Log.d("gerwalex", String.format("Purchases found: %1$s ", list.size()));
                            for (Purchase purchase : list) {
                                handlePurchase(purchase);
                            }
                        } else {
                            //try to add item/product id "purchase" inside managed product in google play console
                            Log.d("gerwalex", "Purchases queried. No Produkts for this App");
                        }
                    } else {
                        Log.d("gerwalex", " Error " + billingResult.getDebugMessage());
                    }
                    checkForInvalidProducts(list);
                }
            });
            // Not ready: retry delayed
        } else {
            if (billingClientConnected != null && !billingClientConnected) {
                billingClientConnected = null;
                billingClient.startConnection(this);
            }
            Log.d("gerwalex",
                    String.format("QueryPurchase: BillingClient not ready. Wait for %1$d Millis. ", retryDelay));
            requireView().postDelayed(new Runnable() {
                @Override
                public void run() {
                    queryPurchases();
                }
            }, retryDelay);
            retryDelay = Math.min(TimeUnit.MINUTES.toMillis(5), retryDelay * 2);
        }
    }

    /**
     * Verifies that the purchase was signed correctly for this developer's public key.
     * <p>Note: It's strongly recommended to perform such check on your backend since hackers can
     * replace this method with "constant true" if they decompile/rebuild your app.
     * </p>
     */

    protected abstract boolean verifyValidSignature(String originalJson, String signature);
}
