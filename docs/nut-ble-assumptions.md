# Nut BLE Assumptions

## Confirmed In Code

- Nut support is isolated behind `NutFindthingAdapter`.
- Candidates are inferred conservatively from advertised or resolved names containing `nut` or `findthing`.
- Service discovery, characteristic inventory, standard battery reads, BLE logging, and diagnostic export are protocol-agnostic.
- The app can probe an explicitly selected writable characteristic for controlled real-device validation.
- Finder uses BLE RSSI only. No Nut-specific UWB, direction, or angle-of-arrival path is implemented.

## Not Confirmed Without Current Hardware Evidence

- Exact manufacturer-data fingerprints for the target Nut Findthing revisions.
- Exact service/characteristic UUID and payload used for ring/beep.
- Whether a successful Android write corresponds to an audible/visible tracker reaction.
- Whether battery is exposed through the standard Battery Service, a proprietary characteristic, or not at all.
- Whether addresses rotate in a way that makes MAC-address reconnect unreliable.
- Whether different Nut units or firmware revisions expose the same GATT structure.

## Current Product Behavior

- Generic BLE devices:
  - proximity: filtered RSSI estimate, never a directional claim;
  - battery: shown only when the standard service is observed;
  - ring: unsupported unless a protocol adapter proves a safe path.
- Nut candidates:
  - battery: standard Battery Service is attempted first;
  - ring: remains unconfirmed until live evidence proves the characteristic and payload;
  - diagnostics: exposes discovered writable candidates and an explicit raw-write probe.

An acknowledged GATT write proves only that Android accepted the operation. It is not proof that the accessory rang, and it must not automatically promote the capability to supported.

## Real-Device Validation Checklist

For each physical tracker and firmware revision, capture:

- advertised name, address behavior, service UUIDs, and manufacturer data;
- every discovered characteristic and its Read/Write/Write No Response/Notify/Indicate flags;
- standard Battery Service presence and returned value;
- each safe write payload attempted and Android's result;
- direct observation of beep, light, vibration, notification, disconnect, or characteristic update;
- diagnostics immediately before and after each attempt.

An emulator is sufficient for navigation and persistence but is not evidence for any item above.

## Promotion Criteria For Ring Support

1. Reproduce the same service, characteristic, and payload on the intended tracker model.
2. Observe a repeatable physical reaction, not just a successful callback.
3. Confirm failure behavior and avoid writes to unrelated characteristics.
4. Encode the validated request in `NutFindthingAdapter` with model/firmware guards where needed.
5. Add positive and negative tests plus a real-device record.
6. Only then change UI/documentation from `unconfirmed` to supported.
