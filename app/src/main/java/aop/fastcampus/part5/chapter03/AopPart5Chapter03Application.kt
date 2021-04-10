package aop.fastcampus.part5.chapter03

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

class AopPart5Chapter03Application: Application(), CameraXConfig.Provider {

    override fun getCameraXConfig(): CameraXConfig = Camera2Config.defaultConfig()




}
