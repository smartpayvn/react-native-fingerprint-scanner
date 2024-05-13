import { NativeModules } from "react-native";
import createError from "./createError";
import { requiresLegacyAuthentication } from "./config";

const { ReactNativeFingerprintScanner } = NativeModules;

export default () => {
  return new Promise((resolve, reject) => {
    if (requiresLegacyAuthentication) {
      reject(createError("FingerprintScannerNotSupported"));
      return;
    }
    ReactNativeFingerprintScanner.isSensorAvailable()
      .then((biometryType) => resolve(biometryType))
      .catch((error) => reject(createError(error.code, error.message)));
  });
};
