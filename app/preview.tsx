import React, { useRef, useState } from "react";
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  StatusBar,
  Platform,
  Dimensions,
  ScrollView,
  Image,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { WebView } from "react-native-webview";
import { useColors } from "@/hooks/use-colors";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { MaterialIcons } from "@expo/vector-icons";

const { width: SCREEN_WIDTH } = Dimensions.get("window");
const VIDEO_HEIGHT = (SCREEN_WIDTH * 9) / 16;

export default function PreviewScreen() {
  const colors = useColors();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const params = useLocalSearchParams();

  const videoId = params.videoId as string;
  const title = decodeURIComponent((params.title as string) || "");
  const channel = decodeURIComponent((params.channel as string) || "");
  const thumbnail = decodeURIComponent((params.thumbnail as string) || "");
  const duration = decodeURIComponent((params.duration as string) || "");
  const views = decodeURIComponent((params.views as string) || "");

  const [webViewLoaded, setWebViewLoaded] = useState(false);
  const webViewRef = useRef(null);

  // YouTube embed HTML with autoplay and full controls
  const embedHtml = `
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
    #player { width: 100%; height: 100%; }
    iframe { width: 100%; height: 100%; border: none; }
  </style>
</head>
<body>
  <div id="player">
    <iframe
      src="https://www.youtube.com/embed/${videoId}?autoplay=1&controls=1&rel=0&modestbranding=1&playsinline=1&enablejsapi=1"
      allow="autoplay; fullscreen; picture-in-picture"
      allowfullscreen
      frameborder="0"
    ></iframe>
  </div>
</body>
</html>
  `;

  return (
    <View style={[styles.container, { backgroundColor: "#000" }]}>
      <StatusBar barStyle="light-content" backgroundColor="#000" />

      {/* Header */}
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <TouchableOpacity
          style={styles.backBtn}
          onPress={() => router.back()}
          activeOpacity={0.7}
        >
          <MaterialIcons name="arrow-back" size={24} color="#fff" />
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>
          Vista previa
        </Text>
        <View style={{ width: 40 }} />
      </View>

      {/* Video Player - YouTube WebView Embed */}
      <View style={[styles.videoContainer, { height: VIDEO_HEIGHT }]}>
        {Platform.OS !== "web" ? (
          <WebView
            ref={webViewRef}
            source={{ html: embedHtml }}
            style={styles.webView}
            allowsFullscreenVideo
            allowsInlineMediaPlayback
            mediaPlaybackRequiresUserAction={false}
            javaScriptEnabled
            domStorageEnabled
            onLoad={() => setWebViewLoaded(true)}
            onError={(e) => console.warn("WebView error:", e.nativeEvent)}
            startInLoadingState
            originWhitelist={["*"]}
            mixedContentMode="always"
          />
        ) : (
          // Web fallback: use iframe directly
          <iframe
            src={`https://www.youtube.com/embed/${videoId}?autoplay=1&controls=1&rel=0&modestbranding=1`}
            style={{ width: "100%", height: "100%", border: "none" }}
            allow="autoplay; fullscreen"
            allowFullScreen
          />
        )}
      </View>

      {/* Video Info */}
      <ScrollView
        style={styles.infoContainer}
        contentContainerStyle={{ paddingBottom: insets.bottom + 20 }}
        showsVerticalScrollIndicator={false}
      >
        {/* Title */}
        <Text style={styles.videoTitle}>{title}</Text>

        {/* Meta info */}
        <View style={styles.metaRow}>
          {views ? (
            <Text style={styles.metaText}>{views}</Text>
          ) : null}
          {duration ? (
            <>
              <Text style={styles.metaDot}>·</Text>
              <Text style={styles.metaText}>{duration}</Text>
            </>
          ) : null}
        </View>

        {/* Channel */}
        <View style={styles.channelRow}>
          <View style={styles.channelAvatar}>
            <Text style={styles.channelAvatarText}>
              {channel.charAt(0).toUpperCase()}
            </Text>
          </View>
          <Text style={styles.channelName}>{channel}</Text>
        </View>

        {/* Divider */}
        <View style={styles.divider} />

        {/* Action hint */}
        <View style={styles.hintContainer}>
          <MaterialIcons name="info-outline" size={18} color="#aaa" />
          <Text style={styles.hintText}>
            Toca el botón de descarga en la tarjeta del video para guardar en tu dispositivo.
          </Text>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#000",
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 12,
    paddingBottom: 8,
    backgroundColor: "#000",
  },
  backBtn: {
    width: 40,
    height: 40,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 20,
  },
  headerTitle: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "600",
    flex: 1,
    textAlign: "center",
  },
  videoContainer: {
    width: "100%",
    backgroundColor: "#000",
  },
  webView: {
    flex: 1,
    backgroundColor: "#000",
  },
  infoContainer: {
    flex: 1,
    backgroundColor: "#0f0f0f",
    paddingHorizontal: 16,
    paddingTop: 16,
  },
  videoTitle: {
    color: "#fff",
    fontSize: 17,
    fontWeight: "700",
    lineHeight: 24,
    marginBottom: 8,
  },
  metaRow: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 14,
  },
  metaText: {
    color: "#aaa",
    fontSize: 13,
  },
  metaDot: {
    color: "#aaa",
    fontSize: 13,
    marginHorizontal: 6,
  },
  channelRow: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 16,
  },
  channelAvatar: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "#ff0000",
    alignItems: "center",
    justifyContent: "center",
    marginRight: 10,
  },
  channelAvatarText: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "700",
  },
  channelName: {
    color: "#fff",
    fontSize: 14,
    fontWeight: "600",
  },
  divider: {
    height: 1,
    backgroundColor: "#222",
    marginBottom: 16,
  },
  hintContainer: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
    backgroundColor: "#1a1a1a",
    borderRadius: 10,
    padding: 12,
  },
  hintText: {
    color: "#aaa",
    fontSize: 13,
    lineHeight: 20,
    flex: 1,
  },
});
