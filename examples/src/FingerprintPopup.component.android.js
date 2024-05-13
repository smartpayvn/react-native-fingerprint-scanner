import PropTypes from "prop-types";
import { Component } from "react";
import { Alert } from "react-native";

import FingerprintScanner from "react-native-fingerprint-scanner";

class BiometricPopup extends Component {
  componentDidMount() {
    this.authCurrent();
  }

  componentWillUnmount = () => {
    FingerprintScanner.release();
  };

  authCurrent() {
    FingerprintScanner.authenticate({
      description: this.props.description || "Log in with Biometrics",
    })
      .then(() => {
        Alert.alert("Fingerprint Authentication", "Authenticated successfully");
      })
      .catch((error) => {
        Alert.alert("Fingerprint Authentication", error.message);
      });
  }

  render = () => {
    // current API UI provided by native BiometricPrompt
    return null;
  };
}

BiometricPopup.propTypes = {
  description: PropTypes.string,
};

export default BiometricPopup;
