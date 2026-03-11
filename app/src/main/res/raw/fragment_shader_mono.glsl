#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTexCoord;
uniform samplerExternalOES uTexture;
uniform vec3 uColor; // Optional tint color, default to (1,1,1) for pure grayscale

void main() {
    vec4 color = texture2D(uTexture, vTexCoord);
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    
    // Mix original color with grayscale, or just output grayscale
    // For pure monochrome:
    vec3 mono = vec3(gray);
    
    // Apply tint if provided (simple multiply)
    // If uColor is (1.0, 1.0, 1.0), it's just grayscale.
    // If uColor is (1.0, 0.8, 0.6), it's sepia-like.
    
    gl_FragColor = vec4(mono * uColor, color.a);
}
