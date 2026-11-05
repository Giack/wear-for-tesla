"""Renders a Car model .blend into the two layers the app composes (see README.md).

Usage: blender -b model.blend --python tools/car_model/render_car.py -- <out dir>
           [--frames 16] [--size 960] [--width 400] [--samples 48] [--preview "#B3141C"]
Writes paint_NN.webp and details_NN.webp (frame 00 = three-quarter front), and preview_NN.png when asked.
The paint pass is lit dimmer than the details and gloss passes: the Standard view transform clips at
white, and a clipped paint layer has no form once the app multiplies it by the paint colour.
Part of Wear for Tesla, Apache-2.0.
"""
import bpy, sys, os, math, argparse
import numpy as np
from mathutils import Matrix, Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument("out")
ap.add_argument("--frames", type=int, default=16)
ap.add_argument("--size", type=int, default=960)
ap.add_argument("--width", type=int, default=400)
ap.add_argument("--samples", type=int, default=48)
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
scene.cycles.use_adaptive_sampling = True
scene.cycles.adaptive_threshold = 0.02
scene.cycles.use_denoising = True
# No caustics on this opaque studio subject; fewer fireflies on the glass and rims.
scene.cycles.caustics_reflective = False
scene.cycles.caustics_refractive = False
scene.cycles.filter_width = 1.5
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
# Full studio level, used for details and gloss. The paint pass multiplies this (see lights_for).
# At 1.0 the HDRI's bright windows burn white patches into glass and rims.
BASE_HDRI = 0.14

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
# Energies are the details/gloss level. A wide key from the camera side, a weak frontal fill, and a
# rim from the far shoulder: enough ratio that the white paint pass keeps a readable gradient.
# The key sits at the front quarter, not overhead: an overhead key turns the glass roof into a white patch.
key = area_light("RigKey", 460, 3.4)
KEY_OFFSET = Vector((2.4, -4.2, 2.6))
fill = area_light("RigFill", 80, 6.5)
FILL_OFFSET = Vector((-1.4, -4.6, 0.7))    # low and frontal: lifts fascias and lower doors out of shadow
fill.visible_glossy = False                # it lifts shadows; as a reflection it would wash flat panels white
rim = area_light("RigRim", 110, 2.8)
RIM_OFFSET = Vector((-3.2, 2.4, 1.6))      # far shoulder, level with the belt line
rim.visible_glossy = False                 # paints the dark side; a glossy rim lights the glass roof white
# Paint pass scale. Tuned so a white body stays under 1.0 in the Standard view transform (no Filmic
# rolloff) while the darkest panels stay well above the ground shadow. See lights_for.
PAINT_SCALE = 0.36


def place_studio(turn):
    """Rotate the lights and the HDRI by `turn` (radians) round the car's vertical axis."""
    rot = Matrix.Rotation(turn, 3, "Z")
    for light, offset in ((key, KEY_OFFSET), (fill, FILL_OFFSET), (rim, RIM_OFFSET)):
        light.location = center + rot @ offset
        light.rotation_euler = (center - light.location).to_track_quat("-Z", "Y").to_euler()
    env_map.inputs["Rotation"].default_value = (0, 0, -turn)  # rotating the lookup turns the image the other way


def lights_for(mode):
    """Dim only the paint pass. Details and gloss keep the full studio so glass, lamps and rims stay crisp."""
    scale = PAINT_SCALE if mode == "paint" else 1.0
    key.data.energy = 460 * scale
    fill.data.energy = 80 * scale
    rim.data.energy = 110 * scale
    nt.nodes["Background"].inputs["Strength"].default_value = BASE_HDRI * scale

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


def paint_shade():
    """White diffuse plus a soft ambient-occlusion gradient, so arches and gaps read after the colour multiply."""
    m = bpy.data.materials.new("RigPaintShade")
    m.use_nodes = True
    nt = m.node_tree
    nodes, links = nt.nodes, nt.links
    nodes.clear()
    out = nodes.new("ShaderNodeOutputMaterial")
    diff = nodes.new("ShaderNodeBsdfDiffuse")
    ao = nodes.new("ShaderNodeAmbientOcclusion")
    ao.inputs["Distance"].default_value = 0.55
    ramp = nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.05
    ramp.color_ramp.elements[0].color = (0.55, 0.55, 0.55, 1)
    ramp.color_ramp.elements[1].position = 0.72
    ramp.color_ramp.elements[1].color = (1, 1, 1, 1)
    links.new(ao.outputs["AO"], ramp.inputs["Fac"])
    links.new(ramp.outputs["Color"], diff.inputs["Color"])
    links.new(diff.outputs["BSDF"], out.inputs["Surface"])
    return m


HOLD = holdout()
SHADE = paint_shade()
GLOSS = principled("RigPaintGloss", (0, 0, 0), 0.18, 0.4)  # clear coat: a broad sheen, not a mirror of the studio

original = {o: [s.material for s in o.material_slots] for o in car_objs}


def is_paint(m):
    return m is not None and m.name.startswith("Paint")


def apply(mode):
    """mode: 'paint' (body shading), 'details' (all but body), 'gloss' (body reflections)."""
    lights_for(mode)
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


# The gloss pass is lit at the full studio level while the paint pass is dimmed, so reflections would
# otherwise sit too hot on the shaded body. This gain is the ratio that still reads as clear coat.
GLOSS_GAIN = 0.55


def gloss_to_over(g):
    """Reflections were additive light; express them as a colour over the paint with matching alpha."""
    light = np.clip(g[..., :3] * g[..., 3:4] * GLOSS_GAIN, 0, 1)
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
scene.render.image_settings.quality = 92


def save(px, path, width):
    """Area-scale the shared crop to `width`. The render is several times wider, so this is the AA."""
    crop = np.ascontiguousarray(px[y0:y1, x0:x1])
    out_h = max(1, round(width * crop.shape[0] / crop.shape[1]))
    img = bpy.data.images.new("out", crop.shape[1], crop.shape[0], alpha=True)
    img.pixels[:] = crop.ravel()
    img.scale(width, out_h)
    img.filepath_raw = path
    img.file_format = "WEBP" if path.endswith(".webp") else "PNG"
    img.save()
    bpy.data.images.remove(img)


tint = None
if args.preview:
    h = args.preview.lstrip("#")
    tint = np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], dtype=np.float32)
preview_w = min(800, max(args.width, x1 - x0))
for f in range(args.frames):
    save(paint[f], os.path.join(out, "paint_%02d.webp" % f), args.width)
    save(details[f], os.path.join(out, "details_%02d.webp" % f), args.width)
    if tint is not None:  # what the app shows: paint x colour, details on top, on black
        tinted = paint[f].copy()
        tinted[..., :3] *= tint
        comp = over(details[f], over(tinted, np.concatenate([np.zeros_like(tinted[..., :3]), np.ones_like(tinted[..., 3:])], -1)))
        save(comp, os.path.join(out, "preview_%02d.png" % f), preview_w)
print("RENDER_DONE", out, "crop", (x0, y0, x1, y1), "->", args.width, "x", height)
