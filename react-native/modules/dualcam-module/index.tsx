import { requireNativeViewManager } from 'expo-modules-core';
import * as React from 'react';

export type DualcamModuleViewProps = {
  splitScreen?: boolean;
  onCamerasReady?: (event: any) => void;
  onError?: (event: any) => void;
  onRecordingStarted?: (event: any) => void;
  onRecordingStopped?: (event: any) => void;
  style?: any;
};

const NativeView: React.ComponentType<DualcamModuleViewProps & { ref?: any }> =
  requireNativeViewManager('DualcamModule');

export const DualcamViewRef = React.createRef<any>();

export default function DualcamView(props: DualcamModuleViewProps) {
  return <NativeView ref={DualcamViewRef} {...props} />;
}
