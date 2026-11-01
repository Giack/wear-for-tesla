"""Creates a starter .blend for modelling a Car model (see README.md), optionally with a placeholder car.

Usage: blender -b --python tools/car_model/new_scene.py -- <out.blend> [--blueprints DIR] [--placeholder]
DIR may hold front/side/top/back images (png or jpg, any of them) shown as reference images.
Part of Wear for Tesla, Apache-2.0.
"""
import bpy, sys, os, math, argparse

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument("out")
ap.add_argument("--blueprints")
ap.add_argument("--placeholder", action="store_true")
args = ap.parse_args(argv)

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.unit_settings.system = "METRIC"
scene.unit_settings.scale_length = 1.0

car = bpy.data.collections.new("Car")
scene.collection.children.link(car)
refs = bpy.data.collections.new("Reference")
scene.collection.children.link(refs)


def material(name, colour, metallic=0.0, roughness=0.5, alpha=1.0, emission=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*colour, 1)
    b.inputs["Metallic"].default_value = metallic
    b.inputs["Roughness"].default_value = roughness
    b.inputs["Alpha"].default_value = alpha
    if emission:
        b.inputs["Emission Color"].default_value = (*colour, 1)
        b.inputs["Emission Strength"].default_value = emission
    return m


# The naming contract: "Paint*" materials become the paint layer; everything else is drawn as is.
mats = {
    "Paint": material("Paint", (0.8, 0.8, 0.8), roughness=0.3),
    "Glass": material("Glass", (0.02, 0.025, 0.03), roughness=0.05),
    "Trim": material("Trim", (0.015, 0.015, 0.015), roughness=0.45),
    "Tire": material("Tire", (0.02, 0.02, 0.02), roughness=0.85),
    "Rim": material("Rim", (0.35, 0.36, 0.38), metallic=1.0, roughness=0.3),
    "Light": material("Light", (0.9, 0.92, 1.0), roughness=0.1, emission=0.5),
    "TailLight": material("TailLight", (0.5, 0.02, 0.02), roughness=0.15, emission=0.3),
}
for m in mats.values():
    m.use_fake_user = True

# Reference images: front looks along +Y (car front faces -Y), side looks along -X, top looks down.
if args.blueprints:
    views = {
        "front": ((math.pi / 2, 0, 0), (0, -6, 0)),
        "back": ((math.pi / 2, 0, math.pi), (0, 6, 0)),
        "side": ((math.pi / 2, 0, math.pi / 2), (6, 0, 0)),
        "top": ((0, 0, 0), (0, 0, -0.01)),
    }
    for f in sorted(os.listdir(args.blueprints)):
        key = os.path.splitext(f)[0].lower()
        if key not in views or not f.lower().endswith((".png", ".jpg", ".jpeg")):
            continue
        e = bpy.data.objects.new("Ref_" + key, None)
        e.empty_display_type = "IMAGE"
        e.data = bpy.data.images.load(os.path.join(args.blueprints, f))
        e.empty_display_size = 5.0  # rescale in the viewport to match the car's real length
        e.rotation_euler, e.location = views[key]
        e.use_empty_image_alpha = True
        e.color[3] = 0.5
        e.empty_image_side = "FRONT"
        refs.objects.link(e)


def link(obj, mat):
    obj.data.materials.append(mats[mat])
    for c in obj.users_collection:
        c.objects.unlink(obj)
    car.objects.link(obj)
    return obj


def bevelled(name, loc, dims, mat, bevel=0.15, segments=4):
    bpy.ops.mesh.primitive_cube_add(location=loc)
    o = bpy.context.object
    o.name = name
    o.scale = (dims[0] / 2, dims[1] / 2, dims[2] / 2)
    bpy.ops.object.transform_apply(scale=True)
    mod = o.modifiers.new("Bevel", "BEVEL")
    mod.width, mod.segments = bevel, segments
    bpy.ops.object.shade_smooth()
    return link(o, mat)


if args.placeholder:
    # A crude crossover-sized blockout (4.75 m x 1.92 m x 1.62 m), only to exercise the render pipeline.
    bevelled("Body", (0, 0, 0.62), (1.9, 4.7, 0.7), "Paint", bevel=0.25)
    cabin = bevelled("Cabin", (0, 0.25, 1.2), (1.6, 2.6, 0.55), "Glass", bevel=0.22)
    bm = cabin.data
    for v in bm.vertices:  # taper the greenhouse towards the roof
        if v.co.z > 0:
            v.co.x *= 0.82
            v.co.y *= 0.8
    bevelled("Roof", (0, 0.35, 1.47), (1.25, 1.9, 0.06), "Paint", bevel=0.03)
    bevelled("Bumper", (0, -2.33, 0.4), (1.86, 0.12, 0.25), "Trim", bevel=0.05)
    bevelled("HeadlightL", (0.62, -2.33, 0.78), (0.42, 0.06, 0.07), "Light", bevel=0.02)
    bevelled("HeadlightR", (-0.62, -2.33, 0.78), (0.42, 0.06, 0.07), "Light", bevel=0.02)
    bevelled("TailLights", (0, 2.34, 0.85), (1.7, 0.05, 0.08), "TailLight", bevel=0.02)
    for x in (0.86, -0.86):
        for y in (-1.45, 1.45):
            bpy.ops.mesh.primitive_cylinder_add(vertices=48, radius=0.38, depth=0.26, location=(x, y, 0.38), rotation=(0, math.pi / 2, 0))
            t = bpy.context.object
            t.name = "Tire"
            bpy.ops.object.shade_smooth()
            link(t, "Tire")
            bpy.ops.mesh.primitive_cylinder_add(vertices=48, radius=0.27, depth=0.03, location=(x * 1.16, y, 0.38), rotation=(0, math.pi / 2, 0))
            r = bpy.context.object
            r.name = "Rim"
            bpy.ops.object.shade_smooth()
            link(r, "Rim")

bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(args.out))
print("SCENE_SAVED", args.out)
