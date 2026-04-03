import { registerWebModule, NativeModule } from 'expo';

import { ChangeEventPayload } from './DualcamModule.types';

type DualcamModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
}

class DualcamModule extends NativeModule<DualcamModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
};

export default registerWebModule(DualcamModule, 'DualcamModule');
