# Android Audio/Video Processing App

This project demonstrates a robust and extensible Android application for real-time video processing using OpenGL ES 2.0/3.0. It captures video from the camera, renders it to a full-screen `GLSurfaceView`, and provides advanced image analysis tools (Histogram, Waveform) and artistic filters (Monochrome).

## Features

- **Real-time Camera Preview**: Efficiently renders camera frames using `GL_TEXTURE_EXTERNAL_OES`.
- **Full Screen Rendering**: Maximizes screen real estate for video preview.
- **Interactive Menu**: A sleek bottom menu bar for controlling visualizations.
- **Image Analysis**:
  - **Histogram**: Visualizes the luminance distribution of the current frame.
  - **Waveform**: Displays a luminance waveform monitor, useful for exposure analysis.
- **Artistic Filters**:
  - **Monochrome**: Applies a high-quality single-color (grayscale/tint) filter to the video feed.

## Architecture

The application follows a clean architecture pattern, separating concerns into distinct layers:

1.  **UI Layer**:
    - `MainActivity`: Handles UI interactions and lifecycle events.
    - `CameraSurfaceView`: A custom `GLSurfaceView` that encapsulates OpenGL context management.

2.  **Rendering Layer (`renderer/`)**:
    - `CameraRenderer`: The core `GLSurfaceView.Renderer` implementation. Orchestrates texture updates and drawing calls.
    - `ShaderManager`: A helper class to load and compile GLSL shaders.
    - `FilterFactory`: A factory pattern implementation to easily switch between different visual effects.

3.  **Data/Hardware Layer (`camera/`)**:
    - `CameraHelper`: Wraps the Android `Camera2` API to handle camera device opening, session configuration, and preview requests. It outputs texture frames to the `CameraRenderer`.

4.  **Shader Resources (`res/raw/`)**:
    - `vertex_shader.glsl`: Standard vertex shader for full-screen quad rendering.
    - `fragment_shader_oes.glsl`: Fragment shader for standard camera preview.
    - `fragment_shader_mono.glsl`: Fragment shader for the monochrome filter.
    - `fragment_shader_histogram.glsl`: (Advanced) Shader logic for histogram computation.

## Development Setup

### Prerequisites

- Android Studio Koala or newer.
- Android SDK API Level 24 (Nougat) or higher (for Camera2 and OpenGL ES 3.0 support).
- A physical Android device (emulators may have limited camera/OpenGL support).

### Permissions

This app requires the following permissions, which are requested at runtime:
- `android.permission.CAMERA`
- `android.permission.RECORD_AUDIO`

### Building and Running

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/your-repo/android-av-processing.git
    ```
2.  **Open in Android Studio**: Select the project root directory.
3.  **Sync Gradle**: Allow Android Studio to download dependencies.
4.  **Connect Device**: Enable USB Debugging on your Android device.
5.  **Run**: Click the "Run" button (Shift+F10).

## Extensibility

The project is designed for easy extension:

- **Adding New Filters**:
  1.  Create a new fragment shader in `res/raw/`.
  2.  Implement a new `Filter` class inheriting from the base `BaseFilter`.
  3.  Register the new filter in `FilterFactory`.

- **Adding New Analysis Tools**:
  1.  Implement the analysis logic (e.g., using Compute Shaders or standard GL drawing).
  2.  Add a toggle button in the UI.

## License

MIT License
