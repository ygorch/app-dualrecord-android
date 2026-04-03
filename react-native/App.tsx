import React, { useState, useEffect, useRef } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, SafeAreaView, ActivityIndicator } from 'react-native';
import { Camera } from 'expo-camera';
import * as MediaLibrary from 'expo-media-library';
import DualcamView, { DualcamViewRef } from './modules/dualcam-module';
import * as NativeModulesProxy from 'expo-modules-core';

// Helper to call view commands since we mapped AsyncFunctions to the view
const startRecordingNative = () => {
  if (DualcamViewRef.current) {
     NativeModulesProxy.requireNativeModule('DualcamModule').startRecording(DualcamViewRef.current);
  }
};

const stopRecordingNative = () => {
  if (DualcamViewRef.current) {
     NativeModulesProxy.requireNativeModule('DualcamModule').stopRecording(DualcamViewRef.current);
  }
};

export default function App() {
  const [hasPermission, setHasPermission] = useState<boolean | null>(null);
  const [splitScreen, setSplitScreen] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [camerasReady, setCamerasReady] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const { status: camStatus } = await Camera.requestCameraPermissionsAsync();
      const { status: micStatus } = await Camera.requestMicrophonePermissionsAsync();
      const { status: mediaStatus } = await MediaLibrary.requestPermissionsAsync();

      setHasPermission(camStatus === 'granted' && micStatus === 'granted' && mediaStatus === 'granted');
    })();
  }, []);

  const toggleRecording = () => {
    if (isRecording) {
      stopRecordingNative();
    } else {
      startRecordingNative();
    }
  };

  if (hasPermission === null) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#ffffff" />
      </View>
    );
  }

  if (hasPermission === false) {
    return (
      <View style={styles.center}>
        <Text style={styles.text}>Sem acesso a câmera/mic/arquivos</Text>
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.cameraContainer}>
        {error ? (
          <Text style={styles.errorText}>{error}</Text>
        ) : (
          <DualcamView
            style={StyleSheet.absoluteFill}
            splitScreen={splitScreen}
            onCamerasReady={() => setCamerasReady(true)}
            onError={(e: any) => setError(e.nativeEvent.message)}
            onRecordingStarted={() => setIsRecording(true)}
            onRecordingStopped={() => setIsRecording(false)}
          />
        )}
      </View>

      <View style={styles.controls}>
        <TouchableOpacity
          style={styles.button}
          onPress={() => setSplitScreen(!splitScreen)}
        >
          <Text style={styles.buttonText}>
            {splitScreen ? "Modo PiP" : "Modo Split"}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.recordButton, isRecording && styles.recordingButton]}
          onPress={toggleRecording}
          disabled={!camerasReady}
        >
          <View style={[styles.innerRecord, isRecording && styles.innerRecording]} />
        </TouchableOpacity>

        <View style={{ width: 80 }} />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  center: { flex: 1, backgroundColor: '#000', justifyContent: 'center', alignItems: 'center' },
  text: { color: '#fff', fontSize: 18 },
  errorText: { color: '#ff4444', fontSize: 16, textAlign: 'center', padding: 20 },
  cameraContainer: { flex: 1, position: 'relative' },
  controls: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    padding: 20, paddingBottom: 40, backgroundColor: 'rgba(0,0,0,0.5)',
    position: 'absolute', bottom: 0, left: 0, right: 0,
  },
  button: { backgroundColor: 'rgba(255,255,255,0.2)', padding: 12, borderRadius: 8, width: 100, alignItems: 'center' },
  buttonText: { color: '#fff', fontWeight: 'bold' },
  recordButton: { width: 70, height: 70, borderRadius: 35, borderWidth: 4, borderColor: '#fff', justifyContent: 'center', alignItems: 'center' },
  recordingButton: { borderColor: '#ff4444' },
  innerRecord: { width: 54, height: 54, borderRadius: 27, backgroundColor: '#ff4444' },
  innerRecording: { width: 24, height: 24, borderRadius: 4 }
});
