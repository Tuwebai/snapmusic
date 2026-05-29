import React from "react";
import { View, Text, Image, Pressable, StyleSheet } from "react-native";
import { MaterialIcons } from "@expo/vector-icons";
import { VideoResult } from "@/lib/youtube-service";

interface VideoCardProps {
  video: VideoResult;
  onDownload: (video: VideoResult) => void;
  onPreview?: (video: VideoResult) => void;
}

function VideoCardBase({ video, onDownload, onPreview }: VideoCardProps) {
  return (
    <Pressable
      style={({ pressed }) => [styles.card, pressed && { opacity: 0.85 }]}
      onPress={() => onPreview?.(video)}
    >
      {/* Thumbnail */}
      <View style={styles.thumbnailContainer}>
        <Image
          source={{ uri: video.thumbnail }}
          style={styles.thumbnail}
          resizeMode="cover"
        />
        {/* Duration badge */}
        <View style={styles.durationBadge}>
          <Text style={styles.durationText}>{video.duration}</Text>
        </View>
        {/* Play overlay */}
        <View style={styles.playOverlay}>
          <MaterialIcons name="play-circle-outline" size={32} color="rgba(255,255,255,0.85)" />
        </View>
        {/* Download button overlay */}
        <Pressable
          style={({ pressed }) => [
            styles.downloadOverlay,
            pressed && { opacity: 0.7 },
          ]}
          onPress={(e) => {
            e.stopPropagation?.();
            onDownload(video);
          }}
        >
          <MaterialIcons name="file-download" size={20} color="#FFFFFF" />
        </Pressable>
      </View>

      {/* Info */}
      <View style={styles.info}>
        <Text style={styles.title} numberOfLines={2}>
          {video.title}
        </Text>
        <View style={styles.metaRow}>
          <MaterialIcons name="person" size={12} color="#AAAAAA" />
          <Text style={styles.channel} numberOfLines={1}>
            {video.channel}
          </Text>
        </View>
        <View style={styles.metaRow}>
          <MaterialIcons name="visibility" size={12} color="#AAAAAA" />
          <Text style={styles.views}>{video.views}</Text>
          <Text style={styles.dot}>·</Text>
          <Text style={styles.views}>{video.publishedAt}</Text>
        </View>
      </View>
    </Pressable>
  );
}

export const VideoCard = React.memo(VideoCardBase);

const styles = StyleSheet.create({
  card: {
    flexDirection: "row",
    paddingHorizontal: 16,
    paddingVertical: 10,
    gap: 12,
    borderBottomWidth: 0.5,
    borderBottomColor: "#2A2A2A",
  },
  thumbnailContainer: {
    position: "relative",
    width: 140,
    height: 82,
    borderRadius: 8,
    overflow: "hidden",
    backgroundColor: "#242424",
    flexShrink: 0,
  },
  thumbnail: {
    width: "100%",
    height: "100%",
  },
  playOverlay: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: "center",
    alignItems: "center",
  },
  durationBadge: {
    position: "absolute",
    bottom: 4,
    left: 4,
    backgroundColor: "rgba(0,0,0,0.8)",
    borderRadius: 4,
    paddingHorizontal: 5,
    paddingVertical: 2,
    zIndex: 2,
  },
  durationText: {
    color: "#FFFFFF",
    fontSize: 10,
    fontWeight: "600",
  },
  downloadOverlay: {
    position: "absolute",
    bottom: 4,
    right: 4,
    backgroundColor: "#E53935",
    borderRadius: 20,
    width: 32,
    height: 32,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.4,
    shadowRadius: 4,
    elevation: 4,
    zIndex: 3,
  },
  info: {
    flex: 1,
    justifyContent: "center",
    gap: 4,
  },
  title: {
    color: "#FFFFFF",
    fontSize: 13,
    fontWeight: "500",
    lineHeight: 18,
  },
  metaRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  channel: {
    color: "#AAAAAA",
    fontSize: 12,
    flex: 1,
  },
  views: {
    color: "#AAAAAA",
    fontSize: 11,
  },
  dot: {
    color: "#AAAAAA",
    fontSize: 11,
  },
});
