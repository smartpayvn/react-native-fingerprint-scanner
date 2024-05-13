import { DeviceEventEmitter, NativeModules } from "react-native";
import { requiresLegacyAuthentication } from "./config";

const { ReactNativeFingerprintScanner } = NativeModules;

export default () => {
  if (requiresLegacyAuthentication) {
    DeviceEventEmitter.removeAllListeners("FINGERPRINT_SCANNER_AUTHENTICATION");
  }

  ReactNativeFingerprintScanner.release();
};
