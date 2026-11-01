# Car models

Project-owned 3D car models, rendered into the app's car images by `android/wear/tools/car_model/`
(see its README for the model contract and the render pipeline).

| File | What it is |
|------|------------|
| `model_y.blend` | The Model Y (2025). Generated: do not edit by hand, change the script or the data and rebuild. |
| `build_model_y.py` | Builds the car in the open `.blend`: body lofted from cross-sections, wheel arches, wheels, mirrors, materials by zone. |
| `model_y_2025.json` | Shape data: public dimensions, side-profile points traced by eye on a studio photo, section and plan ratios. |

Rebuild and render:

```sh
blender -b design/car-model/model_y.blend --python-expr \
  'exec(open("design/car-model/build_model_y.py").read())'
blender -b design/car-model/model_y.blend --python android/wear/tools/car_model/render_car.py -- out/ --preview "#8C0B14"
```

## References stay out of git

Photos used as modelling references (Tesla's media gallery, configurator images) live in the git-ignored
`refs/` folder and are never committed, embedded in a `.blend`, or shipped (ADR-0002; the gallery is
licensed for media use only). Only numbers measured from them (points, ratios) are kept, in the JSON file.
The geometry is the project's own: no Tesla 3D data, logos or lettering; the wheels are a project design.
