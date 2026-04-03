package expo.modules.dualcammodule

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class DualcamModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("DualcamModule")

    View(DualcamModuleView::class) {
      Prop("splitScreen") { view: DualcamModuleView, splitScreen: Boolean ->
        view.setSplitScreen(splitScreen)
      }

      Events("onCamerasReady", "onError", "onRecordingStarted", "onRecordingStopped")

      AsyncFunction("startRecording") { view: DualcamModuleView ->
         view.startRecording()
      }

      AsyncFunction("stopRecording") { view: DualcamModuleView ->
         view.stopRecording()
      }

      OnViewDestroys { view: DualcamModuleView ->
        view.stopCamera()
      }
    }
  }
}
