import React, { useRef, useState, useEffect } from "react";
import { View, Text, Pressable, ActivityIndicator } from "react-native";
import { VideoView, useVideoPlayer } from "expo-video";
import { useColors } from "@/hooks/use-colors";
import { cn } from "@/lib/utils";
import * as Haptics from "expo-haptics";

interface VideoPlayerProps {
  url: string;
  title: string;
  thumbnail?: string;
  onClose?: () => void;
}

export function VideoPlayer({ url, title, thumbnail, onClose }: VideoPlayerProps) {
  const colors = useColors();
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);

  const player = useVideoPlayer(url, (player) => {
    player.loop = false;
  });

  const handlePlayPause = async () => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    if (isPlaying) {
      player.pause();
    } else {
      player.play();
    }
    setIsPlaying(!isPlaying);
  };

  useEffect(() => {
    // Set loading to false after a short delay
    const timer = setTimeout(() => setIsLoading(false), 1000);
    return () => clearTimeout(timer);
  }, [url]);

  useEffect(() => {
    // Auto-play when component mounts
    player.play();
    setIsPlaying(true);
  }, [player]);

  return (
    <View className="flex-1 bg-black">
      {/* Video Container */}
      <View className="flex-1 bg-black justify-center items-center relative">
        {error ? (
          <View className="flex-1 justify-center items-center p-4">
            <Text className="text-red-500 text-center text-base">{error}</Text>
          </View>
        ) : (
          <>
            <VideoView
              style={{ width: "100%", height: "100%" }}
              player={player}
              allowsFullscreen
              allowsPictureInPicture
            />

            {/* Loading Indicator */}
            {isLoading && (
              <View className="absolute inset-0 justify-center items-center bg-black/50">
                <ActivityIndicator size="large" color={colors.primary} />
              </View>
            )}

            {/* Play/Pause Overlay */}
            <Pressable
              onPress={handlePlayPause}
              style={({ pressed }) => [
                {
                  position: "absolute",
                  width: "100%",
                  height: "100%",
                  justifyContent: "center",
                  alignItems: "center",
                  opacity: pressed ? 0.7 : 0,
                },
              ]}
            >
              <View
                className="w-16 h-16 rounded-full justify-center items-center"
                style={{ backgroundColor: colors.primary }}
              >
                <Text className="text-2xl text-white">{isPlaying ? "⏸" : "▶"}</Text>
              </View>
            </Pressable>
          </>
        )}
      </View>

      {/* Controls */}
      <View className="bg-surface p-4 border-t border-border">
        <Text className="text-foreground font-semibold text-base mb-3 line-clamp-2">{title}</Text>

        <View className="flex-row gap-2">
          <Pressable
            onPress={handlePlayPause}
            style={({ pressed }) => [
              {
                flex: 1,
                paddingVertical: 12,
                paddingHorizontal: 16,
                borderRadius: 8,
                backgroundColor: colors.primary,
                opacity: pressed ? 0.8 : 1,
              },
            ]}
          >
            <Text className="text-white text-center font-semibold">
              {isPlaying ? "Pausar" : "Reproducir"}
            </Text>
          </Pressable>

          {onClose && (
            <Pressable
              onPress={() => {
                Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                onClose();
              }}
              style={({ pressed }) => [
                {
                  paddingVertical: 12,
                  paddingHorizontal: 16,
                  borderRadius: 8,
                  backgroundColor: colors.surface,
                  borderWidth: 1,
                  borderColor: colors.border,
                  opacity: pressed ? 0.7 : 1,
                },
              ]}
            >
              <Text className="text-foreground text-center font-semibold">Cerrar</Text>
            </Pressable>
          )}
        </View>
      </View>
    </View>
  );
}
