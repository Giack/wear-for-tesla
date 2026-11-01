"""Renders a Car model .blend into the two layers the app composes (see README.md).

Usage: blender -b model.blend --python tools/car_model/render_car.py -- <out dir>
           [--frames 16] [--size 600] [--width 400] [--samples 64] [--preview "#8C0B14"]
Writes paint_NN.webp and details_NN.webp (frame 00 = three-quarter front), and preview_NN.png when asked.
Part of Wear for Tesla, Apache-2.0.
"""
import bpy, sys, os, math, argparse
import numpy as np
from mathutils import Matrix, Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument("out")
ap.add_argument("--frames", type=int, default=16)
ap.add_argument("--size", type=int, default=600)
ap.add_argument("--width", type=int, default=400)
ap.add_argument("--samples", type=int, default=64)
ap.add_argument("--preview")
args = ap.parse_args(argv)
out = os.path.abspath(args.out)
raw = os.path.join(out, "raw")
os.makedirs(raw, exist_ok=True)

scene = bpy.context.scene
car = bpy.data.collections.get("Car")
if car is None:
    sys.exit("No 'Car' collection in this file (see README.md)")
# Helpers the model hides from render (boolean cutters and the like) stay hidden.
car_objs = [o for o in car.all_objects if o.type == "MESH" and not o.hide_render]

# --- Render settings -------------------------------------------------------------------------------
scene.render.engine = "CYCLES"
prefs = bpy.context.preferences.addons["cycles"].preferences
try:
    prefs.compute_device_type = "METAL"
    prefs.get_devices()
    for d in prefs.devices:
        d.use = True
    scene.cycles.device = "GPU"
except Exception:
    scene.cycles.device = "CPU"
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x = scene.render.resolution_y = args.size
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
# Standard (not AgX/Filmic) so that a white paint layer stays white and multiplies to the exact paint colour.
scene.view_settings.view_transform = "Standard"
scene.view_settings.look = "None"
scene.view_settings.exposure = 0.0
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.render.image_settings.color_depth = "8"

# Only the car (plus our own rig) renders.
for o in scene.objects:
    o.hide_render = o not in car_objs

# --- Studio: HDRI world, key light, shadow-catching floor -----------------------------------------
world = bpy.data.worlds.new("RenderStudio")
scene.world = world
world.use_nodes = True
nt = world.node_tree
env = nt.nodes.new("ShaderNodeTexEnvironment")
env.image = bpy.data.images.load(os.path.join(bpy.utils.system_resource("DATAFILES", path="studiolights/world"), "studio.exr"))
nt.links.new(env.outputs["Color"], nt.nodes["Background"].inputs["Color"])
# The studio turns with the camera (a turntable: the car turns under fixed lights), so every frame is lit alike.
env_map = nt.nodes.new("ShaderNodeMapping")
nt.links.new(nt.nodes.new("ShaderNodeTexCoord").outputs["Generated"], env_map.inputs["Vector"])
nt.links.new(env_map.outputs["Vector"], env.inputs["Vector"])
# Softer than the HDRI's default: at 1.0 its bright windows burn white patches into glossy paint and glass.
nt.nodes["Background"].inputs["Strength"].default_value = 0.6

lo = Vector((min((o.matrix_world @ Vector(c)).x for o in car_objs for c in o.bound_box),
             min((o.matrix_world @ Vector(c)).y for o in car_objs for c in o.bound_box),
             min((o.matrix_world @ Vector(c)).z for o in car_objs for c in o.bound_box)))
hi = Vector((max((o.matrix_world @ Vector(c)).x for o in car_objs for c in o.bound_box),
             max((o.matrix_world @ Vector(c)).y for o in car_objs for c in o.bound_box),
             max((o.matrix_world @ Vector(c)).z for o in car_objs for c in o.bound_box)))
center = (lo + hi) / 2
size = hi - lo

def area_light(name, energy, size_m):
    o = bpy.data.objects.new(name, bpy.data.lights.new(name, "AREA"))
    o.data.energy, o.data.size = energy, size_m
    scene.collection.objects.link(o)
    return o


# Offsets as seen from frame 0 (camera at the front-left three-quarter); they turn with the camera.
key = area_light("RigKey", 1500, 4.0)
KEY_OFFSET = Vector((2.5, -3.0, 5.0))      # high, from the camera's side
fill = area_light("RigFill", 450, 5.0)
FILL_OFFSET = Vector((-1.0, -4.5, 1.0))    # low and frontal: lifts fascias and lower doors out of shadow
fill.visible_glossy = False                # it lifts shadows; as a reflection it would wash flat panels white


def place_studio(turn):
    """Rotate the lights and the HDRI by `turn` (radians) round the car's vertical axis."""
    rot = Matrix.Rotation(turn, 3, "Z")
    for light, offset in ((key, KEY_OFFSET), (fill, FILL_OFFSET)):
        light.location = center + rot @ offset
        light.rotation_euler = (center - light.location).to_track_quat("-Z", "Y").to_euler()
    env_map.inputs["Rotation"].default_value = (0, 0, -turn)  # rotating the lookup turns the image the other way

bpy.ops.mesh.primitive_plane_add(size=max(size.x, size.y) * 4, location=(center.x, center.y, lo.z))
floor = bpy.context.object
floor.name = "RigFloor"
floor.is_shadow_catcher = True

cam = bpy.data.objects.new("RigCam", bpy.data.cameras.new("RigCam"))
cam.data.sensor_fit = "VERTICAL"
cam.data.angle = math.radians(26)
scene.collection.objects.link(cam)
scene.camera = cam
radius = max(size.x, size.y) * 2.7

# --- Material variants per pass ----------------------------------------------------------------------
def holdout():
    m = bpy.data.materials.new("RigHoldout")
    m.use_nodes = True
    n = m.node_tree.nodes
    n.clear()
    h = n.new("ShaderNodeHoldout")
    o = n.new("ShaderNodeOutputMaterial")
    m.node_tree.links.new(h.outputs[0], o.inputs["Surface"])
    return m


def principled(name, base, roughness, ior_level):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*base, 1)
    b.inputs["Roughness"].default_value = roughness
    b.inputs["Specular IOR Level"].default_value = ior_level
    return m


HOLD = holdout()
SHADE = principled("RigPaintShade", (1, 1, 1), 0.5, 0.0)   # diffuse light on a white body, no reflections
GLOSS = principled("RigPaintGloss", (0, 0, 0), 0.06, 0.5)  # clear coat: Fresnel reflections only

original = {o: [s.material for s in o.material_slots] for o in car_objs}


def is_paint(m):
    return m is not None and m.name.startswith("Paint")


def apply(mode):
    """mode: 'paint' (body shading), 'details' (all but body), 'gloss' (body reflections)."""
    for o, mats in original.items():
        for slot, m in zip(o.material_slots, mats):
            if mode == "details":
                slot.material = HOLD if is_paint(m) else m
            else:
                slot.material = (SHADE if mode == "paint" else GLOSS) if is_paint(m) else HOLD
    floor.hide_render = mode != "paint"  # the floor shadow belongs to the paint layer (black stays black)


def render(path):
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def load(path):
    img = bpy.data.images.load(path)
    w, h = img.size
    px = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)
    bpy.data.images.remove(img)
    return px


def over(top, bottom):
    a = top[..., 3:4] + bottom[..., 3:4] * (1 - top[..., 3:4])
    rgb = (top[..., :3] * top[..., 3:4] + bottom[..., :3] * bottom[..., 3:4] * (1 - top[..., 3:4])) / np.maximum(a, 1e-6)
    return np.concatenate([rgb, a], axis=-1)


def gloss_to_over(g):
    """Reflections were additive light; express them as a colour over the paint with matching alpha."""
    light = g[..., :3] * g[..., 3:4]
    a = np.clip(light.max(axis=-1, keepdims=True), 0, 1)
    rgb = np.where(a > 1e-4, light / np.maximum(a, 1e-4), 1.0)
    return np.concatenate([np.clip(rgb, 0, 1), a], axis=-1)


# --- Render all frames -------------------------------------------------------------------------------
paint, details, body = [], [], []
for f in range(args.frames):
    # Car front faces -Y; frame 0 looks at the front-left three-quarter, then the camera turns around the car.
    a = math.radians(45) + 2 * math.pi * f / args.frames
    place_studio(a - math.radians(45))
    cam.location = center + Vector((math.sin(a) * radius, -math.cos(a) * radius, radius * 0.22))
    cam.rotation_euler = (center - cam.location).to_track_quat("-Z", "Y").to_euler()
    layers = {}
    for mode in ("paint", "details", "gloss"):
        apply(mode)
        p = os.path.join(raw, "%s_%02d.png" % (mode, f))
        render(p)
        layers[mode] = load(p)
    paint.append(layers["paint"])
    details.append(over(layers["details"], gloss_to_over(layers["gloss"])))
    body.append(np.maximum(layers["details"][..., 3], layers["gloss"][..., 3]))  # the car without its shadow
    print("FRAME_DONE", f, flush=True)
apply("details")
for o, mats in original.items():  # leave the file's materials untouched
    for slot, m in zip(o.material_slots, mats):
        slot.material = m

# --- One crop for every frame (the car keeps its size while turning), scale, save -------------------
# The box comes from the car alone: the floor shadow spreads far and would defeat the crop.
alpha = np.maximum.reduce(body)
ys, xs = np.nonzero(alpha > 0.02)
y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
m = int((x1 - x0) * 0.04)
y0, y1, x0, x1 = max(0, y0 - m), min(args.size, y1 + m), max(0, x0 - m), min(args.size, x1 + m)
# Fade the shadow to nothing at the crop's edges so it never ends in a hard line.
yy, xx = np.mgrid[0:args.size, 0:args.size]
edge = np.minimum.reduce([yy - y0, y1 - 1 - yy, xx - x0, x1 - 1 - xx]).astype(np.float32)
fade = np.clip(edge / max(m, 1), 0, 1)
for p, b in zip(paint, body):
    shadow_only = b < 0.02
    p[..., 3] = np.where(shadow_only, p[..., 3] * fade, p[..., 3])
height = round(args.width * (y1 - y0) / (x1 - x0))
scene.render.image_settings.file_format = "WEBP"
scene.render.image_settings.quality = 90


def save(px, path):
    crop = np.ascontiguousarray(px[y0:y1, x0:x1])
    img = bpy.data.images.new("out", crop.shape[1], crop.shape[0], alpha=True)
    img.pixels[:] = crop.ravel()
    img.scale(args.width, height)
    img.filepath_raw = path
    img.file_format = "WEBP" if path.endswith(".webp") else "PNG"
    img.save()
    bpy.data.images.remove(img)


tint = None
if args.preview:
    h = args.preview.lstrip("#")
    tint = np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], dtype=np.float32)
for f in range(args.frames):
    save(paint[f], os.path.join(out, "paint_%02d.webp" % f))
    save(details[f], os.path.join(out, "details_%02d.webp" % f))
    if tint is not None:  # what the app shows: paint x colour, details on top, on black
        tinted = paint[f].copy()
        tinted[..., :3] *= tint
        comp = over(details[f], over(tinted, np.concatenate([np.zeros_like(tinted[..., :3]), np.ones_like(tinted[..., 3:])], -1)))
        save(comp, os.path.join(out, "preview_%02d.png" % f))
print("RENDER_DONE", out, "crop", (x0, y0, x1, y1), "->", args.width, "x", height)
