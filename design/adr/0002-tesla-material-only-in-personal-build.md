# Tesla-owned material only in the Personal build

The Public build contains nothing owned by Tesla: the car is shown with project-owned Car illustrations tinted with the car's paint colour, and no Tesla logo. Official renders (from the configurator compositor or the Tesla app's 3D models) and the logo exist only in the maintainer's Personal build, whose sources live in git-ignored folders behind an image-provider extension point. We chose this to publish the project without redistributing, hot-linking or extracting Tesla's copyrighted assets, accepting less realistic images in the public app.

## Considered Options

- **Fetch compositor images at runtime in the Public build**: nothing redistributed and used by other third-party apps, rejected because it depends on an undocumented Tesla endpoint and on Tesla's tolerance.
- **Extract the 3D models from the user's own Tesla app**: rejected for the Public build as decompilation of Tesla's assets.

## Consequences

- The Public build has a different application ID (`io.github.giack.wearfortesla`) from the Personal build (`dev.giack.teslawear`), so both can be installed side by side, each with its own BLE key and Wear OS login redirect.
- The public repository starts from a clean snapshot, because this repository's history contains the extraction scripts.
