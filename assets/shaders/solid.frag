#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

// One shader for the tower, slices and weapons (spec §12). Patterns use the mesh's own coordinates,
// so grain and weak spots travel with a slice as it tumbles away.

varying vec3 v_world;
varying vec3 v_local;
varying vec3 v_normal;
varying float v_cut;

uniform vec3 u_camPos;
uniform vec3 u_lightDir;
uniform vec3 u_lightColor;
uniform vec3 u_skyTop;
uniform vec3 u_skyBottom;
uniform vec3 u_rimColor;
uniform float u_time;

uniform vec3 u_base;
uniform vec3 u_accent;
uniform vec3 u_cutColor;
uniform float u_rough;
uniform float u_metal;
uniform float u_transl;
uniform float u_grainStrength;
uniform float u_grainFreq;
uniform float u_mottle;
uniform vec3 u_emissive;
uniform vec4 u_gem;
uniform vec3 u_gemColor;
uniform float u_glow;
uniform vec3 u_grainN;

uniform vec4 u_spots[3];
uniform vec4 u_crackPlane;
uniform vec4 u_crackStart;
uniform vec3 u_crackDir;
uniform vec4 u_seam;
uniform float u_seamAlpha;
uniform float u_heightShade;
uniform float u_alpha;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.11, 0.17, 0.13));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), f.x),
            mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
        mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),
            mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

void main() {
    vec3 N = normalize(v_normal);
    vec3 V = normalize(u_camPos - v_world);
    vec3 L = normalize(u_lightDir);

    // Grain: crisp parallel lines lying exactly in the "with the grain" cut planes, with a gentle wobble
    // that never changes their direction. A swipe that follows these lines is a with-the-grain cut.
    float t = dot(v_local, u_grainN) * u_grainFreq;
    float wob = (noise(v_local * 2.5) - 0.5) * 0.22;
    float f = abs(fract(t + wob) - 0.5);
    float line = 1.0 - smoothstep(0.05, 0.13, f);
    float f2 = abs(fract(t * 3.0 + wob * 3.0) - 0.5);
    float fine = (1.0 - smoothstep(0.08, 0.2, f2)) * 0.35;
    float grain = max(line, fine);

    // Mottling: a surface texture that does not follow the grain (pores, speckles, clouds).
    float m = noise(v_local * 5.0) * 0.6 + noise(v_local * 17.0) * 0.3 + noise(v_local * 53.0) * 0.1;
    float mottle = mix(1.0, 0.72 + 0.56 * m, u_mottle * 0.5);

    // A little bump from the same noise, so the surface catches light like a real material.
    vec3 bump = vec3(noise(v_local * 31.0 + 1.7), noise(v_local * 31.0 + 4.3), noise(v_local * 31.0 + 8.9)) - 0.5;
    N = normalize(N + bump * 0.18 * u_mottle * (1.0 - u_metal * 0.7));

    vec3 albedo = mix(u_base, u_accent, grain * u_grainStrength) * mottle;
    if (v_cut > 0.5) {
        albedo = mix(u_cutColor, u_accent, grain * u_grainStrength * 0.8) * mix(1.0, mottle, 0.6);
    }
    float stripe = line;

    // Soft wrapped diffuse, so the material reads as matte and slightly translucent like the video.
    float wrap = 0.35 + u_transl * 0.4;
    float diff = clamp((dot(N, L) + wrap) / (1.0 + wrap), 0.0, 1.0);
    vec3 H = normalize(L + V);
    float shininess = mix(160.0, 8.0, u_rough);
    float spec = pow(max(dot(N, H), 0.0), shininess) * mix(1.1, 0.06, u_rough);
    float fres = pow(1.0 - max(dot(N, V), 0.0), 4.0);
    vec3 amb = mix(u_skyBottom, u_skyTop, N.y * 0.5 + 0.5);

    vec3 col = albedo * (diff * u_lightColor * 0.82 + amb * 0.28 + 0.12);
    col += albedo * (1.0 - diff) * u_transl * 0.25;

    // Metals mirror a blurred sky.
    vec3 R = reflect(-V, N);
    vec3 sky = mix(u_skyBottom, u_skyTop, clamp(R.y * 0.5 + 0.5, 0.0, 1.0));
    vec3 studio = mix(vec3(0.18), vec3(1.0), smoothstep(-0.3, 0.7, R.y)) + vec3(0.6) * smoothstep(0.85, 0.98, R.y);
    vec3 env = mix(studio, sky, 0.35);
    col = mix(col, env * mix(albedo, vec3(1.0), 0.25) * 0.95, u_metal * 0.75);

    col += spec * u_lightColor;
    col += u_rimColor * fres * (0.7 + 0.5 * u_transl);
    col += u_accent * stripe * u_glow;

    // Deeper parts of the tower fall into shade.
    float shade = mix(0.55, 1.0, clamp((v_world.y + 3.5) / 3.5, 0.0, 1.0));
    col *= mix(1.0, shade, u_heightShade);

    // Weak spots: pulsing golden glows with a cracked core.
    float pulse = 0.75 + 0.25 * sin(u_time * 4.2);
    vec3 gold = vec3(1.0, 0.8, 0.32);
    for (int i = 0; i < 3; i++) {
        vec4 s = u_spots[i];
        if (s.w > 0.0) {
            float d = length(v_local - s.xyz);
            float n = noise(v_local * 40.0);
            float halo = 1.0 - smoothstep(0.0, s.w * 2.2, d);
            float core = 1.0 - smoothstep(s.w * 0.35, s.w * 0.85, d + (n - 0.5) * s.w * 0.6);
            col = mix(col, col * 0.45, core * 0.6);
            col += gold * (halo * halo * 0.95 * pulse + core * 0.5 * pulse);
        }
    }

    // A crack from a stuck cut: a dark line with a warm glow.
    if (u_crackStart.w > 0.0) {
        float d = abs(dot(u_crackPlane.xyz, v_local) - u_crackPlane.w);
        float along = dot(v_local - u_crackStart.xyz, u_crackDir);
        float inside = step(0.0, along) * (1.0 - smoothstep(u_crackStart.w - 0.08, u_crackStart.w, along));
        float jag = (noise(v_local * 60.0) - 0.5) * 0.01;
        float line = 1.0 - smoothstep(0.004, 0.012, d + jag);
        col = mix(col, vec3(0.04, 0.02, 0.06), line * inside * 0.85);
        col += gold * (1.0 - smoothstep(0.0, 0.05, d)) * inside * 0.35 * pulse;
    }

    // The seam of a fresh reconnect, fading away.
    if (u_seamAlpha > 0.0) {
        float d = abs(dot(u_seam.xyz, v_local) - u_seam.w);
        col = mix(col, col * 0.55, (1.0 - smoothstep(0.003, 0.012, d)) * u_seamAlpha);
    }

    // A gem hidden inside gives the nearby surface a faint sparkle.
    if (u_gem.w > 0.0) {
        float d = length(v_local - u_gem.xyz);
        float near = 1.0 - smoothstep(0.25, 0.62, d);
        float tw = step(0.9, noise(v_local * 38.0 + vec3(u_time * 1.7)));
        col += u_gemColor * near * (0.08 + tw * 0.7) * (0.6 + 0.4 * sin(u_time * 3.0));
    }

    col += u_emissive;
    gl_FragColor = vec4(col, u_alpha);
}
