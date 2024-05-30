package io.iskopasi.shader_test.utils.camera_utils


/** Generates a fullscreen quad to cover the entire viewport. Applies the transform set on the
camera surface to adjust for orientation and scaling when used for copying from the camera
surface to the render surface. We will pass an identity matrix when copying from the render
surface to the recording / preview surfaces. */
val TRANSFORM_VSHADER = """
attribute vec4 vPosition;
uniform mat4 texMatrix;
varying vec2 v_textureCoord;
void main() {
    gl_Position = vPosition;
    vec4 texCoord = vec4((vPosition.xy + vec2(1.0, 1.0)) / 2.0, 0.0, 1.0);
    v_textureCoord = (texMatrix * texCoord).xy;
}
"""

/**
 * Fragment shaders
 */
val INCLUDE_HLG_EOTF = """
// BT.2100 / BT.2020 HLG EOTF for one channel.
highp float hlgEotfSingleChannel(highp float hlgChannel) {
  // Specification:
  // https://www.khronos.org/registry/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_HLG
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=265-279;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float a = 0.17883277;
  const highp float b = 0.28466892;
  const highp float c = 0.55991073;
  return hlgChannel <= 0.5 ? hlgChannel * hlgChannel / 3.0 :
      (b + exp((hlgChannel - c) / a)) / 12.0;
}

// BT.2100 / BT.2020 HLG EOTF.
highp vec3 hlgEotf(highp vec3 hlgColor) {
  return vec3(
      hlgEotfSingleChannel(hlgColor.r),
      hlgEotfSingleChannel(hlgColor.g),
      hlgEotfSingleChannel(hlgColor.b)
  );
}
"""

val INCLUDE_YUV_TO_RGB = """
vec3 yuvToRgb(vec3 yuv) {
  const mat3 yuvToRgbColorTransform = mat3(
    1.1689f, 1.1689f, 1.1689f,
    0.0000f, -0.1881f, 2.1502f,
    1.6853f, -0.6530f, 0.0000f
  );
  const vec3 yuvOffset = vec3(0.0625, 0.5, 0.5);
  yuv = yuv - yuvOffset;
  return clamp(yuvToRgbColorTransform * yuv, 0.0, 1.0);
}
"""

val TRANSFORM_HDR_VSHADER = """#version 300 es
in vec4 vPosition;
uniform mat4 texMatrix;
out vec2 v_textureCoord;
out vec4 outPosition;
void main() {
    outPosition = vPosition;
    vec4 texCoord = vec4((vPosition.xy + vec2(1.0, 1.0)) / 2.0, 0.0, 1.0);
    v_textureCoord = (texMatrix * texCoord).xy;
    gl_Position = vPosition;
}
"""

/** Passthrough fragment shader, simply copies from the source texture */
val PASSTHROUGH_FSHADER = """
#extension GL_OES_EGL_image_external : require
precision highp float;
varying vec2 v_textureCoord;
uniform samplerExternalOES u_texture;
void main() {
    gl_FragColor = texture2D(u_texture, v_textureCoord);
//    gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);
}
"""

val PASSTHROUGH_HDR_FSHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 v_textureCoord;
uniform samplerExternalOES u_texture;
out vec4 outColor;
void main() {
    outColor = texture(u_texture, v_textureCoord);
}
"""

val YUV_TO_RGB_PASSTHROUGH_HDR_FSHADER = """#version 300 es
#extension GL_EXT_YUV_target : require
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform __samplerExternal2DY2YEXT u_texture;
in vec2 v_textureCoord;
out vec4 outColor;
""" + INCLUDE_YUV_TO_RGB + """
void main() {
    vec4 color = texture(u_texture, v_textureCoord);
    color.rgb = yuvToRgb(color.rgb);
    outColor = color;
}
"""

val YUV_TO_RGB_PORTRAIT_HDR_FSHADER = """#version 300 es
#extension GL_EXT_YUV_target : require
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform __samplerExternal2DY2YEXT u_texture;
in vec2 v_textureCoord;
out vec4 outColor;
""" + INCLUDE_YUV_TO_RGB + """
// BT.2100 / BT.2020 HLG OETF for one channel.
highp float hlgOetfSingleChannel(highp float linearChannel) {
  // Specification:
  // https://www.khronos.org/registry/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_HLG
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=529-543;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float a = 0.17883277;
  const highp float b = 0.28466892;
  const highp float c = 0.55991073;

  return linearChannel <= 1.0 / 12.0 ? sqrt(3.0 * linearChannel) :
      a * log(12.0 * linearChannel - b) + c;
}

// BT.2100 / BT.2020 HLG OETF.
highp vec3 hlgOetf(highp vec3 linearColor) {
  return vec3(
      hlgOetfSingleChannel(linearColor.r),
      hlgOetfSingleChannel(linearColor.g),
      hlgOetfSingleChannel(linearColor.b)
  );
}
""" + INCLUDE_HLG_EOTF + """
void main() {
    vec4 color = texture(u_texture, v_textureCoord);

    // Convert from YUV to RGB
    color.rgb = yuvToRgb(color.rgb);

    // Convert from HLG to linear
    color.rgb = hlgEotf(color.rgb);

    // Apply the portrait effect. Use gamma 2.4, roughly equivalent to what we expect in sRGB
    float x = v_textureCoord.x * 2.0 - 1.0, y = v_textureCoord.y * 2.0 - 1.0;
    float r = sqrt(x * x + y * y);
    color.rgb *= pow(1.0f - r, 2.4f);

    // Convert back to HLG
    color.rgb = hlgOetf(color.rgb);
    outColor = color;
}
"""

val HLG_TO_LINEAR_HDR_FSHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES u_texture;
in vec2 v_textureCoord;
out vec4 outColor;
""" + INCLUDE_HLG_EOTF + """
void main() {
    vec4 color = texture(u_texture, v_textureCoord);

    // Convert from HLG electrical to linear optical [0.0, 1.0]
    color.rgb = hlgEotf(color.rgb);

    outColor = color;
}
"""

val HLG_TO_PQ_HDR_FSHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES u_texture;
in vec2 v_textureCoord;
out vec4 outColor;
""" + INCLUDE_HLG_EOTF + """
// BT.2100 / BT.2020, PQ / ST2084 OETF.
highp vec3 pqOetf(highp vec3 linearColor) {
  // Specification:
  // https://registry.khronos.org/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_PQ
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=514-527;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float m1 = (2610.0 / 16384.0);
  const highp float m2 = (2523.0 / 4096.0) * 128.0;
  const highp float c1 = (3424.0 / 4096.0);
  const highp float c2 = (2413.0 / 4096.0) * 32.0;
  const highp float c3 = (2392.0 / 4096.0) * 32.0;

  highp vec3 temp = pow(linearColor, vec3(m1));
  temp = (c1 + c2 * temp) / (1.0 + c3 * temp);
  return pow(temp, vec3(m2));
}

void main() {
    vec4 color = texture(u_texture, v_textureCoord);

    // Convert from HLG electrical to linear optical [0.0, 1.0]
    color.rgb = hlgEotf(color.rgb);

    // HLG has a different L = 1 than PQ, which is 10,000 cd/m^2.
    color.rgb /= 40.0f;

    // Convert from linear optical [0.0, 1.0] to PQ electrical
    color.rgb = pqOetf(color.rgb);

    outColor = color;
}
"""

val PORTRAIT_FSHADER = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 v_textureCoord;
uniform samplerExternalOES u_texture;
void main() {
    float x = v_textureCoord.x * 2.0 - 1.0, y = v_textureCoord.y * 2.0 - 1.0;
    vec4 color = texture2D(u_texture, v_textureCoord);
    float r = sqrt(x * x + y * y);
    gl_FragColor = color * (1.0 - r);
}
"""