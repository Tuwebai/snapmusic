import React, { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  FlatList,
  Pressable,
  StyleSheet,
  Image,
  Alert,
} from "react-native";
import { MaterialIcons } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { ScreenContainer } from "@/components/screen-container";
import {
  getDownloads,
  subscribe,
  removeDownload,
  clearCompleted,
  Download,
} from "@/lib/downloads-store";

function DownloadItem({ item, onRemove }: { item: Download; onRemove: (id: string) => void }) {
  const isDownloading = item.status === "downloading";
  const isCompleted = item.status === "completed";
  const isError = item.status === "error";

  const statusColor = isCompleted ? "#4CAF50" : isError ? "#F44336" : "#FF9800";
  const statusIcon = isCompleted ? "check-circle" : isError ? "error" : "downloading";
  const statusLabel = isCompleted
    ? "Completado"
    : isError
    ? item.error ?? "Error"
    : `${item.progress}%`;

  const handleRemove = () => {
    Alert.alert(
      "Eliminar descarga",
      "¿Deseas eliminar esta descarga del historial?",
      [
        { text: "Cancelar", style: "cancel" },
        {
          text: "Eliminar",
          style: "destructive",
          onPress: () => {
            Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
            onRemove(item.id);
          },
        },
      ]
    );
  };

  return (
    <View style={styles.downloadItem}>
      <Image source={{ uri: item.thumbnail }} style={styles.itemThumbnail} resizeMode="cover" />

      <View style={styles.itemContent}>
        <Text style={styles.itemTitle} numberOfLines={2}>
          {item.title}
        </Text>
        <Text style={styles.itemChannel}>{item.channel}</Text>

        {/* Format Badge */}
        <View style={styles.itemMeta}>
          <View style={styles.formatBadge}>
            <Text style={styles.formatBadgeText}>
              {item.format.ext.toUpperCase()} · {item.format.quality}
            </Text>
          </View>
          <View style={[styles.statusBadge, { backgroundColor: `${statusColor}20` }]}>
            <MaterialIcons name={statusIcon as any} size={12} color={statusColor} />
            <Text style={[styles.statusText, { color: statusColor }]}>{statusLabel}</Text>
          </View>
        </View>

        {/* Progress Bar */}
        {isDownloading && (
          <View style={styles.progressContainer}>
            <View style={styles.progressTrack}>
              <View
                style={[styles.progressFill, { width: `${item.progress}%` }]}
              />
            </View>
          </View>
        )}
      </View>

      {/* Actions */}
      <View style={styles.itemActions}>
        {isCompleted && (
          <Pressable
            style={({ pressed }) => [styles.actionBtn, pressed && { opacity: 0.6 }]}
            onPress={() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light)}
          >
            <MaterialIcons name="play-arrow" size={20} color="#E53935" />
          </Pressable>
        )}
        <Pressable
          style={({ pressed }) => [styles.actionBtn, pressed && { opacity: 0.6 }]}
          onPress={handleRemove}
        >
          <MaterialIcons name="delete-outline" size={20} color="#666" />
        </Pressable>
      </View>
    </View>
  );
}

export default function DownloadsScreen() {
  const [downloads, setDownloads] = useState<Download[]>(() => getDownloads());

  useEffect(() => {
    const unsubscribe = subscribe(() => {
      setDownloads([...getDownloads()]);
    });
    return unsubscribe;
  }, []);

  const handleRemove = useCallback(async (id: string) => {
    await removeDownload(id);
  }, []);

  const handleClearCompleted = useCallback(() => {
    Alert.alert(
      "Limpiar historial",
      "¿Deseas eliminar todas las descargas completadas?",
      [
        { text: "Cancelar", style: "cancel" },
        {
          text: "Limpiar",
          style: "destructive",
          onPress: async () => {
            Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
            await clearCompleted();
          },
        },
      ]
    );
  }, []);

  const activeDownloads = downloads.filter((d) => d.status === "downloading");
  const completedDownloads = downloads.filter((d) => d.status !== "downloading");

  const renderEmpty = () => (
    <View style={styles.emptyState}>
      <MaterialIcons name="file-download-off" size={72} color="#2A2A2A" />
      <Text style={styles.emptyTitle}>Sin descargas</Text>
      <Text style={styles.emptySubtitle}>
        Las descargas que inicies aparecerán aquí
      </Text>
    </View>
  );

  return (
    <ScreenContainer containerClassName="bg-background" edges={["top", "left", "right"]}>
      {/* Header */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <MaterialIcons name="file-download" size={24} color="#E53935" />
          <Text style={styles.headerTitle}>Descargas</Text>
          {downloads.length > 0 && (
            <View style={styles.countBadge}>
              <Text style={styles.countText}>{downloads.length}</Text>
            </View>
          )}
        </View>
        {completedDownloads.length > 0 && (
          <Pressable
            style={({ pressed }) => [styles.clearBtn, pressed && { opacity: 0.7 }]}
            onPress={handleClearCompleted}
          >
            <MaterialIcons name="delete-sweep" size={18} color="#AAAAAA" />
            <Text style={styles.clearBtnText}>Limpiar</Text>
          </Pressable>
        )}
      </View>

      {downloads.length === 0 ? (
        renderEmpty()
      ) : (
        <FlatList
          data={downloads}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => (
            <DownloadItem item={item} onRemove={handleRemove} />
          )}
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.listContent}
          ListHeaderComponent={
            activeDownloads.length > 0 ? (
              <View style={styles.sectionHeader}>
                <View style={styles.sectionDot} />
                <Text style={styles.sectionTitle}>
                  {activeDownloads.length} descarga{activeDownloads.length > 1 ? "s" : ""} activa{activeDownloads.length > 1 ? "s" : ""}
                </Text>
              </View>
            ) : null
          }
        />
      )}
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 12,
  },
  headerLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  headerTitle: {
    color: "#FFFFFF",
    fontSize: 22,
    fontWeight: "700",
    letterSpacing: -0.5,
  },
  countBadge: {
    backgroundColor: "#E53935",
    borderRadius: 10,
    paddingHorizontal: 7,
    paddingVertical: 2,
    minWidth: 20,
    alignItems: "center",
  },
  countText: {
    color: "#FFFFFF",
    fontSize: 11,
    fontWeight: "700",
  },
  clearBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: "#1A1A1A",
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#2A2A2A",
  },
  clearBtnText: {
    color: "#AAAAAA",
    fontSize: 12,
    fontWeight: "500",
  },
  sectionHeader: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 8,
    gap: 8,
  },
  sectionDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: "#FF9800",
  },
  sectionTitle: {
    color: "#AAAAAA",
    fontSize: 13,
    fontWeight: "500",
  },
  listContent: {
    paddingBottom: 20,
  },
  downloadItem: {
    flexDirection: "row",
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 12,
    borderBottomWidth: 0.5,
    borderBottomColor: "#1E1E1E",
    alignItems: "flex-start",
  },
  itemThumbnail: {
    width: 80,
    height: 50,
    borderRadius: 8,
    backgroundColor: "#242424",
    flexShrink: 0,
  },
  itemContent: {
    flex: 1,
    gap: 4,
  },
  itemTitle: {
    color: "#FFFFFF",
    fontSize: 13,
    fontWeight: "500",
    lineHeight: 18,
  },
  itemChannel: {
    color: "#AAAAAA",
    fontSize: 11,
  },
  itemMeta: {
    flexDirection: "row",
    gap: 6,
    flexWrap: "wrap",
    marginTop: 2,
  },
  formatBadge: {
    backgroundColor: "#242424",
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 3,
  },
  formatBadgeText: {
    color: "#CCCCCC",
    fontSize: 10,
    fontWeight: "600",
  },
  statusBadge: {
    flexDirection: "row",
    alignItems: "center",
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 3,
    gap: 3,
  },
  statusText: {
    fontSize: 10,
    fontWeight: "600",
  },
  progressContainer: {
    marginTop: 6,
  },
  progressTrack: {
    height: 3,
    backgroundColor: "#2A2A2A",
    borderRadius: 2,
    overflow: "hidden",
  },
  progressFill: {
    height: "100%",
    backgroundColor: "#E53935",
    borderRadius: 2,
  },
  itemActions: {
    flexDirection: "column",
    gap: 4,
    alignItems: "center",
    justifyContent: "center",
  },
  actionBtn: {
    width: 36,
    height: 36,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#1A1A1A",
    borderRadius: 8,
  },
  emptyState: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 12,
    paddingBottom: 80,
  },
  emptyTitle: {
    color: "#FFFFFF",
    fontSize: 20,
    fontWeight: "600",
    marginTop: 8,
  },
  emptySubtitle: {
    color: "#AAAAAA",
    fontSize: 14,
    textAlign: "center",
    paddingHorizontal: 40,
    lineHeight: 20,
  },
});
