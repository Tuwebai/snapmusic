import React, { useRef, useState, useEffect } from "react";
import { View, Text, Pressable, ActivityIndicator, ScrollView } from "react-native";
import { useAudioPlayer, setAudioModeAsync } from "expo-audio";
import { useColors } from "@/hooks/use-colors";
import * as Haptics from "expo-haptics";

interface AudioPlayerProps {
  url: string;
  title: string;
  channel?: string;
  onClose?: () => void;
}

export function AudioPlayer({ url, title, channel, onClose }: AudioPlayerProps) {
  const colors = useColors();
  const [isLoading, setIsLoading] = useState(true);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

  const player = useAudioPlayer(url);

  useEffect(() => {
    // Enable playback in silent mode
    setAudioModeAsync({ playsInSilentMode: true });

    // Set loading to false after a short delay
    const timer = setTimeout(() => setIsLoading(false), 1000);
    return () => clearTimeout(timer);
  }, [url]);

  useEffect(() => {
    // Auto-play when component mounts
    player.play();
    setIsPlaying(true);

    // Cleanup on unmount
    return () => {
      player.pause();
    };
  }, [player]);

  const handlePlayPause = async () => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    if (isPlaying) {
      player.pause();
    } else {
      player.play();
    }
    setIsPlaying(!isPlaying);
  };

  const formatTime = (ms: number) => {
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, "0")}`;
  };

  return (
    <View className="flex-1 bg-gradient-to-b from-surface to-background">
      <ScrollView contentContainerStyle={{ flexGrow: 1 }} className="flex-1">
        {/* Album Art / Placeholder */}
        <View
          className="mx-6 mt-12 mb-8 rounded-2xl justify-center items-center"
          style={{
            aspectRatio: 1,
            backgroundColor: colors.primary,
            opacity: 0.9,
          }}
        >
          <Text className="text-6xl">🎵</Text>
        </View>

        {/* Title and Channel */}
        <View className="px-6 mb-8">
          <Text className="text-2xl font-bold text-foreground mb-2 text-center line-clamp-3">
            {title}
          </Text>
          {channel && (
            <Text className="text-base text-muted text-center line-clamp-2">{channel}</Text>
          )}
        </View>

        {/* Progress Bar */}
        <View className="px-6 mb-4">
          <View
            className="h-1 rounded-full mb-2"
            style={{ backgroundColor: colors.border }}
          >
            <View
              className="h-1 rounded-full"
              style={{
                backgroundColor: colors.primary,
                width: duration > 0 ? `${(currentTime / duration) * 100}%` : "0%",
              }}
            />
          </View>
          <View className="flex-row justify-between">
            <Text className="text-xs text-muted">{formatTime(currentTime)}</Text>
            <Text className="text-xs text-muted">{formatTime(duration)}</Text>
          </View>
        </View>

        {/* Loading Indicator */}
        {isLoading && (
          <View className="justify-center items-center py-4">
            <ActivityIndicator size="large" color={colors.primary} />
          </View>
        )}
      </ScrollView>

      {/* Controls */}
      <View className="bg-surface border-t border-border p-6">
        <View className="flex-row gap-3 justify-center mb-4">
          <Pressable
            onPress={handlePlayPause}
            style={({ pressed }) => [
              {
                width: 60,
                height: 60,
                borderRadius: 30,
                backgroundColor: colors.primary,
                justifyContent: "center",
                alignItems: "center",
                opacity: pressed ? 0.8 : 1,
              },
            ]}
          >
            <Text className="text-2xl text-white">{isPlaying ? "⏸" : "▶"}</Text>
          </Pressable>
        </View>

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
  );
}
