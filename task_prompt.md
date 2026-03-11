# Android Audio/Video Processing App Task Prompt

## Goal
Develop an Android application that captures audio and video streams using the camera and microphone, renders the video to the full screen using `GLSurfaceView`, and provides a bottom menu with three functional buttons: "Histogram", "Waveform", and "Monochrome".

## Requirements

### 1. Audio & Video Capture
- Use Android's `Camera2` API (or `CameraX` if preferred for simplicity, but `Camera2` is often better for raw data handling in OpenGL) to capture video frames.
- Use `AudioRecord` or `MediaRecorder` (if raw data is needed for future processing, `AudioRecord` is better) to capture audio. *Note: The prompt mainly emphasizes video processing features, but audio capture is requested.*
- Ensure permissions for Camera and Microphone are handled correctly (runtime permissions).

### 2. Rendering
- Use `GLSurfaceView` to render the camera preview to the entire screen.
- Implement a custom `GLSurfaceView.Renderer`.
- Use OES textures (`GLES11Ext.GL_TEXTURE_EXTERNAL_OES`) to handle the camera preview frames efficiently.

### 3. UI/UX
- The video preview should fill the entire screen.
- A bottom menu bar should overlay the video at the bottom.
- The menu should contain three buttons:
    1.  **Histogram** (直方图)
    2.  **Waveform** (波形图)
    3.  **Monochrome** (单色)

### 4. Features
- **Monochrome (Single Color Filter)**:
    - When clicked, apply a shader-based filter to the video stream.
    - The effect should be similar to a photography "grayscale" or specific single-color tint.
    - This should be implemented in the Fragment Shader.
- **Histogram & Waveform**:
    - These buttons should toggle the visualization of the *current screen's brightness information*.
    - **Histogram**: Display a brightness histogram overlay. This requires computing the luminance distribution of the frame.
    - **Waveform**: Display a luminance waveform overlay (typically x-axis is image x-position, y-axis is luminance intensity).
    - These visualizations should ideally be rendered using OpenGL (e.g., drawing lines/points on top of the video) or a custom View on top of the GLSurfaceView. Given the "render to GLSurfaceView" requirement, doing it in GL is more efficient and "standard" for video apps.

### 5. Code Quality & Architecture
- **Language**: Kotlin (preferred for modern Android development) or Java. *I will use Kotlin.*
- **Architecture**: MVVM or clean separation of concerns.
- **Style**: Strict adherence to Google's Android coding standards.
- **Extensibility**: The filter and analysis pipeline should be designed so new filters or analysis tools can be added easily.
- **Robustness**: Handle lifecycle events (onPause, onResume) correctly to prevent crashes or battery drain.

## Development Steps
1.  **Project Setup**: Configure `AndroidManifest.xml` for permissions and OpenGL version.
2.  **Shader Implementation**: Write Vertex and Fragment shaders for:
    - Basic OES texture rendering.
    - Monochrome/Grayscale filter.
    - (Optional) Compute shaders or efficient logic for Histogram/Waveform calculation.
3.  **OpenGL Renderer**: Implement `GLSurfaceView.Renderer` to manage textures, shaders, and drawing.
4.  **Camera Setup**: Implement a helper class to manage `Camera2` session and connect it to the OpenGL texture.
5.  **UI Implementation**: Create the layout with `GLSurfaceView` and the bottom button bar.
6.  **Logic Integration**: Connect buttons to the Renderer to switch shaders or enable overlays.

## Deliverables
- Fully functional Android project code.
- `README.md` explaining the architecture and how to build/run.
