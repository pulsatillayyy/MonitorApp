# Android 音视频处理应用

本项目展示了一个基于 OpenGL ES 2.0/3.0 的健壮且可扩展的 Android 实时视频处理应用程序。它从摄像头捕获视频，将其渲染到全屏 `GLSurfaceView`，并提供高级图像分析工具（直方图、波形图）和艺术滤镜（单色）。

## 功能

- **实时摄像头预览**：使用 `GL_TEXTURE_EXTERNAL_OES` 高效渲染摄像头帧。
- **全屏渲染**：最大化视频预览的屏幕空间。
- **交互式菜单**：底部有一个时尚的菜单栏，用于控制可视化效果。
- **图像分析**：
  - **直方图**：可视化当前帧的亮度分布。
  - **波形图**：显示亮度波形监视器，用于曝光分析。
- **艺术滤镜**：
  - **单色**：对视频流应用高质量的单色（灰度/色调）滤镜。

## 架构

应用程序遵循清晰的架构模式，将关注点分离到不同的层：

1.  **UI 层**：
    - `MainActivity`：处理 UI 交互和生命周期事件。
    - `CameraSurfaceView`：一个封装了 OpenGL 上下文管理的自定义 `GLSurfaceView`。

2.  **渲染层 (`renderer/`)**：
    - `CameraRenderer`：核心的 `GLSurfaceView.Renderer` 实现。协调纹理更新和绘制调用。
    - `ShaderManager`：用于加载和编译 GLSL 着色器的辅助类。
    - `FilterFactory`：工厂模式实现，便于在不同的视觉效果之间切换。

3.  **数据/硬件层 (`camera/`)**：
    - `CameraHelper`：封装 Android `Camera2` API，用于处理摄像头设备打开、会话配置和预览请求。它将纹理帧输出到 `CameraRenderer`。

4.  **着色器资源 (`res/raw/`)**：
    - `vertex_shader.glsl`：用于全屏四边形渲染的标准顶点着色器。
    - `fragment_shader_oes.glsl`：用于标准摄像头预览的片元着色器。
    - `fragment_shader_mono.glsl`：用于单色滤镜的片元着色器。
    - `fragment_shader_histogram.glsl`：（高级）用于直方图计算的着色器逻辑。

## 开发设置

### 前提条件

- Android Studio Koala 或更新版本。
- Android SDK API Level 24 (Nougat) 或更高（用于支持 Camera2 和 OpenGL ES 3.0）。
- 一台物理 Android 设备（模拟器可能对摄像头/OpenGL 支持有限）。

### 权限

此应用需要以下权限，将在运行时请求：
- `android.permission.CAMERA`
- `android.permission.RECORD_AUDIO`

### 构建和运行

1.  **克隆仓库**：
    ```bash
    git clone https://github.com/your-repo/android-av-processing.git
    ```
2.  **在 Android Studio 中打开**：选择项目根目录。
3.  **同步 Gradle**：允许 Android Studio 下载依赖项。
4.  **连接设备**：在 Android 设备上启用 USB 调试。
5.  **运行**：点击“Run”按钮 (Shift+F10)。

## 可扩展性

项目设计便于扩展：

- **添加新滤镜**：
  1.  在 `res/raw/` 中创建一个新的片元着色器。
  2.  实现一个继承自 `BaseFilter` 的新 `Filter` 类。
  3.  在 `FilterFactory` 中注册新滤镜。

- **添加新分析工具**：
  1.  实现分析逻辑（例如，使用计算着色器或标准 GL 绘制）。
  2.  在 UI 中添加切换按钮。

## 许可证

MIT License
