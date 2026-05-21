import { Tabs } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Platform, View, Text, StyleSheet } from "react-native";
import { MaterialIcons } from "@expo/vector-icons";
import { HapticTab } from "@/components/haptic-tab";
import { useColors } from "@/hooks/use-colors";
import { useEffect, useState } from "react";
import { getDownloads, subscribe } from "@/lib/downloads-store";

function DownloadTabIcon({ color, focused }: { color: string; focused: boolean }) {
  const [activeCount, setActiveCount] = useState(0);

  useEffect(() => {
    const update = () => {
      const count = getDownloads().filter((d) => d.status === "downloading").length;
      setActiveCount(count);
    };
    update();
    return subscribe(update);
  }, []);

  return (
    <View style={tabStyles.iconContainer}>
      <MaterialIcons name="file-download" size={26} color={color} />
      {activeCount > 0 && (
        <View style={tabStyles.badge}>
          <Text style={tabStyles.badgeText}>{activeCount > 9 ? "9+" : activeCount}</Text>
        </View>
      )}
    </View>
  );
}

const tabStyles = StyleSheet.create({
  iconContainer: {
    position: "relative",
    width: 28,
    height: 28,
    alignItems: "center",
    justifyContent: "center",
  },
  badge: {
    position: "absolute",
    top: -4,
    right: -6,
    backgroundColor: "#E53935",
    borderRadius: 8,
    minWidth: 16,
    height: 16,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 3,
  },
  badgeText: {
    color: "#FFFFFF",
    fontSize: 9,
    fontWeight: "700",
  },
});

export default function TabLayout() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const bottomPadding = Platform.OS === "web" ? 12 : Math.max(insets.bottom, 8);
  const tabBarHeight = 60 + bottomPadding;

  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: "#E53935",
        tabBarInactiveTintColor: "#666666",
        headerShown: false,
        tabBarButton: HapticTab,
        tabBarStyle: {
          paddingTop: 8,
          paddingBottom: bottomPadding,
          height: tabBarHeight,
          backgroundColor: "#0F0F0F",
          borderTopColor: "#1A1A1A",
          borderTopWidth: 1,
        },
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: "600",
          marginTop: 2,
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: "Buscar",
          tabBarIcon: ({ color }) => (
            <MaterialIcons name="search" size={26} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="url"
        options={{
          title: "URL",
          tabBarIcon: ({ color }) => (
            <MaterialIcons name="link" size={26} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="downloads"
        options={{
          title: "Descargas",
          tabBarIcon: ({ color, focused }) => (
            <DownloadTabIcon color={color} focused={focused} />
          ),
        }}
      />
    </Tabs>
  );
}
