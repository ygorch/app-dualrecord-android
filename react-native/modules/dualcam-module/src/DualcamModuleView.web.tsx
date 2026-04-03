import * as React from 'react';

import { DualcamModuleViewProps } from './DualcamModule.types';

export default function DualcamModuleView(props: DualcamModuleViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
