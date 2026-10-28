// Level 0's wall map, shared by the Level 0 light shaders (see client/light/Level0WallGrid).
//
// Every Level 0 wall is a whole column of blocks, so "is a wall between this spot and that lamp"
// can be answered from a map seen from above: one texel per block column, 128 x 128 columns
// around the camera. Each texel holds 32 bits, one per block from WallGridInfo.y upward (bit 0 =
// the carpet's block y40, bits 1-3 = the room, bit 4 = the ceiling, the rest = tall rooms up to
// y71). So half walls, window holes, door tops and the atrium's balcony all block light only
// at their own height. The map wraps around: column (x, z) lives in texel (x & 127, z & 127).

uniform usampler2D WallGrid;
// xz = world x/z of the window's corner (columns outside the window count as open), y = base y.
uniform vec3 WallGridInfo;

#define WALL_GRID_SIZE 128
#define WALL_GRID_MAX_STEPS 40

// The 32 solid/open bits of one block column (0 = all open, also outside the window).
uint level0Column(ivec2 cell) {
    ivec2 rel = cell - ivec2(WallGridInfo.xz);
    if (rel.x < 0 || rel.y < 0 || rel.x >= WALL_GRID_SIZE || rel.y >= WALL_GRID_SIZE) {
        return 0u;
    }
    return texelFetch(WallGrid, cell & ivec2(WALL_GRID_SIZE - 1), 0).r;
}

// The bits of every block a ray touches between heights yLo and yHi (world y).
uint level0Span(float yLo, float yHi) {
    int lo = max(int(floor(yLo - WallGridInfo.y)), 0);
    int hi = min(int(floor(yHi - WallGridInfo.y)), 31);
    if (hi < lo) {
        return 0u;
    }
    uint count = uint(hi - lo + 1);
    uint mask = count >= 32u ? ~0u : ((1u << count) - 1u);
    return mask << uint(lo);
}

// Is the block at this world position solid?
bool level0Solid(ivec3 at) {
    int bit = at.y - int(WallGridInfo.y);
    if (bit < 0 || bit > 31) {
        return false;
    }
    return ((level0Column(at.xz) >> uint(bit)) & 1u) != 0u;
}

// 1 when nothing solid lies on the straight line from p to l, 0 when a wall is in the way.
// Walks the columns the line crosses seen from above (a 2D grid walk), and in each column only
// tests the blocks at the heights the line passes through there. The column p is in is skipped:
// p sits on the surface being lit, nudged into the air in front of it.
float level0Ray(vec3 p, vec3 l) {
    vec2 start = p.xz;
    vec2 delta = l.xz - start;
    float len = length(delta);
    if (len < 1.0e-4) {
        return 1.0;
    }
    vec2 dir = delta / len;
    ivec2 cell = ivec2(floor(start));
    ivec2 stepDir = ivec2(sign(dir));
    vec2 invAbs = 1.0 / max(abs(dir), vec2(1.0e-6));
    vec2 tMax;
    tMax.x = dir.x >= 0.0 ? (float(cell.x) + 1.0 - start.x) * invAbs.x : (start.x - float(cell.x)) * invAbs.x;
    tMax.y = dir.y >= 0.0 ? (float(cell.y) + 1.0 - start.y) * invAbs.y : (start.y - float(cell.y)) * invAbs.y;
    float climb = (l.y - p.y) / len; // height gained per block walked sideways
    float tPrev = 0.0;
    for (int i = 0; i < WALL_GRID_MAX_STEPS; i++) {
        float tNext = min(min(tMax.x, tMax.y), len);
        if (i > 0) {
            float y0 = p.y + climb * tPrev;
            float y1 = p.y + climb * tNext;
            if ((level0Column(cell) & level0Span(min(y0, y1), max(y0, y1))) != 0u) {
                return 0.0;
            }
        }
        if (tNext >= len) {
            break;
        }
        tPrev = tNext;
        if (tMax.x < tMax.y) {
            tMax.x += invAbs.x;
            cell.x += stepDir.x;
        } else {
            tMax.y += invAbs.y;
            cell.y += stepDir.y;
        }
    }
    return 1.0;
}

// A tube is a panel, not a point: two rays to opposite corners of it give the shadow edges a
// short, soft fade (a block-exact wedge behind every post, but not a razor line). The rays end
// both to the side of the lamp and nearer/farther along the line to it: the sideways part
// softens the upright edges of shadows, the along part the level ones (the top edge of a low
// wall's shadow on the wall behind it, which the 1-block-tall map would otherwise draw as a
// hard line).
float level0Shadow(vec3 p, vec3 l) {
    vec2 side = l.xz - p.xz;
    float len = length(side);
    if (len < 1.0e-4) {
        return 1.0;
    }
    vec2 along = side / len;
    vec2 offset = (vec2(-along.y, along.x) * 0.3 + along * 0.35);
    return 0.5 * (level0Ray(p, l + vec3(offset.x, 0.0, offset.y)) + level0Ray(p, l - vec3(offset.x, 0.0, offset.y)));
}

// How much a corner hides this spot from bounced light, 1 = open, smaller = tucked in.
float level0Edge(float d) {
    return mix(0.30, 1.0, smoothstep(0.0, 0.8, d));
}

// Contact darkening from the wall map: where floor meets wall, in room corners, at the foot and
// top of every wall and in inside corners. Exact geometry, so no halos around things in front.
// p is the surface point nudged into the air, n its world normal.
float level0Ao(vec3 p, vec3 n) {
    ivec3 c = ivec3(floor(p));
    vec3 f = p - vec3(c);
    float ao = 1.0;
    if (abs(n.y) > 0.5) {
        // Floor or ceiling: the walls around this air block, at this height.
        bool w = level0Solid(c + ivec3(-1, 0, 0));
        bool e = level0Solid(c + ivec3(1, 0, 0));
        bool no = level0Solid(c + ivec3(0, 0, -1));
        bool so = level0Solid(c + ivec3(0, 0, 1));
        if (w) ao *= level0Edge(f.x);
        if (e) ao *= level0Edge(1.0 - f.x);
        if (no) ao *= level0Edge(f.z);
        if (so) ao *= level0Edge(1.0 - f.z);
        // Outside corners of posts and wall ends: a small round shadow at the tip.
        if (!w && !no && level0Solid(c + ivec3(-1, 0, -1))) ao *= level0Edge(length(vec2(f.x, f.z)));
        if (!e && !no && level0Solid(c + ivec3(1, 0, -1))) ao *= level0Edge(length(vec2(1.0 - f.x, f.z)));
        if (!w && !so && level0Solid(c + ivec3(-1, 0, 1))) ao *= level0Edge(length(vec2(f.x, 1.0 - f.z)));
        if (!e && !so && level0Solid(c + ivec3(1, 0, 1))) ao *= level0Edge(length(vec2(1.0 - f.x, 1.0 - f.z)));
    } else {
        // A wall: the foot (floor below this air block), the top (ceiling above) and the two
        // sides (an inside corner where another wall meets this one).
        if (level0Solid(c + ivec3(0, -1, 0))) ao *= level0Edge(f.y);
        if (level0Solid(c + ivec3(0, 1, 0))) ao *= level0Edge(1.0 - f.y) * 0.5 + 0.5;
        if (abs(n.x) > abs(n.z)) {
            if (level0Solid(c + ivec3(0, 0, -1))) ao *= level0Edge(f.z);
            if (level0Solid(c + ivec3(0, 0, 1))) ao *= level0Edge(1.0 - f.z);
        } else {
            if (level0Solid(c + ivec3(-1, 0, 0))) ao *= level0Edge(f.x);
            if (level0Solid(c + ivec3(1, 0, 0))) ao *= level0Edge(1.0 - f.x);
        }
    }
    return ao;
}
