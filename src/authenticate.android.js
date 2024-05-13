import { NativeModules } from "react-native";
import createError from "./createError";
import { requiresLegacyAuthentication } from "./config";

const { ReactNativeFingerprintScanner } = NativeModules;

const authCurrent = (title, subTitle, description, cancelButton, resolve, reject) => {
  if (requiresLegacyAuthentication) {
    reject(createError("FingerprintScannerNotSupported"));
    return;
  }
  ReactNativeFingerprintScanner.authenticate(title, subTitle, description, cancelButton)
    .then(() => {
      resolve(true);
    })
    .catch((error) => {
      // translate errors
      reject(createError(error.code, error.message));
    });
};

const nullOnAttempt = () => null;

export default ({ title, subTitle, description, cancelButton, onAttempt }) => {
  return new Promise((resolve, reject) => {
    if (!title) {
      title = description ? description : "Log In";
      description = "";
    }
    if (!subTitle) {
      subTitle = "";
    }
    if (!description) {
      description = "";
    }
    if (!cancelButton) {
      cancelButton = "Cancel";
    }
    if (!onAttempt) {
      onAttempt = nullOnAttempt;
    }

    return authCurrent(title, subTitle, description, cancelButton, resolve, reject);
  });
};
