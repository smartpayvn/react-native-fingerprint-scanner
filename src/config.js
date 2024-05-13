import { Platform } from "react-native";

export const requiresLegacyAuthentication = Platform.Version < 23;
