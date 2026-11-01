# Car model pipeline

Turns a project-owned 3D car model (Blender) into the two image layers the app composes, the same way it
composes the vector Car illustrations (see `ILLUSTRATIONS.md`): the **paint** layer is multiplied by the
car's paint colour, the **details** layer is drawn on top. One model therefore covers every paint.

The model must be original work: built from public reference photos or blueprints, **never** from Tesla's
files or the Tesla app's 3D models (ADR-0002). No logos, badges or lettering; wheels in a project design.

## Model contract

- Real scale in metres, wheels on the ground at `z = 0`, car **front facing −Y**, centred on the origin.
- Everything that renders sits in a collection named **`Car`**; anything else (reference images, helpers)
  is ignored.
- Body paint uses materials whose name starts with **`Paint`**; their colour in the file does not matter.
  Every other material (glass, trim, tyres, rims, lights) renders as it looks in the file.

## Start a model

```sh
blender -b --python tools/car_model/new_scene.py -- model_y.blend --blueprints refs/
```

`refs/` may contain `front`, `side`, `top`, `back` images (png/jpg); they appear as reference images to
model over. The file starts with the `Car` collection and the materials above. `--placeholder` adds a crude
blockout car, useful only to test the pipeline.

## Render

```sh
blender -b model_y.blend --python tools/car_model/render_car.py -- out/ --preview "#8C0B14"
```

The studio works like a turntable: the HDRI, a high key light and a low frontal fill turn with the camera,
so every frame is lit alike (the rear is not in shadow just because the lights sit at the front).

Per frame (16 by default, frame 00 = front-left three-quarter) Cycles renders three passes:

| Pass    | Paint materials             | Other materials | Floor           | Goes into      |
|---------|-----------------------------|-----------------|-----------------|----------------|
| paint   | white diffuse, no reflection| holdout         | shadow catcher  | `paint_NN`     |
| details | holdout                     | as in the file  | hidden          | `details_NN`   |
| gloss   | black clear coat (Fresnel)  | holdout         | hidden          | `details_NN`   |

The gloss pass is additive light; it is turned into white-ish colour with matching alpha and placed under
the details pass, so reflections stay white whatever the paint. All frames share one crop (the car keeps its
size while turning), the floor shadow fades out at the crop's edges, and frames are scaled to 400 px wide
WebP. `--preview` also writes `preview_NN.png`: what the app shows for that paint colour on black.

Options: `--frames`, `--size` (render px, default 600), `--width` (output px, default 400), `--samples`.
