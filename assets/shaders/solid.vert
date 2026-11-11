attribute vec3 a_position;
attribute vec3 a_normal;
attribute float a_cut;

uniform mat4 u_projView;
uniform mat4 u_model;
uniform mat3 u_normalMatrix;

varying vec3 v_world;
varying vec3 v_local;
varying vec3 v_normal;
varying float v_cut;

void main() {
    vec4 w = u_model * vec4(a_position, 1.0);
    v_world = w.xyz;
    v_local = a_position;
    v_normal = normalize(u_normalMatrix * a_normal);
    v_cut = a_cut;
    gl_Position = u_projView * w;
}
