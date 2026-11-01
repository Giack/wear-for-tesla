"""Builds one of the project's car models from its shape data (<car>.json next to this script).

Run inside Blender with the car's .blend open, naming the data file in CAR:
    CAR = "model_y_2025"; exec(open("design/car-model/build_car.py").read())
    (or: CAR_DATA=model_3_2024 blender -b model_3.blend --python design/car-model/build_car.py)
The body is lofted from cross-sections. The traced side profile gives each section's roof, floor and belt
line; public dimensions give the scale; the section is a stepped shape (wide lower body, shoulder, narrower
glasshouse, rounded roof) and the plan view rounds the ends. Materials follow the README contract
(Paint*, Glass, Trim, Tire, Rim, Light, TailLight). Wheels are a project design. Part of Wear for Tesla, Apache-2.0.
"""
import bpy, bmesh, json, math, os
import numpy as np

HERE = os.path.dirname(bpy.data.filepath)
CAR = globals().get("CAR") or os.environ.get("CAR_DATA", "model_y_2025")
D = json.load(open(os.path.join(HERE, CAR + ".json")))
DIM, SP, SEC, PLAN, LIGHTS = D["dimensions_m"], D["side_px"], D["section"], D["plan"], D["lights"]
TRIM = D.get("trim", {})   # black trim: sill strip height, lower front intake band


def setup_materials():
    """The look every car shares (the file's own values do not matter, so any starter .blend renders the same)."""
    spec = {  # name: (base colour, metallic, roughness, specular, emission colour, emission strength)
        "Paint": ((0.8, 0.8, 0.8), 0.0, 0.3, 0.5, None, 0.0),
        "Glass": ((0.004, 0.005, 0.006), 0.0, 0.22, 0.2, None, 0.0),
        "Trim": ((0.015, 0.015, 0.015), 0.0, 0.45, 0.5, None, 0.0),
        "Tire": ((0.012, 0.012, 0.013), 0.0, 0.9, 0.25, None, 0.0),
        "Rim": ((0.72, 0.73, 0.75), 1.0, 0.25, 0.5, None, 0.0),
        "Light": ((0.25, 0.27, 0.3), 0.0, 0.1, 0.5, (0.85, 0.9, 1.0), 0.5),
        "TailLight": ((0.03, 0.004, 0.004), 0.0, 0.1, 0.5, (1.0, 0.03, 0.02), 1.5),
    }
    for name, (col, metal, rough, specular, ecol, estr) in spec.items():
        m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
        m.use_nodes = True
        m.use_fake_user = True
        b = m.node_tree.nodes["Principled BSDF"]
        b.inputs["Base Color"].default_value = (*col, 1)
        b.inputs["Metallic"].default_value = metal
        b.inputs["Roughness"].default_value = rough
        b.inputs["Specular IOR Level"].default_value = specular
        b.inputs["Emission Color"].default_value = (*(ecol or col), 1)
        b.inputs["Emission Strength"].default_value = estr


setup_materials()

L, WIDTH, WB, TYRE_R = DIM["length"], DIM["width"], DIM["wheelbase"], DIM["tyre_radius"]
TYRE_W = DIM.get("tyre_width", 0.255)
STATIONS = 200

# --- Side profile in metres: y along the car (front = -y), z up from the ground -------------------
s = L / (SP["tail_x"] - SP["nose_x"])
mid_x = (SP["nose_x"] + SP["tail_x"]) / 2


def to_m(pts):
    a = np.array(pts, dtype=float)
    return (a[:, 0] - mid_x) * s, (SP["ground_y"] - a[:, 1]) * s


TOP_Y, TOP_Z = to_m(SP["top"])
BOT_Y, BOT_Z = to_m(SP["bottom"])
BELT_Y, BELT_Z = to_m(SP["belt"])
y_front, y_rear = -L / 2, L / 2
y_a = (SP["belt"][0][0] - mid_x) * s                         # A-pillar base: the glasshouse starts here
glass_y = [(x - mid_x) * s for x in SP["side_glass_x"]]

# Axles: the photo's overhang split, with the public wheelbase
ov_f = (SP["front_wheel_x"] - SP["nose_x"]) / ((SP["front_wheel_x"] - SP["nose_x"]) + (SP["tail_x"] - SP["rear_wheel_x"])) * (L - WB)
AXLES = [y_front + ov_f, y_front + ov_f + WB]


def roof(y): return float(np.interp(y, TOP_Y, TOP_Z))
def floor_(y): return float(np.interp(y, BOT_Y, BOT_Z))


def belt(y):
    """Glasshouse floor; in front of the A-pillar base it blends into the roof line (no glass on the hood)."""
    b, r = float(np.interp(y, BELT_Y, BELT_Z)), roof(y)
    if y >= y_a:
        return b
    t = min(max((y - (y_a - 0.12)) / 0.12, 0.0), 1.0)    # short blend: a crisp cowl line, not a hump
    return r + (b - r) * t


HA = D["haunches"]


def haunch(y):
    """Extra width of the lower body over the wheels (the bulging wheel arches)."""
    return max(a * math.exp(-((y - ya) / (HA["length_m"] / 2)) ** 2) for ya, a in zip(AXLES, (HA["front"], HA["rear"])))


def nose_taper(y, t):
    """Near both ends the upper part pulls in, so nose and tail read rounded rather than flat walls."""
    f = 1.0
    u = (y - y_front) / PLAN["nose_taper_m"]
    if u < 1:
        f -= PLAN["nose_top_taper"] * (1 - u) ** 1.5 * t ** 1.2
    u = (y_rear - y) / PLAN["tail_taper_m"]
    if u < 1:
        f -= PLAN["tail_top_taper"] * (1 - u) ** 1.5 * t ** 1.5
    return f


def plan_half(y):
    half = WIDTH / 2 / (1 + HA["rear"])          # the widest point (over the rear wheels) is the spec width
    if y < y_front + PLAN["front_m"]:
        u = (y - y_front) / PLAN["front_m"]; tip, e = PLAN["front_tip"], PLAN["front_exp"]
    elif y > y_rear - PLAN["rear_m"]:
        u = (y_rear - y) / PLAN["rear_m"]; tip, e = PLAN["rear_tip"], PLAN["rear_exp"]
    else:
        return half
    u = min(max(u, 0.0), 1.0)
    return half * (tip + (1 - tip) * (1 - (1 - u) ** e) ** (1 / e))


def lerp(a, b, t): return a + (b - a) * t
def smooth(t): t = min(max(t, 0.0), 1.0); return t * t * (3 - 2 * t)


N_FLOOR, N_LOW, N_GH, N_ROOF = 3, 16, 10, 14


def section(y):
    """Right half from the floor to the roof crown, as (x, z); the ring mirrors it."""
    zt, zb = roof(y), floor_(y)
    hw = plan_half(y)
    k = smooth((zt - belt(y)) / 0.30)                      # 0 = no glasshouse (hood, tail), 1 = full
    rr = lerp(0.08, SEC["roof_round_m"], k)
    zs = min(belt(y), zt - rr)                              # top of the lower body (shoulder or hood edge)
    zs = max(zs, zb + 0.05)
    pts = []
    for f in (0.3, 0.6, 0.85):                              # flat floor
        pts.append((hw * SEC["floor"] * f, zb))
    for i in range(N_LOW):                                  # lower body side, bulging below the shoulder
        t = 0.5 - 0.5 * math.cos(math.pi * (i + 0.5) / N_LOW)
        z = zb + 0.01 + t * (zs - zb - 0.01)
        g = float(np.interp(t, [0, 0.06, 0.3, 0.55, 0.9, 1.0],
                            [SEC["floor"], 0.96, SEC["lower_max"], SEC["lower_max"], 0.94, SEC["shoulder"]]))
        g *= 1 + haunch(y) * math.sin(math.pi * min(max((t - 0.15) / 0.8, 0), 1))   # arches bulge mid-height
        g *= nose_taper(y, (z - zb) / max(zt - zb, 1e-3))
        pts.append((hw * g, z))
    ws = smooth((y - y_a) / 0.7)                             # 0 at the windshield base: the glass is wide there
    g0 = lerp(SEC["shoulder"] - 0.02, lerp(0.84, SEC["glass_base"], ws), k)  # shoulder ledge, then the glasshouse
    zg0 = zs + 0.03 * k
    zg1 = max(zt - rr, zg0)
    wtop = hw * lerp(SEC["shoulder"] - 0.04, lerp(0.76, SEC["glass_top"], ws), k)
    for i in range(N_GH):
        t = i / N_GH
        z = lerp(zg0, zg1, t)
        pts.append((hw * lerp(g0, wtop / hw, t) * nose_taper(y, (z - zb) / max(zt - zb, 1e-3)), z))
    for i in range(N_ROOF):                                 # rounded roof / hood crown to the centre line
        phi = (math.pi / 2) * i / N_ROOF
        c, sn = math.cos(phi), math.sin(phi)
        z = zg1 + (zt - zg1) * sn ** (2 / 2.6)
        pts.append((wtop * c ** (2 / 2.6) * nose_taper(y, (z - zb) / max(zt - zb, 1e-3)), z))
    return zb, zt, pts


def stations():
    u = np.linspace(0, 1, STATIONS)
    u = 0.5 - 0.5 * np.cos(np.pi * u)
    return y_front + 0.004 + u * (L - 0.008)


def mat(name): return bpy.data.materials[name]


def link(obj, coll):
    for c in obj.users_collection:
        c.objects.unlink(obj)
    coll.objects.link(obj)


car = bpy.data.collections["Car"]
for o in list(car.objects):
    bpy.data.objects.remove(o, do_unlink=True)

# --- Body ---------------------------------------------------------------------------------------------
bm = bmesh.new()
rings = []
for y in stations():
    zb, zt, half = section(y)
    ring = [(0.0, zb)] + half + [(0.0, zt)] + [(-x, z) for x, z in reversed(half)]
    rings.append([bm.verts.new((x, y, z)) for x, z in ring])
n = len(rings[0])
for a, b in zip(rings, rings[1:]):
    for k in range(n):
        bm.faces.new((a[k], a[(k + 1) % n], b[(k + 1) % n], b[k]))
bm.faces.new(list(reversed(rings[0])))
bm.faces.new(rings[-1])
bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
me = bpy.data.meshes.new("Body")
bm.to_mesh(me); bm.free()
body = bpy.data.objects.new("Body", me)
car.objects.link(body)
for name in ("Paint", "Glass", "Trim", "Light", "TailLight"):
    me.materials.append(mat(name))
IDX = {m.name: i for i, m in enumerate(me.materials)}

# --- Materials by zone ----------------------------------------------------------------------------------
HANDLE_Z = (SP["ground_y"] - SP["handle_y"]) * s
HANDLES = [((a - mid_x) * s, (b - mid_x) * s) for a, b in SP["handles_x"]]
me.update()
for f in me.polygons:
    cx, cy, cz = f.center
    nx, ny, nz = f.normal
    zb = floor_(cy)
    m = "Paint"
    if nz < -0.6 or cz < zb + TRIM.get("sill_m", 0.08):
        m = "Trim"                                                   # underside, black sill strip
    elif cz > belt(cy) + 0.005 and roof(cy) - belt(cy) > 0.03:
        side = abs(nx) > 0.55
        m = "Glass" if (not side or glass_y[0] <= cy <= glass_y[1]) else "Paint"   # C-pillar is painted
    elif cy > y_rear - 0.22 and cz < 0.40:
        m = "Trim"                                                   # rear diffuser
    f.material_index = IDX[m]


# --- Clean-edged details: thin strips cut from the body surface between two parallel planes -------------
def strip(name, material, axis, lo, hi, keep, lift=0.0015):
    """A band of the body between planes axis=lo and axis=hi, kept where keep(center, normal), lifted off the paint."""
    sb = bmesh.new()
    sb.from_mesh(me)
    no = [0, 0, 0]; no["xyz".index(axis)] = 1
    co_lo = [0, 0, 0]; co_lo["xyz".index(axis)] = lo
    co_hi = [0, 0, 0]; co_hi["xyz".index(axis)] = hi
    bmesh.ops.bisect_plane(sb, geom=sb.verts[:] + sb.edges[:] + sb.faces[:], plane_co=co_lo, plane_no=no, clear_inner=True)
    bmesh.ops.bisect_plane(sb, geom=sb.verts[:] + sb.edges[:] + sb.faces[:], plane_co=co_hi, plane_no=no, clear_outer=True)
    sb.normal_update()
    drop = [f for f in sb.faces if not keep(f.calc_center_median(), f.normal)]
    bmesh.ops.delete(sb, geom=drop, context="FACES")
    bmesh.ops.delete(sb, geom=[v for v in sb.verts if not v.link_faces], context="VERTS")
    sb.normal_update()
    for v in sb.verts:
        v.co += v.normal * lift
    m2 = bpy.data.meshes.new(name)
    sb.to_mesh(m2); sb.free()
    m2.materials.append(mat(material))
    o = bpy.data.objects.new(name, m2)
    car.objects.link(o)
    for p in m2.polygons:
        p.use_smooth = True
    return o


LF, LR = LIGHTS["front"], LIGHTS["rear"]
strip("LightsFront", "Light", "z", *LF["z"],
      lambda c, n: c.y < y_front + LF["reach_m"] and n.y < LF.get("max_ny", 0.1) and abs(c.x) >= LF["min_x"])
IN = TRIM.get("intake", {"z": [0.0, 0.40], "half_width": 9.0})                           # lower front intake
strip("Intake", "Trim", "z", *IN["z"], lambda c, n: c.y < y_front + 0.35 and n.y < -0.3 and abs(c.x) <= IN["half_width"])
strip("LightsRear", "TailLight", "z", *LR["z"],                                             # wrap only round the corners
      lambda c, n: c.y > y_rear - LR["reach_m"] and n.y > 0.3 and abs(c.x) >= LR["min_x"])
for a, b in HANDLES:
    strip("Handle", "Trim", "z", HANDLE_Z - 0.009, HANDLE_Z + 0.009, lambda c, n, a=a, b=b: abs(n.x) > 0.7 and a <= c.y <= b)
for x_px, zmin in SP["seams"]:                            # door seams: photo x, lowest height (m)
    ys = (x_px - mid_x) * s
    strip("Seam", "Trim", "y", ys - 0.0025, ys + 0.0025,
          lambda c, n, zmin=zmin: abs(n.x) > 0.5 and max(floor_(c.y) + 0.09, zmin) < c.z < belt(c.y) - 0.01, lift=0.001)

# --- Wheel arches --------------------------------------------------------------------------------------
for i, ya in enumerate(AXLES):
    bpy.ops.mesh.primitive_cylinder_add(vertices=96, radius=TYRE_R * 1.12, depth=WIDTH * 1.4,
                                        location=(0, ya, TYRE_R + 0.02), rotation=(0, math.pi / 2, 0))
    cut = bpy.context.object; cut.name = "ArchCutter%d" % i
    cut.data.materials.append(mat("Trim"))
    cut.display_type = "WIRE"; cut.hide_render = True
    link(cut, car)
    mod = body.modifiers.new("Arch%d" % i, "BOOLEAN")
    mod.operation, mod.object, mod.solver, mod.material_mode = "DIFFERENCE", cut, "EXACT", "TRANSFER"
    cut.hide_set(True)

bpy.ops.object.select_all(action="DESELECT")
bpy.context.view_layer.objects.active = body
body.select_set(True)
bpy.ops.object.shade_smooth()
bpy.ops.object.shade_auto_smooth(angle=math.radians(50))


# --- Wheels: tyre and a ten-spoke project design -------------------------------------------------------
def add(op, name, material, coll=car, smooth_=True, **kw):
    op(**kw)
    o = bpy.context.object; o.name = name
    o.data.materials.append(mat(material))
    if smooth_: bpy.ops.object.shade_smooth()
    link(o, coll)
    return o


def tyre(name, x, y, z, rim_r):
    """A tyre lathed from its cross-section: tread, rounded shoulders, sidewalls bulging down to the rim."""
    prof = []                                               # (radius, axial offset) round the section, closed
    hw = TYRE_W / 2
    for i in range(9):                                      # outer sidewall, rim to shoulder, slight bulge
        t = i / 8
        prof.append((lerp(rim_r, TYRE_R - 0.03, t), hw * (0.92 + 0.08 * math.sin(math.pi * t))))
    for i in range(1, 8):                                   # rounded shoulder into the tread
        a = (math.pi / 2) * i / 8
        prof.append((TYRE_R - 0.03 + 0.03 * math.sin(a), hw - 0.03 + 0.03 * math.cos(a) - 0.0))
    prof.append((TYRE_R, 0.0))
    prof += [(r, -a) for r, a in reversed(prof[:-1])]       # mirror to the inner side
    bm2 = bmesh.new()
    seg = 96
    rings2 = []
    for k in range(seg):
        th = 2 * math.pi * k / seg
        rings2.append([bm2.verts.new((a, math.cos(th) * r, math.sin(th) * r)) for r, a in prof])
    for k in range(seg):
        A, B = rings2[k], rings2[(k + 1) % seg]
        for j in range(len(prof) - 1):
            bm2.faces.new((A[j], A[j + 1], B[j + 1], B[j]))
    bmesh.ops.recalc_face_normals(bm2, faces=bm2.faces)
    m2 = bpy.data.meshes.new(name); bm2.to_mesh(m2); bm2.free()
    m2.materials.append(mat("Tire"))
    for p in m2.polygons:
        p.use_smooth = True
    o = bpy.data.objects.new(name, m2); o.location = (x, y, z)
    car.objects.link(o)
    return o


def wheel(tag, x, y, side):
    z = TYRE_R
    rim_r = TYRE_R * 0.68
    tyre(tag + "Tyre", x, y, z, rim_r)
    face = x + side * (TYRE_W / 2 + 0.004)
    add(bpy.ops.mesh.primitive_cylinder_add, tag + "Barrel", "Trim", vertices=96, radius=rim_r, depth=0.02,
        location=(face, y, z), rotation=(0, math.pi / 2, 0))
    add(bpy.ops.mesh.primitive_torus_add, tag + "Lip", "Rim", major_radius=rim_r * 0.97, minor_radius=0.014,
        location=(face + side * 0.012, y, z), rotation=(0, math.pi / 2, 0))
    for k in range(10):
        a = 2 * math.pi * k / 10 + (0.12 if side > 0 else -0.12)
        sp = add(bpy.ops.mesh.primitive_cube_add, tag + "Spoke%d" % k, "Rim", smooth_=False,
                 location=(face + side * 0.014, y + math.cos(a) * rim_r * 0.52, z + math.sin(a) * rim_r * 0.52))
        sp.scale = (0.01, rim_r * 0.47, 0.022)
        sp.rotation_euler = (a, 0, 0)
        bv = sp.modifiers.new("Round", "BEVEL"); bv.width, bv.segments = 0.006, 2
    add(bpy.ops.mesh.primitive_cylinder_add, tag + "Hub", "Rim", vertices=48, radius=rim_r * 0.2, depth=0.03,
        location=(face + side * 0.02, y, z), rotation=(0, math.pi / 2, 0))


for ya, fr in zip(AXLES, "FR"):
    near = [abs(v.co.x) for v in me.vertices if abs(v.co.y - ya) < 0.05 and abs(v.co.z - TYRE_R) < 0.15]
    x = max(near) - TYRE_W / 2 - 0.02                                  # tyres 2 cm inside the body side
    for side, lr in ((1, "L"), (-1, "R")):
        wheel("Wheel" + lr + fr, side * x, ya, side)

# --- Mirrors at the A-pillar base -------------------------------------------------------------------------
for side, lr in ((1, "L"), (-1, "R")):
    ym = y_a + 0.14
    zm = belt(ym) + 0.06
    near = [abs(v.co.x) for v in me.vertices if abs(v.co.y - ym) < 0.04 and abs(v.co.z - zm) < 0.08]
    xm = (max(near) if near else plan_half(ym) * 0.8) + 0.06
    mo = add(bpy.ops.mesh.primitive_uv_sphere_add, "Mirror" + lr, "Paint", segments=32, ring_count=16, radius=1,
             location=(side * (xm + 0.03), ym + 0.02, zm + 0.01))
    mo.scale = (0.10, 0.06, 0.05)
    arm = add(bpy.ops.mesh.primitive_cube_add, "MirrorArm" + lr, "Trim", smooth_=False,
              location=(side * (xm - 0.05), ym, zm - 0.015))
    arm.scale = (0.06, 0.03, 0.012)
    ab = arm.modifiers.new("Round", "BEVEL"); ab.width, ab.segments = 0.008, 2

bpy.ops.object.select_all(action="DESELECT")
bpy.ops.wm.save_mainfile()
result = {"objects": len(car.objects), "body_faces": len(me.polygons), "axles": [round(a, 3) for a in AXLES],
          "materials": {k: sum(1 for f in me.polygons if f.material_index == v) for k, v in IDX.items()}}
print("BUILD_DONE", result)
