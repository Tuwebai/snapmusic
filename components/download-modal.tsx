import React, { useState } from "react";
import {
  Modal,
  View,
  Text,
  Pressable,
  ScrollView,
  Image,
  StyleSheet,
  Dimensions,
  ActivityIndicator,
} from "react-native";
import { MaterialIcons } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { VideoResult, DownloadFormat, ALL_FORMATS } from "@/lib/youtube-service";
import { startDownload } from "@/lib/downloads-store";

interface DownloadModalProps {
  visible: boolean;
  video: VideoResult | null;
  onClose: () => void;
  onDownloadStarted?: () => void;
  onPreview?: (type: "audio" | "video") => void;
}

const { width: SCREEN_WIDTH } = Dimensions.get("window");

export function DownloadModal({ visible, video, onClose, onDownloadStarted, onPreview }: DownloadModalProps) {
  const [selectedFormat, setSelectedFormat] = useState<DownloadFormat | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [activeTab, setActiveTab] = useState<"audio" | "video">("audio");

  const handleSelectFormat = (format: DownloadFormat) => {
    setSelectedFormat(format);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  };

  const handleDownload = async () => {
    if (!video || !selectedFormat) return;
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    setDownloading(true);
    await startDownload(video, selectedFormat);
    setDownloading(false);
    setSelectedFormat(null);
    onClose();
    onDownloadStarted?.();
  };

  const handleClose = () => {
    setSelectedFormat(null);
    setActiveTab("audio");
    onClose();
  };

  if (!video) return null;

  const formats = ALL_FORMATS[activeTab];

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={handleClose}
      statusBarTranslucent
    >
      <Pressable style={styles.overlay} onPress={handleClose}>
        <Pressable style={styles.sheet} onPress={() => {}}>
          {/* Handle bar */}
          <View style={styles.handleBar} />

          {/* Video Info */}
          <View style={styles.videoInfo}>
            <Image
              source={{ uri: video.thumbnail }}
              style={styles.thumbnail}
              resizeMode="cover"
            />
            <View style={styles.videoMeta}>
              <Text style={styles.videoTitle} numberOfLines={2}>
                {video.title}
              </Text>
              <Text style={styles.videoChannel}>{video.channel}</Text>
              <Text style={styles.videoDuration}>
                <MaterialIcons name="access-time" size={12} color="#AAAAAA" /> {video.duration}
              </Text>
            </View>
          </View>

          {/* Tab Selector */}
          <View style={styles.tabRow}>
            <Pressable
              style={[styles.tab, activeTab === "audio" && styles.tabActive]}
              onPress={() => {
                setActiveTab("audio");
                setSelectedFormat(null);
                Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
              }}
            >
              <MaterialIcons
                name="music-note"
                size={16}
                color={activeTab === "audio" ? "#E53935" : "#AAAAAA"}
              />
              <Text style={[styles.tabText, activeTab === "audio" && styles.tabTextActive]}>
                Audio
              </Text>
            </Pressable>
            <Pressable
              style={[styles.tab, activeTab === "video" && styles.tabActive]}
              onPress={() => {
                setActiveTab("video");
                setSelectedFormat(null);
                Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
              }}
            >
              <MaterialIcons
                name="videocam"
                size={16}
                color={activeTab === "video" ? "#E53935" : "#AAAAAA"}
              />
              <Text style={[styles.tabText, activeTab === "video" && styles.tabTextActive]}>
                Video
              </Text>
            </Pressable>
          </View>

          {/* Format Grid */}
          <ScrollView style={styles.formatsScroll} showsVerticalScrollIndicator={false}>
            <View style={styles.formatsGrid}>
              {formats.map((format) => {
                const isSelected = selectedFormat?.id === format.id;
                return (
                  <Pressable
                    key={format.id}
                    style={[styles.formatChip, isSelected && styles.formatChipSelected]}
                    onPress={() => handleSelectFormat(format)}
                  >
                    <Text style={[styles.formatExt, isSelected && styles.formatExtSelected]}>
                      {format.ext.toUpperCase()}
                    </Text>
                    <Text style={[styles.formatQuality, isSelected && styles.formatQualitySelected]}>
                      {format.quality}
                    </Text>
                    {format.size && (
                      <Text style={styles.formatSize}>{format.size}</Text>
                    )}
                    {isSelected && (
                      <View style={styles.checkBadge}>
                        <MaterialIcons name="check" size={10} color="#FFFFFF" />
                      </View>
                    )}
                  </Pressable>
                );
              })}
            </View>
          </ScrollView>

          {/* Buttons */}
          <View style={styles.footer}>
            {/* Preview Button */}
            <Pressable
              style={styles.previewBtn}
              onPress={() => {
                Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                onPreview?.(activeTab);
              }}
            >
              <MaterialIcons
                name={activeTab === "audio" ? "play-arrow" : "play-circle-outline"}
                size={22}
                color="#E53935"
              />
              <Text style={styles.previewBtnText}>
                {activeTab === "audio" ? "Escuchar vista previa" : "Ver vista previa"}
              </Text>
            </Pressable>

            {/* Download Button */}
            <Pressable
              style={[
                styles.downloadBtn,
                !selectedFormat && styles.downloadBtnDisabled,
              ]}
              onPress={handleDownload}
              disabled={!selectedFormat || downloading}
            >
              {downloading ? (
                <ActivityIndicator color="#FFFFFF" size="small" />
              ) : (
                <>
                  <MaterialIcons name="file-download" size={22} color="#FFFFFF" />
                  <Text style={styles.downloadBtnText}>
                    {selectedFormat
                      ? `Descargar ${selectedFormat.ext.toUpperCase()} · ${selectedFormat.quality}`
                      : "Selecciona un formato"}
                  </Text>
                </>
              )}
            </Pressable>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.7)",
    justifyContent: "flex-end",
  },
  sheet: {
    backgroundColor: "#1A1A1A",
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingBottom: 32,
    maxHeight: "85%",
  },
  handleBar: {
    width: 40,
    height: 4,
    backgroundColor: "#444",
    borderRadius: 2,
    alignSelf: "center",
    marginTop: 12,
    marginBottom: 16,
  },
  videoInfo: {
    flexDirection: "row",
    paddingHorizontal: 16,
    marginBottom: 16,
    gap: 12,
  },
  thumbnail: {
    width: 100,
    height: 60,
    borderRadius: 8,
    backgroundColor: "#242424",
  },
  videoMeta: {
    flex: 1,
    justifyContent: "center",
    gap: 2,
  },
  videoTitle: {
    color: "#FFFFFF",
    fontSize: 13,
    fontWeight: "600",
    lineHeight: 18,
  },
  videoChannel: {
    color: "#AAAAAA",
    fontSize: 12,
    lineHeight: 16,
  },
  videoDuration: {
    color: "#AAAAAA",
    fontSize: 11,
    lineHeight: 16,
  },
  tabRow: {
    flexDirection: "row",
    marginHorizontal: 16,
    backgroundColor: "#242424",
    borderRadius: 10,
    padding: 3,
    marginBottom: 16,
  },
  tab: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 8,
    borderRadius: 8,
    gap: 6,
  },
  tabActive: {
    backgroundColor: "#2A2A2A",
  },
  tabText: {
    color: "#AAAAAA",
    fontSize: 14,
    fontWeight: "500",
  },
  tabTextActive: {
    color: "#E53935",
    fontWeight: "600",
  },
  formatsScroll: {
    maxHeight: 200,
    paddingHorizontal: 16,
  },
  formatsGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
    paddingBottom: 8,
  },
  formatChip: {
    width: (SCREEN_WIDTH - 32 - 30) / 3,
    backgroundColor: "#242424",
    borderRadius: 12,
    padding: 12,
    alignItems: "center",
    borderWidth: 1.5,
    borderColor: "#2A2A2A",
    position: "relative",
  },
  formatChipSelected: {
    borderColor: "#E53935",
    backgroundColor: "rgba(229,57,53,0.1)",
  },
  formatExt: {
    color: "#FFFFFF",
    fontSize: 15,
    fontWeight: "700",
    marginBottom: 2,
  },
  formatExtSelected: {
    color: "#E53935",
  },
  formatQuality: {
    color: "#AAAAAA",
    fontSize: 11,
    textAlign: "center",
    lineHeight: 14,
  },
  formatQualitySelected: {
    color: "#E53935",
  },
  formatSize: {
    color: "#666",
    fontSize: 10,
    marginTop: 2,
  },
  checkBadge: {
    position: "absolute",
    top: 6,
    right: 6,
    width: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: "#E53935",
    alignItems: "center",
    justifyContent: "center",
  },
  footer: {
    paddingHorizontal: 16,
    paddingTop: 16,
    gap: 10,
  },
  previewBtn: {
    backgroundColor: "#1A1A1A",
    borderRadius: 14,
    paddingVertical: 14,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    borderWidth: 1.5,
    borderColor: "#E53935",
  },
  previewBtnText: {
    color: "#E53935",
    fontSize: 15,
    fontWeight: "600",
  },
  downloadBtn: {
    backgroundColor: "#E53935",
    borderRadius: 14,
    paddingVertical: 16,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
  },
  downloadBtnDisabled: {
    backgroundColor: "#3A2020",
  },
  downloadBtnText: {
    color: "#FFFFFF",
    fontSize: 15,
    fontWeight: "700",
  },
});
