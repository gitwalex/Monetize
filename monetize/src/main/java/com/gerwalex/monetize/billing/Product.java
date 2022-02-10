package com.gerwalex.monetize.billing;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.room.Ignore;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;

public abstract class Product {
    // @formatter:off
    /**
     * Produktid wie in der GooglePlayConsole abgelegt.
     */
    public final String produktid;
    /**
     * Type des Kauf: Abo oder einmaliger inApp-Kauf
     */
    @BillingClient.SkuType
    public final String skuType;
    /**
     * Timestamp des Kaufs. Entspricht Purchase.getTime().
     */
    protected Long acknowledgeTime;
    /**
     * Timestamp, wann das Produkt als abgelaufen markiert wurde. Ein Produkt kann ablaufen, wenn es ein Abo ist oder
     * bei inApp-Kaeufen, wenn der Kauf mehrmals moeglich sein soll. Wird im Produkt gesetzt
     */
    protected Long consumeTime;
    /**
     * Timestamp, wann das Abo ablaeuft. Wird im Produkt errechnet
     */
    protected Long endSubscribeTime;
    /**
     * Token wird nbeim Kauf mitgeliefert und identifiziert den Kauf eindeutig
     */
    protected String purchaseToken;
    /**
     * Timestamp, wann das Abo abgeschlossen wurde. Wird im Produkt gesetzt.
     */
    protected Long subscribeTime;
    /**
     * Timestamp, wann das Abo gekuendigt wurde. Wird im Produkt gesetzt..
     */
    protected Long unSubscribeTime;
    // @formatter:on
    public Product(String produktid, String purchaseToken, @BillingClient.SkuType String skuType, Long acknowledgeTime,
                   Long consumeTime, Long subscribeTime, Long unSubscribeTime, Long endSubscribeTime) {
        this.produktid = produktid;
        this.purchaseToken = purchaseToken;
        this.skuType = skuType;
        this.acknowledgeTime = acknowledgeTime;
        this.consumeTime = consumeTime;
        this.subscribeTime = subscribeTime;
        this.unSubscribeTime = unSubscribeTime;
        this.endSubscribeTime = endSubscribeTime;
    }

    @Ignore
    public Product(@BillingClient.SkuType String skuType, @NonNull String produktid) {
        this.produktid = produktid;
        this.skuType = skuType;
    }

    @WorkerThread
    protected abstract long acknowledge(@NonNull Purchase purchase);

    @WorkerThread
    protected abstract void consume();

    public Long getAcknowledgeTime() {
        return acknowledgeTime;
    }

    public Long getConsumeTime() {
        return consumeTime;
    }

    public Long getEndSubscribeTime() {
        return endSubscribeTime;
    }

    protected abstract long getId();

    public String getPurchaseToken() {
        return purchaseToken;
    }

    public Long getSubscribeTime() {
        return subscribeTime;
    }

    public Long getUnSubscribeTime() {
        return unSubscribeTime;
    }

    @NonNull
    @Override
    public String toString() {
        return "Produkt{" + "id=" + getId() + ", produktid='" + produktid + ", skuType='" + skuType + ", consumeTime=" +
                consumeTime + ",  subscribeTime=" + subscribeTime + ", unSubscribeTime=" + unSubscribeTime +
                ", endSubscribeTime=" + endSubscribeTime + '}';
    }
}
