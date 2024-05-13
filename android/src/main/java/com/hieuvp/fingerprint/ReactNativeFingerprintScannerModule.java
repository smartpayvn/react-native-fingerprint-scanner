package com.hieuvp.fingerprint;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.biometric.BiometricPrompt.PromptInfo;
import androidx.fragment.app.FragmentActivity;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.module.annotations.ReactModule;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;


@ReactModule(name="ReactNativeFingerprintScanner")
public class ReactNativeFingerprintScannerModule
        extends ReactContextBaseJavaModule
        implements LifecycleEventListener {
    private static final int CUSTOM_ERROR_FAILED_RESULT = -100;
    private static final int CUSTOM_ERROR_PERMANENTLY_INVALIDATED = -101;

    public static final int MAX_AVAILABLE_TIMES = Integer.MAX_VALUE;
    public static final String TYPE_BIOMETRICS = "Biometrics";

    private final ReactApplicationContext mReactContext;
    private BiometricPrompt biometricPrompt;
    private final CryptoHelper cryptoHelper;

    public ReactNativeFingerprintScannerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        mReactContext = reactContext;
        cryptoHelper = new CryptoHelper(reactContext);
    }

    @Override
    public String getName() {
        return "ReactNativeFingerprintScanner";
    }

    @Override
    public void onHostResume() {
    }

    @Override
    public void onHostPause() {
    }

    @Override
    public void onHostDestroy() {
        this.release();
    }

    private int currentAndroidVersion() {
        return Build.VERSION.SDK_INT;
    }

    private boolean requiresCryptoAuthentication() {
        return currentAndroidVersion() >= 30;
    }

    public class AuthCallback extends BiometricPrompt.AuthenticationCallback {
        private Promise promise;

        public AuthCallback(final Promise promise) {
            super();
            this.promise = promise;
        }

        @Override
        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
            super.onAuthenticationError(errorCode, errString);
            this.promise.reject(biometricPromptErrName(errorCode), TYPE_BIOMETRICS);
        }

        @Override
        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
            super.onAuthenticationSucceeded(result);
            Cipher cipher = result.getCryptoObject().getCipher();
            if (cryptoHelper.checkCipherResult(cipher)) {
                this.promise.resolve(true);
            } else {
                this.promise.reject(biometricPromptErrName(CUSTOM_ERROR_FAILED_RESULT), TYPE_BIOMETRICS);
            }
        }
    }

    public BiometricPrompt getBiometricPrompt(final FragmentActivity fragmentActivity, final Promise promise) {
        // memoize so can be accessed to cancel
        if (biometricPrompt != null) {
            return biometricPrompt;
        }

        // listen for onHost* methods
        mReactContext.addLifecycleEventListener(this);

        AuthCallback authCallback = new AuthCallback(promise);
        Executor executor = Executors.newSingleThreadExecutor();
        biometricPrompt = new BiometricPrompt(
                fragmentActivity,
                executor,
                authCallback
        );

        return biometricPrompt;
    }

    private void biometricAuthenticate(final String title, final String subtitle, final String description, final String cancelButton, final Promise promise) {
        UiThreadUtil.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        FragmentActivity fragmentActivity = (FragmentActivity) mReactContext.getCurrentActivity();

                        if (fragmentActivity == null) return;

                        BiometricPrompt bioPrompt = getBiometricPrompt(fragmentActivity, promise);

                        PromptInfo promptInfo = new PromptInfo.Builder()
                                .setConfirmationRequired(false)
                                .setNegativeButtonText(cancelButton)
                                .setDescription(description)
                                .setSubtitle(subtitle)
                                .setTitle(title)
                                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                .build();

                        try {
                            if (requiresCryptoAuthentication()) {
                                Cipher cipher = cryptoHelper.getCipher();
                                if (cipher != null) {
                                    bioPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
                                    return;
                                }
                            }
                            bioPrompt.authenticate(promptInfo);
                        } catch (Exception e) {
                            e.printStackTrace();
                            promise.reject(biometricPromptErrName(CUSTOM_ERROR_PERMANENTLY_INVALIDATED), TYPE_BIOMETRICS);
                        }
                    }
                });

    }

    // the below constants are consistent across BiometricPrompt and BiometricManager
    private String biometricPromptErrName(int errCode) {
        switch (errCode) {
            case BiometricPrompt.ERROR_CANCELED:
                return "SystemCancel";
            case BiometricPrompt.ERROR_HW_NOT_PRESENT:
                return "FingerprintScannerNotSupported";
            case BiometricPrompt.ERROR_HW_UNAVAILABLE:
                return "FingerprintScannerNotAvailable";
            case BiometricPrompt.ERROR_LOCKOUT:
                return "DeviceLocked";
            case BiometricPrompt.ERROR_LOCKOUT_PERMANENT:
                return "DeviceLockedPermanent";
            case BiometricPrompt.ERROR_NEGATIVE_BUTTON:
                return "UserCancel";
            case BiometricPrompt.ERROR_NO_BIOMETRICS:
                return "FingerprintScannerNotEnrolled";
            case BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL:
                return "PasscodeNotSet";
            case BiometricPrompt.ERROR_NO_SPACE:
                return "DeviceOutOfMemory";
            case BiometricPrompt.ERROR_TIMEOUT:
                return "AuthenticationTimeout";
            case BiometricPrompt.ERROR_UNABLE_TO_PROCESS:
                return "AuthenticationProcessFailed";
            case BiometricPrompt.ERROR_USER_CANCELED:  // actually 'user elected another auth method'
                return "UserFallback";
            case BiometricPrompt.ERROR_VENDOR:
                // hardware-specific error codes
                return "HardwareError";
            case CUSTOM_ERROR_FAILED_RESULT:
                return "AuthenticationResultFailed";
            case CUSTOM_ERROR_PERMANENTLY_INVALIDATED:
                return "FingerprintScannerChanged";
            default:
                return "FingerprintScannerUnknownError";
        }
    }

    private String getSensorError() {
        BiometricManager biometricManager = BiometricManager.from(mReactContext);
        int authResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

        if (authResult == BiometricManager.BIOMETRIC_SUCCESS) {
            return null;
        }
        if (authResult == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            return "FingerprintScannerNotSupported";
        } else if (authResult == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            return "FingerprintScannerNotEnrolled";
        } else if (authResult == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
            return "FingerprintScannerNotAvailable";
        }

        return "FingerprintScannerUnknownError";
    }

    @ReactMethod
    public void authenticate(String title, String subtitle, String description, String cancelButton, final Promise promise) {
        String errorName = getSensorError();
        if (errorName != null) {
            promise.reject(errorName, TYPE_BIOMETRICS);
            ReactNativeFingerprintScannerModule.this.release();
            return;
        }

        biometricAuthenticate(title, subtitle, description, cancelButton, promise);
    }

    @ReactMethod
    public synchronized void release() {
        if (biometricPrompt != null) {
            biometricPrompt.cancelAuthentication();  // if release called from eg React
        }
        biometricPrompt = null;
        mReactContext.removeLifecycleEventListener(this);
    }

    @ReactMethod
    public void isSensorAvailable(final Promise promise) {
        String errorName = getSensorError();
        if (errorName != null) {
            promise.reject(errorName, TYPE_BIOMETRICS);
            ReactNativeFingerprintScannerModule.this.release();
        } else {
            promise.resolve(TYPE_BIOMETRICS);
        }
    }
}
