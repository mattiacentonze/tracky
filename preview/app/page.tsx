"use client";

import { CSSProperties, ReactNode, useEffect, useMemo, useState } from "react";

type Device = {
  id: string;
  name: string;
  advertisedName: string;
  kind: string;
  distance: number;
  rssi: number;
  trend: "closer" | "steady" | "away";
  saved: boolean;
  precision: "signal" | "uwb";
};

type Rule = {
  id: number;
  deviceId: string;
  trigger: "enters" | "leaves";
  radius: number;
  action: "notification" | "wifi" | "whatsapp" | "telegram";
  enabled: boolean;
};

type Tab = "devices" | "automations" | "settings";

const initialDevices: Device[] = [
  {
    id: "nut-keys",
    name: "Keys",
    advertisedName: "Nut Findthing",
    kind: "Tracker",
    distance: 0.9,
    rssi: -51,
    trend: "closer",
    saved: true,
    precision: "signal",
  },
  {
    id: "buds",
    name: "Earbuds",
    advertisedName: "Redmi Buds 5 Pro",
    kind: "Audio",
    distance: 3.4,
    rssi: -66,
    trend: "steady",
    saved: true,
    precision: "signal",
  },
  {
    id: "wallet",
    name: "Wallet card",
    advertisedName: "Track Card UWB",
    kind: "Tracker · UWB",
    distance: 6.8,
    rssi: -74,
    trend: "away",
    saved: false,
    precision: "uwb",
  },
  {
    id: "unknown",
    name: "Unknown device",
    advertisedName: "A4:7C:9D:••:••:31",
    kind: "Bluetooth LE",
    distance: 12.6,
    rssi: -86,
    trend: "steady",
    saved: false,
    precision: "signal",
  },
];

const actionLabels: Record<Rule["action"], string> = {
  notification: "Send a notification",
  wifi: "Open Wi-Fi controls",
  whatsapp: "Prepare WhatsApp message",
  telegram: "Prepare Telegram message",
};

const triggerLabels: Record<Rule["trigger"], string> = {
  enters: "enters",
  leaves: "leaves",
};

function Icon({ name, size = 22 }: { name: string; size?: number }) {
  const paths: Record<string, ReactNode> = {
    bluetooth: <path d="m7 7 5 5-5 5V7Zm5 5 4-4M12 12l4 4M12 3v18" />,
    radar: <><circle cx="12" cy="12" r="2" /><path d="M5.6 5.6a9 9 0 0 0 0 12.8M18.4 5.6a9 9 0 0 1 0 12.8M8.5 8.5a5 5 0 0 0 0 7M15.5 8.5a5 5 0 0 1 0 7" /></>,
    bolt: <path d="m13 2-8 12h7l-1 8 8-12h-7l1-8Z" />,
    settings: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.8 1.8 0 0 0 .36 2l.05.05-2.76 2.76-.05-.05a1.8 1.8 0 0 0-2-.36 1.8 1.8 0 0 0-1.1 1.65V21H10v-.07A1.8 1.8 0 0 0 8.9 19.3a1.8 1.8 0 0 0-2 .36l-.05.05-2.76-2.76.05-.05a1.8 1.8 0 0 0 .36-2 1.8 1.8 0 0 0-1.65-1.1H2V10h.07A1.8 1.8 0 0 0 3.7 8.9a1.8 1.8 0 0 0-.36-2l-.05-.05 2.76-2.76.05.05a1.8 1.8 0 0 0 2 .36A1.8 1.8 0 0 0 9.2 2.85V2H13v.07a1.8 1.8 0 0 0 1.1 1.63 1.8 1.8 0 0 0 2-.36l.05-.05 2.76 2.76-.05.05a1.8 1.8 0 0 0-.36 2 1.8 1.8 0 0 0 1.65 1.1H22V13h-.07A1.8 1.8 0 0 0 19.4 15Z" /></>,
    chevron: <path d="m9 18 6-6-6-6" />,
    back: <path d="m15 18-6-6 6-6" />,
    edit: <><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4L16.5 3.5Z" /></>,
    plus: <path d="M12 5v14M5 12h14" />,
    bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" /><path d="M10 21h4" /></>,
    wifi: <><path d="M5 12.6a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0" /><circle cx="12" cy="20" r=".7" fill="currentColor" /></>,
    message: <path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v8Z" />,
    sound: <><path d="M11 5 6 9H2v6h4l5 4V5Z" /><path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13" /></>,
    vibrate: <><path d="M8 5h8v14H8zM4 8v8M20 8v8M1 10v4M23 10v4" /></>,
    shield: <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />,
    info: <><circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7h.01" /></>,
    check: <path d="m5 12 4 4L19 6" />,
  };
  return (
    <svg className="icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {paths[name]}
    </svg>
  );
}

function bandFor(distance: number) {
  if (distance <= 1.2) return { key: "immediate", label: "Very close", detail: "Within reach", hue: 159 };
  if (distance <= 4) return { key: "near", label: "Nearby", detail: "Keep moving slowly", hue: 153 };
  if (distance <= 9) return { key: "warm", label: "Getting warmer", detail: "Signal is usable", hue: 39 };
  return { key: "far", label: "Far away", detail: "Weak signal", hue: 12 };
}

function formatDistance(distance: number) {
  return distance < 1 ? `${Math.round(distance * 100)} cm` : `~${distance.toFixed(distance < 10 ? 1 : 0)} m`;
}

export default function Home() {
  const [tab, setTab] = useState<Tab>("devices");
  const [devices, setDevices] = useState(initialDevices);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [scanning, setScanning] = useState(true);
  const [sound, setSound] = useState(false);
  const [haptics, setHaptics] = useState(true);
  const [renameOpen, setRenameOpen] = useState(false);
  const [draftName, setDraftName] = useState("");
  const [createRule, setCreateRule] = useState(false);
  const [ruleDevice, setRuleDevice] = useState("nut-keys");
  const [ruleTrigger, setRuleTrigger] = useState<Rule["trigger"]>("leaves");
  const [ruleRadius, setRuleRadius] = useState(8);
  const [ruleAction, setRuleAction] = useState<Rule["action"]>("notification");
  const [toast, setToast] = useState<string | null>(null);
  const [rules, setRules] = useState<Rule[]>([
    { id: 1, deviceId: "nut-keys", trigger: "leaves", radius: 8, action: "notification", enabled: true },
    { id: 2, deviceId: "buds", trigger: "enters", radius: 4, action: "wifi", enabled: false },
  ]);

  const selected = devices.find((device) => device.id === selectedId) ?? null;

  useEffect(() => {
    if (!scanning) return;
    const timer = window.setInterval(() => {
      setDevices((current) => current.map((device) => {
        const change = (Math.random() - 0.48) * Math.max(0.18, device.distance * 0.08);
        const nextDistance = Math.min(24, Math.max(0.25, device.distance + change));
        return {
          ...device,
          distance: nextDistance,
          rssi: Math.round(-48 - Math.log10(Math.max(nextDistance, 0.3)) * 26),
          trend: change < -0.12 ? "closer" : change > 0.12 ? "away" : "steady",
        };
      }));
    }, 1600);
    return () => window.clearInterval(timer);
  }, [scanning]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const savedCount = useMemo(() => devices.filter((device) => device.saved).length, [devices]);

  function openFinder(device: Device) {
    setSelectedId(device.id);
    setDraftName(device.name);
  }

  function updateSelectedDistance(distance: number) {
    if (!selectedId) return;
    setDevices((current) => current.map((device) => device.id === selectedId ? {
      ...device,
      trend: distance < device.distance ? "closer" : distance > device.distance ? "away" : "steady",
      distance,
      rssi: Math.round(-48 - Math.log10(Math.max(distance, 0.3)) * 26),
    } : device));
  }

  function pulseFeedback() {
    if (haptics && "vibrate" in navigator) navigator.vibrate([45, 35, 45]);
    if (sound) {
      try {
        const AudioContextClass = window.AudioContext ?? (window as typeof window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
        if (AudioContextClass) {
          const audio = new AudioContextClass();
          const oscillator = audio.createOscillator();
          const gain = audio.createGain();
          oscillator.frequency.value = selected && selected.distance < 4 ? 720 : 480;
          gain.gain.setValueAtTime(0.05, audio.currentTime);
          gain.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + 0.16);
          oscillator.connect(gain).connect(audio.destination);
          oscillator.start();
          oscillator.stop(audio.currentTime + 0.16);
        }
      } catch {
        // Feedback is optional in the browser simulator.
      }
    }
    setToast("Proximity feedback tested");
  }

  function saveRename() {
    if (!selectedId || !draftName.trim()) return;
    setDevices((current) => current.map((device) => device.id === selectedId ? { ...device, name: draftName.trim(), saved: true } : device));
    setRenameOpen(false);
    setToast("Device name saved");
  }

  function saveRule() {
    setRules((current) => [...current, {
      id: Date.now(),
      deviceId: ruleDevice,
      trigger: ruleTrigger,
      radius: ruleRadius,
      action: ruleAction,
      enabled: true,
    }]);
    setCreateRule(false);
    setTab("automations");
    setToast("Automation created");
  }

  return (
    <main className="demo-shell">
      <aside className="demo-context" aria-label="Prototype information">
        <div className="brand-lockup">
          <div className="brand-mark"><Icon name="radar" size={25} /></div>
          <span>Tracky</span>
        </div>
        <h1>Find what is close.<br />Act when it moves.</h1>
        <p>A hands-on simulation of the Android experience. Move the signal slider, rename a device, and create a proximity automation.</p>
        <div className="context-points">
          <div><Icon name="bluetooth" /><span><strong>Live on Android</strong>Scans nearby BLE broadcasts</span></div>
          <div><Icon name="shield" /><span><strong>Private by design</strong>Names and rules stay on-device</span></div>
          <div><Icon name="info" /><span><strong>Honest ranging</strong>Shows confidence, not false precision</span></div>
        </div>
        <span className="prototype-badge">Interactive prototype</span>
      </aside>

      <section className="phone-frame" aria-label="Tracky app preview">
        <div className="phone-status"><span>09:41</span><span>● ᴡɪꜰɪ ◢</span></div>
        <div className="app-content">
          {selected ? (
            <FinderScreen
              device={selected}
              sound={sound}
              haptics={haptics}
              onBack={() => setSelectedId(null)}
              onRename={() => { setDraftName(selected.name); setRenameOpen(true); }}
              onDistance={updateSelectedDistance}
              onSound={() => setSound((value) => !value)}
              onHaptics={() => setHaptics((value) => !value)}
              onPulse={pulseFeedback}
            />
          ) : createRule ? (
            <RuleBuilder
              devices={devices}
              deviceId={ruleDevice}
              trigger={ruleTrigger}
              radius={ruleRadius}
              action={ruleAction}
              onDevice={setRuleDevice}
              onTrigger={setRuleTrigger}
              onRadius={setRuleRadius}
              onAction={setRuleAction}
              onBack={() => setCreateRule(false)}
              onSave={saveRule}
            />
          ) : (
            <>
              {tab === "devices" && (
                <DevicesScreen
                  devices={devices}
                  scanning={scanning}
                  savedCount={savedCount}
                  onScanning={() => setScanning((value) => !value)}
                  onDevice={openFinder}
                />
              )}
              {tab === "automations" && (
                <AutomationsScreen
                  rules={rules}
                  devices={devices}
                  onAdd={() => setCreateRule(true)}
                  onToggle={(id) => setRules((current) => current.map((rule) => rule.id === id ? { ...rule, enabled: !rule.enabled } : rule))}
                  onTest={(rule) => setToast(`${actionLabels[rule.action]} · test complete`)}
                />
              )}
              {tab === "settings" && (
                <SettingsScreen sound={sound} haptics={haptics} onSound={() => setSound((value) => !value)} onHaptics={() => setHaptics((value) => !value)} />
              )}
              <BottomNav tab={tab} onTab={setTab} />
            </>
          )}
        </div>
        {toast && <div className="toast" role="status"><Icon name="check" size={18} />{toast}</div>}
      </section>

      {renameOpen && selected && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setRenameOpen(false)}>
          <div className="modal" role="dialog" aria-modal="true" aria-labelledby="rename-title" onMouseDown={(event) => event.stopPropagation()}>
            <div className="modal-icon"><Icon name="edit" /></div>
            <h2 id="rename-title">Rename device</h2>
            <p>Its Bluetooth name stays visible underneath.</p>
            <label>
              Display name
              <input autoFocus value={draftName} onChange={(event) => setDraftName(event.target.value)} onKeyDown={(event) => event.key === "Enter" && saveRename()} />
            </label>
            <span className="technical-name">{selected.advertisedName}</span>
            <div className="modal-actions">
              <button className="button secondary" onClick={() => setRenameOpen(false)}>Cancel</button>
              <button className="button primary" onClick={saveRename}>Save</button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}

function ScreenHeader({ eyebrow, title, action }: { eyebrow?: string; title: string; action?: ReactNode }) {
  return <header className="screen-header"><div>{eyebrow && <span>{eyebrow}</span>}<h2>{title}</h2></div>{action}</header>;
}

function DevicesScreen({ devices, scanning, savedCount, onScanning, onDevice }: {
  devices: Device[]; scanning: boolean; savedCount: number; onScanning: () => void; onDevice: (device: Device) => void;
}) {
  return (
    <div className="screen devices-screen">
      <ScreenHeader eyebrow="Nearby devices" title="Find a device" action={<button className={`scan-control ${scanning ? "active" : ""}`} onClick={onScanning}><span />{scanning ? "Scanning" : "Paused"}</button>} />
      <div className="scan-summary">
        <div className={`mini-radar ${scanning ? "is-scanning" : ""}`}><Icon name="bluetooth" size={24} /></div>
        <div><strong>{devices.length} devices in range</strong><span>{savedCount} saved · updated just now</span></div>
        <button className="icon-button" aria-label="Toggle scan" onClick={onScanning}><Icon name={scanning ? "check" : "radar"} /></button>
      </div>
      <div className="section-label"><span>Signal strength</span><span>Estimated</span></div>
      <div className="device-list">
        {devices.map((device) => {
          const band = bandFor(device.distance);
          return (
            <button className="device-row" key={device.id} onClick={() => onDevice(device)}>
              <SignalOrb distance={device.distance} small />
              <span className="device-copy"><strong>{device.name}</strong><span>{device.advertisedName}</span><small>{device.kind}{device.saved ? " · Saved" : ""}</small></span>
              <span className="distance-copy"><strong>{formatDistance(device.distance)}</strong><span className={`band band-${band.key}`}>{band.label}</span></span>
              <Icon name="chevron" size={18} />
            </button>
          );
        })}
      </div>
      <div className="honesty-note"><Icon name="info" size={18} /><p><strong>Distance is an estimate.</strong> Walls, pockets and device radios affect Bluetooth signal.</p></div>
    </div>
  );
}

function SignalOrb({ distance, small = false }: { distance: number; small?: boolean }) {
  const band = bandFor(distance);
  return <span className={`signal-orb ${small ? "small" : ""} band-${band.key}`}><span /><span /><Icon name="bluetooth" size={small ? 17 : 30} /></span>;
}

function FinderScreen({ device, sound, haptics, onBack, onRename, onDistance, onSound, onHaptics, onPulse }: {
  device: Device; sound: boolean; haptics: boolean; onBack: () => void; onRename: () => void; onDistance: (value: number) => void; onSound: () => void; onHaptics: () => void; onPulse: () => void;
}) {
  const band = bandFor(device.distance);
  const trendLabel = device.trend === "closer" ? "Getting closer" : device.trend === "away" ? "Moving away" : "Holding steady";
  const style = { "--signal-hue": band.hue } as CSSProperties;
  return (
    <div className="screen finder-screen" style={style}>
      <div className="finder-topbar">
        <button className="icon-button" aria-label="Back" onClick={onBack}><Icon name="back" /></button>
        <div><span>Finding</span><strong>{device.name}</strong></div>
        <button className="icon-button" aria-label="Rename" onClick={onRename}><Icon name="edit" /></button>
      </div>
      <div className="finder-stage">
        <div className="finder-glow" />
        <div className="radar-rings"><span /><span /><span /><div className="finder-core"><Icon name={device.precision === "uwb" ? "radar" : "bluetooth"} size={34} /></div></div>
        <div className="distance-reading"><strong>{formatDistance(device.distance)}</strong><span>{band.label}</span></div>
        <div className={`trend trend-${device.trend}`}><span>{device.trend === "closer" ? "↑" : device.trend === "away" ? "↓" : "•"}</span>{trendLabel}</div>
      </div>
      <div className="finder-panel">
        <div className="signal-chart" aria-label="Recent signal strength"><i /><i /><i /><i /><i /><i /><i /><i /></div>
        <div className="signal-meta"><span>RSSI {device.rssi} dBm</span><span>{device.precision === "uwb" ? "Precision-capable" : "BLE signal estimate"}</span></div>
        <label className="demo-slider"><span><strong>Move the demo device</strong><small>Simulate walking closer or farther</small></span><input type="range" min="0.3" max="18" step="0.1" value={device.distance} onChange={(event) => onDistance(Number(event.target.value))} /></label>
        <div className="feedback-row">
          <button className={sound ? "selected" : ""} onClick={onSound}><Icon name="sound" /><span>Sound<strong>{sound ? "On" : "Off"}</strong></span></button>
          <button className={haptics ? "selected" : ""} onClick={onHaptics}><Icon name="vibrate" /><span>Haptics<strong>{haptics ? "On" : "Off"}</strong></span></button>
          <button onClick={onPulse}><Icon name="radar" /><span>Feedback<strong>Test</strong></span></button>
        </div>
        <p className="finder-tip">Walk slowly and rotate the phone. Tracky compares multiple readings before showing a trend.</p>
      </div>
    </div>
  );
}

function AutomationsScreen({ rules, devices, onAdd, onToggle, onTest }: {
  rules: Rule[]; devices: Device[]; onAdd: () => void; onToggle: (id: number) => void; onTest: (rule: Rule) => void;
}) {
  return (
    <div className="screen automations-screen">
      <ScreenHeader eyebrow="On-device rules" title="Automations" action={<button className="round-add" aria-label="New automation" onClick={onAdd}><Icon name="plus" /></button>} />
      <div className="automation-intro"><div><Icon name="bolt" /></div><p><strong>Let distance do the work.</strong><span>Trigger an action when a saved device enters or leaves your chosen range.</span></p></div>
      <div className="rule-list">
        {rules.map((rule) => {
          const device = devices.find((item) => item.id === rule.deviceId);
          return <article className={`rule-card ${rule.enabled ? "" : "disabled"}`} key={rule.id}>
            <div className="rule-top"><span className="action-icon"><Icon name={rule.action === "notification" ? "bell" : rule.action === "wifi" ? "wifi" : "message"} /></span><div><strong>{device?.name ?? "Device"} {triggerLabels[rule.trigger]} {rule.radius} m</strong><span>{actionLabels[rule.action]}</span></div><button className={`switch ${rule.enabled ? "on" : ""}`} aria-label="Toggle automation" aria-pressed={rule.enabled} onClick={() => onToggle(rule.id)}><span /></button></div>
            <div className="rule-footer"><span>{rule.enabled ? "Monitoring" : "Paused"}</span><button onClick={() => onTest(rule)}>Test action</button></div>
          </article>;
        })}
      </div>
      <button className="button primary wide" onClick={onAdd}><Icon name="plus" size={19} />New automation</button>
      <p className="small-print">Background monitoring uses a visible Android service so it remains reliable and transparent.</p>
    </div>
  );
}

function RuleBuilder({ devices, deviceId, trigger, radius, action, onDevice, onTrigger, onRadius, onAction, onBack, onSave }: {
  devices: Device[]; deviceId: string; trigger: Rule["trigger"]; radius: number; action: Rule["action"]; onDevice: (value: string) => void; onTrigger: (value: Rule["trigger"]) => void; onRadius: (value: number) => void; onAction: (value: Rule["action"]) => void; onBack: () => void; onSave: () => void;
}) {
  return (
    <div className="screen rule-builder">
      <div className="simple-topbar"><button className="icon-button" onClick={onBack} aria-label="Back"><Icon name="back" /></button><strong>New automation</strong><span>1 of 1</span></div>
      <div className="builder-content">
        <label className="field-label">Device<select value={deviceId} onChange={(event) => onDevice(event.target.value)}>{devices.filter((device) => device.saved).map((device) => <option key={device.id} value={device.id}>{device.name}</option>)}</select></label>
        <fieldset><legend>When it…</legend><div className="segmented"><button className={trigger === "enters" ? "active" : ""} onClick={() => onTrigger("enters")}>Enters range</button><button className={trigger === "leaves" ? "active" : ""} onClick={() => onTrigger("leaves")}>Leaves range</button></div></fieldset>
        <label className="radius-control"><span><strong>Range</strong><b>{radius} m</b></span><input type="range" min="1" max="20" value={radius} onChange={(event) => onRadius(Number(event.target.value))} /><small>Tracky uses hysteresis to avoid repeated triggers near the boundary.</small></label>
        <fieldset className="action-picker"><legend>Then…</legend>{(Object.keys(actionLabels) as Rule["action"][]).map((key) => <button className={action === key ? "active" : ""} key={key} onClick={() => onAction(key)}><span className="action-icon"><Icon name={key === "notification" ? "bell" : key === "wifi" ? "wifi" : "message"} /></span><span><strong>{actionLabels[key]}</strong><small>{key === "wifi" ? "Android requires confirmation" : key === "notification" ? "Works entirely on-device" : "Opens a pre-filled message"}</small></span>{action === key && <Icon name="check" />}</button>)}</fieldset>
        <div className="automation-summary"><Icon name="bolt" /><p>When <strong>{devices.find((device) => device.id === deviceId)?.name}</strong> {triggerLabels[trigger]} <strong>{radius} m</strong>, Tracky will <strong>{actionLabels[action].toLowerCase()}</strong>.</p></div>
      </div>
      <button className="button primary wide builder-save" onClick={onSave}>Save automation</button>
    </div>
  );
}

function SettingsScreen({ sound, haptics, onSound, onHaptics }: { sound: boolean; haptics: boolean; onSound: () => void; onHaptics: () => void }) {
  return (
    <div className="screen settings-screen">
      <ScreenHeader eyebrow="Tracky 0.2" title="Settings" />
      <section className="settings-group"><h3>Finding feedback</h3><SettingRow icon="sound" label="Proximity sound" detail="Speeds up as you get closer" active={sound} onClick={onSound} /><SettingRow icon="vibrate" label="Haptic feedback" detail="Short pulses while finding" active={haptics} onClick={onHaptics} /></section>
      <section className="settings-group"><h3>Scanning</h3><SettingRow icon="bluetooth" label="Background monitoring" detail="Visible notification while active" active /><SettingRow icon="radar" label="Distance display" detail="Metric · confidence-aware" /></section>
      <section className="privacy-card"><Icon name="shield" /><div><strong>Your data stays here</strong><p>Device names, sightings and automations remain on this phone. Tracky has no account and no tracking cloud.</p></div></section>
      <section className="capability-card"><span>Precision finding</span><strong>Used only when both phone and accessory support a compatible ranging protocol.</strong><p>Standard Bluetooth devices use signal strength and trend instead of a fake direction arrow.</p></section>
    </div>
  );
}

function SettingRow({ icon, label, detail, active, onClick }: { icon: string; label: string; detail: string; active?: boolean; onClick?: () => void }) {
  return <button className="setting-row" onClick={onClick}><span className="setting-icon"><Icon name={icon} /></span><span><strong>{label}</strong><small>{detail}</small></span>{onClick ? <span className={`switch ${active ? "on" : ""}`}><span /></span> : <Icon name="chevron" size={18} />}</button>;
}

function BottomNav({ tab, onTab }: { tab: Tab; onTab: (tab: Tab) => void }) {
  return <nav className="bottom-nav" aria-label="Main navigation"><button className={tab === "devices" ? "active" : ""} onClick={() => onTab("devices")}><Icon name="radar" /><span>Devices</span></button><button className={tab === "automations" ? "active" : ""} onClick={() => onTab("automations")}><Icon name="bolt" /><span>Automations</span></button><button className={tab === "settings" ? "active" : ""} onClick={() => onTab("settings")}><Icon name="settings" /><span>Settings</span></button></nav>;
}
