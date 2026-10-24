# Fleet mode helper

`setup.sh` sets up Fleet mode with your own Developer app and sends its credentials to the watch. The step-by-step guide is on the site: [Fleet mode](https://giack.github.io/wear-for-tesla/fleet-mode/).

```sh
./setup/setup.sh keys                  # Cloud key pair in secrets/
./setup/setup.sh pages                 # publish the public key on <you>.github.io
./setup/setup.sh portal                # Developer app values for developer.tesla.com; saves its credentials
./setup/setup.sh register              # register the domain in every Fleet API region
./setup/setup.sh pair                  # approve the Cloud key in the car (tesla.com/_ak link)
./setup/setup.sh push-watch <ip:port>  # send key and credentials to the watch over adb
```

Everything it creates lives in `secrets/`, which git ignores. Each step can be run again safely.
