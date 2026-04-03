import { NativeModule, requireNativeModule } from 'expo';

import { DualcamModuleEvents } from './DualcamModule.types';

declare class DualcamModule extends NativeModule<DualcamModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<DualcamModule>('DualcamModule');
