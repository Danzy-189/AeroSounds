#!/usr/bin/env python3
"""
Procedural generator for every sound shipped with Aero Sounds.

All samples are synthesized from physical descriptions instead of recordings:

* wind layers are spectrally shaped noise built in the frequency domain, which
  makes them mathematically seamless loops (no click at the wrap-around);
* the pass-by whoosh is a noise band whose centre frequency sweeps down while
  it passes, i.e. an audible Doppler shift baked into the sample;
* the sonic boom is a classic N-wave: two shock fronts separated by the time
  the aircraft needs to pass its own length, plus a rolling ground echo tail.

Run:  python3 tools/generate_sounds.py
Needs: numpy + ffmpeg (libvorbis). Output: src/main/resources/assets/aerosounds/sounds/*.ogg
"""

import os
import subprocess
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "aerosounds", "sounds")
rng = np.random.default_rng(20260919)


def loop_noise(seconds, shape_fn, seed=0):
    """Spectrally shaped noise that loops perfectly (built in the FFT domain)."""
    n = int(SR * seconds)
    local = np.random.default_rng(1234 + seed)
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    mag = shape_fn(np.maximum(freqs, 1e-6))
    phase = local.uniform(0, 2 * np.pi, len(freqs))
    spec = mag * np.exp(1j * phase)
    spec[0] = 0.0
    return np.fft.irfft(spec, n)


def band(f, low, high, slope_low=2.0, slope_high=2.0):
    """Smooth band-pass magnitude response."""
    hp = 1.0 / (1.0 + (low / f) ** slope_low)
    lp = 1.0 / (1.0 + (f / high) ** slope_high)
    return hp * lp


def one_pole_lp(x, cutoff):
    a = np.exp(-2.0 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def sweep_bandpass(x, centers, q=1.6):
    """Time varying resonant band-pass (state variable filter)."""
    y = np.empty_like(x)
    low = bandp = 0.0
    for i, sample in enumerate(x):
        f = 2.0 * np.sin(np.pi * min(centers[i], SR * 0.45) / SR)
        high = sample - low - (1.0 / q) * bandp
        bandp += f * high
        low += f * bandp
        y[i] = bandp
    return y


def normalize(x, peak=0.89):
    m = np.max(np.abs(x))
    return x if m < 1e-9 else x * (peak / m)


def fade(x, ms=12):
    n = int(SR * ms / 1000)
    if n * 2 >= len(x):
        return x
    env = np.ones(len(x))
    env[:n] = np.linspace(0, 1, n)
    env[-n:] = np.linspace(1, 0, n)
    return x * env


def write(name, data, seamless=False):
    data = normalize(data if seamless else fade(data))
    pcm = (np.clip(data, -1.0, 1.0) * 32767).astype("<i2")
    wav_path = os.path.join(OUT, name + ".wav")
    ogg_path = os.path.join(OUT, name + ".ogg")
    os.makedirs(OUT, exist_ok=True)
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)          # mono: Minecraft only pans mono sources
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path,
                    "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", ogg_path], check=True)
    os.remove(wav_path)
    print(f"  {name}.ogg  {len(data) / SR:.2f}s")


def wind_light():
    t = np.linspace(0, 4, int(SR * 4), endpoint=False)
    base = loop_noise(4, lambda f: band(f, 260, 2600, 1.6, 1.4), seed=1)
    air = loop_noise(4, lambda f: band(f, 1500, 6500, 2.0, 1.2), seed=2) * 0.25
    # slow gusting, itself periodic over the loop length
    gust = 0.78 + 0.22 * np.sin(2 * np.pi * t / 4.0) + 0.12 * np.sin(2 * np.pi * 3 * t / 4.0)
    return (base + air) * gust


def wind_heavy():
    t = np.linspace(0, 4, int(SR * 4), endpoint=False)
    body = loop_noise(4, lambda f: band(f, 45, 700, 2.4, 1.6), seed=3)
    growl = loop_noise(4, lambda f: band(f, 120, 320, 3.0, 3.0), seed=4) * 0.5
    hiss = loop_noise(4, lambda f: band(f, 900, 4200, 1.8, 1.2), seed=5) * 0.18
    beat = 0.8 + 0.2 * np.sin(2 * np.pi * 2 * t / 4.0)
    return (body + growl + hiss) * beat


def wind_cabin():
    t = np.linspace(0, 4, int(SR * 4), endpoint=False)
    hull = loop_noise(4, lambda f: band(f, 30, 420, 2.6, 1.8), seed=6)
    leak = loop_noise(4, lambda f: band(f, 800, 5200, 2.0, 1.1), seed=7) * 0.3
    buffet = 0.85 + 0.15 * np.sin(2 * np.pi * 5 * t / 4.0)
    return (hull + leak) * buffet


def whoosh_pass():
    dur = 1.7
    n = int(SR * dur)
    t = np.linspace(0, dur, n, endpoint=False)
    noise = rng.normal(0, 1, n)
    # Doppler: approach compresses the spectrum, recession stretches it.
    approach = 1.0 / (1.0 + np.exp((t - dur * 0.42) * 14.0))
    centre = 420 + 1500 * approach
    swoosh = sweep_bandpass(noise, centre, q=1.1)
    low = one_pole_lp(noise, 180) * 0.8
    # amplitude peaks at the closest point, decays as 1/r
    r = np.abs(t - dur * 0.45) * 26.0 + 2.2
    env = (2.2 / r) ** 1.05
    body = (swoosh * 1.0 + low * 0.55) * env
    return body


def transonic_rumble():
    t = np.linspace(0, 3, int(SR * 3), endpoint=False)
    rumble = loop_noise(3, lambda f: band(f, 28, 260, 2.8, 2.2), seed=8)
    shear = loop_noise(3, lambda f: band(f, 300, 2000, 1.8, 1.4), seed=9) * 0.22
    # shock-induced buffeting, ~11 Hz, phase-locked to the loop length
    buffet = 0.62 + 0.38 * np.sin(2 * np.pi * 33 * t / 3.0)
    return (rumble + shear) * buffet


def n_wave(n, shock_len, spacing, amp=1.0):
    """Ideal N-wave: sharp compression, linear decay, sharp rarefaction."""
    x = np.zeros(n)
    i0 = int(SR * 0.02)
    ramp = int(SR * spacing)
    tail = int(SR * shock_len)
    x[i0] = amp
    x[i0:i0 + ramp] = np.linspace(amp, -amp, ramp)
    x[i0 + ramp] = -amp
    decay = np.exp(-np.linspace(0, 6, tail))
    x[i0 + ramp:i0 + ramp + tail] += -amp * 0.35 * decay
    return x


def sonic_boom_near():
    dur = 2.6
    n = int(SR * dur)
    wave = n_wave(n, 0.35, 0.085, 1.0)
    crack = rng.normal(0, 1, n) * np.exp(-np.linspace(0, 60, n)) * 0.7
    tail_noise = rng.normal(0, 1, n)
    tail = one_pole_lp(tail_noise, 150) * np.exp(-np.linspace(0, 7, n)) * 2.4
    rumble = one_pole_lp(rng.normal(0, 1, n), 55) * np.exp(-np.linspace(0.6, 5, n)) * 3.0
    body = wave * 1.0 + crack + tail + rumble
    return one_pole_lp(body, 9000)


def sonic_boom_far():
    dur = 4.2
    n = int(SR * dur)
    wave = n_wave(n, 0.9, 0.13, 0.8)
    # distance turns the crack into a deep rolling thunder
    rolled = one_pole_lp(one_pole_lp(wave, 420), 260)
    ground_echo = np.zeros(n)
    for delay, gain in ((0.11, 0.55), (0.27, 0.32), (0.46, 0.18), (0.8, 0.09)):
        d = int(SR * delay)
        ground_echo[d:] += rolled[:n - d] * gain
    rumble = one_pole_lp(rng.normal(0, 1, n), 70) * np.exp(-np.linspace(0.3, 4.5, n)) * 2.6
    body = rolled + ground_echo + rumble
    return one_pole_lp(body, 900)


if __name__ == "__main__":
    print("generating Aero Sounds samples...")
    write("wind_light", wind_light(), seamless=True)
    write("wind_heavy", wind_heavy(), seamless=True)
    write("wind_cabin", wind_cabin(), seamless=True)
    write("transonic_rumble", transonic_rumble(), seamless=True)
    write("whoosh_pass", whoosh_pass())
    write("sonic_boom_near", sonic_boom_near())
    write("sonic_boom_far", sonic_boom_far())
    print("done")
