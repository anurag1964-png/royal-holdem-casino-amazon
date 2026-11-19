package com.playzonestudio.royalholdem;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.amazon.device.drm.LicensingService;
import com.amazon.device.iap.PurchasingListener;
import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.model.FulfillmentResult;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.Receipt;
import com.amazon.device.iap.model.UserDataResponse;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsShowOptions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String UNITY_GAME_ID = "80043681";
    private static final String UNITY_PLACEMENT_ID = "BP_Rewarded_Android";
    private static final boolean TEST_MODE = false;

    private WebView webView;
    private boolean adLoaded = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Amazon Licensing & IAP SDK
        try {
            LicensingService.verifyLicense(getApplicationContext(), licenseVerificationResult -> {});
        } catch (Throwable ignored) {}

        PurchasingService.registerListener(getApplicationContext(), new AmazonPurchasingListener());

        // Initialize Unity Ads SDK
        UnityAds.initialize(getApplicationContext(), UNITY_GAME_ID, TEST_MODE, new IUnityAdsInitializationListener() {
            @Override
            public void onInitializationComplete() {
                loadRewardedAd();
            }

            @Override
            public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
            }
        });

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.addJavascriptInterface(new AmazonIAPBridge(), "AndroidIAP");
        webView.addJavascriptInterface(new UnityAdsBridge(), "AndroidAds");

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Set<String> skus = new HashSet<>(Arrays.asList(
                "holdem_pack_starter",
                "holdem_pack_bronze",
                "holdem_pack_popular",
                "holdem_pack_value",
                "holdem_pack_vip",
                "holdem_pack_emperor"
        ));
        PurchasingService.getProductData(skus);
        PurchasingService.getPurchaseUpdates(false);
    }

    private void loadRewardedAd() {
        UnityAds.load(UNITY_PLACEMENT_ID, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String placementId) {
                adLoaded = true;
            }

            @Override
            public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
                adLoaded = false;
            }
        });
    }

    private void notifyWeb(final String js) {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(js, null);
            }
        });
    }

    public class AmazonIAPBridge {
        @JavascriptInterface
        public void purchase(String sku) {
            runOnUiThread(() -> PurchasingService.purchase(sku));
        }
    }

    public class UnityAdsBridge {
        @JavascriptInterface
        public void showRewardedAd(String placementId) {
            final String targetPlacement = (placementId != null && !placementId.isEmpty()) ? placementId : UNITY_PLACEMENT_ID;
            runOnUiThread(() -> {
                if (adLoaded) {
                    UnityAds.show(MainActivity.this, targetPlacement, new UnityAdsShowOptions(), new IUnityAdsShowListener() {
                        @Override
                        public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
                            adLoaded = false;
                            loadRewardedAd();
                            notifyWeb("if(window.onUnityAdComplete)window.onUnityAdComplete('FAILED');");
                        }

                        @Override
                        public void onUnityAdsShowStart(String placementId) {}

                        @Override
                        public void onUnityAdsShowClick(String placementId) {}

                        @Override
                        public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
                            adLoaded = false;
                            loadRewardedAd();
                            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                notifyWeb("if(window.onUnityAdComplete)window.onUnityAdComplete('COMPLETED');");
                            } else {
                                notifyWeb("if(window.onUnityAdComplete)window.onUnityAdComplete('SKIPPED');");
                            }
                        }
                    });
                } else {
                    UnityAds.load(targetPlacement, new IUnityAdsLoadListener() {
                        @Override
                        public void onUnityAdsAdLoaded(String pId) {
                            UnityAds.show(MainActivity.this, pId, new UnityAdsShowOptions(), new IUnityAdsShowListener() {
                                @Override
                                public void onUnityAdsShowFailure(String id, UnityAds.UnityAdsShowError e, String m) {
                                    loadRewardedAd();
                                    notifyWeb("if(window.onUnityAdComplete)window.onUnityAdComplete('FAILED');");
                                }
                                @Override
                                public void onUnityAdsShowStart(String id) {}
                                @Override
                                public void onUnityAdsShowClick(String id) {}
                                @Override
                                public void onUnityAdsShowComplete(String id, UnityAds.UnityAdsShowCompletionState state) {
                                    loadRewardedAd();
                                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                        notifyWeb("if(window.onUnityAdComplete)window.onUnityAdComplete('COMPLETED');");
                                    } else {
                                        notifyWeb("if(window.onUnityAdComplete)window.onUnityAdComplete('SKIPPED');");
                                    }
                                }
                            });
                        }

                        @Override
                        public void onUnityAdsFailedToLoad(String pId, UnityAds.UnityAdsLoadError error, String message) {
                            notifyWeb("if(window.onUnityAdComplete)window.onUnityAdComplete('FAILED');");
                        }
                    });
                }
            });
        }
    }

    private class AmazonPurchasingListener implements PurchasingListener {
        @Override
        public void onUserDataResponse(UserDataResponse response) {}

        @Override
        public void onProductDataResponse(ProductDataResponse response) {}

        @Override
        public void onPurchaseResponse(PurchaseResponse response) {
            if (response == null) return;
            PurchaseResponse.RequestStatus status = response.getRequestStatus();
            if (status == PurchaseResponse.RequestStatus.SUCCESSFUL) {
                Receipt receipt = response.getReceipt();
                if (receipt != null) {
                    String sku = receipt.getSku();
                    PurchasingService.notifyFulfillment(receipt.getReceiptId(), FulfillmentResult.FULFILLED);
                    notifyWeb("if(window.onAmazonPurchaseComplete)window.onAmazonPurchaseComplete('" + sku + "', true);");
                }
            } else {
                notifyWeb("if(window.onAmazonPurchaseComplete)window.onAmazonPurchaseComplete('', false);");
            }
        }

        @Override
        public void onPurchaseUpdatesResponse(PurchaseUpdatesResponse response) {
            if (response != null && response.getRequestStatus() == PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) {
                for (Receipt receipt : response.getReceipts()) {
                    if (!receipt.isCanceled()) {
                        String sku = receipt.getSku();
                        PurchasingService.notifyFulfillment(receipt.getReceiptId(), FulfillmentResult.FULFILLED);
                        notifyWeb("if(window.onAmazonPurchaseComplete)window.onAmazonPurchaseComplete('" + sku + "', true);");
                    }
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
