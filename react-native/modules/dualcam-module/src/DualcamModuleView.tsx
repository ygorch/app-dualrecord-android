import { requireNativeView } from 'expo';
import * as React from 'react';

import { DualcamModuleViewProps } from './DualcamModule.types';

const NativeView: React.ComponentType<DualcamModuleViewProps> =
  requireNativeView('DualcamModule');

export default function DualcamModuleView(props: DualcamModuleViewProps) {
  return <NativeView {...props} />;
}
