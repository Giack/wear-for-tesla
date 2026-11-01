# Car models

Project-owned 3D car models, rendered into the app's car images by `android/wear/tools/car_model/`
(see its README for the model contract and the render pipeline).

| File | What it is |
|------|------------|
| `build_car.py` | Builds a car in the open `.blend` from its data file: body lofted from cross-sections, wheel arches, wheels, mirrors, lights, door seams, materials. |
| `model_y_2025.json`, `model_3_2024.json` | Shape data per car: public dimensions, side-profile points traced by eye on a studio photo, section and plan ratios, lights, handles, seams. |
| `model_y.blend`, `model_3.blend` | The built cars. Generated: do not edit by hand, change the data or the script and rebuild. |

Rebuild and render (a new car starts from `tools/car_model/new_scene.py`):

```sh
CAR_DATA=model_3_2024 blender -b design/car-model/model_3.blend --python design/car-model/build_car.py
blender -b design/car-model/model_3.blend --python android/wear/tools/car_model/render_car.py -- out/ --preview "#B3141C"
cp out/paint_*.webp out/details_*.webp android/wear/src/main/assets/cars/model_3/
```

## References stay out of git

Photos used as modelling references (Tesla's media gallery, configurator images) live in the git-ignored
`refs/` folder and are never committed, embedded in a `.blend`, or shipped (ADR-0002; the gallery is
licensed for media use only). Only numbers measured from them (points, ratios) are kept, in the JSON files.
The geometry is the project's own: no Tesla 3D data, logos or lettering; the wheels are a project design.
