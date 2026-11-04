"""Offscreen preview renders of Level 228's props (moderngl), for reviewing the art without the game:
a contact sheet of every prop from three angles, and assembled room shots lit by warm bulbs.
Not shipped; writes PNGs to a preview folder (level228_props.py --preview DIR)."""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from . import geo as G

VS = """
#version 330
uniform mat4 mvp;
in vec3 in_pos; in vec2 in_uv; in vec3 in_n; in float in_shade; in float in_glow;
out vec2 uv; out vec3 n; out float shade; out vec3 wpos; out float glow;
void main() { gl_Position = mvp * vec4(in_pos, 1.0); uv = in_uv; n = in_n; shade = in_shade; wpos = in_pos; glow = in_glow; }
"""
FS = """
#version 330
uniform sampler2D tex;
uniform vec3 lpos[32]; uniform vec3 lcol[32]; uniform int nl;
uniform vec3 ambient; uniform vec3 cam; uniform float fog; uniform float alpha_mode; uniform vec4 tint;
in vec2 uv; in vec3 n; in float shade; in vec3 wpos; in float glow;
out vec4 frag;
void main() {
    vec4 t = texture(tex, uv) * tint;
    if (alpha_mode < 0.5 && t.a < 0.1) discard;
    vec3 albedo = pow(t.rgb, vec3(2.2));
    vec3 N = normalize(n);
    vec3 V = normalize(cam - wpos);
    if (dot(N, V) < 0.0) N = -N;
    vec3 L = ambient * (0.75 + 0.25 * N.y);
    for (int i = 0; i < nl; i++) {
        vec3 d = lpos[i] - wpos; float r = length(d);
        float att = 1.0 / (1.0 + 0.6 * r * r);
        L += lcol[i] * max(dot(N, d / r), 0.0) * att;
        // A little spec for gilt and glass sheen.
        vec3 H = normalize(d / r + V);
        L += lcol[i] * pow(max(dot(N, H), 0.0), 40.0) * att * 0.25;
    }
    vec3 c = albedo * L * shade + albedo * glow * 2.5;
    float dist = length(cam - wpos);
    c = mix(c, vec3(0.012, 0.010, 0.009), 1.0 - exp(-dist * fog));
    c = c / (1.0 + c * 0.6);
    frag = vec4(pow(c, vec3(1.0 / 2.2)), alpha_mode < 0.5 ? 1.0 : t.a);
}
"""


def perspective(fovy, aspect, near, far):
    f = 1.0 / math.tan(math.radians(fovy) / 2)
    return np.array([[f / aspect, 0, 0, 0], [0, f, 0, 0], [0, 0, (far + near) / (near - far), 2 * far * near / (near - far)],
                     [0, 0, -1, 0]], dtype=np.float64)


def look_at(eye, target, up=(0, 1, 0)):
    eye, target, up = (np.asarray(a, float) for a in (eye, target, up))
    f = G.norm(target - eye)
    s = G.norm(np.cross(f, up))
    u = np.cross(s, f)
    m = np.eye(4)
    m[0, :3], m[1, :3], m[2, :3] = s, u, -f
    m[:3, 3] = -m[:3, :3] @ eye
    return m


class Renderer:
    # Lights per shot (the real-layout shots light a whole room with its own bulbs).
    MAX_LIGHTS = 32

    def __init__(self, sheets_rgba: dict, uv_of):
        import moderngl
        self.ctx = moderngl.create_standalone_context()
        self.prog = self.ctx.program(vertex_shader=VS, fragment_shader=FS)
        self.textures = {}
        for name, img in sheets_rgba.items():
            arr = np.clip(np.round(img * 255), 0, 255).astype(np.uint8)
            t = self.ctx.texture((arr.shape[1], arr.shape[0]), 4, arr.tobytes())
            t.build_mipmaps()
            t.filter = (moderngl.LINEAR_MIPMAP_LINEAR, moderngl.LINEAR)
            t.anisotropy = 8.0
            self.textures[name] = t
        self.uv_of = uv_of

    def arrays(self, mesh: G.Mesh, xform=None, sheet_of=None, glow_default=0.0):
        """mesh -> {sheet: float32 vertex array} (pos3 uv2 n3 shade glow), triangles."""
        out = {}
        m3 = np.eye(3) if xform is None else xform[0]
        t3 = np.zeros(3) if xform is None else np.asarray(xform[1], float)
        for q in mesh.quads:
            s = sheet_of(q.region)
            p = q.p @ m3.T + t3
            n = q.n @ np.linalg.inv(m3).T.T if False else q.n @ m3.T
            uv = np.array([self.uv_of(q.region, q.uv[k, 0], q.uv[k, 1]) for k in range(4)])
            glow = glow_default if q.layer == G.LAYER_GLOW else 0.0
            rows = [np.concatenate([p[k], uv[k], n[k], [q.shade[k], glow]]) for k in range(4)]
            buf = out.setdefault(s, [])
            buf.extend([rows[0], rows[1], rows[2], rows[0], rows[2], rows[3]])
        return {k: np.asarray(v, np.float32) for k, v in out.items()}

    def render(self, batches, eye, target, size=(640, 640), fov=60.0, lights=(), ambient=(0.05, 0.045, 0.04),
               fog=0.0, translucent=(), bg=(0.015, 0.013, 0.012)):
        import moderngl
        ss = 2
        W, H = size[0] * ss, size[1] * ss
        fbo = self.ctx.simple_framebuffer((W, H), components=4)
        fbo.use()
        self.ctx.enable(moderngl.DEPTH_TEST)
        self.ctx.disable(moderngl.CULL_FACE)
        fbo.clear(*bg, 1.0)
        proj = perspective(fov, W / H, 0.05, 200.0)
        view = look_at(eye, target)
        mvp = (proj @ view).T.astype(np.float32)
        self.prog["mvp"].write(mvp.tobytes())
        lp = np.zeros((self.MAX_LIGHTS, 3), np.float32)
        lc = np.zeros((self.MAX_LIGHTS, 3), np.float32)
        for i, (p, c) in enumerate(lights[:self.MAX_LIGHTS]):
            lp[i], lc[i] = p, c
        self.prog["lpos"].write(lp.tobytes())
        self.prog["lcol"].write(lc.tobytes())
        self.prog["nl"].value = min(self.MAX_LIGHTS, len(lights))
        self.prog["ambient"].value = tuple(ambient)
        self.prog["cam"].value = tuple(float(c) for c in eye)
        self.prog["fog"].value = fog
        self.prog["tint"].value = (1.0, 1.0, 1.0, 1.0)

        def draw(batch, mode):
            for sheet, arr in batch.items():
                if len(arr) == 0:
                    continue
                vbo = self.ctx.buffer(arr.tobytes())
                vao = self.ctx.vertex_array(self.prog, [(vbo, "3f 2f 3f 1f 1f", "in_pos", "in_uv", "in_n", "in_shade", "in_glow")])
                self.textures[sheet].use(0)
                self.prog["tex"].value = 0
                self.prog["alpha_mode"].value = mode
                vao.render(moderngl.TRIANGLES)
                vao.release()
                vbo.release()

        for b in batches:
            draw(b, 0.0)
        if translucent:
            self.ctx.enable(moderngl.BLEND)
            self.ctx.blend_func = moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA
            self.ctx.depth_mask = False
            for b in translucent:
                draw(b, 1.0)
            self.ctx.depth_mask = True
            self.ctx.disable(moderngl.BLEND)
        data = fbo.read(components=3)
        img = Image.frombytes("RGB", (W, H), data).transpose(Image.FLIP_TOP_BOTTOM)
        fbo.release()
        return img.resize(size, Image.LANCZOS)


def contact_sheet(renderer: Renderer, meshes, sheet_of, path: Path, cols=3, cell=300, views=((35, 18), (215, 22))):
    """Every mesh from two angles (front three-quarter, back three-quarter) in a grid, lit by a
    warm bulb above-front and a dim cold fill."""
    font = ImageFont.load_default()
    tiles = []
    for mesh in meshes:
        lo, hi = mesh.bounds()
        c = (lo + hi) / 2
        ext = float(np.max(hi - lo))
        batch = renderer.arrays(mesh, sheet_of=sheet_of, glow_default=0.8)
        tile = Image.new("RGB", (cell * len(views), cell + 16), (20, 20, 20))
        for k, (az, el) in enumerate(views):
            d = ext * 1.1 + 0.35
            # Directional props face -z: look at them from -z first.
            eye = c + d * np.array([math.sin(math.radians(az)) * math.cos(math.radians(el)),
                                    math.sin(math.radians(el)),
                                    -math.cos(math.radians(az)) * math.cos(math.radians(el))])
            lights = [(eye + np.array([0.3, ext * 0.6 + 0.4, 0.0]), (2.4, 1.7, 1.05)),
                      (c + np.array([-2.0, 0.5, 1.5]), (0.25, 0.27, 0.3))]
            v = renderer.render([batch], eye, c, size=(cell, cell), fov=45, lights=lights, ambient=(0.12, 0.11, 0.10),
                                bg=(0.09, 0.085, 0.08))
            tile.paste(v, (k * cell, 16))
        ImageDraw.Draw(tile).text((4, 2), f"{mesh.name}  {len(mesh.quads)}q  {ext:.2f}b", fill=(230, 220, 200), font=font)
        tiles.append(tile)
    tw, th = tiles[0].size
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (tw * cols, th * rows), (0, 0, 0))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * tw, (i // cols) * th))
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)
    return path
