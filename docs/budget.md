# Fleet API budget

Tesla bills Fleet API usage to the owner of the Developer app, which in Fleet mode is you. Tesla also gives each developer account a monthly credit, and the app's budget defaults to the same amount, **$10 a month**, so with the default you should not pay anything.

Bluetooth mode makes no Fleet API calls and costs nothing.

## How the app counts

The app keeps a count of its own Fleet API calls for the current calendar month (UTC) and turns it into an estimate:

| Call | Estimated cost |
|---|---|
| State read | $0.002 |
| Command | $0.001 |
| Waking the car | $0.02 |

The estimate is the app's own count, not Tesla's invoice: check your real usage on developer.tesla.com.

The home screen shows **API: used $ / budget $**, amber from 80% of the budget and red when it is used up. **Settings → This month** shows the reads, commands and wake-ups behind it.

## Changing the budget

Tap **Settings → API budget** to cycle through $2, $5, $10, $20 and $50 a month.

## When the budget runs out

- Cloud state reads stop until the next month. The home screen keeps the last known state and shows how old it is.
- Bluetooth keeps working as usual, for state and commands.
- Commands you confirm still go through the Fleet API when the car is out of Bluetooth range.

## Background check

While the app is closed it checks the car twice an hour, so the watch face, Tile and app show a recent state. Each check first tries a direct Bluetooth connection to your car (free; it works once the watch has seen the car nearby). If the car is not near, in Fleet mode it makes **one** state read, skipped if the app already checked in the last 25 minutes or the budget is used up. The check never wakes the car: a sleeping car answers "asleep" and stays asleep, and reading twice an hour is rare enough to let it fall asleep. That is at most about 1,440 reads a month, roughly **$3**. Android may run the check less often when the watch battery is low or the watch is idle. Each run is written to **Settings → Bluetooth log** ("Background: …").

## Keeping usage low

The app already avoids the expensive parts: the Tile and confirmation screen read only over Bluetooth, opening the app reads over the cloud only when the state on screen is more than two minutes old, and it wakes the car only for a command you confirmed. Tapping **Refresh** while the car is far away is what spends the budget, so refresh when you need to.
