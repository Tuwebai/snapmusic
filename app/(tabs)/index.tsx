import React, { useState, useRef, useCallback } from "react";
import {
  View,
  Text,
  TextInput,
  FlatList,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  Keyboard,
  Image,
} from "react-native";
import { MaterialIcons } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { ScreenContainer } from "@/components/screen-container";
import { VideoCard } from "@/components/video-card";
import { DownloadModal } from "@/components/download-modal";
import { searchVideos, VideoResult } from "@/lib/youtube-service";
import { router } from "expo-router";

const POPULAR_SEARCHES = [
  "Bad Bunny",
  "Shakira",
  "Reggaeton 2024",
  "Maluma",
  "J Balvin",
  "Karol G",
  "Ozuna",
  "Daddy Yankee",
];

export default function SearchScreen() {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<VideoResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [selectedVideo, setSelectedVideo] = useState<VideoResult | null>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const inputRef = useRef<TextInput>(null);
  const renderResultItem = useCallback(
    ({ item }: { item: VideoResult }) => (
      <VideoCard video={item} onDownload={handleDownload} onPreview={handlePreview} />
    ),
    [handleDownload, handlePreview],
  );

  const handleSearch = useCallback(async (searchQuery?: string) => {
    const q = searchQuery ?? query;
    if (!q.trim()) return;
    Keyboard.dismiss();
    setLoading(true);
    setSearched(true);
    try {
      const data = await searchVideos(q.trim());
      setResults(data);
    } catch {
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, [query]);

  const handleDownload = useCallback((video: VideoResult) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    setSelectedVideo(video);
    setModalVisible(true);
  }, []);

  const handlePreview = useCallback((video: VideoResult) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    router.push({
      pathname: "/preview",
      params: {
        videoId: video.id,
        title: encodeURIComponent(video.title),
        channel: encodeURIComponent(video.channel),
        thumbnail: encodeURIComponent(video.thumbnail),
        duration: encodeURIComponent(video.duration),
        views: encodeURIComponent(video.views),
      },
    } as never);
  }, []);

  const handleDownloadStarted = useCallback(() => {
    router.push("/(tabs)/downloads" as never);
  }, []);

  const handleClear = useCallback(() => {
    setQuery("");
    setResults([]);
    setSearched(false);
    inputRef.current?.focus();
  }, []);

  return (
    <ScreenContainer containerClassName="bg-background" edges={["top", "left", "right"]}>
      {/* Header */}
      <View style={styles.header}>
        <Image
          source={require("@/assets/images/icon.png")}
          style={styles.logo}
          resizeMode="contain"
        />
        <Text style={styles.headerTitle}>SnapMusic</Text>
      </View>

      {/* Search Bar */}
      <View style={styles.searchContainer}>
        <View style={styles.searchBar}>
          <MaterialIcons name="search" size={22} color="#AAAAAA" style={styles.searchIcon} />
          <TextInput
            ref={inputRef}
            style={styles.searchInput}
            placeholder="Buscar música, videos..."
            placeholderTextColor="#666"
            value={query}
            onChangeText={setQuery}
            onSubmitEditing={() => handleSearch()}
            returnKeyType="search"
            autoCorrect={false}
            autoCapitalize="none"
          />
          {query.length > 0 && (
            <Pressable onPress={handleClear} style={styles.clearBtn}>
              <MaterialIcons name="close" size={18} color="#AAAAAA" />
            </Pressable>
          )}
        </View>
        <Pressable
          style={({ pressed }) => [styles.searchBtn, pressed && { opacity: 0.8 }]}
          onPress={() => handleSearch()}
        >
          <MaterialIcons name="search" size={22} color="#FFFFFF" />
        </Pressable>
      </View>

      {/* Content */}
      {loading ? (
        <View style={styles.centerContent}>
          <ActivityIndicator size="large" color="#E53935" />
          <Text style={styles.loadingText}>Buscando...</Text>
        </View>
      ) : searched && results.length === 0 ? (
        <View style={styles.centerContent}>
          <MaterialIcons name="search-off" size={64} color="#333" />
          <Text style={styles.emptyTitle}>Sin resultados</Text>
          <Text style={styles.emptySubtitle}>Intenta con otra búsqueda</Text>
        </View>
      ) : results.length > 0 ? (
        <FlatList
          data={results}
          keyExtractor={(item) => item.id}
          renderItem={renderResultItem}
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.listContent}
          keyboardShouldPersistTaps="handled"
          initialNumToRender={5}
          maxToRenderPerBatch={5}
          windowSize={7}
          removeClippedSubviews
        />
      ) : (
        // Empty state with popular searches
        <View style={styles.emptyState}>
          <MaterialIcons name="trending-up" size={32} color="#E53935" />
          <Text style={styles.trendingTitle}>Búsquedas populares</Text>
          <View style={styles.chipsContainer}>
            {POPULAR_SEARCHES.map((term) => (
              <Pressable
                key={term}
                style={({ pressed }) => [styles.chip, pressed && { opacity: 0.7 }]}
                onPress={() => {
                  setQuery(term);
                  handleSearch(term);
                }}
              >
                <MaterialIcons name="search" size={14} color="#AAAAAA" />
                <Text style={styles.chipText}>{term}</Text>
              </Pressable>
            ))}
          </View>
        </View>
      )}

      {/* Download Modal */}
      <DownloadModal
        visible={modalVisible}
        video={selectedVideo}
        onClose={() => setModalVisible(false)}
        onDownloadStarted={handleDownloadStarted}
        onPreview={(type) => {
          if (!selectedVideo) return;
          setModalVisible(false);
          router.push({
            pathname: "/preview",
            params: {
              videoId: selectedVideo.id,
              title: selectedVideo.title,
              channel: selectedVideo.channel,
              type,
            },
          } as never);
        }}
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
    paddingBottom: 12,
    gap: 10,
  },
  logo: {
    width: 32,
    height: 32,
    borderRadius: 8,
  },
  headerTitle: {
    color: "#FFFFFF",
    fontSize: 22,
    fontWeight: "700",
    letterSpacing: -0.5,
  },
  searchContainer: {
    flexDirection: "row",
    paddingHorizontal: 16,
    paddingBottom: 12,
    gap: 10,
    alignItems: "center",
  },
  searchBar: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#242424",
    borderRadius: 12,
    paddingHorizontal: 12,
    height: 46,
    borderWidth: 1,
    borderColor: "#2A2A2A",
  },
  searchIcon: {
    marginRight: 8,
  },
  searchInput: {
    flex: 1,
    color: "#FFFFFF",
    fontSize: 15,
    height: "100%",
  },
  clearBtn: {
    padding: 4,
  },
  searchBtn: {
    backgroundColor: "#E53935",
    width: 46,
    height: 46,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  centerContent: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 12,
  },
  loadingText: {
    color: "#AAAAAA",
    fontSize: 14,
  },
  emptyTitle: {
    color: "#FFFFFF",
    fontSize: 18,
    fontWeight: "600",
    marginTop: 8,
  },
  emptySubtitle: {
    color: "#AAAAAA",
    fontSize: 14,
  },
  listContent: {
    paddingBottom: 20,
  },
  emptyState: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 24,
    gap: 12,
  },
  trendingTitle: {
    color: "#FFFFFF",
    fontSize: 16,
    fontWeight: "600",
  },
  chipsContainer: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  chip: {
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
  chipText: {
    color: "#CCCCCC",
    fontSize: 13,
  },
});
