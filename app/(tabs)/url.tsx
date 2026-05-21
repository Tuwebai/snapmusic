import React, { useState, useCallback } from "react";
import {
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  Image,
  ActivityIndicator,
  ScrollView,
  Alert,
} from "react-native";
import * as Clipboard from "expo-clipboard";
import { MaterialIcons } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { ScreenContainer } from "@/components/screen-container";
import { DownloadModal } from "@/components/download-modal";
import { getVideoInfo, VideoResult, DownloadFormat, ALL_FORMATS } from "@/lib/youtube-service";
import { startDownload } from "@/lib/downloads-store";
import { router } from "expo-router";

export default function UrlScreen() {
  const [url, setUrl] = useState("");
  const [videoInfo, setVideoInfo] = useState<VideoResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [modalVisible, setModalVisible] = useState(false);
  const [quickDownloading, setQuickDownloading] = useState<string | null>(null);

  const handlePaste = async () => {
    try {
      const text = await Clipboard.getStringAsync();
      if (text) {
        setUrl(text);
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
      }
    } catch {
      // ignore
    }
  };

  const handleAnalyze = async () => {
    const trimmedUrl = url.trim();
    if (!trimmedUrl) {
      setError("Ingresa una URL válida");
      return;
    }
    // Extract video ID from YouTube URL
    const videoIdMatch = trimmedUrl.match(
      /(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([a-zA-Z0-9_-]{11})/
    );
    if (!videoIdMatch) {
      setError("Solo se admiten URLs de YouTube por ahora");
      return;
    }
    const videoId = videoIdMatch[1];
    setError("");
    setVideoInfo(null);
    setLoading(true);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    try {
      const info = await getVideoInfo(videoId);
      if (info) {
        setVideoInfo({ ...info, url: trimmedUrl });
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      } else {
        setError("No se pudo obtener información del video");
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      }
    } catch {
      setError("Error al analizar la URL. Verifica que sea válida.");
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
    } finally {
      setLoading(false);
    }
  };

  const handleQuickDownload = async (format: DownloadFormat) => {
    if (!videoInfo) return;
    setQuickDownloading(format.id);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    await startDownload(videoInfo, format);
    setQuickDownloading(null);
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    router.push("/(tabs)/downloads" as never);
  };

  const handleClear = () => {
    setUrl("");
    setVideoInfo(null);
    setError("");
  };

  const quickAudioFormats = ALL_FORMATS.audio.slice(0, 3);
  const quickVideoFormats = ALL_FORMATS.video.slice(0, 3);

  return (
    <ScreenContainer containerClassName="bg-background" edges={["top", "left", "right"]}>
      <ScrollView showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">
        {/* Header */}
        <View style={styles.header}>
          <MaterialIcons name="link" size={24} color="#E53935" />
          <Text style={styles.headerTitle}>Convertir por URL</Text>
        </View>

        <Text style={styles.subtitle}>
          Pega el enlace de YouTube, SoundCloud u otra plataforma
        </Text>

        {/* URL Input */}
        <View style={styles.inputSection}>
          <View style={[styles.inputContainer, error ? styles.inputError : null]}>
            <MaterialIcons name="link" size={20} color="#AAAAAA" style={styles.inputIcon} />
            <TextInput
              style={styles.urlInput}
              placeholder="https://youtube.com/watch?v=..."
              placeholderTextColor="#555"
              value={url}
              onChangeText={(t) => {
                setUrl(t);
                setError("");
              }}
              autoCorrect={false}
              autoCapitalize="none"
              keyboardType="url"
              returnKeyType="done"
              onSubmitEditing={handleAnalyze}
              multiline={false}
            />
            {url.length > 0 && (
              <Pressable onPress={handleClear} style={styles.clearBtn}>
                <MaterialIcons name="close" size={18} color="#AAAAAA" />
              </Pressable>
            )}
          </View>

          {error ? <Text style={styles.errorText}>{error}</Text> : null}

          {/* Action Buttons */}
          <View style={styles.actionRow}>
            <Pressable
              style={({ pressed }) => [styles.pasteBtn, pressed && { opacity: 0.7 }]}
              onPress={handlePaste}
            >
              <MaterialIcons name="content-paste" size={18} color="#AAAAAA" />
              <Text style={styles.pasteBtnText}>Pegar</Text>
            </Pressable>

            <Pressable
              style={({ pressed }) => [
                styles.analyzeBtn,
                pressed && { opacity: 0.85 },
                loading && { opacity: 0.7 },
              ]}
              onPress={handleAnalyze}
              disabled={loading}
            >
              {loading ? (
                <ActivityIndicator size="small" color="#FFFFFF" />
              ) : (
                <>
                  <MaterialIcons name="search" size={18} color="#FFFFFF" />
                  <Text style={styles.analyzeBtnText}>Analizar</Text>
                </>
              )}
            </Pressable>
          </View>
        </View>

        {/* Supported Platforms */}
        {!videoInfo && !loading && (
          <View style={styles.platformsSection}>
            <Text style={styles.platformsTitle}>Plataformas compatibles</Text>
            <View style={styles.platformsList}>
              {[
                { name: "YouTube", icon: "play-circle-filled", color: "#E53935" },
                { name: "SoundCloud", icon: "cloud", color: "#FF5500" },
                { name: "Vimeo", icon: "videocam", color: "#1AB7EA" },
                { name: "Dailymotion", icon: "ondemand-video", color: "#0066DC" },
              ].map((p) => (
                <View key={p.name} style={styles.platformChip}>
                  <MaterialIcons name={p.icon as any} size={16} color={p.color} />
                  <Text style={styles.platformName}>{p.name}</Text>
                </View>
              ))}
            </View>
          </View>
        )}

        {/* Video Preview */}
        {videoInfo && (
          <View style={styles.previewSection}>
            {/* Thumbnail */}
            <View style={styles.previewThumbnailContainer}>
              <Image
                source={{ uri: videoInfo.thumbnail }}
                style={styles.previewThumbnail}
                resizeMode="cover"
              />
              <View style={styles.previewDuration}>
                <Text style={styles.previewDurationText}>{videoInfo.duration}</Text>
              </View>
            </View>

            {/* Video Info */}
            <View style={styles.previewInfo}>
              <Text style={styles.previewTitle} numberOfLines={2}>
                {videoInfo.title}
              </Text>
              <View style={styles.previewMeta}>
                <MaterialIcons name="person" size={13} color="#AAAAAA" />
                <Text style={styles.previewChannel}>{videoInfo.channel}</Text>
              </View>
              <View style={styles.previewMeta}>
                <MaterialIcons name="visibility" size={13} color="#AAAAAA" />
                <Text style={styles.previewViews}>{videoInfo.views}</Text>
              </View>
            </View>

            {/* Quick Download - Audio */}
            <View style={styles.quickSection}>
              <View style={styles.quickHeader}>
                <MaterialIcons name="music-note" size={16} color="#E53935" />
                <Text style={styles.quickTitle}>Descargar Audio</Text>
              </View>
              <View style={styles.quickFormats}>
                {quickAudioFormats.map((fmt) => (
                  <Pressable
                    key={fmt.id}
                    style={({ pressed }) => [
                      styles.quickBtn,
                      pressed && { opacity: 0.7 },
                      quickDownloading === fmt.id && styles.quickBtnActive,
                    ]}
                    onPress={() => handleQuickDownload(fmt)}
                    disabled={quickDownloading !== null}
                  >
                    {quickDownloading === fmt.id ? (
                      <ActivityIndicator size="small" color="#E53935" />
                    ) : (
                      <>
                        <Text style={styles.quickBtnExt}>{fmt.ext.toUpperCase()}</Text>
                        <Text style={styles.quickBtnQuality}>{fmt.quality}</Text>
                      </>
                    )}
                  </Pressable>
                ))}
              </View>
            </View>

            {/* Quick Download - Video */}
            <View style={styles.quickSection}>
              <View style={styles.quickHeader}>
                <MaterialIcons name="videocam" size={16} color="#E53935" />
                <Text style={styles.quickTitle}>Descargar Video</Text>
              </View>
              <View style={styles.quickFormats}>
                {quickVideoFormats.map((fmt) => (
                  <Pressable
                    key={fmt.id}
                    style={({ pressed }) => [
                      styles.quickBtn,
                      pressed && { opacity: 0.7 },
                      quickDownloading === fmt.id && styles.quickBtnActive,
                    ]}
                    onPress={() => handleQuickDownload(fmt)}
                    disabled={quickDownloading !== null}
                  >
                    {quickDownloading === fmt.id ? (
                      <ActivityIndicator size="small" color="#E53935" />
                    ) : (
                      <>
                        <Text style={styles.quickBtnExt}>{fmt.ext.toUpperCase()}</Text>
                        <Text style={styles.quickBtnQuality}>{fmt.quality}</Text>
                      </>
                    )}
                  </Pressable>
                ))}
              </View>
            </View>

            {/* More Formats Button */}
            <Pressable
              style={({ pressed }) => [styles.moreFormatsBtn, pressed && { opacity: 0.8 }]}
              onPress={() => setModalVisible(true)}
            >
              <MaterialIcons name="tune" size={18} color="#E53935" />
              <Text style={styles.moreFormatsBtnText}>Ver todos los formatos</Text>
              <MaterialIcons name="chevron-right" size={18} color="#E53935" />
            </Pressable>
          </View>
        )}

        <View style={{ height: 40 }} />
      </ScrollView>

      {/* Download Modal */}
      <DownloadModal
        visible={modalVisible}
        video={videoInfo}
        onClose={() => setModalVisible(false)}
        onDownloadStarted={() => router.push("/(tabs)/downloads" as never)}
      />
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 4,
    gap: 10,
  },
  headerTitle: {
    color: "#FFFFFF",
    fontSize: 22,
    fontWeight: "700",
    letterSpacing: -0.5,
  },
  subtitle: {
    color: "#AAAAAA",
    fontSize: 13,
    paddingHorizontal: 16,
    marginBottom: 20,
    lineHeight: 18,
  },
  inputSection: {
    paddingHorizontal: 16,
    gap: 10,
  },
  inputContainer: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#1A1A1A",
    borderRadius: 14,
    paddingHorizontal: 14,
    minHeight: 52,
    borderWidth: 1.5,
    borderColor: "#2A2A2A",
  },
  inputError: {
    borderColor: "#E53935",
  },
  inputIcon: {
    marginRight: 10,
  },
  urlInput: {
    flex: 1,
    color: "#FFFFFF",
    fontSize: 14,
    paddingVertical: 12,
  },
  clearBtn: {
    padding: 4,
  },
  errorText: {
    color: "#E53935",
    fontSize: 12,
    marginLeft: 4,
  },
  actionRow: {
    flexDirection: "row",
    gap: 10,
  },
  pasteBtn: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#242424",
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 6,
    borderWidth: 1,
    borderColor: "#2A2A2A",
  },
  pasteBtnText: {
    color: "#AAAAAA",
    fontSize: 14,
    fontWeight: "500",
  },
  analyzeBtn: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#E53935",
    borderRadius: 12,
    paddingVertical: 12,
    gap: 6,
  },
  analyzeBtnText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "700",
  },
  platformsSection: {
    paddingHorizontal: 16,
    marginTop: 28,
    gap: 12,
  },
  platformsTitle: {
    color: "#AAAAAA",
    fontSize: 13,
    fontWeight: "600",
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  platformsList: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  platformChip: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#1A1A1A",
    borderRadius: 20,
    paddingHorizontal: 14,
    paddingVertical: 8,
    gap: 6,
    borderWidth: 1,
    borderColor: "#2A2A2A",
  },
  platformName: {
    color: "#CCCCCC",
    fontSize: 13,
  },
  previewSection: {
    paddingHorizontal: 16,
    marginTop: 20,
    gap: 16,
  },
  previewThumbnailContainer: {
    position: "relative",
    borderRadius: 14,
    overflow: "hidden",
    backgroundColor: "#242424",
  },
  previewThumbnail: {
    width: "100%",
    height: 200,
  },
  previewDuration: {
    position: "absolute",
    bottom: 10,
    right: 10,
    backgroundColor: "rgba(0,0,0,0.8)",
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  previewDurationText: {
    color: "#FFFFFF",
    fontSize: 12,
    fontWeight: "600",
  },
  previewInfo: {
    gap: 6,
  },
  previewTitle: {
    color: "#FFFFFF",
    fontSize: 16,
    fontWeight: "600",
    lineHeight: 22,
  },
  previewMeta: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  previewChannel: {
    color: "#AAAAAA",
    fontSize: 13,
  },
  previewViews: {
    color: "#AAAAAA",
    fontSize: 13,
  },
  quickSection: {
    backgroundColor: "#1A1A1A",
    borderRadius: 14,
    padding: 14,
    gap: 12,
    borderWidth: 1,
    borderColor: "#2A2A2A",
  },
  quickHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  quickTitle: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "600",
  },
  quickFormats: {
    flexDirection: "row",
    gap: 8,
  },
  quickBtn: {
    flex: 1,
    backgroundColor: "#242424",
    borderRadius: 10,
    paddingVertical: 10,
    alignItems: "center",
    gap: 2,
    borderWidth: 1,
    borderColor: "#2A2A2A",
    minHeight: 52,
    justifyContent: "center",
  },
  quickBtnActive: {
    borderColor: "#E53935",
    backgroundColor: "rgba(229,57,53,0.1)",
  },
  quickBtnExt: {
    color: "#FFFFFF",
    fontSize: 13,
    fontWeight: "700",
  },
  quickBtnQuality: {
    color: "#AAAAAA",
    fontSize: 10,
  },
  moreFormatsBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "rgba(229,57,53,0.08)",
    borderRadius: 12,
    paddingVertical: 14,
    gap: 6,
    borderWidth: 1,
    borderColor: "rgba(229,57,53,0.3)",
  },
  moreFormatsBtnText: {
    color: "#E53935",
    fontSize: 14,
    fontWeight: "600",
    flex: 1,
    textAlign: "center",
  },
});
