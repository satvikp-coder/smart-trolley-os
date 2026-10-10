# Airport API research

## Implemented adapter status

The backend contains a server-side Aviationstack adapter for airport search and
arrival/departure flight boards. It maps provider response fields without
inventing terminal/gate values and omits missing values. It has not been tested
against a live credential; account access and an API key are absent. The key is
read only from backend environment. No deployment exists.

Aviationstack advertises airport and flight endpoints, real-time data, HTTPS,
and usage-tier limits. Current coverage and supplied gate/terminal fields for
Ahmedabad/Indian flights, exact retention permission, and redistribution rights
must be confirmed under the actual account terms before production use. The API
adapter must not be treated as confirmed coverage. [Official pricing](https://aviationstack.com/pricing),
[official documentation](https://aviationstack.com/documentation)

FlightAware AeroAPI documents scheduled airport arrivals/departures and flight
details, but requires account credentials and commercial usage is metered. Its
terms and commercial pricing should be reviewed before use; no credentials are
available. [AeroAPI](https://www.flightaware.com/commercial/aeroapi/),
[AeroAPI docs](https://docs.flightaware.com/aeroapi/)

## Not configured

No verified live airport result, actual Indian gate/terminal coverage, local
flight cache, or production authorization is available yet. Android screen
demonstration entries remain labeled simulation/manual.

