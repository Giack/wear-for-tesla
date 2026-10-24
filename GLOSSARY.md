# Wear for Tesla

Wear OS app that shows a Tesla's state and sends commands to it, over Bluetooth when the car is near and through the Fleet API otherwise.

## Language

### Keys

**Cloud key**:
The key pair registered for the app's domain and approved through the Tesla app (`tesla.com/_ak`). The car accepts it only for commands arriving through the Fleet API.
_Avoid_: domain key, app key, virtual key

**BLE key**:
A key pair added to the car over Bluetooth with the driver role and confirmed with the keycard. It is the only key the car accepts for commands sent directly over Bluetooth.
_Avoid_: phone key, second key, driver key

**Keycard**:
The physical NFC card of the car, needed to confirm a new BLE key.
_Avoid_: key card, NFC key

### Modes

**Bluetooth mode**:
The default way of using the app: it talks to the car only when nearby, with a BLE key, and needs no Tesla account or server.
_Avoid_: offline mode, local mode, BLE-only

**Fleet mode**:
The optional way of using the app through the Fleet API, with a Developer app the user registers themselves; it adds remote control and the car's configuration.
_Avoid_: cloud mode, online mode

**Developer app**:
A user's own registration on developer.tesla.com, with its client credentials and the domain that publishes its Cloud key.
_Avoid_: Tesla app (that is Tesla's phone app), partner app

### Builds and images

**Public build**:
The app as published in the open-source repository, containing nothing owned by Tesla.
_Avoid_: OSS build, release

**Personal build**:
The maintainer's own build, which adds Tesla-owned material (logo, Official renders) kept outside the repository.
_Avoid_: private build, debug build

**Car illustration**:
A project-owned drawing of the car, tinted with the car's paint colour; the only car image in the Public build.
_Avoid_: render, silhouette

**Official render**:
An image of the car produced from Tesla's own assets (the configurator compositor or the Tesla app's 3D models); only in the Personal build.
_Avoid_: render, photo
