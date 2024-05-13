import { NativeModules } from "react-native";
import createError from "./createError";

const { ReactNativeFingerprintScanner } = NativeModules;

export default () => {
  return new Promise((resolve, reject) => {
    console.log(ReactNativeFingerprintScanner);
    ReactNativeFingerprintScanner.isSensorAvailable()
      .then((biometryType) => resolve(biometryType))
      .catch((error) => reject(createError(error.code, error.message)));
  });
};
